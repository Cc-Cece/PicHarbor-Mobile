package com.cccece.picharbor

import org.json.JSONArray
import org.json.JSONObject

internal fun liveWarning(plan:String):String=runCatching{JSONObject(plan).optString("warning")}.getOrDefault("")
fun itemPhaseLabel(item:BackupItem)=if(item.state in setOf("done","exists")&&liveWarning(item.livePlan).isNotBlank())"已保存 · 动态部分未确认" else phaseLabel(item.state)
fun livePlanLabel(plan:String)=when(runCatching{JSONObject(plan).optString("kind")}.getOrDefault("")) {
    "apple_pair"->"自动 · 照片与视频配对"
    "motion_photo"->"自动 · 动态照片原文件上传"
    "photo","video","raw_resources"->"自动 · 原文件上传"
    else->"自动识别中"
}

internal object AutomaticLivePhoto {
    fun observations(media:MediaEntry,all:List<MediaEntry>,motionHint:(MediaEntry)->Boolean):JSONObject {
        val partner=if(media.video)LivePhotoPairing.findPhotoPartner(media,all) else LivePhotoPairing.findPair(media,all)
        val members=listOfNotNull(media,partner)
        val repeated=media.pairingID.isNotBlank()&&media.pairingID!=LivePhotoPairing.EMBEDDED_MOTION&&partner==null&&all.any{it.id!=media.id&&it.video!=media.video&&it.pairingID==media.pairingID}
        return JSONObject().put("strategy","automatic").put("ambiguous",repeated).put("resources",JSONArray(members.map { row->
            JSONObject().put("id",row.id).put("video",row.video).put("size",row.size).put("modified",row.modified)
                .put("identifier",row.pairingID.takeUnless{it==LivePhotoPairing.EMBEDDED_MOTION}.orEmpty())
                .put("embedded_motion",row.pairingID==LivePhotoPairing.EMBEDDED_MOTION).put("motion_hint",motionHint(row))
        }))
    }
    fun validate(plan:JSONObject,all:List<MediaEntry>) {
        if(plan.optInt("version")!=1||plan.optString("requested")!="automatic")throw EngineFailure("INVALID_LIVE_PLAN")
        val sources=plan.optJSONArray("sources")?:throw EngineFailure("INVALID_LIVE_PLAN")
        if(sources.length() !in 1..16)throw EngineFailure("INVALID_LIVE_PLAN")
        for(index in 0 until sources.length()) {
            val source=sources.getJSONObject(index)
            val media=all.firstOrNull{it.id==source.getString("id")}?:throw EngineFailure("FILE_ACCESS")
            if(media.size!=source.getLong("size")||media.modified!=source.getLong("modified"))throw EngineFailure("SOURCE_CHANGED")
        }
    }
    fun group(plan:JSONObject,all:List<MediaEntry>):LivePhotoPairing.LivePhotoGroup? {
        if(plan.optString("kind")!="apple_pair")return null
        val pairs=plan.getJSONArray("pairs")
        if(pairs.length()!=1)throw EngineFailure("INVALID_LIVE_PLAN")
        val pair=pairs.getJSONObject(0)
        val photo=all.firstOrNull{it.id==pair.getString("photo")}?:throw EngineFailure("FILE_ACCESS")
        val video=all.firstOrNull{it.id==pair.getString("video")}?:throw EngineFailure("FILE_ACCESS")
        return LivePhotoPairing.LivePhotoGroup(photo,video,false,plan.getString("strategy"))
    }
}
