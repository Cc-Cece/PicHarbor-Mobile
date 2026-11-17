@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.cccece.picharbor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Locale-specific contexts are not Activities; external intents need a new task. */
fun android.content.Context.openProductIntent(intent:Intent) {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun rememberFolderCounts(data:HarborUiData):Map<String,Int> = remember(data.logicalMedia,data.media) {
    buildMap {data.logicalMedia.forEach{asset->LivePhotoPairing.folders(asset,data.media).forEach{folder->put(folder,(get(folder)?:0)+1)}}}
}

@Composable
fun ProductPage(title:String,vm:HarborViewModel,root:Boolean=false,actions:@Composable RowScope.()->Unit={},content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(title,vm,root,actions)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            content();Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ProductSection(title:String="",footer:String="",content:@Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(title.isNotEmpty())LText(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=12.dp))
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth(),content=content)
        }
        if(footer.isNotEmpty())LText(footer,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=12.dp))
    }
}

@Composable
fun ProductValue(label:String,value:String,localized:Boolean=true) {
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        LText(label,modifier=Modifier.weight(1f));Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f),contentAlignment=Alignment.CenterEnd) {
            LText(value,localize=localized,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProductAction(label:String,icon:ImageVector?=null,enabled:Boolean=true,onClick:()->Unit) {
    TextButton(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp)) {
        if(icon!=null){LIcon(icon,null);Spacer(Modifier.width(12.dp))}
        LText(label,modifier=Modifier.weight(1f))
    }
}

@Composable
fun ProductPicker(label:String,value:String,options:List<Pair<String,String>>,enabled:Boolean=true,onSelect:(String)->Unit) {
    var open by remember{mutableStateOf(false)}
    Box {
        SettingsRow(label,options.firstOrNull{it.first==value}?.second?:value,enabled=enabled,onClick={open=true})
        DropdownMenu(open,{open=false}) {options.forEach{(id,text)->DropdownMenuItem(text={LText(text)},onClick={open=false;onSelect(id)},leadingIcon=if(id==value){{LIcon(Icons.Outlined.Check,null)}}else null)}}
    }
}

@Composable
fun ProductSegments(value:String,choices:List<Pair<String,String>>,onSelect:(String)->Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
        choices.forEachIndexed { index,(id,label) ->
            SegmentedButton(selected=value==id,onClick={onSelect(id)},shape=SegmentedButtonDefaults.itemShape(index,choices.size)){LText(label)}
        }
    }
}

