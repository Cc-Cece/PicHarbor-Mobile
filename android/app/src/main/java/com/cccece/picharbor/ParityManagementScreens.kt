@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.json.JSONObject

@Composable
fun UnifiedSettingsScreen(vm:HarborViewModel,data:HarborUiData,onPermissions:()->Unit,onExport:()->Unit) {
    var restore by remember{mutableStateOf(false)}
    ProductPage("设置",vm,true) {
        ProductSection {
            Surface(onClick={vm.navigate("accounts")},color=MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    AccountAvatar(data.settings.account)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        LText(data.settings.account.ifEmpty{"连接 Google 账号"},localize=data.settings.account.isEmpty(),style=MaterialTheme.typography.titleMedium,maxLines=1)
                        LText("凭据仅保存在本机安全存储",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LIcon(Icons.Outlined.ChevronRight,null)
                }
            }
        }
        ProductSection("备份") {
            SettingsRow("备份偏好",icon=Icons.Outlined.CloudUpload,onClick={vm.navigate("preferences")})
            SettingsRow("备份范围",icon=Icons.Outlined.Folder,onClick={vm.navigate("folders")})
            SettingsRow("网络与后台",icon=Icons.Outlined.Wifi,onClick={vm.navigate("network")})
            SettingsRow("应用内代理",icon=Icons.Outlined.Public,onClick={vm.navigate("proxy")})
            SettingsRow("活动记录",icon=Icons.Outlined.Archive,onClick={vm.navigate("archive-settings")})
            SettingsRow("传输到电脑",icon=Icons.Outlined.Computer,onClick={vm.navigate("live-link")})
        }
        ProductSection("设备") {
            SettingsRow("界面",icon=Icons.Outlined.Palette,onClick={vm.navigate("interface")})
            SettingsRow("相册访问",icon=Icons.Outlined.PhotoLibrary,onClick={vm.navigate("permissions")})
            SettingsRow("Android 后台运行设置",icon=Icons.Outlined.BatteryChargingFull,onClick={vm.navigate("background-settings")})
        }
        ProductSection {ProductAction("恢复备份默认设置",enabled=!vm.busy,destructive=true){restore=true}}
        ProductSection {
            SettingsRow("诊断",icon=Icons.Outlined.BugReport,onClick={vm.navigate("diagnostics")})
            SettingsRow("关于 PicHarbor",icon=Icons.Outlined.Info,onClick={vm.navigate("about")})
        }
    }
    if(restore)AlertDialog(onDismissRequest={restore=false},title={LText("恢复默认设置？")},text={LText("重置当前账号的备份和网络偏好。自动备份将关闭，范围需要重新确认。账号、照片、历史任务、语言及外观会保留，不会重新上传照片。")},confirmButton={TextButton(onClick={restore=false;vm.restoreDefaults()}){LText("恢复默认设置")}},dismissButton={TextButton(onClick={restore=false}){LText("取消")}})
}

