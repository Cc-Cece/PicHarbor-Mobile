package com.cccece.picharbor

/** A Room projection over the entire pending queue, independent of the activity list limit. */
data class UploadWorkloadEntry(val id:String,val bytes:Long,val state:String,val remoteKey:String,val account:String="",val multiResource:Boolean=false)
data class BackupTransferSummary(val remainingBytes:Long,val unknownSizeCount:Int) {
    val uploadFinished get()=remainingBytes==0L&&unknownSizeCount==0
}

fun backupTransferSummary(work:List<UploadWorkloadEntry>,transfers:Map<String,TransferProgress>):BackupTransferSummary {
    var remaining=0L;var unknown=0
    for(item in work) {
        // Committed content and confirmation/album work never require another byte upload.
        if(item.remoteKey.isNotBlank()||item.state in setOf("finalizing","finalizing_album","waiting_confirmation"))continue
        // A paired task emits per-resource progress. Never mislabel one part as the whole pair.
        if(item.multiResource){unknown++;continue}
        val transfer=transfers[item.id]?.takeIf{it.phase=="uploading"&&it.total>0}
        val total=transfer?.total?:item.bytes
        if(total<=0){unknown++;continue}
        val left=total-(transfer?.bytes?:0L).coerceIn(0,total)
        remaining=if(Long.MAX_VALUE-remaining<left)Long.MAX_VALUE else remaining+left
    }
    return BackupTransferSummary(remaining,unknown)
}

/** One monotonic moving byte window across parallel/sequential uploads, as on iOS. */
internal class UploadRateTracker {
    private data class Sample(val sent:Long,val total:Long,val time:Long)
    private data class Event(val time:Long,val cumulative:Double)
    private val streams=mutableMapOf<String,Sample>()
    private val events=ArrayDeque<Event>()
    private var bytes=0.0
    private var lastData=Long.MIN_VALUE
    @Synchronized fun record(progress:TransferProgress,now:Long=System.nanoTime()) {
        if(progress.phase!="uploading"){streams.remove(progress.item);return}
        if(progress.total<=0||progress.bytes<0)return
        val previous=streams[progress.item]
        if(previous!=null&&now<previous.time)return
        streams[progress.item]=Sample(progress.bytes.coerceAtMost(progress.total),progress.total,now)
        if(events.lastOrNull()?.let{now-it.time>8_000_000_000}==true){events.clear();lastData=Long.MIN_VALUE}
        if(events.isEmpty())events.add(Event(now,bytes))
        if(previous==null||previous.total!=progress.total||progress.bytes<previous.sent||now-previous.time>8_000_000_000)return
        val delta=progress.bytes.coerceAtMost(progress.total)-previous.sent
        if(delta<=0)return
        bytes+=delta.toDouble();lastData=now;events.add(Event(now,bytes))
        while(events.size>2&&events.elementAt(1).time<=now-8_000_000_000)events.removeFirst()
        while(events.size>512)events.removeFirst()
    }
    @Synchronized fun finish(id:String){streams.remove(id)}
    @Synchronized fun clear(){streams.clear();events.clear();bytes=0.0;lastData=Long.MIN_VALUE}
    @Synchronized fun speed(now:Long,active:Set<String>):Double? {
        if(active.isEmpty())return 0.0
        val first=events.firstOrNull()?:return null
        if(now<first.time||now-first.time<1_000_000_000)return null
        if(lastData==Long.MIN_VALUE||now-lastData>3_000_000_000)return 0.0
        val anchor=events.lastOrNull{it.time<=now-8_000_000_000}?:first
        val elapsed=(now-anchor.time)/1_000_000_000.0
        return if(elapsed>0)((bytes-anchor.cumulative)/elapsed).coerceAtLeast(0.0) else null
    }
}