@Composable
fun PhotosScreen(vm:HarborViewModel,data:HarborUiData,onPermissions:()->Unit,onPickFiles:()->Unit) {
    var mediaMenu by remember{mutableStateOf(false)}
    var albums by remember{mutableStateOf(false)}
    var confirm by rememberSaveable{mutableStateOf(false)}
    val folderCounts=rememberFolderCounts(data)
    val media=remember(data.logicalMedia,data.backupMap,data.itemMap,vm.filter,vm.type,vm.libraryAlbum,data.settings.account,data.transfer.item){data.logicalMedia.filter { m ->
        val backed=data.backed(m)
        (vm.filter=="all"||vm.filter=="uploaded"&&backed||vm.filter=="pending"&&!backed)&&
        (vm.type=="all"||vm.type=="video"&&m.video||vm.type=="photo"&&!m.video||vm.type=="live"&&!m.video&&m.pairingID.isNotEmpty())&&
        (vm.libraryAlbum.isEmpty()||vm.libraryAlbum in LivePhotoPairing.folders(m,data.media))
    }}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Box {IconButton(onClick={mediaMenu=true}){LIcon(Icons.Outlined.FilterAlt,"筛选照片")}
                DropdownMenu(mediaMenu,{mediaMenu=false}) {listOf("all" to "全部","photo" to "照片","video" to "视频","live" to "Live Photo").forEach{(id,label)->DropdownMenuItem(text={LText(label)},onClick={vm.type=id;mediaMenu=false})}}
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick=vm::toggleSelection){LText(if(vm.selectionMode)"完成" else "选择")}
        }
        LText("照片",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(start=20.dp,top=6.dp,bottom=12.dp))
        if(!vm.graph.media.hasAccess()&&data.logicalMedia.isEmpty()) {
            EmptyState("你的照片，尽在这里","允许访问后，直接浏览并备份照片、视频与 Live Photo。也可以仅授权部分内容。",Icons.Outlined.PhotoLibrary) {
                Button(onClick=onPermissions){LText("允许访问相册")}
            }
        } else {
            if(!vm.graph.media.fullAccess())Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                LText("仅显示已授权内容",modifier=Modifier.weight(1f));TextButton(onClick=onPermissions){LText("管理")}
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick={albums=true}){LText(data.media.firstOrNull{it.folderKey==vm.libraryAlbum}?.folderName?:"最近项目",localize=vm.libraryAlbum.isEmpty());LIcon(Icons.Outlined.ExpandMore,null)}
                    DropdownMenu(albums,{albums=false}) {
                        DropdownMenuItem(text={LText("全部可访问项目")},onClick={vm.libraryAlbum="";albums=false})
                        data.media.distinctBy{it.folderKey}.forEach{album->DropdownMenuItem(text={LText("${album.folderName} · ${folderCounts[album.folderKey]?:0}",localize=false)},onClick={vm.libraryAlbum=album.folderKey;albums=false})}
                    }
                }
                LText("${media.size} 项",style=MaterialTheme.typography.bodySmall)
            }
            ProductSegments(vm.filter,listOf("all" to "全部","pending" to "未备份","uploaded" to "已备份")){vm.filter=it}
            LText("备份状态以当前账号的记录为准。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp))
            if(media.isEmpty())Box(Modifier.weight(1f)){EmptyState("没有可显示的项目","调整筛选条件或相册访问权限。",Icons.Outlined.PhotoLibrary)}
            else LazyVerticalGrid(columns=GridCells.Adaptive(112.dp),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                items(media,key={it.id}){m->Box(Modifier.aspectRatio(1f).combinedClickable(onClick={vm.open(m)},onLongClick={vm.select(m.id)})) {
                    AsyncImage(Uri.parse(m.id),m.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    if(data.status(m)=="uploaded")LIcon(Icons.Outlined.CloudDone,"已备份",Modifier.align(Alignment.BottomEnd).padding(5.dp),tint=Color.White)
                    else if(data.status(m)=="uploading")LIcon(Icons.Outlined.Sync,"备份中",Modifier.align(Alignment.BottomEnd).padding(5.dp),tint=Color.White)
                    else if(data.status(m)=="error")LIcon(Icons.Outlined.ErrorOutline,"需要处理",Modifier.align(Alignment.BottomEnd).padding(5.dp),tint=Color.White)
                    if(m.video)LIcon(Icons.Outlined.PlayArrow,"视频",Modifier.align(Alignment.BottomStart).padding(5.dp),tint=Color.White)
                    if(vm.selectionMode)Checkbox(m.id in vm.selected,onCheckedChange={vm.select(m.id)},modifier=Modifier.align(Alignment.TopEnd))
                }}
            }
            if(vm.selectionMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    LText("已选择 ${vm.selected.size} 项",modifier=Modifier.weight(1f))
                    Button(onClick={confirm=true},enabled=vm.selected.isNotEmpty()&&data.settings.account.isNotEmpty()&&!vm.busy){LText("备份")}
                }
                TextButton(onClick={vm.selectAll(media.map{it.id})}){LText("选择当前范围全部")}
            }
        }
    }
    if(confirm)BackupConfirmation(vm,data,"备份已选项目",vm.selected.size,{confirm=false}){val selected=vm.selected.toList();confirm=false;vm.start(selected);vm.clearSelection()}
}

@Composable
fun BackupConfirmation(vm:HarborViewModel,data:HarborUiData,title:String,count:Int,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={LText(title)},text={Column {LText("${count} 项");LText(qualityName(data.settings.quality));LText(livePhotoLabel(data.settings.livePhotoPairing));LText(editPolicyLabel(data.settings.editPolicy))}},confirmButton={TextButton(onClick=onConfirm,enabled=!vm.busy){LText("开始备份")}},dismissButton={TextButton(onClick=onDismiss){LText("取消")}})
}

