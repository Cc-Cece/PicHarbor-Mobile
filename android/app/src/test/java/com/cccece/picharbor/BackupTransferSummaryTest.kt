package com.cccece.picharbor

import org.junit.Assert.*
import org.junit.Test

class BackupTransferSummaryTest {
    private fun item(id:String,bytes:Long,state:String="queued",key:String="")=UploadWorkloadEntry(id,bytes,state,key)
    private fun sample(id:String,sent:Long,total:Long=1000,phase:String="uploading")=TransferProgress(item=id,phase=phase,bytes=sent,total=total)
    @Test fun remainingCountsTheQueueAndAllParallelUploadsWithoutConfirmationBytes() {
        val work=listOf(item("a",1000,"uploading"),item("b",2000,"uploading"),item("c",3000),item("d",4000,"finalizing"),item("e",5000,key="committed"))
        val summary=backupTransferSummary(work,mapOf("a" to sample("a",300),"b" to sample("b",500,2000)))
        assertEquals(5200L,summary.remainingBytes)
        assertEquals(0,summary.unknownSizeCount)
    }
    @Test fun unknownAndPairedSizesAreExplicitAndKnownProgressIsClamped() {
        val work=listOf(item("unknown",0),item("pair",1000).copy(multiResource=true),item("a",1000,"uploading"))
        val summary=backupTransferSummary(work,mapOf("a" to sample("a",1200)))
        assertEquals(0L,summary.remainingBytes)
        assertEquals(2,summary.unknownSizeCount)
        assertFalse(summary.uploadFinished)
    }
    @Test fun confirmedItemsHaveNoRemainingUploadAndSizesCannotOverflow() {
        assertTrue(backupTransferSummary(listOf(item("a",1,"waiting_confirmation")),emptyMap()).uploadFinished)
        assertEquals(Long.MAX_VALUE,backupTransferSummary(listOf(item("a",Long.MAX_VALUE),item("b",Long.MAX_VALUE)),emptyMap()).remainingBytes)
    }
    @Test fun parallelRateUsesOneSharedWindowAndExpiresWhenStalled() {
        val tracker=UploadRateTracker()
        tracker.record(sample("a",0),0);tracker.record(sample("b",0),0)
        assertNull(tracker.speed(500_000_000,setOf("a","b")))
        tracker.record(sample("a",100),1_000_000_000);tracker.record(sample("b",200),1_000_000_000)
        assertEquals(300.0,tracker.speed(1_000_000_000,setOf("a","b"))!!,0.001)
        assertEquals(0.0,tracker.speed(5_000_000_000,setOf("a","b"))!!,0.0)
        assertEquals(0.0,tracker.speed(1_000_000_000,emptySet())!!,0.0)
    }
    @Test fun sequentialFilesDoNotAddTheirIndependentRatesAndResetsDoNotCreateBytes() {
        val tracker=UploadRateTracker()
        tracker.record(sample("a",0),0);tracker.record(sample("a",100),1_000_000_000);tracker.finish("a")
        tracker.record(sample("b",0),1_000_000_000);tracker.record(sample("b",100),2_000_000_000)
        assertEquals(100.0,tracker.speed(2_000_000_000,setOf("b"))!!,0.001)
        tracker.record(sample("b",0),2_100_000_000)
        assertEquals(100.0,tracker.speed(2_000_000_000,setOf("b"))!!,0.001)
        tracker.clear();assertNull(tracker.speed(3_000_000_000,setOf("b")))
    }
}
