package com.cccece.picharbor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun AutomaticBackupIndicator(enabled:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.heightIn(min=48.dp).semantics(mergeDescendants=true){}.padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            LIcon(if(enabled)Icons.Outlined.CheckCircle else Icons.Outlined.PauseCircle,null,Modifier.size(18.dp),if(enabled)Color(0xFF2E9D5C) else MaterialTheme.colorScheme.onSurfaceVariant)
            LText("自动备份",style=MaterialTheme.typography.labelMedium)
            LText(if(enabled)"已开启" else "未开启",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BackupTransferMetrics(vm:HarborViewModel,work:List<UploadWorkloadEntry>) {
    val transfers by vm.graph.backup.inFlight.collectAsState()
    var now by remember{mutableLongStateOf(System.nanoTime())}
    LaunchedEffect(vm){while(isActive){now=System.nanoTime();delay(1000)}}
    val visible=remember(work,transfers){val ids=work.map{it.id}.toSet();transfers.filterKeys{it in ids}}
    val summary=remember(work,visible){backupTransferSummary(work,visible)}
    val uploading=visible.filterValues{it.phase=="uploading"}.keys
    val speed=vm.graph.backup.transferSpeed(now,uploading)
    val speedText=when {
        speed==null->localize("计算中…")
        speed<=0->"0 KB/s"
        else->formatSize(speed.coerceAtMost(Long.MAX_VALUE.toDouble()/2).toLong())+"/s"
    }
    val remaining=when {
        summary.remainingBytes==0L&&summary.unknownSizeCount>0->localize("计算中…")
        summary.unknownSizeCount>0->localize("至少 ${formatSize(summary.remainingBytes)}")
        summary.remainingBytes==0L->"0 KB"
        else->formatSize(summary.remainingBytes)
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            TransferMetric("当前速度",speedText,Modifier.weight(1f))
            TransferMetric("剩余大小",remaining,Modifier.weight(1f))
        }
        LText(if(summary.unknownSizeCount>0)"另有 ${summary.unknownSizeCount} 项待计算大小。" else if(summary.uploadFinished)"等待确认保存" else "仅统计尚未上传的内容。",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TransferMetric(title:String,value:String,modifier:Modifier) {
    Column(modifier.semantics(mergeDescendants=true){},verticalArrangement=Arrangement.spacedBy(4.dp)) {
        LText(title,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LText(value,style=MaterialTheme.typography.titleMedium,localize=false)
    }
}