@Composable
fun BackupScreen(vm:HarborViewModel,data:HarborUiData,onLogin:()->Unit) {
    var confirm by rememberSaveable{mutableStateOf(false)}
    val active=data.items.count{!it.hidden&&it.state !in setOf("done","exists","cancelled")}
    ProductPage("备份",vm,true) {
        ProductSection {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                LIcon(if(active>0)Icons.Outlined.CloudUpload else Icons.Outlined.CloudDone,null,Modifier.size(44.dp),MaterialTheme.colorScheme.primary)
                LText(if(active>0)"备份进行中" else "让回忆安心留存",style=MaterialTheme.typography.titleLarge)
                LText(data.settings.account.ifEmpty{"连接 Google 账号，开始备份你的照片与视频。"},localize=data.settings.account.isEmpty())
                if(data.settings.account.isEmpty())ProductAction("连接 Google 账号",onClick={vm.navigate("connection")})
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf("已备份" to data.doneCount,"待完成" to active,"可访问" to data.logicalMedia.size).forEach{(label,count)->Column {LText(count.toString(),style=MaterialTheme.typography.titleLarge);LText(label,style=MaterialTheme.typography.labelMedium)}}
                }
                if(data.activeJob.isNotEmpty()) {
                    LinearProgressIndicator(progress={if(data.transfer.total>0)(data.transfer.bytes.toFloat()/data.transfer.total).coerceIn(0f,1f) else 0f},modifier=Modifier.fillMaxWidth())
                    LText("${localize(phaseLabel(data.transfer.phase))} · ${formatSize(data.transfer.bytes)} / ${formatSize(data.transfer.total)} · ${formatSize(data.transfer.speed)}/s",localize=false,style=MaterialTheme.typography.bodySmall)
                }
                val pipeline by vm.graph.pipeline.activity.collectAsState()
                if(pipeline.values.any{it>0})LText("准备中 ${(pipeline["preparation"]?:0)+(pipeline["lookup"]?:0)} · 上传中 ${pipeline["upload"]?:0} · 确认中 ${(pipeline["commit"]?:0)+(pipeline["confirmation"]?:0)}",style=MaterialTheme.typography.bodySmall)
            }
        }
        ProductSection {
            ProductAction("备份当前范围",Icons.Outlined.Upload,enabled=data.settings.account.isNotEmpty()&&!vm.busy&&vm.engineReady,onClick={confirm=true})
            SettingsRow("备份范围",icon=Icons.Outlined.Folder,onClick={vm.navigate("folders")})
            SettingsRow("备份偏好",icon=Icons.Outlined.Tune,onClick={vm.navigate("preferences")})
        }
        ProductSection(footer="通过 Google 相册释放本机空间。后台备份由系统安排，打开 PicHarbor 可继续推进。") {SettingsRow("释放手机空间",icon=Icons.Outlined.Storage,onClick={vm.navigate("free-space")})}
        ProductSection("当前设置") {
            ProductValue("上传画质",qualityName(data.settings.quality));ProductValue("Live Photo",livePhotoLabel(data.settings.livePhotoPairing))
            ProductValue("自动备份",if(data.settings.auto)"已开启" else "未开启")
            ProductValue("网络",if(data.settings.wifi)"仅 Wi-Fi" else "Wi-Fi 与蜂窝")
            if(data.settings.wifi&&!vm.graph.backup.onWifi())ProductValue("当前状态","等待 Wi-Fi")
        }
        if(!vm.engineReady)Helper("应用初始化尚未完成。请检查运行环境后重试。")
    }
    if(confirm)BackupConfirmation(vm,data,"确认备份范围与设置",data.scope.size,{confirm=false}){confirm=false;vm.start()}
}