@Composable
fun ActivityScreen(vm:HarborViewModel,data:HarborUiData,archived:Boolean=false) {
    var source by rememberSaveable{mutableStateOf(if(archived)data.settings.archiveSource else data.settings.activitySource)}
    var status by rememberSaveable{mutableStateOf(if(archived)"completed" else data.settings.activityStatus)}
    var menu by remember(data.settings.account,archived){mutableStateOf(false)}
    var remove by remember(data.settings.account,archived){mutableStateOf(false)}
    var selecting by rememberSaveable(data.settings.account,archived){mutableStateOf(false)}
    var selected by remember(data.settings.account,archived){mutableStateOf(setOf<String>())}
    var revealed by remember(data.settings.account,archived,source,status){mutableStateOf<String?>(null)}
    var rowRemoval by remember(data.settings.account,archived){mutableStateOf<List<String>>(emptyList())}
    BackHandler(revealed!=null){revealed=null}
    var limit by rememberSaveable{mutableIntStateOf(200)}
    LaunchedEffect(source,status){vm.graph.preferences.string(if(archived)"archiveSource" else "activitySource",source);if(!archived)vm.graph.preferences.string("activityStatus",status)}
    val allItems=remember(data.items){data.items.filter{!it.hidden}.groupBy{it.jobId}}
    val visible=remember(allItems,archived,status){allItems.mapValues{(_,rows)->rows.filter{(it.archivedAt>0)==archived&&(status=="all"||(it.state in setOf("done","exists"))==(status=="completed")&&it.state!="cancelled")}}}
    val jobs=remember(data.jobs,visible,source){data.jobs.filter{!it.hidden&&(source=="all"||it.automatic==(source=="automatic"))&&!visible[it.id].isNullOrEmpty()}}
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val formatter=remember(locale){java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT).withLocale(locale).withZone(java.time.ZoneId.systemDefault())}
    val matchingItems=remember(jobs,visible){jobs.flatMap{visible[it.id].orEmpty()}}
    val pending=remember(jobs,visible){matchingItems.filter{it.state !in setOf("done","exists","cancelled")}.map{it.id}}
    val paused=remember(jobs,visible){matchingItems.filter{it.state=="paused"}.map{it.id}}
    val retryable=remember(jobs,visible){matchingItems.filter{it.state in setOf("failed","album_pending","waiting_confirmation")}.map{it.id}}
    val complete=remember(jobs,visible){jobs.filter{visible[it.id].orEmpty().any{it.state in setOf("done","exists")}}.map{it.id}}
    val archivable=remember(jobs,visible){jobs.flatMap{visible[it.id].orEmpty()}.filter{it.state in setOf("done","exists")}}
    Column(Modifier.fillMaxSize()) {
        PageTitle(if(archived)"归档记录" else "活动",vm,!archived) {
            Box {IconButton(onClick={menu=true}){LIcon(Icons.Outlined.MoreHoriz,"活动选项")}
                DropdownMenu(menu,{menu=false}) {
                    if(!archived){
                        DropdownMenuItem(text={LText("暂停任务")},enabled=pending.isNotEmpty(),onClick={menu=false;vm.itemActions(pending,"pause")})
                        DropdownMenuItem(text={LText("继续暂停任务")},enabled=paused.isNotEmpty(),onClick={menu=false;vm.itemActions(paused,"resume")})
                        DropdownMenuItem(text={LText("重试全部异常任务")},enabled=!vm.busy&&retryable.isNotEmpty(),onClick={menu=false;vm.itemActions(retryable,"resume")})
                        DropdownMenuItem(text={LText("归档记录")},onClick={menu=false;vm.navigate("archives")})
                        DropdownMenuItem(text={LText("停止并移除未完成")},enabled=pending.isNotEmpty(),onClick={menu=false;remove=true})
                    }
                    DropdownMenuItem(text={LText(if(archived)"取消归档全部" else "归档已完成")},enabled=complete.isNotEmpty(),onClick={menu=false;vm.archiveSelected(archivable.map{it.id},archived)})
                    DropdownMenuItem(text={LText(if(archived)"选择取消归档" else "选择归档")},enabled=archivable.isNotEmpty(),onClick={menu=false;selected=emptySet();selecting=true})
                }
            }
        }
        ProductSegments(source,listOf("all" to "全部来源","manual" to "手动","automatic" to "自动")){source=it}
        if(!archived)ProductSegments(status,listOf("all" to "全部状态","unfinished" to "未完成","completed" to "已完成")){status=it}
        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            if(jobs.isEmpty())item{EmptyState("暂无符合条件的任务","调整来源或状态筛选，查看其他备份活动。",Icons.Outlined.History)}
            items(jobs.take(limit),key={it.id}){job->
                val matching=visible[job.id].orEmpty()
                val all=allItems[job.id].orEmpty()
                val completed=matching.all{it.state in setOf("done","exists")}
                val unfinished=matching.filter{it.state !in setOf("done","exists","cancelled")}
                val swipeLabel=if(completed)if(archived)"取消归档" else "归档" else if(unfinished.isNotEmpty())"移除" else ""
                ActivitySwipeRow(job.id,swipeLabel,!completed,!vm.busy,revealed,{revealed=it},{
                    if(completed)vm.archiveSelected(matching.map{it.id},archived) else rowRemoval=unfinished.map{it.id}
                }) {
                Surface(onClick={if(revealed==job.id)revealed=null else if(all.size>1)vm.showJob(job.id,status,archived)else matching.firstOrNull()?.let(vm::showItem)},color=MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        AsyncImage(matching.firstOrNull()?.mediaId,null,Modifier.size(52.dp),contentScale=ContentScale.Crop)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            if(all.size>1)LText("备份批次 · ${all.size} 项",style=MaterialTheme.typography.titleMedium)
                            else FilenameText(matching.firstOrNull()?.name.orEmpty(),Modifier.fillMaxWidth(),MaterialTheme.typography.titleMedium)
                            LText(formatter.format(java.time.Instant.ofEpochMilli(job.created)),localize=false,style=MaterialTheme.typography.bodySmall)
                            LText("${all.size} 项 · 已完成 ${all.count{it.state in setOf("done","exists")}} 项",style=MaterialTheme.typography.bodySmall)
                            if(all.size==1)LText(phaseLabel(matching.first().state),style=MaterialTheme.typography.labelSmall)
                            if(matching.size!=all.size)LText("当前筛选 ${matching.size} 项",style=MaterialTheme.typography.bodySmall)
                        }
                        LIcon(Icons.Outlined.ChevronRight,null)
                    }
                }
                }
            }
            if(jobs.size>limit)item{TextButton(onClick={limit+=200}){LText("显示更多")}}
        }
    }
    if(remove)AlertDialog(onDismissRequest={remove=false},title={LText("移除未完成任务？")},text={LText("停止并从活动中移除，保留本机及云端照片。同一版本仍可手动重新备份。")},confirmButton={TextButton(onClick={remove=false;vm.itemActions(pending,"remove")}){LText("停止并移除")}},dismissButton={TextButton(onClick={remove=false}){LText("取消")}})
    if(rowRemoval.isNotEmpty())AlertDialog(onDismissRequest={rowRemoval=emptyList()},title={LText("移除此任务？")},text={LText("停止并从活动中移除，保留本机及云端照片。同一版本仍可手动重新备份。")},
        confirmButton={TextButton(enabled=!vm.busy,onClick={val ids=rowRemoval.filter{it in pending};rowRemoval=emptyList();vm.itemActions(ids,"remove")},colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)){LText("停止并移除")}},
        dismissButton={TextButton(onClick={rowRemoval=emptyList()}){LText("取消")}})
    if(selecting)ProductBottomSheet(if(archived)"选择取消归档" else "选择归档",{selecting=false}) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=420.dp)) {
            items(archivable,key={it.id}){item->
                NativeCheckRow(item.name,item.id in selected,localized=false,filename=true){selected=if(it)selected+item.id else selected-item.id}
            }
        }
        Button(onClick={vm.archiveSelected(archivable.filter{it.id in selected}.map{it.id},archived);selecting=false},enabled=!vm.busy&&archivable.any{it.id in selected},modifier=Modifier.fillMaxWidth()){LText("完成")}
    }
}

