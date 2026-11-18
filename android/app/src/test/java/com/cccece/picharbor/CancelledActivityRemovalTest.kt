package com.cccece.picharbor

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class CancelledActivityRemovalTest {
    private fun item(state:String,hidden:Boolean=false)=BackupItem(state,"batch","account","photo-$state","photo.jpg",123,
        state=state,remoteKey="receipt",modified=42,hidden=hidden,error="original-error")

    @Test fun cancelledBatchRemainsUnfinishedAndRemovableWithoutBecomingPausable() {
        val cancelled=listOf(item("cancelled"),item("cancelled").copy(id="second"))
        assertEquals(2,cancelled.count{ActivityItemPolicy.matchesStatus(it,"unfinished")})
        assertEquals(2,cancelled.count(ActivityItemPolicy::canRemove))
        assertFalse(cancelled.any(ActivityItemPolicy::canPause))
        assertFalse(cancelled.any{ActivityItemPolicy.matchesStatus(it,"completed")})
        val mixed=cancelled+item("done")+item("exists")+item("failed")+item("paused")+item("queued")+item("cancelled",hidden=true)
        assertEquals(listOf("cancelled","second","failed","paused","queued"),mixed.filter(ActivityItemPolicy::canRemove).map{it.id})
    }

    @Test fun hidingCancelledRecordRetainsBackupEvidenceSuppressionAndCoreState():Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),HarborDatabase::class.java).build()
        try {
            val dao=db.dao()
            val cancelled=item("cancelled")
            val record=BackupRecord("account",cancelled.mediaId,"sha1","sha256","cloud",123,42,"original","manual",100)
            val suppression=SuppressedAsset("account",cancelled.mediaId,42)
            val core=CoreTaskSnapshot(cancelled.id,"{\"state\":\"cancelled\"}",5)
            dao.putItem(cancelled);dao.putBackup(record);dao.suppress(suppression);dao.putCoreTask(core)
            assertEquals(0,dao.hideCancelledItem(cancelled.id,"other-account"))
            assertEquals(cancelled,dao.item(cancelled.id))
            dao.hideCancelledItem(cancelled.id,"account")
            dao.hideCancelledItem(cancelled.id,"account")
            assertEquals(cancelled.copy(hidden=true),dao.item(cancelled.id))
            assertFalse(ActivityItemPolicy.canRemove(dao.item(cancelled.id)!!))
            assertEquals(record,dao.backup("account",cancelled.mediaId))
            assertEquals(1,dao.suppressed("account",cancelled.mediaId,42))
            assertEquals(core,dao.coreTask(cancelled.id))
        } finally {db.close()}
    }

    @Test fun cancelledHideDoesNotRemoveCompletedOrResumedRecords():Unit=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),HarborDatabase::class.java).build()
        try {
            val dao=db.dao()
            for(state in listOf("done","exists","queued","uploading")) {
                val current=item(state);dao.putItem(current)
                val persisted=dao.item(current.id)
                assertEquals(0,dao.hideCancelledItem(current.id,"account"))
                assertEquals(persisted,dao.item(current.id))
            }
        } finally {db.close()}
    }
}