@Composable
fun SettingsScreen(vm:HarborViewModel,data:HarborUiData) {
    val s=data.settings
    ProductPage("备份偏好",vm) {
        ProductSection {ProductPicker("并发模式",s.pipelineMode,listOf("automatic" to "自动","custom" to "自定义"),!vm.busy){vm.setString("pipelineMode",it)};SettingsRow("高级配置",onClick={vm.navigate("pipeline")})}
        ProductSection("上传画质","是否占用空间，以 Google 相册显示为准。") {ProductPicker("上传画质",s.quality,listOf("original" to "不计配额原画质","quota" to "计配额原画质","saver" to "节省空间"),!vm.busy){vm.setString("quality",it)}}
        ProductSection("Live Photo 备份方式","切换方式只影响新任务，已有照片不会自动重新上传。") {ProductPicker("备份方式",s.livePhotoPairing,listOf("original" to "原始照片配对","stepwise" to "原始照片配对（分步上传）","motion" to "动态照片封装"),!vm.busy){vm.setString("livePhotoPairing",it)}}
        ProductSection("编辑过的照片与视频","同时保留修改前后的内容；内容相同只备份一份。不保留撤销编辑的记录。") {
            SettingsRow("版本保留",editPolicyLabel(s.editPolicy),onClick={vm.sheet="edit-policy"})
            LText("Android 通常只提供当前文件，无法验证的编辑前原件不会被伪造。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(16.dp))
        }
        ProductSection(footer="将本机相簿同步到 Google 相册。从本机相簿移除照片，不会删除云端内容。只对新任务生效。") {ToggleRow("单向同步相簿","",s.albumMode!="none",enabled=!vm.busy){vm.setAlbumSync(it)}}
        ProductSection {SettingsRow("高级备份检查",onClick={vm.navigate("checks")})}
    }
}

@Composable
fun FoldersScreen(vm:HarborViewModel,data:HarborUiData,onPermissions:()->Unit,onPickTree:()->Unit) {
    val s=data.settings
    var all by rememberSaveable(s.account){mutableStateOf(s.allFolders)}
    var chosen by rememberSaveable(s.account){mutableStateOf(ArrayList(s.folders))}
    var auto by rememberSaveable(s.account){mutableStateOf(if(s.onboarded)s.auto else true)}
    val folders=data.media.distinctBy{it.folderKey}.sortedBy{it.folderName}
    val folderCounts=rememberFolderCounts(data)
    val count=data.logicalMedia.count{all||LivePhotoPairing.folders(it,data.media).any{key->key in chosen}||"@camera" in chosen&&it.folderName.equals("Camera",true)}
    ProductPage("备份范围",vm) {
        ProductSection("备份范围",if(vm.graph.media.fullAccess())"照片和视频均包含在范围中。选中相簿按成员合并去重，不重复上传同一个项目。" else "有限授权下仅备份你授权的项目，系统可能不提供完整用户相簿。") {
            SettingsRow("全部可访问照片与视频",onClick={all=true;chosen=arrayListOf()}){if(all)LIcon(Icons.Outlined.Check,null)}
            folders.forEach{album->SettingsRow(album.folderName,"${folderCounts[album.folderKey]?:0} 项",onClick={val previous=if(all)emptyList() else chosen.toList();val normalized=if(album.folderName.equals("Camera",true)&&"@camera" in previous)previous-"@camera"+album.folderKey else previous;chosen=ArrayList(if(album.folderKey in normalized)normalized-album.folderKey else normalized+album.folderKey);all=false}){if(!all&&(album.folderKey in chosen||"@camera" in chosen&&album.folderName.equals("Camera",true)))LIcon(Icons.Outlined.Check,null)}}
        }
        ProductSection(footer="自动备份会识别范围内新增和修改内容。系统后台安排不保证即时，打开应用可继续。") {
            ProductValue("将备份","$count 项")
            ToggleRow("确认后开启自动备份","",auto){auto=it}
            ProductValue("上传画质",qualityName(s.quality));ProductValue("Live Photo",livePhotoLabel(s.livePhotoPairing));ProductValue("网络",if(s.wifi)"仅 Wi-Fi" else "Wi-Fi 与蜂窝")
            ProductAction("确认范围与设置",enabled=s.account.isNotEmpty()&&(all||chosen.isNotEmpty())&&!vm.busy){vm.confirmScope(all,chosen.toSet(),auto)}
            if(s.account.isEmpty())SettingsRow("请先连接 Google 账号",onClick={vm.navigate("accounts")})
        }
        ProductSection("Android 存储访问") {
            SettingsRow("管理相册访问",onClick=onPermissions)
            SettingsRow("添加文件夹 / SD 卡",onClick=onPickTree)
            s.trees.filter{tree->folders.none{it.folderKey==tree}}.forEach{SettingsRow("已授权文件夹暂不可用","可能为空、存储卡已拔出或权限失效",onClick=onPickTree)}
        }
    }
}

@Composable
fun NetworkScreen(vm:HarborViewModel,data:HarborUiData) {
    ProductPage("网络与后台",vm) {
        ProductSection(footer="网络设置立即生效，手动暂停的任务保持暂停。正在上传的文件可能需要重传。关闭自动备份不会取消已有任务。") {
            ToggleRow("仅通过 Wi-Fi 备份","",data.settings.wifi,enabled=!vm.busy){vm.setMobile(if(it)"none" else "all")}
            ToggleRow("自动备份","",data.settings.auto,enabled=!vm.busy){if(it)vm.navigate("folders") else vm.setBoolean("auto",false)}
            SettingsRow("检查备份范围",onClick={vm.navigate("folders")})
        }
        ProductSection("后台执行") {
            LText("后台备份由系统安排，省电或网络限制时可能延后。打开应用可继续备份。",modifier=Modifier.padding(16.dp))
            ProductValue("当前网络",if(vm.graph.backup.onWifi())"Wi-Fi" else "非 Wi-Fi")
            val power=LocalContext.current.getSystemService(android.os.PowerManager::class.java)
            ProductValue("省电模式",if(power.isPowerSaveMode)"已开启" else "未开启")
            SettingsRow("Android 后台运行设置",onClick={vm.navigate("background-settings")})
        }
    }
}

@Composable
fun AccountsScreen(vm:HarborViewModel,data:HarborUiData,onLogin:()->Unit) {
    val accounts by vm.accounts.collectAsState()
    var logout by remember{mutableStateOf(false)}
    ProductPage("账号",vm) {
        ProductSection("本机账号") {
            accounts.forEach{account->SettingsRow(account.email,enabled=!vm.busy,onClick={if(account.needsLogin)vm.navigate("connection") else vm.selectAccount(account.email)}){if(account.email==data.settings.account)LIcon(Icons.Outlined.Check,null)}}
            SettingsRow("添加 Google 账号",icon=Icons.Outlined.Add,onClick={vm.navigate("connection")})
        }
        if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(data.settings.account.isNotEmpty())ProductSection(footer="移除本机凭据并暂停任务；历史记录保留并按账号隔离。") {ProductAction("退出当前账号",enabled=!vm.busy){logout=true}}
    }
    if(logout)AlertDialog(onDismissRequest={logout=false},title={LText("退出当前账号？")},confirmButton={TextButton(onClick={logout=false;vm.logout()}){LText("退出")}},dismissButton={TextButton(onClick={logout=false}){LText("取消")}})
}

@Composable
fun ConnectionScreen(vm:HarborViewModel,data:HarborUiData,onLogin:()->Unit) {
    var credential by remember{mutableStateOf("")}
    ProductPage("欢迎使用 PicHarbor",vm,actions={TextButton(onClick=vm::back,enabled=!vm.busy){LText("稍后")}}) {
        ProductSection {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                LIcon(Icons.Outlined.CloudUpload,null,Modifier.size(48.dp),MaterialTheme.colorScheme.primary)
                LText("回忆有处安放",style=MaterialTheme.typography.headlineLarge)
                LText("将手机照片与视频备份到 Google Photos。原生浏览，持续记录进度；备份完成后可前往 Google 相册释放空间。")
                Button(onClick=onLogin,enabled=!vm.busy&&vm.engineReady){LText("连接 Google 账号")}
            }
        }
        ProductSection("已有 gpmc 凭据","凭据仅保存在本机安全存储，不导出到诊断报告。应用内网页登录受 Google 规则影响时，可导入已有凭据。") {
            OutlinedTextField(credential,{credential=it},label={LText("粘贴登录凭据")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth().padding(16.dp))
            ProductAction("导入并连接",enabled=credential.isNotBlank()&&!vm.busy&&vm.engineReady){val value=credential;credential="";vm.login(value)}
        }
        if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!vm.engineReady)Helper("应用初始化尚未完成。请检查运行环境后重试。")
    }
}

