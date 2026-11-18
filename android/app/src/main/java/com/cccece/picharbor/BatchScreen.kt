package com.cccece.picharbor

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun JobDetailsScreen(vm:HarborViewModel,data:HarborUiData) {
    val id by vm.inspectedJob.collectAsState()
    val details by vm.jobDetails.collectAsState()
    val media=remember(data.media){data.media.associateBy{it.id}}
    val all=details.filter{it.account==data.settings.account&&it.jobId==id&&!it.hidden}
    val rows=all.filter{(it.archivedAt>0)==vm.batchArchived&&ActivityItemPolicy.matchesStatus(it,vm.batchStatus)}
    val pending=rows.filter(ActivityItemPolicy::canPause)
    val removable=rows.filter(ActivityItemPolicy::canRemove)
    var menu by remember{mutableStateOf(false)}
    var remove by remember{mutableStateOf(false)}
    var revealed by remember(id,data.settings.account,vm.batchArchived,vm.batchStatus){mutableStateOf<String?>(null)}
    var rowRemoval by remember(id,data.settings.account){mutableStateOf<String?>(null)}
    BackHandler(revealed!=null){revealed=null}
    Column(Modifier.fillMaxSize()) {
        PageTitle("备份批次",vm,action={
            Box {
                IconButton(onClick={menu=true}){LIcon(Icons.Outlined.MoreHoriz,"活动选项")}
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={LText("暂停任务")},enabled=pending.isNotEmpty(),onClick={menu=false;vm.itemActions(pending.map{it.id},"pause")})
                    DropdownMenuItem(text={LText("继续暂停任务")},enabled=rows.any{it.state=="paused"},onClick={menu=false;vm.itemActions(rows.filter{it.state=="paused"}.map{it.id},"resume")})
                    DropdownMenuItem(text={LText("重试异常任务")},enabled=rows.any{it.state in setOf("failed","album_pending","waiting_confirmation")},onClick={menu=false;vm.itemActions(rows.filter{it.state in setOf("failed","album_pending","waiting_confirmation")}.map{it.id},"resume")})
                    DropdownMenuItem(text={LText(if(vm.batchArchived)"取消归档已完成" else "归档已完成")},enabled=rows.any{it.state in setOf("done","exists")},onClick={menu=false;vm.archiveSelected(rows.filter{it.state in setOf("done","exists")}.map{it.id},vm.batchArchived)})
                    DropdownMenuItem(text={LText("停止并移除未完成")},enabled=removable.isNotEmpty(),onClick={menu=false;remove=true})
                }
            }
        })
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            item {
                ProductSection {
                    ProductValue("备份批次","${all.size} 项")
                    ProductValue("已完成","${all.count{it.state in setOf("done","exists")}} 项")
                    if(rows.size!=all.size)ProductValue("当前筛选","${rows.size} 项")
                }
                Spacer(Modifier.height(16.dp))
            }
            if(rows.isEmpty())item{EmptyState("本批暂无符合筛选的任务","返回活动页可查看其他状态。")}
            items(rows.take(vm.detailLimit),key={it.id}){item->
                val completed=item.state in setOf("done","exists")
                val label=if(completed)if(item.archivedAt>0)"取消归档" else "归档" else "移除"
                ActivitySwipeRow(item.id,label,!completed,!vm.busy,revealed,{revealed=it},{
                    if(completed)vm.archiveItem(item.id,item.archivedAt>0) else rowRemoval=item.id
                }) {
                    MediaStatusRow(item.name,"${formatSize(item.bytes)} · ${localize(phaseLabel(item.state))}",media[item.mediaId]){if(revealed==item.id)revealed=null else vm.showItem(item)}
                }
            }
            if(rows.size>vm.detailLimit)item{TextButton(onClick={vm.detailLimit+=200}){LText("显示更多")}}
        }
    }
    if(remove)AlertDialog(onDismissRequest={remove=false},title={LText("移除未完成任务？")},text={LText("保留本机及云端照片，同一版本仍可手动重新备份。")},confirmButton={TextButton(onClick={remove=false;vm.itemActions(removable.map{it.id},"remove")}){LText("停止并移除")}},dismissButton={TextButton(onClick={remove=false}){LText("取消")}})
    if(rowRemoval!=null)AlertDialog(onDismissRequest={rowRemoval=null},title={LText("移除此任务？")},text={LText("停止并从活动中移除，保留本机及云端照片。同一版本仍可手动重新备份。")},
        confirmButton={TextButton(enabled=!vm.busy,onClick={val item=removable.firstOrNull{it.id==rowRemoval};rowRemoval=null;item?.let{vm.itemAction(it.id,"remove")}},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){LText("停止并移除")}},
        dismissButton={TextButton(onClick={rowRemoval=null}){LText("取消")}})
}