@Composable
fun ProxyScreen(vm:HarborViewModel,data:HarborUiData) {
    val proxy=vm.graph.proxy;val library by proxy.library.collectAsState();val testing by proxy.testing.collectAsState();val health by proxy.health.collectAsState();val message by proxy.message.collectAsState()
    ProductPage("应用内代理",vm) {
        ProductSection(footer="仅用于 PicHarbor 连接 Google，不影响其他应用。") {ToggleRow("仅代理 PicHarbor","",data.settings.proxyEnabled,enabled=!vm.busy&&!testing){vm.setBoolean("proxyEnabled",it)}}
        ProductSection {
            SettingsRow("配置与订阅",icon=Icons.Outlined.List,onClick={vm.navigate("proxy-profiles")})
            proxy.profile()?.let{ProductValue("正在使用",it.getString("name"),false);ProductValue("节点数量",it.getJSONArray("nodes").length().toString())}
        }
        ProductSection("节点选择与切换","任务可单独设置。测速不会中断正在上传的文件；仅在允许时使用直连。") {
            ProductPicker("节点选择",library.optString("selection","automatic"),listOf("automatic" to "自动","manual" to "手动"),!vm.busy&&!testing){value->vm.proxyAction{proxy.policy("selection",value)}}
            val names=proxy.profile()?.getJSONArray("nodes")?.let{array->(0 until array.length()).map{array.getJSONObject(it).getString("name")}}.orEmpty()
            if(library.optString("selection")=="manual") {
                ProductPicker("主节点",library.optString("primary"),names.map{it to it},!vm.busy&&!testing){value->vm.proxyAction{proxy.policy("primary",value)}}
                val backups=library.optJSONArray("backups")?.let{a->(0 until a.length()).map{a.getString(it)}}.orEmpty()
                names.filter{it!=library.optString("primary")}.forEach{name->NativeCheckRow(name,name in backups,enabled=!vm.busy&&!testing,localized=false){checked->vm.proxyAction{proxy.backups(if(checked)backups+name else backups-name)}}}
            } else LText("优先保持正常连接，失败后结合延时和近期表现选择其他节点。",modifier=Modifier.padding(16.dp))
            ToggleRow("所有节点不可用时允许直连","关闭时等待连接，不自动绕过代理",library.optString("exhaustion")=="direct",enabled=!vm.busy&&!testing){allowed->vm.proxyAction{proxy.policy("exhaustion",if(allowed)"direct" else "wait")}}
        }
        ProductSection {
            if(testing){LinearProgressIndicator(Modifier.fillMaxWidth());ProductAction("停止测速",onClick=proxy::cancelTest)}
            else ProductAction("测速全部节点",enabled=proxy.profile()!=null&&!vm.busy,onClick=proxy::testAll)
        }
        ProductSection("节点结果","测试访问 Google 的请求延时，并非上传速度。配置凭据保存在本机安全存储中。") {
            val nodes=proxy.profile()?.getJSONArray("nodes")
            if(nodes!=null)(0 until nodes.length()).map{nodes.getJSONObject(it).getString("name")}.sortedBy{health[it]?.milliseconds?:Long.MAX_VALUE}.forEach{name->
                val value=health[name];ProductValue(name,if(value==null)"未测速" else if(value.healthy)"${value.milliseconds} 毫秒" else "不可用")
            }
            if(message.isNotEmpty())LText(message,modifier=Modifier.padding(16.dp))
        }
    }
}