@Composable
fun PermissionsScreen(vm:HarborViewModel,data:HarborUiData,onPermissions:()->Unit,onPickTree:()->Unit) {
    val context=LocalContext.current
    ProductPage("相册访问",vm) {
        ProductSection(footer="有限授权仅显示和备份已授权内容。权限减少不会被视为照片已经删除。") {
            ProductValue("当前权限",if(vm.graph.media.fullAccess())"完整访问" else if(vm.graph.media.hasAccess())"有限访问" else "未授权")
            ProductAction(if(vm.graph.media.hasAccess())"管理已授权内容" else "允许访问相册",onClick=onPermissions)
            ProductAction("打开系统设置"){context.openProductIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))}
        }
        ProductSection("Android 存储访问") {SettingsRow("添加文件夹 / SD 卡",onClick=onPickTree)}
    }
}

@Composable
fun BackgroundSettingsScreen(vm:HarborViewModel,data:HarborUiData) {
    ProductPage("Android 后台运行设置",vm) {
        ProductSection(footer="系统省电、网络和充电条件可能使任务延后。后台检查不会要求手机一直亮屏。") {
            SettingsRow("通知、电池优化和自启动",onClick={vm.sheet="background"})
            ToggleRow("重启后恢复备份","恢复未完成任务与自动检查",data.settings.restart,enabled=!vm.busy){vm.setBoolean("restart",it)}
        }
    }
}

