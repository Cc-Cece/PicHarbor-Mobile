package com.cccece.picharbor

import androidx.compose.foundation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LiveLinkScreen(vm:HarborViewModel,data:HarborUiData) {
    val focus=LocalFocusManager.current
    val link=vm.graph.computer
    val progress by link.progress.collectAsState()
    var host by rememberSaveable {mutableStateOf("")}
    var port by rememberSaveable {mutableStateOf("")}
    var fingerprint by rememberSaveable {mutableStateOf("")}
    var code by remember {mutableStateOf("")}
    var saved by remember {mutableStateOf(link.saved())}
    var manual by rememberSaveable{mutableStateOf(false)}
    var scopePicker by rememberSaveable{mutableStateOf(false)}
    var forget by rememberSaveable{mutableStateOf(false)}
    var all by rememberSaveable{mutableStateOf(data.settings.allFolders)}
    var folders by rememberSaveable{mutableStateOf(ArrayList(data.settings.folders))}
    val scopeSettings=data.settings.copy(allFolders=all,folders=folders.toSet())
    val assets=data.logicalMedia.filter{all||LivePhotoPairing.folders(it,data.media).any{key->key in folders}||scopeSettings.includesFolder(it)}
    val valid=runCatching{LinkWire.target(host,port,fingerprint,code)}.getOrNull()?.takeIf{target->target.code.isNotEmpty()||saved?.let{it.host==target.host&&it.port==target.port&&it.fingerprint.startsWith(target.fingerprint)}==true}
    LaunchedEffect(progress.busy){if(!progress.busy)saved=link.saved()}
    LaunchedEffect(vm.scanResults){
        if(vm.scanPurpose=="computer"&&vm.scanResults.isNotEmpty()){
            val target=vm.scanResults.firstNotNullOfOrNull{runCatching{LinkWire.qr(it)}.getOrNull()}
            if(target==null)vm.say("这不是连接电脑的二维码。")
            else {host=target.host;port=target.port.toString();fingerprint=target.fingerprint;code=target.code;manual=false}
            vm.clearScan()
        }
    }
    DisposableEffect(Unit){onDispose{link.stopSending()}}
    ProductPage("传输到电脑",vm) {
            saved?.let{target->ProductSection("已保存的电脑") {

                ProductValue("电脑","${target.host}:${target.port}",false)
                Row {
                    TextButton(onClick={host=target.host;port=target.port.toString();fingerprint=target.fingerprint;code="";manual=false},enabled=!progress.busy){LText("使用已保存的电脑")}
                    TextButton(onClick={forget=true},enabled=!progress.busy){LText("忘记这台电脑")}
                }
            }}
            ProductSection("连接电脑") {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                LText("打开电脑版的接收页面，让手机和电脑连接同一局域网。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={focus.clearFocus();vm.scanQr("computer",false)},enabled=!progress.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){LIcon(Icons.Outlined.QrCodeScanner,null);Spacer(Modifier.width(8.dp));LText("扫描电脑二维码")}
                OutlinedButton(onClick={focus.clearFocus();vm.scanQr("computer",true)},enabled=!progress.busy,modifier=Modifier.fillMaxWidth()){LIcon(Icons.Outlined.Image,null);Spacer(Modifier.width(8.dp));LText("从图片识别")}
            }
            if(valid!=null){ProductValue("目标电脑","${valid.host}:${valid.port}",false);Helper("连接信息已就绪，点击开始发送后等待电脑确认。")}
            SettingsRow("手动输入连接信息",enabled=!progress.busy,onClick={manual=!manual},trailing={LIcon(if(manual)Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,null)})
            Column(Modifier.animateContentSize()) {if(manual)Column(Modifier.padding(start=16.dp,end=16.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(host,{host=it},label={LText("地址")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,autoCorrectEnabled=false,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),enabled=!progress.busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(port,{port=it},label={LText("端口")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,autoCorrectEnabled=false,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),enabled=!progress.busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(fingerprint,{fingerprint=it},label={LText("证书前 16 位或完整指纹")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,autoCorrectEnabled=false,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),enabled=!progress.busy,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(code,{code=it},label={LText("配对码")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii,autoCorrectEnabled=false,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),enabled=!progress.busy,modifier=Modifier.fillMaxWidth())
            Helper("连接信息可在电脑版的接收页面查看。已配对的电脑无需再次填写配对码。")
            }}
            }
            ProductSection("发送范围") {
            ProductValue("将发送","${assets.size} 项")
            SettingsRow("选择照片与视频",if(all)"全部可访问照片与视频" else "已选择 ${folders.size} 个相册",enabled=!progress.busy,onClick={scopePicker=true},trailing={LIcon(Icons.Outlined.KeyboardArrowDown,null)})
            Helper("仅用于本次传输，不改变自动备份。无需 Google 账号。")
            if(assets.isEmpty())ProductAction("照片访问权限",Icons.Outlined.PhotoLibrary,!progress.busy){vm.navigate("permissions")}
            }
            if(progress.busy||progress.status.isNotBlank())ProductSection("传输状态") {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(progress.status.isNotBlank())LText(progress.status)
            if(progress.busy||progress.fraction>0)LinearProgressIndicator(progress={progress.fraction},modifier=Modifier.fillMaxWidth())
            if(progress.sent+progress.existing+progress.failed>0)LText("已发送 ${progress.sent} · 电脑已有 ${progress.existing} · 失败 ${progress.failed}")
            }}
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(progress.busy)OutlinedButton(onClick=link::stopSending,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){LText("停止发送")}
            else Button(onClick={focus.clearFocus();valid?.let{link.send(it,assets.map{row->row.id}.toSet())}},enabled=assets.isNotEmpty()&&valid!=null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){LIcon(Icons.Outlined.Send,null);Spacer(Modifier.width(8.dp));LText("开始发送")}
            if(valid==null&&!progress.busy)LText("请先扫码或填写连接信息。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Helper("仅在局域网内直接传输。发送前需电脑确认；请保持应用在前台，锁屏后传输停止。")
        }
    }
    ProductBottomSheet("发送范围",{scopePicker=false},visible=scopePicker,description="仅用于本次传输，不改变自动备份。无需 Google 账号。") {
        ToggleRow("全部可访问照片与视频","",all,enabled=!progress.busy){all=it;if(it)folders=arrayListOf()}
        data.media.distinctBy{it.folderKey}.sortedBy{it.folderName}.forEach{folder->
            val checked=!all&&(folder.folderKey in folders||"@camera" in folders&&folder.folderName.equals("Camera",true))
            ToggleRow(folder.folderName,"",checked,enabled=!progress.busy){selected->
                val previous=if(all)emptyList() else folders.toList()
                val normalized=if(folder.folderName.equals("Camera",true))previous-"@camera" else previous
                folders=ArrayList(if(selected)normalized+folder.folderKey else normalized-folder.folderKey);all=false
            }
        }
        val dismiss=sheetDismissAction()
        Button(onClick={dismiss{}},modifier=Modifier.fillMaxWidth()){LText("完成")}
    }
    if(forget)AlertDialog(onDismissRequest={forget=false},title={LText("忘记这台电脑？")},text={LText("下次连接需要重新扫码配对，已发送的文件会保留。")},confirmButton={TextButton(onClick={val old=saved;link.forget();saved=null;if(old!=null&&host==old.host&&port==old.port.toString()){host="";port="";fingerprint="";code=""};forget=false}){LText("忘记这台电脑")}},dismissButton={TextButton(onClick={forget=false}){LText("取消")}})
}