@Composable
fun ProxyProfilesScreen(vm:HarborViewModel,data:HarborUiData) {
    val library by vm.graph.proxy.library.collectAsState()
    ProductPage("配置与订阅",vm) {
        ProductSection(footer="从链接或文件导入，任选一种。一次使用一份配置，订阅需手动更新。") {
            SettingsRow("添加订阅",icon=Icons.Outlined.Add,onClick={vm.navigate("proxy-editor")})
            ProductAction("扫码添加订阅",Icons.Outlined.QrCodeScanner,enabled=!vm.busy){vm.navigate("proxy-editor");vm.scanQr("proxy",false)}
            ProductAction("导入 Clash 配置文件",Icons.Outlined.FileOpen,enabled=!vm.busy,onClick=vm.proxyFilePicker)
        }
        ProductSection("已保存的配置", "支持 Clash Meta 配置，最多保存 20 份，每份最多 64 个节点。") {
            val profiles=library.getJSONArray("profiles")
            if(profiles.length()==0)LText("尚未添加配置。",modifier=Modifier.padding(16.dp))
            for(i in 0 until profiles.length()) {
                val profile=profiles.getJSONObject(i)
                SettingsRow(profile.getString("name"),"${profile.getJSONArray("nodes").length()} 个节点",icon=if(profile.getString("id")==library.optString("active"))Icons.Outlined.CheckCircle else Icons.Outlined.Description,onClick={vm.showProfile(profile.getString("id"))})
            }
        }
    }
}

