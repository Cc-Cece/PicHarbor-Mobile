package com.cccece.picharbor

/** A cancelled backup is stopped, but remains unfinished until removed from activity. */
object ActivityItemPolicy {
    fun completed(item:BackupItem)=item.state in setOf("done","exists")
    fun canRemove(item:BackupItem)=!item.hidden&&!completed(item)
    fun canPause(item:BackupItem)=canRemove(item)&&item.state!="cancelled"
    fun matchesStatus(item:BackupItem,status:String)=status=="all"||completed(item)==(status=="completed")
}
