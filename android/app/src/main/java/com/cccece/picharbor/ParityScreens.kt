@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.cccece.picharbor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** Keep share/settings flows in the current task when an Activity is available. */
fun android.content.Context.openProductIntent(intent:Intent):Boolean = try {
    var current=this
    while(current is android.content.ContextWrapper && current !is android.app.Activity) {
        val base=current.baseContext;if(base===current)break;current=base
    }
    if(current is android.app.Activity)current.startActivity(intent)
    else startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch(_:android.content.ActivityNotFoundException) {
    false
} catch(_:SecurityException) {
    false
}

@Composable
private fun rememberFolderCounts(data:HarborUiData):Map<String,Int> = remember(data.logicalMedia,data.media) {
    buildMap {data.logicalMedia.forEach{asset->LivePhotoPairing.folders(asset,data.media).forEach{folder->put(folder,(get(folder)?:0)+1)}}}
}

@Composable
fun ProductPage(title:String,vm:HarborViewModel,root:Boolean=false,actions:@Composable RowScope.()->Unit={},content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize()) {
        PageTitle(title,vm,root,action=actions)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            content();Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ProductSection(title:String="",footer:String="",content:@Composable ColumnScope.()->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(title.isNotEmpty())LText(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=12.dp).semantics{heading()})
        Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth(),content=content)
        }
        if(footer.isNotEmpty())LText(footer,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=12.dp))
    }
}