@Composable
fun ProxyEditorScreen(vm:HarborViewModel,data:HarborUiData) {
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    val focus=LocalFocusManager.current
    var revealAddress by remember{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("")};var address by remember{mutableStateOf("")};var text by remember{mutableStateOf("")};var paste by remember{mutableStateOf(false)}
    LaunchedEffect(vm.scanResults){if(vm.scanPurpose=="proxy"&&vm.scanResults.size==1){address=vm.scanResults.first();vm.clearScan()}}
    ProductPage("添加订阅",vm) {
        ProductSection(footer="链接仅保存在本机，不加入诊断报告。") {
            OutlinedTextField(name,{name=it},label={LText("配置名称")},singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next),modifier=Modifier.fillMaxWidth().padding(12.dp))
            OutlinedTextField(address,{address=it},label={LText("HTTP 或 HTTPS 订阅链接")},singleLine=true,visualTransformation=if(revealAddress)VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,autoCorrectEnabled=false,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),
                trailingIcon={IconButton(onClick={revealAddress=!revealAddress}){LIcon(if(revealAddress)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(revealAddress)"隐藏订阅地址" else "显示订阅地址")}},modifier=Modifier.fillMaxWidth().padding(12.dp))
        }
        ProductSection {
            ProductAction("下载并添加",enabled=!vm.busy&&address.isNotBlank()){val origin=vm.pageKey;val submittedAddress=address;val submittedName=name;vm.proxyAction{vm.graph.proxy.download(submittedAddress,submittedName);address="";vm.backFrom(origin)}}
            ProductAction("扫描二维码",enabled=!vm.busy){vm.scanQr("proxy",false)}
            ProductAction("从图片识别",enabled=!vm.busy){vm.scanQr("proxy",true)}
            if(vm.scanPurpose=="proxy"&&vm.scanResults.size>1)vm.scanResults.forEachIndexed{i,value->ProductAction("订阅 ${i+1}"){address=value;vm.clearScan()}}
            ProductAction("粘贴配置",enabled=!vm.busy){
                paste=true
                runCatching{clipboard.getText()?.text}.onSuccess{value->
                    if(value.isNullOrBlank())vm.say("剪贴板没有可用文本，可在下方输入。")
                    else if(value.toByteArray(Charsets.UTF_8).size>4*1024*1024)vm.say("配置文件过大")
                    else text=value
                }.onFailure{vm.say("无法读取剪贴板，可在下方输入。")}
            }
            if(paste){OutlinedTextField(text,{text=it},label={LText("Clash YAML / JSON")},minLines=4,maxLines=8,modifier=Modifier.fillMaxWidth().padding(12.dp));ProductAction("保存配置",enabled=!vm.busy&&text.isNotBlank()){val origin=vm.pageKey;val submittedText=text;val submittedName=name;vm.proxyAction{vm.graph.proxy.importText(submittedText,submittedName);text="";vm.backFrom(origin)}}}
        }
    }
}

@Composable
fun ProxyProfileScreen(vm:HarborViewModel,data:HarborUiData) {
    val proxy=vm.graph.proxy;val library by proxy.library.collectAsState();val profiles=library.getJSONArray("profiles")
    val profile=(0 until profiles.length()).map{profiles.getJSONObject(it)}.firstOrNull{it.getString("id")==vm.inspectedProfile}
    var remove by remember{mutableStateOf(false)}
    ProductPage("配置详情",vm) {
        if(profile!=null) {
            ProductSection {ProductValue("配置名称",profile.getString("name"),false);ProductValue("类型",if(profile.optString("url").isEmpty())"本地文件" else "订阅")}
            ProductSection {
                ProductAction("使用此配置",enabled=!vm.busy&&library.optString("active")!=vm.inspectedProfile){vm.proxyAction{proxy.activate(profile.getString("id"))}}
                if(profile.optString("url").isNotBlank())ProductAction("更新订阅",enabled=!vm.busy){vm.proxyAction{proxy.download(profile.getString("url"),profile.getString("name"),profile.getString("id"))}}
                ProductAction("移除配置",enabled=!vm.busy,destructive=true){remove=true}
            }
            ProductSection("节点") {val nodes=profile.getJSONArray("nodes");for(i in 0 until nodes.length())LText(nodes.getJSONObject(i).getString("name"),localize=false,modifier=Modifier.padding(16.dp))}
        } else Helper("配置已不可用")
    }
    if(remove)AlertDialog(onDismissRequest={remove=false},title={LText("移除此配置？")},confirmButton={TextButton(onClick={remove=false;val origin=vm.pageKey;val identifier=vm.inspectedProfile;vm.proxyAction{proxy.remove(identifier);vm.backFrom(origin)}}){LText("移除")}},dismissButton={TextButton(onClick={remove=false}){LText("取消")}})
}
