package com.cccece.picharbor

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[29])
class AutomaticLivePhotoTest {
    private fun resource(id:String,video:Boolean=false,folder:String="camera",pair:String="pair")=
        MediaEntry(id,"same.jpg",if(video)"video/mp4" else "image/jpeg",100,42,42,folder,folder,pairingID=pair)

    @Test fun automaticDefaultDoesNotChangeLegacyJobStrategy() {
        assertEquals("automatic",HarborSettings().livePhotoPairing)
        assertEquals("original",BackupJob("job","a",0,false).liveStrategy)
    }
    @Test fun migratedPairsUseIdentifiersAndRejectAmbiguousCopies() {
        val photo=resource("photo")
        val video=resource("video",true,"imported")
        assertEquals(video,LivePhotoPairing.findPair(photo,listOf(video)))
        assertNull(LivePhotoPairing.findPair(photo,listOf(photo,video,video.copy(id="duplicate"))))
        assertNull(LivePhotoPairing.findPair(photo.copy(pairingID=""),listOf(video.copy(pairingID=""))))
        assertNull(LivePhotoPairing.findPair(photo,listOf(photo,photo.copy(id="copy"),video)))
    }
    @Test fun frozenSourceValidationDetectsChanges() {
        val photo=resource("photo")
        val plan=JSONObject("""{"version":1,"requested":"automatic","sources":[{"id":"photo","size":100,"modified":42}]}""")
        AutomaticLivePhoto.validate(plan,listOf(photo))
        try { AutomaticLivePhoto.validate(plan,listOf(photo.copy(size=101)));fail("changed source accepted") }
        catch(error:EngineFailure){assertEquals("SOURCE_CHANGED",error.code)}
    }
    @Test fun planCannotBeOverwrittenOrResurrectCancelledTask():Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),HarborDatabase::class.java).build()
        try {
            val dao=db.dao();val item=BackupItem("i","j","a","p","p.jpg",100,state="cancelled")
            dao.putItem(item)
            assertEquals(0,dao.freezeLivePlan("i","other","wrong"))
            assertEquals(1,dao.freezeLivePlan("i","a","first"))
            assertEquals(0,dao.freezeLivePlan("i","a","second"))
            assertEquals(item.copy(livePlan="first"),dao.item("i"))
        } finally {db.close()}
    }
    @Test fun migrationPreservesLegacyTasksAndCloudEvidence():Unit=runBlocking {
        val context=RuntimeEnvironment.getApplication()
        val name="automatic-live-migration-test";context.deleteDatabase(name)
        val schema=JSONObject(File("schemas/com.cccece.picharbor.HarborDatabase/9.json").readText()).getJSONObject("database")
        context.openOrCreateDatabase(name,0,null).use { old->
            val entities=schema.getJSONArray("entities")
            for(index in 0 until entities.length()) {
                val entity=entities.getJSONObject(index);val table=entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                val indices=entity.optJSONArray("indices")?:org.json.JSONArray()
                for(i in 0 until indices.length())old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}",table))
                if(table in setOf("items","jobs","backups","core_tasks")) {
                    val values=android.content.ContentValues();val fields=entity.getJSONArray("fields")
                    for(i in 0 until fields.length()) {
                        val field=fields.getJSONObject(i);val key=field.getString("columnName")
                        if(field.getString("affinity")=="TEXT")values.put(key,"") else values.put(key,0L)
                    }
                    values.put("account","a")
                    if(table=="core_tasks")values.remove("account")
                    if(table!="backups")values.put("id",table)
                    when(table) {
                        "jobs"->{values.put("liveStrategy","stepwise");values.put("state","paused")}
                        "items"->{values.put("state","cancelled");values.put("remoteKey","cloud");values.put("jobId","jobs")}
                        "backups"->values.put("remoteKey","cloud")
                        "core_tasks"->values.put("payload","{\"state\":\"cancelled\"}")
                    }
                    assertTrue(old.insertOrThrow(table,null,values)>=0)
                }
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length())old.execSQL(setup.getString(i))
            old.version=9
        }
        val db=Room.databaseBuilder(context,HarborDatabase::class.java,name).addMigrations(HarborDatabase.MIGRATION_9_10).build()
        try {
            assertEquals("stepwise",db.dao().job("jobs")?.liveStrategy)
            val item=db.dao().item("items")!!
            assertEquals("cancelled",item.state);assertEquals("cloud",item.remoteKey);assertEquals("",item.livePlan)
            assertTrue(db.dao().coreTask("core_tasks")!!.payload.contains("cancelled"))
            db.openHelper.readableDatabase.query("SELECT remoteKey FROM backups").use{assertTrue(it.moveToFirst());assertEquals("cloud",it.getString(0))}
        } finally {db.close();context.deleteDatabase(name)}
    }
}