@Composable
fun DiagnosticsScreen(vm:HarborViewModel,data:HarborUiData,onExport:()->Unit) {
    ProductPage("诊断",vm) {
        ProductSection(footer="日志默认常开，保留 14 天或 64 MiB。导出仅包含现存日志，实际覆盖范围记录在导出文件中。") {
            ProductValue("日志保留","14 天 / 64 MiB")
            SettingsRow("导出诊断日志","选择日期范围，包含 JSONL 与同源可读文本 ZIP",onClick=onExport)
        }
    }
}

@Composable
fun AboutScreen(vm:HarborViewModel,data:HarborUiData) {
    val context=LocalContext.current
    ProductPage("关于 PicHarbor",vm) {
        ProductSection {ProductValue("版本",BuildConfig.VERSION_NAME);ProductValue("应用标识",BuildConfig.APPLICATION_ID,false)}
        ProductSection("组件与许可证") {
            ProductValue("界面","Kotlin / Jetpack Compose",false);ProductValue("共享核心","gpmc / Python",false);ProductValue("应用内代理","ProjectClash / Clash",false)
            SettingsRow("开源许可",onClick={vm.sheet="licenses"})
            SettingsRow("项目仓库",onClick={context.openProductIntent(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/Cc-Cece/PicHarbor-Mobile")))})
        }
    }
}

@Composable
fun FreeSpaceScreen(vm:HarborViewModel,data:HarborUiData) {
    var alternatives by rememberSaveable{mutableStateOf(false)}
    val context=LocalContext.current
    ProductPage("释放空间",vm) {
        ProductSection {
            LText("通过 Google 相册释放空间",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(16.dp))
            LText("备份完成后，使用 Google 相册的“释放空间”，由它识别并移除已备份的本机照片和视频。",modifier=Modifier.padding(horizontal=16.dp))
            ProductAction("前往 Google 相册释放空间",enabled=!vm.busy,onClick=vm::openOfficialFreeSpace)
        }
        ProductSection(footer="请确认 Google 相册当前使用的是备份账号。菜单名称和位置可能因版本或语言有所不同。") {
            ProductAction("其他打开方式"){alternatives=!alternatives}
            if(alternatives){LText("打开 Google 相册，点击头像，再选择“释放空间”。",modifier=Modifier.padding(16.dp));ProductAction("打开 Google 相册"){val intent=context.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos");if(intent!=null)context.openProductIntent(intent)else vm.say("请先安装 Google 相册")}}
        }
        ProductSection {LText("请选择释放设备空间，避免误入清理云端存储。请先确认备份完成；PicHarbor 不会删除手机原件。",modifier=Modifier.padding(16.dp))}
    }
}