@Composable
fun ProductValue(label:String,value:String,localized:Boolean=true) {
    var filename by rememberSaveable(value){mutableStateOf(false)}
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        LText(label,modifier=Modifier.weight(1f));Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f),contentAlignment=Alignment.CenterEnd) {
            if(!localized&&label in setOf("文件名","动态视频"))TextButton(onClick={filename=true}){FilenameText(value,Modifier.weight(1f),MaterialTheme.typography.bodyMedium)}
            else if(localized)LText(value,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else SelectionContainer{LText(value,localize=false,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
    FilenameSheet(value,visible=filename){filename=false}
}

@Composable
fun ProductAction(label:String,icon:ImageVector?=null,enabled:Boolean=true,destructive:Boolean=false,onClick:()->Unit) {
    val focus=LocalFocusManager.current
    TextButton(onClick={focus.clearFocus();onClick()},enabled=enabled,colors=ButtonDefaults.textButtonColors(contentColor=if(destructive)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp)) {
        if(icon!=null){LIcon(icon,null);Spacer(Modifier.width(12.dp))}
        LText(label,modifier=Modifier.weight(1f))
    }
}

@Composable
fun ProductBottomSheet(title:String,onDismiss:()->Unit,description:String="",visible:Boolean=true,onHidden:()->Unit={},contentKey:Any?=title,content:@Composable ColumnScope.()->Unit) {
    val height=minOf(640.dp,androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp*.9f)
    NativeModalSheet(visible,onDismiss,onHidden,contentKey) {close->
        Column(Modifier.fillMaxWidth().heightIn(max=height)) {
            Row(Modifier.fillMaxWidth().padding(start=24.dp,end=16.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                LText(title,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
                IconButton(onClick=close,enabled=!LocalSheetClosing.current){LIcon(Icons.Outlined.Close,"关闭")}
            }
            Column(Modifier.weight(1f,fill=false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start=24.dp,end=24.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                content()
                if(description.isNotEmpty())Helper(description)
            }
        }
    }
}

@Composable
fun ProductPicker(label:String,value:String,options:List<Pair<String,String>>,enabled:Boolean=true,description:String="",optionDescriptions:Map<String,String> = emptyMap(),onSelect:(String)->Unit) {
    val focus=LocalFocusManager.current
    var open by rememberSaveable(label){mutableStateOf(false)}
    SettingsRow(label,options.firstOrNull{it.first==value}?.second?:value,enabled=enabled&&options.isNotEmpty(),onClick={focus.clearFocus();open=true}) {LIcon(Icons.Outlined.ExpandMore,null)}
    ProductBottomSheet(label,{open=false},description,visible=open) {
        val dismiss=sheetDismissAction()
        Column(Modifier.selectableGroup()) {
            options.forEach{(id,text)->Choice(text,id==value,optionDescriptions[id].orEmpty(),enabled){dismiss{open=false;onSelect(id)}}}
        }
    }
}

@Composable
fun NativeChoiceRow(title:String,selected:Boolean,enabled:Boolean=true,localized:Boolean=true,onClick:()->Unit) {
    val interactive=enabled&&!LocalSheetClosing.current
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=selected,enabled=interactive,role=Role.RadioButton,onClick=onClick).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        RadioButton(selected,onClick=null,enabled=interactive)
        LText(title,modifier=Modifier.weight(1f),localize=localized)
    }
}

@Composable
fun NativeCheckRow(title:String,checked:Boolean,enabled:Boolean=true,localized:Boolean=true,filename:Boolean=false,onChange:(Boolean)->Unit) {
    val interactive=enabled&&!LocalSheetClosing.current
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=checked,enabled=interactive,role=Role.Checkbox,onValueChange=onChange).padding(horizontal=16.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Checkbox(checked,onCheckedChange=null,enabled=interactive)
        if(filename)FilenameText(title,Modifier.weight(1f)) else LText(title,modifier=Modifier.weight(1f),localize=localized)
    }
}

@Composable
fun ProductSegments(value:String,choices:List<Pair<String,String>>,onSelect:(String)->Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
        choices.forEachIndexed { index,(id,label) ->
            SegmentedButton(selected=value==id,onClick={onSelect(id)},shape=SegmentedButtonDefaults.itemShape(index,choices.size)){LText(label,maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
        }
    }
}

@Composable
fun PhotosScreen(vm:HarborViewModel,data:HarborUiData,onPermissions:()->Unit,onPickFiles:()->Unit) {
    val haptic=LocalHapticFeedback.current
    val context=LocalContext.current
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
        PageTitle("照片",vm,root=true,navigation={
            Box {IconButton(onClick={mediaMenu=true}){LIcon(Icons.Outlined.FilterAlt,"筛选照片")}
                ProductBottomSheet("筛选照片",{mediaMenu=false},visible=mediaMenu) {val dismiss=sheetDismissAction();listOf("all" to "全部","photo" to "照片","video" to "视频","live" to "Live Photo").forEach{(id,label)->Choice(label,vm.type==id){dismiss{vm.type=id;mediaMenu=false}}}}
            }
        },action={
            TextButton(onClick=vm::toggleSelection){LText(if(vm.selectionMode)"完成" else "选择")}
        })
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
                    ProductBottomSheet("相册",{albums=false},visible=albums) {
                        val dismiss=sheetDismissAction()
                        Choice("全部可访问项目",vm.libraryAlbum.isEmpty()){dismiss{vm.libraryAlbum="";albums=false}}
                        data.media.distinctBy{it.folderKey}.forEach{album->NativeChoiceRow("${album.folderName} · ${folderCounts[album.folderKey]?:0}",vm.libraryAlbum==album.folderKey,localized=false){dismiss{vm.libraryAlbum=album.folderKey;albums=false}}}
                    }
                }
                LText("${media.size} 项",style=MaterialTheme.typography.bodySmall)
            }
            ProductSegments(vm.filter,listOf("all" to "全部","pending" to "未备份","uploaded" to "已备份")){vm.filter=it}
            LText("备份状态以当前账号的记录为准。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp))
            if(media.isEmpty())Box(Modifier.weight(1f)){EmptyState("没有可显示的项目","调整筛选条件或相册访问权限。",Icons.Outlined.PhotoLibrary)}
            else LazyVerticalGrid(columns=GridCells.Adaptive(112.dp),modifier=Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(2.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                items(media,key={it.id}){m->Box(Modifier.aspectRatio(1f).semantics {if(vm.selectionMode)selected=m.id in vm.selected}.combinedClickable(onClick={vm.open(m)},onLongClick={if(!vm.selectionMode)haptic.performHapticFeedback(HapticFeedbackType.LongPress);vm.select(m.id)})) {
                    val imageKey="${m.id}:${m.modified}:${m.size}"
                    var failed by remember(imageKey){mutableStateOf(false)}
                    AsyncImage(remember(imageKey,context){coil.request.ImageRequest.Builder(context).data(Uri.parse(m.id)).memoryCacheKey(imageKey).diskCacheKey(imageKey).build()},m.name,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,onError={failed=true},onSuccess={failed=false})
                    if(failed)Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),contentAlignment=Alignment.Center){LIcon(Icons.Outlined.BrokenImage,"无法加载预览",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
                    val status=data.status(m)
                    if(status in setOf("uploaded","uploading","error"))Surface(
                        modifier=Modifier.align(Alignment.BottomEnd).padding(5.dp).size(28.dp),shape=CircleShape,shadowElevation=1.dp,
                        color=when(status){"uploaded"->Color(0xFF1B7F46);"uploading"->MaterialTheme.colorScheme.primary;else->MaterialTheme.colorScheme.error},
                        contentColor=when(status){"uploaded"->Color.White;"uploading"->MaterialTheme.colorScheme.onPrimary;else->MaterialTheme.colorScheme.onError}
                    ) {LIcon(when(status){"uploaded"->Icons.Outlined.CloudDone;"uploading"->Icons.Outlined.Sync;else->Icons.Outlined.ErrorOutline},
                        when(status){"uploaded"->"已备份";"uploading"->"备份中";else->"需要处理"},Modifier.padding(5.dp))}
                    if(m.video)LIcon(Icons.Outlined.PlayArrow,"视频",Modifier.align(Alignment.BottomStart).padding(5.dp),tint=Color.White)
                    if(vm.selectionMode)Checkbox(m.id in vm.selected,onCheckedChange=null,modifier=Modifier.align(Alignment.TopEnd))
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
    AlertDialog(onDismissRequest=onDismiss,title={LText(title)},text={Column {LText("${count} 项");LText(qualityName(data.settings.quality));LText(livePhotoLabel(data.settings.livePhotoPairing))}},confirmButton={TextButton(onClick=onConfirm,enabled=!vm.busy){LText("开始备份")}},dismissButton={TextButton(onClick=onDismiss){LText("取消")}})
}

@Composable
fun BackupScreen(vm:HarborViewModel,data:HarborUiData,onLogin:()->Unit) {
    var confirm by rememberSaveable{mutableStateOf(false)}
    val workload by vm.uploadWorkload.collectAsState()
    val work=remember(workload,data.settings.account){workload.filter{it.account==data.settings.account}}
    val active=work.size
    ProductPage("备份",vm,true) {
        ProductSection {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    LIcon(if(active>0)Icons.Outlined.CloudUpload else Icons.Outlined.CloudDone,null,Modifier.size(44.dp),MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.weight(1f));AutomaticBackupIndicator(data.settings.auto,onClick=vm::showAutomaticBackup)
                }
                LText(if(active>0)"备份进行中" else "让回忆安心留存",style=MaterialTheme.typography.titleLarge)
                LText(data.settings.account.ifEmpty{"连接 Google 账号，开始备份你的照片与视频。"},localize=data.settings.account.isEmpty())
                if(data.settings.account.isEmpty())ProductAction("连接 Google 账号",onClick={vm.navigate("connection")})
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    listOf("已备份" to data.doneCount,"待完成" to active,"可访问" to data.logicalMedia.size).forEach{(label,count)->Column {LText(count.toString(),style=MaterialTheme.typography.titleLarge);LText(label,style=MaterialTheme.typography.labelMedium)}}
                }
                if(active>0)BackupTransferMetrics(vm,work)
                val pipeline by vm.graph.pipeline.activity.collectAsState()
                if(active>0||pipeline.values.any{it>0})LText("准备中 ${(pipeline["preparation"]?:0)+(pipeline["lookup"]?:0)} · 上传中 ${pipeline["upload"]?:0} · 确认中 ${(pipeline["commit"]?:0)+(pipeline["confirmation"]?:0)}",style=MaterialTheme.typography.bodySmall)
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
        ProductSection {ProductPicker("并发模式",s.pipelineMode,listOf("automatic" to "自动","custom" to "自定义"),!vm.busy,description="自动模式根据设备资源调整，自定义参数可在高级配置中设置。"){vm.setString("pipelineMode",it)};if(s.pipelineMode=="custom")SettingsRow("高级配置",onClick={vm.navigate("pipeline")})}
        ProductSection("上传画质") {ProductPicker("上传画质",s.quality,listOf("original" to "原始画质（不占用配额）","saver" to "节省空间（不占用配额）","quota" to "原始画质（占用配额）"),!vm.busy,description="元信息会随文件上传，云端处理可能调整部分信息。节省空间由设备或 Google 相册处理，不使用第三方图片处理服务。此处仅选择上传方式，实际配额占用以 Google 相册显示为准。大量备份前，建议先少量测试。",optionDescriptions=mapOf("original" to "保留原始清晰度，使用当前免配额上传方式","quota" to "使用 Google 账号存储空间","saver" to "可能压缩照片和视频")){vm.setString("quality",it)}}
        ProductSection("Live Photo 备份方式") {ProductPicker("备份方式",s.livePhotoPairing,listOf("original" to "原始照片配对","stepwise" to "原始照片配对（分步上传）","motion" to "动态照片封装"),!vm.busy,description="切换方式只影响新任务，已有照片不会自动重新上传。",optionDescriptions=mapOf("original" to "照片与视频配对，按一个媒体项目备份并一次确认","stepwise" to "先保存照片，再关联动态视频","motion" to "单个文件内嵌动态视频，按单文件备份")){vm.setString("livePhotoPairing",it)}}
        ProductSection(footer="备份时，按手机里的文件夹名称整理到 Google 相册。例如，“相机”里的照片会放进云端的“相机”相册，后续备份继续放进同一个相册，不会每次新建。手机上删除照片或修改文件夹名称，不会改变云端内容。只对开启后的新备份任务生效。") {ToggleRow("保留相册分类","",s.albumMode!="none",enabled=!vm.busy){vm.setAlbumSync(it)}}
        ProductSection {SettingsRow("高级备份检查",onClick={vm.navigate("checks")})}
    }
}

@Composable
fun FoldersScreen(vm:HarborViewModel,data:HarborUiData,onPermissions:()->Unit,onPickTree:()->Unit) {
    val s=data.settings
    var all by rememberSaveable(s.account){mutableStateOf(s.allFolders)}
    var chosen by rememberSaveable(s.account){mutableStateOf(ArrayList(s.folders))}
    var auto by rememberSaveable(s.account){mutableStateOf(if(vm.focusAutomaticBackup||s.onboarded)s.auto else true)}
    val automaticTarget=remember{BringIntoViewRequester()}
    var automaticReady by remember{mutableStateOf(false)}
    LaunchedEffect(vm.focusAutomaticBackup,automaticReady) {
        if(vm.focusAutomaticBackup&&automaticReady){automaticTarget.bringIntoView();vm.automaticBackupFocused()}
    }
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
            Box(Modifier.bringIntoViewRequester(automaticTarget).onGloballyPositioned{automaticReady=true}) {
                ToggleRow("开启自动备份","",auto){auto=it}
            }
            ProductValue("上传画质",qualityName(s.quality));ProductValue("Live Photo",livePhotoLabel(s.livePhotoPairing));ProductValue("网络",if(s.wifi)"仅 Wi-Fi" else "Wi-Fi 与蜂窝")
            ProductAction("确认范围与设置",enabled=s.account.isNotEmpty()&&(all||chosen.isNotEmpty())&&!vm.busy){vm.confirmScope(all,chosen.toSet(),auto)}
            if(s.account.isEmpty())SettingsRow("请先连接 Google 账号",onClick={vm.navigate("accounts")})
        }
        ProductSection("Android 存储访问") {
            if(vm.graph.media.fullAccess()) {
                ProductValue("照片与视频权限","已允许访问所有照片和视频")
                SettingsRow("照片访问权限",onClick={vm.navigate("permissions")})
            } else SettingsRow(if(vm.graph.media.hasAccess())"更改可访问的照片" else "允许访问照片与视频",onClick=onPermissions)
            SettingsRow("添加文件夹 / SD 卡",onClick=onPickTree)
            s.trees.filter{tree->folders.none{it.folderKey==tree}}.forEach{SettingsRow("已授权文件夹暂不可用","可能为空、存储卡已拔出或权限失效",onClick=onPickTree)}
        }
    }
}

@Composable
fun NetworkScreen(vm:HarborViewModel,data:HarborUiData) {
    val device=rememberNativeDeviceStatus()
    ProductPage("网络与后台",vm) {
        ProductSection(footer="网络设置立即生效，手动暂停的任务保持暂停。正在上传的文件可能需要重传。关闭自动备份不会取消已有任务。") {
            ToggleRow("仅通过 Wi-Fi 备份","",data.settings.wifi,enabled=!vm.busy){vm.setMobile(if(it)"none" else "all")}
            ToggleRow("自动备份","",data.settings.auto,enabled=!vm.busy){if(it)vm.navigate("folders") else vm.setBoolean("auto",false)}
            SettingsRow("检查备份范围",onClick={vm.navigate("folders")})
        }
        ProductSection("后台执行") {
            LText("后台备份由系统安排，省电或网络限制时可能延后。打开应用可继续备份。",modifier=Modifier.padding(16.dp))
            ProductValue("当前网络",device.network)
            ProductValue("省电模式",if(device.powerSaving)"已开启" else "未开启")
            if(device.network!="未连接"&&!device.validated)LText("系统尚未验证互联网连接。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(horizontal=16.dp))
            ProductValue("暂存资源",formatSize(vm.graph.pipeline.stagedBytes))
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
            accounts.forEach{account->SettingsRow(account.email,enabled=!vm.busy,onClick={if(account.needsLogin)vm.navigate("connection") else vm.selectAccount(account.email)},leading={AccountAvatar(account.email,Modifier.size(32.dp))}){if(account.email==data.settings.account)LIcon(Icons.Outlined.Check,null)}}
            SettingsRow("添加 Google 账号",icon=Icons.Outlined.Add,onClick={vm.navigate("connection")})
        }
        if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(data.settings.account.isNotEmpty())ProductSection(footer="移除本机凭据并暂停任务；历史记录保留并按账号隔离。") {ProductAction("退出当前账号",enabled=!vm.busy){logout=true}}
    }
    if(logout)AlertDialog(onDismissRequest={logout=false},title={LText("退出当前账号？")},confirmButton={TextButton(onClick={logout=false;vm.logout()}){LText("退出")}},dismissButton={TextButton(onClick={logout=false}){LText("取消")}})
}

@Composable
fun ConnectionScreen(vm:HarborViewModel,data:HarborUiData,onLogin:()->Unit) {
    val focus=LocalFocusManager.current
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
            OutlinedTextField(credential,{credential=it},label={LText("粘贴登录凭据")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,autoCorrectEnabled=false,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),modifier=Modifier.fillMaxWidth().padding(16.dp))
            ProductAction("导入并连接",enabled=credential.isNotBlank()&&!vm.busy&&vm.engineReady){val value=credential;credential="";vm.login(value)}
        }
        if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(!vm.engineReady)Helper("应用初始化尚未完成。请检查运行环境后重试。")
    }
}

@Composable
fun PermissionsScreen(vm:HarborViewModel,data:HarborUiData,onPermissions:()->Unit,onPickTree:()->Unit) {
    val context=LocalView.current.context
    ProductPage("相册访问",vm) {
        ProductSection(footer="有限授权仅显示和备份已授权内容。权限减少不会被视为照片已经删除。") {
            ProductValue("当前权限",if(vm.graph.media.fullAccess())"完整访问" else if(vm.graph.media.hasAccess())"有限访问" else "未授权")
            if(!vm.graph.media.fullAccess())ProductAction(if(vm.graph.media.hasAccess())"更改可访问的照片" else "允许访问照片与视频",onClick=onPermissions)
            ProductAction("打开系统设置"){if(!context.openProductIntent(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))))vm.say("暂时无法打开此页面")}
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
    val coverage by produceState<Pair<Long,Long>?>(null) {value=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){Diagnostics.retainedRange()}}
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    ProductPage("诊断",vm) {
        ProductSection("持续诊断",footer="覆盖日期按现存日志文件计算，事件范围以导出为准。") {
            ProductValue("日志保留","14 天 / 64 MiB")
            val dates=java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(locale).withZone(java.time.ZoneId.systemDefault())
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                LText("现存覆盖")
                if(coverage==null)LText("暂无日志",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                else listOf("开始日期" to coverage!!.first,"结束日期" to coverage!!.second).forEach{(label,time)->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        LText(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dates.format(java.time.Instant.ofEpochMilli(time)),style=MaterialTheme.typography.bodyMedium,
                            modifier=Modifier.weight(1f),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
        ProductSection("导出范围",footer="日志默认常开，保留 14 天或 64 MiB。导出仅包含现存日志，实际覆盖范围记录在导出文件中。") {
            SettingsRow("导出诊断日志","选择日期范围，包含 JSONL 与同源可读文本 ZIP",onClick=onExport)
        }
    }
}

@Composable
fun AboutScreen(vm:HarborViewModel,data:HarborUiData) {
    val context=LocalView.current.context
    ProductPage("关于",vm) {
        ProductSection {
            Column(Modifier.fillMaxWidth().padding(vertical=32.dp,horizontal=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Image(androidx.compose.ui.res.painterResource(R.drawable.picharbor_brand),contentDescription=null,modifier=Modifier.size(64.dp))
                Text("PicHarbor",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
                LText("开发版 ${BuildConfig.VERSION_NAME}",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ProductSection {LText("面向 Android 的照片与视频备份。取回内容请使用 Google 相册官方客户端。",modifier=Modifier.padding(16.dp))}
        ProductSection("开源组件") {
            LText("gpmc · GPL-3.0\nCPython · Python Software Foundation License\nblackboxprotobuf · MIT\nRequests 及依赖许可证随应用分发。",modifier=Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium)
            SettingsRow("项目源码与许可证",onClick={if(!context.openProductIntent(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/Cc-Cece/PicHarbor-Mobile"))))vm.say("暂时无法打开此页面")})
            LText("Clash · GPL-3.0",localize=false,modifier=Modifier.padding(horizontal=16.dp,vertical=12.dp),style=MaterialTheme.typography.bodyMedium)
            SettingsRow("代理组件开源许可",onClick={vm.sheet="proxy-licenses"})
        }
    }
}

@Composable
fun FreeSpaceScreen(vm:HarborViewModel,data:HarborUiData) {
    var alternatives by rememberSaveable{mutableStateOf(false)}
    val context=LocalView.current.context
    ProductPage("释放空间",vm) {
        ProductSection {
            LText("通过 Google 相册释放空间",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(16.dp))
            LText("备份完成后，使用 Google 相册的“释放空间”，由它识别并移除已备份的本机照片和视频。",modifier=Modifier.padding(horizontal=16.dp))
            LText("请在 Google 相册中登录与 PicHarbor 备份时相同的 Google 账号。",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(horizontal=16.dp,vertical=12.dp))
            Button(enabled=!vm.busy,onClick=vm::openOfficialFreeSpace,modifier=Modifier.fillMaxWidth().padding(16.dp)) {
                LIcon(Icons.Outlined.OpenInNew,null);Spacer(Modifier.width(8.dp));LText("前往 Google 相册释放空间")
            }
        }
        ProductSection(footer="菜单名称和位置可能因版本或语言有所不同。") {
            ProductAction("其他打开方式"){alternatives=!alternatives}
            if(alternatives){
                LText("在主屏幕长按 Google 相册图标，再点“释放空间”。",modifier=Modifier.padding(16.dp))
                LText("打开 Google 相册，点击头像，再选择“释放空间”。",modifier=Modifier.padding(horizontal=16.dp))
                OutlinedButton(onClick={val intent=context.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos");if(intent==null||!context.openProductIntent(intent))vm.say("请先安装 Google 相册")},modifier=Modifier.fillMaxWidth().padding(16.dp)) {
                    LIcon(Icons.Outlined.OpenInNew,null);Spacer(Modifier.width(8.dp));LText("打开 Google 相册")
                }
            }
        }
        ProductSection {LText("请选择释放设备空间，避免误入清理云端存储。请先确认备份完成；PicHarbor 不会删除手机原件。",modifier=Modifier.padding(16.dp))}
    }
}
