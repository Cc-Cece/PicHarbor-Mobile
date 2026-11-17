package com.cccece.picharbor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun TaskDetailScreen(vm:HarborViewModel,data:HarborUiData) {
    val item=data.items.firstOrNull{it.id==vm.inspectedItem&&!it.hidden}
    val job=data.jobs.firstOrNull{it.id==item?.jobId}
    var remove by remember{mutableStateOf(false)}
    var cancel by remember{mutableStateOf(false)}
    var advanced by remember{mutableStateOf(false)}
    var filename by remember{mutableStateOf(false)}
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    ProductPage("任务详情",vm) {
        if(item==null)EmptyState("任务已不可用","返回活动页查看其他任务。",Icons.Outlined.History)
        else {
            ProductSection {
                val media=data.media.firstOrNull{it.id==item.mediaId}
                if(media!=null)TextButton(onClick={vm.open(media)}) {
                    AsyncImage(media.id,null,Modifier.size(56.dp));Spacer(Modifier.width(12.dp));LText("预览照片")
                }
                ProductAction(item.name,onClick={filename=true})
                ProductValue("备份状态",phaseLabel(item.state))
                ProductValue("确认方式",if(job?.strictConfirmation==true)"保存后继续等待动态处理" else "云端保存确认后完成")
            }
            ProductSection("备份流程") {
                val completed=item.state in setOf("done","exists")
                val index=when(item.state){"queued","hashing"->0;"checking","cloud_check"->1;"uploading"->2;"finalizing","finalizing_album","album_pending"->3;"waiting_confirmation","done","exists"->4;else->-1}
                listOf("准备照片","核对云端内容","上传文件","保存与配对","云端确认").forEachIndexed { step,label ->
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        LIcon(if(completed||step<index)Icons.Outlined.CheckCircle else if(step==index)Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,null,tint=if(completed||step<=index)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        LText(label,modifier=Modifier.weight(1f))
                        if(step==index&&!completed)LText("当前阶段",style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            ProductSection {SettingsRow("任务连接策略",onClick={vm.navigate("task-proxy")})}
            ProductSection(if(item.state in setOf("failed","album_pending"))"需要处理" else "当前状态") {
                LText(if(item.error.isNotBlank())Messages.error(item.error) else phaseLabel(item.state),modifier=Modifier.padding(16.dp))
                if(data.transfer.item==item.id)LinearProgressIndicator(progress={if(data.transfer.total>0)(data.transfer.bytes.toFloat()/data.transfer.total).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth().padding(16.dp))
                if(item.state in setOf("done","exists"))SettingsRow("前往 Google 相册释放空间",onClick={vm.navigate("free-space")})
            }
            ProductSection {
                if(item.state in setOf("done","exists"))ProductAction(if(item.archivedAt>0)"取消归档" else "归档",Icons.Outlined.Archive,!vm.busy){vm.archiveItem(item.id,item.archivedAt>0)}
                else if(item.state!="cancelled") {
                    if(item.state=="waiting_confirmation")ProductAction("立即检查保存结果",Icons.Outlined.Refresh,!vm.busy){vm.itemAction(item.id,"resume")}
                    else if(item.state in setOf("paused","failed"))ProductAction(if(item.state=="paused")"继续" else "重试",Icons.Outlined.PlayArrow,!vm.busy){vm.itemAction(item.id,"resume")}
                    else ProductAction("暂停",Icons.Outlined.Pause,!vm.busy){vm.itemAction(item.id,"pause")}
                    ProductAction("取消任务",enabled=!vm.busy){cancel=true}
                    ProductAction("移除未完成任务",enabled=!vm.busy){remove=true}
                }
            }
            ProductSection {ProductAction("高级信息",Icons.Outlined.Info){advanced=true}}
        }
    }
    if(advanced&&item!=null)AlertDialog(onDismissRequest={advanced=false},title={LText("高级信息")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        ProductValue("任务标识",item.id,false);ProductValue("文件大小",formatSize(item.bytes));ProductValue("尝试次数",item.attempt.toString())
        if(job!=null){ProductValue("来源",if(job.automatic)"自动" else "手动");ProductValue("上传画质",qualityName(job.quality));ProductValue("Live Photo",livePhotoLabel(job.liveStrategy))}
    }},confirmButton={TextButton(onClick={advanced=false}){LText("完成")}})
    if(filename&&item!=null)AlertDialog(onDismissRequest={filename=false},title={LText("完整文件名")},text={androidx.compose.foundation.text.selection.SelectionContainer{LText(item.name,localize=false)}},confirmButton={TextButton(onClick={filename=false}){LText("完成")}},dismissButton={TextButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(item.name))}){LText("复制文件名")}})
    if((remove||cancel)&&item!=null)AlertDialog(onDismissRequest={remove=false;cancel=false},title={LText(if(remove)"移除此任务？" else "取消任务？")},text={LText("停止处理并保留本机及云端照片。同一版本仍可手动重新备份。")},confirmButton={TextButton(onClick={vm.itemAction(item.id,if(remove)"remove" else "cancel");remove=false;cancel=false}){LText("确认")}},dismissButton={TextButton(onClick={remove=false;cancel=false}){LText("取消")}})
}
