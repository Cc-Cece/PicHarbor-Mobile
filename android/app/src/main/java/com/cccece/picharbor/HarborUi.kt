@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.cccece.picharbor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LocalPageScrollBehavior=staticCompositionLocalOf<TopAppBarScrollBehavior?>{null}

data class HarborUiData(val settings:HarborSettings,val media:List<MediaEntry>,val backups:List<BackupRecord>,val jobs:List<BackupJob>,
    val items:List<BackupItem>,val transfer:TransferProgress,val activeJob:String,
    val logicalMedia:List<MediaEntry> = LivePhotoPairing.logical(media),
    val backupMap:Map<String,BackupRecord> = backups.filter{it.account==settings.account}.associateBy{it.mediaId},
    val itemMap:Map<String,BackupItem> = items.filter{it.account==settings.account}.asReversed().associateBy{it.mediaId},
    val scope:List<MediaEntry> = logicalMedia.filter{settings.includes(it)||!it.video&&LivePhotoPairing.findPair(it,media)?.let{pair->settings.includesFolder(pair)}==true},
    val pending:List<MediaEntry> = scope.filterNot{LivePhotoPairing.backed(it,media,backupMap)},
    val doneCount:Int = logicalMedia.count{LivePhotoPairing.backed(it,media,backupMap)},val statistics:List<JobStatistics> = emptyList()) {
    val statisticsMap=statistics.associateBy{it.jobId}
    val attentionItems=itemMap.values.filter{it.state in setOf("failed","album_pending")}.sortedBy{it.name}
    fun backed(m:MediaEntry)=backupMap[m.id]?.account==settings.account && LivePhotoPairing.backed(m,media,backupMap)
    fun status(m:MediaEntry):String {
        if(settings.account.isEmpty())return "unknown"
        val task=itemMap[m.id]?.takeIf{it.account==settings.account}
        if(task?.id==transfer.item||task?.state in setOf("hashing","checking","uploading","finalizing","finalizing_album"))return "uploading"
        if(task?.state in setOf("done","exists")&&task!=null&&liveWarning(task.livePlan).isNotBlank())return "warning"
        if(backed(m))return "uploaded"
        if(task?.state=="failed"||task?.state=="album_pending")return "error"
        if(task?.state=="queued")return "pending"
        return "unknown"
    }
    val latestJob=jobs.firstOrNull{it.state in setOf("queued","running","paused","pausing","waiting","auth_required","needs_attention")}
}

@Composable
fun HarborRoot(vm:HarborViewModel,onLogin:()->Unit,onPermissions:()->Unit,onPickFiles:()->Unit,onPickTree:()->Unit,onExport:()->Unit) {
    RemoteAlerts(vm)
    val incoming by vm.graph.computer.incoming.collectAsState()
    val settings by vm.settings.collectAsState()
    val media by vm.media.collectAsState();val records by vm.records.collectAsState();val jobs by vm.jobs.collectAsState()
    val items by vm.items.collectAsState()
    val statistics by vm.statistics.collectAsState()
    val transfer by vm.graph.backup.progress.collectAsState();val active by vm.graph.backup.activeJob.collectAsState()
    val notice by vm.graph.backup.notice.collectAsState()
    val transfers by vm.graph.backup.inFlight.collectAsState()
    val base=remember(settings,media,records,jobs,items,statistics){
        // DataStore and Room emit independently during a switch: hide any old-account frame.
        val accountJobs=jobs.filter{it.account==settings.account}
        val jobIds=accountJobs.map{it.id}.toSet()
        HarborUiData(settings,media,records.filter{it.account==settings.account},accountJobs,
            items.filter{it.account==settings.account},
            TransferProgress(),"",statistics=statistics.filter{it.jobId in jobIds})
    }
    val data=base.copy(transfer=transfer,activeJob=active)
    LaunchedEffect(data.activeJob,data.transfer.item,data.transfer.phase) {
        if(data.activeJob.isNotEmpty())Diagnostics.event("ui.snapshot",DiagnosticContext(job=data.activeJob,item=data.transfer.item),org.json.JSONObject().put("phase",data.transfer.phase).put("source_read_bytes",data.transfer.bytes))
    }
    val hasStatus=false
    val snackbar=remember{SnackbarHostState()}
    val pageState=rememberSaveableStateHolder()
    val focus=LocalFocusManager.current
    val keyboard=LocalSoftwareKeyboardController.current
    val density=LocalDensity.current
    val direction=if(LocalLayoutDirection.current==LayoutDirection.Rtl)-1 else 1
    val keyboardVisible=WindowInsets.ime.getBottom(density)>0
    val showBottomBar=vm.page !in setOf("detail","information")&&!keyboardVisible
    var measuredBar by remember{mutableIntStateOf(0)}
    val barHeight=if(measuredBar>0)with(density){measuredBar.toDp()} else 80.dp+WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var previousPage by remember{mutableStateOf(vm.pageKey)}
    LaunchedEffect(vm.pageKey){if(previousPage!=vm.pageKey){focus.clearFocus();keyboard?.hide()};previousPage=vm.pageKey}
    val retainedKeys=remember{mutableSetOf<String>()}
    val activeKeys=vm.activePageKeys
    LaunchedEffect(activeKeys){(retainedKeys-activeKeys).forEach{pageState.removeState(it)};retainedKeys.clear();retainedKeys.addAll(activeKeys)}
    val messageText=localize(vm.message)
    val noticeText=localize(notice)
    LaunchedEffect(messageText){if(messageText.isNotBlank()){snackbar.showSnackbar(messageText);vm.clearMessage()}}
    LaunchedEffect(noticeText){if(noticeText.isNotBlank())snackbar.showSnackbar(noticeText)}
    BackHandler(vm.canGoBack){vm.back()}
    PicHarborTheme(settings) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
          Column(Modifier.fillMaxSize()) {

            val rootPages=remember{setOf("photos","backup","activity","settings")}
            val lastRoot=vm.selectedTab.route

            Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(if(hasStatus)WindowInsets.statusBars else WindowInsets(0,0,0,0))) {
                AnimatedContent(targetState=Triple(vm.selectedTab,vm.page,vm.pageKey),modifier=Modifier.fillMaxSize(),transitionSpec={
                    val tabs=initialState.first!=targetState.first
                    val enter=fadeIn(tween(260,easing=FastOutSlowInEasing))+
                        slideInHorizontally(tween(320,easing=FastOutSlowInEasing)){if(tabs)0 else it/32*direction*(if(vm.navigationForward)1 else -1)}
                    (enter togetherWith fadeOut(tween(180,easing=FastOutSlowInEasing))).using(null)
                },label="content-transition") { (currentTab,page,key) ->
                    pageState.SaveableStateProvider(key) {
                        val scrollBehavior=if(page in rootPages)TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState()) else null
                        CompositionLocalProvider(LocalPageScrollBehavior provides scrollBehavior) {
                        val padding=Modifier.padding(bottom=if(showBottomBar)barHeight else 0.dp).statusBarsPadding().then(if(showBottomBar)Modifier else Modifier.navigationBarsPadding()).imePadding()
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(padding).then(if(scrollBehavior!=null)Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)) {
                            when(page) {
                                "photos"->PhotosScreen(vm,data,onPermissions,onPickFiles)
                                "backup"->BackupScreen(vm,data,onLogin)
                                "activity"->ActivityScreen(vm,data)
                                "settings"->UnifiedSettingsScreen(vm,data,onPermissions,onExport)
                                "preferences"->SettingsScreen(vm,data)
                                "checks"->ConfirmationSettingsScreen(vm,data)
                                "pipeline"->PipelineSettingsScreen(vm,data)
                                "archive-settings"->ArchiveSettingsScreen(vm,data)
                                "interface"->InterfaceScreen(vm,data)
                                "proxy"->ProxyScreen(vm,data)
                                "proxy-profiles"->ProxyProfilesScreen(vm,data)
                                "proxy-editor"->ProxyEditorScreen(vm,data)
                                "proxy-profile"->ProxyProfileScreen(vm,data)
                                "accounts"->AccountsScreen(vm,data,onLogin)
                                "connection"->ConnectionScreen(vm,data,onLogin)
                                "permissions"->PermissionsScreen(vm,data,onPermissions,onPickTree)
                                "background-settings"->BackgroundSettingsScreen(vm,data)
                                "about"->AboutScreen(vm,data)
                                "more"->RemoteMoreScreen(vm)
                                "information"->MediaInformationScreen(vm,data)
                                "live-link"->LiveLinkScreen(vm,data)
                                "task-proxy"->TaskProxyScreen(vm,data)
                                "task-detail"->TaskDetailScreen(vm,data)
                                "diagnostics"->DiagnosticsScreen(vm,data,onExport)
                                "folders"->FoldersScreen(vm,data,onPermissions,onPickTree)
                                "network"->NetworkScreen(vm,data)
                                "archives"->ActivityScreen(vm,data,archived=true)
                                "free-space"->FreeSpaceScreen(vm,data)
                                "detail"->DetailScreen(vm,data)
                                "backup-list"->BackupListScreen(vm,data)
                                "job-details"->JobDetailsScreen(vm,data)
                                else->PhotosScreen(vm,data,onPermissions,onPickFiles)
                            }
                            if(vm.busy&&!hasStatus)LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                        }
                        }
                    }
                }
                // This single navigation bar stays mounted for all four root tabs.
                androidx.compose.animation.AnimatedVisibility(visible=showBottomBar,modifier=Modifier.align(Alignment.BottomCenter),enter=fadeIn(tween(220)),exit=fadeOut(tween(180))) {
                    NavigationBar(modifier=Modifier.onSizeChanged{measuredBar=it.height},containerColor=MaterialTheme.colorScheme.surfaceContainerLow) {
                        listOf(Triple("photos",Icons.Outlined.PhotoLibrary,"\u7167\u7247"),Triple("backup",Icons.Outlined.Cloud,"\u5907\u4efd"),Triple("activity",Icons.Outlined.History,"活动"),Triple("settings",Icons.Outlined.Settings,"设置")).forEach{(route,icon,label)->
                            NavigationBarItem(selected=(if(vm.page in rootPages)vm.page else lastRoot)==route,onClick={if(vm.page!=route)vm.tab(route)},icon={LIcon(icon,label)},label={LText(label)})
                        }
                    }
                }
            }
          }
            SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter).then(if(showBottomBar)Modifier.padding(bottom=barHeight) else Modifier.navigationBarsPadding().imePadding()))
        }
        HarborSheets(vm,data,onLogin,onPickTree)
        incoming?.let{offer->AlertDialog(onDismissRequest=vm.graph.computer::rejectIncoming,title={LText("传输到电脑")},
            text={LText("电脑 ${offer.host} 请求备份范围内 ${offer.count} 项，是否确认发送？")},
            confirmButton={TextButton(onClick=vm.graph.computer::confirmIncoming){LText("确认发送")}},
            dismissButton={TextButton(onClick=vm.graph.computer::rejectIncoming){LText("拒绝")}})}
    }
}

@Composable
fun PageTitle(title:String,vm:HarborViewModel,root:Boolean=false,close:Boolean=false,navigation:@Composable ()->Unit={},back:(()->Unit)?=null,action:@Composable RowScope.()->Unit={}) {
    if(root) {
        val behavior=LocalPageScrollBehavior.current
        val collapsed=behavior?.state?.collapsedFraction?:0f
        Box {
            LargeTopAppBar(title={LText(title,modifier=Modifier.graphicsLayer{alpha=(1-collapsed*1.6f).coerceIn(0f,1f)}.semantics{if(collapsed>=.5f)invisibleToUser() else heading()})},
                navigationIcon=navigation,actions=action,scrollBehavior=behavior,windowInsets=WindowInsets(0,0,0,0),
                colors=TopAppBarDefaults.largeTopAppBarColors(containerColor=MaterialTheme.colorScheme.background,scrolledContainerColor=MaterialTheme.colorScheme.background))
            if(collapsed>.35f)Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=72.dp),contentAlignment=Alignment.Center) {
                LText(title,style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis,
                    modifier=Modifier.graphicsLayer{alpha=((collapsed-.35f)/.65f).coerceIn(0f,1f)}.semantics{if(collapsed<.5f)invisibleToUser() else heading()})
            }
        }
    } else CenterAlignedTopAppBar(title={LText(title,maxLines=1,overflow=TextOverflow.Ellipsis)},navigationIcon={IconButton(onClick={if(back!=null)back() else vm.back()}){LIcon(if(close)Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack,if(close)"关闭" else "返回")}},actions=action,windowInsets=WindowInsets(0,0,0,0))

}
@Composable
fun SettingsRow(title:String,subtitle:String="",icon:ImageVector?=null,enabled:Boolean=true,onClick:()->Unit,leading:@Composable (()->Unit)?=null,trailing:@Composable (()->Unit)?=null) {
    val remote=(androidx.compose.ui.platform.LocalContext.current.applicationContext as PicHarborApp).graph.remote
    val policyEnabled=LocalRemotePolicyEnabled.current
    val content=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .38f)
    val secondary=MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=if(enabled)1f else .38f)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).clickable(enabled=enabled,role=Role.Button,onClick={if(!policyEnabled||RemotePolicies.labels[title]?.let{remote.intercept(it)}!=true)onClick()})
        .heightIn(min=if(subtitle.isEmpty())56.dp else 72.dp).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        if(leading!=null)leading() else if(icon!=null)LIcon(icon,null,tint=if(enabled)MaterialTheme.colorScheme.primary else content,modifier=Modifier.size(24.dp))
        Column(Modifier.weight(1f)){LText(title,style=MaterialTheme.typography.bodyLarge,color=content);if(subtitle.isNotEmpty())LText(subtitle,style=MaterialTheme.typography.bodySmall,color=secondary,modifier=Modifier.padding(top=3.dp))}
        CompositionLocalProvider(LocalContentColor provides secondary) {
            if(trailing!=null)trailing() else LIcon(Icons.Outlined.ChevronRight,null,tint=secondary,modifier=Modifier.size(20.dp))
        }
    }
}
@Composable
fun ToggleRow(title:String,subtitle:String,checked:Boolean,icon:ImageVector?=null,enabled:Boolean=true,onChange:(Boolean)->Unit) {
    val remote=(androidx.compose.ui.platform.LocalContext.current.applicationContext as PicHarborApp).graph.remote
    val policyEnabled=LocalRemotePolicyEnabled.current
    val color=MaterialTheme.colorScheme.onSurface.copy(alpha=if(enabled)1f else .38f)
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).heightIn(min=if(subtitle.isEmpty())56.dp else 72.dp)
        .toggleable(value=checked,enabled=enabled,role=Role.Switch,onValueChange={if(!policyEnabled||RemotePolicies.labels[title]?.let{remote.intercept(it)}!=true)onChange(it)}).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        if(icon!=null)LIcon(icon,null,tint=if(enabled)MaterialTheme.colorScheme.primary else color,modifier=Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            LText(title,color=color,style=MaterialTheme.typography.bodyLarge)
            if(subtitle.isNotEmpty())LText(subtitle,color=if(enabled)MaterialTheme.colorScheme.onSurfaceVariant else color,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=3.dp))
        }
        Switch(checked=checked,onCheckedChange=null,enabled=enabled)
    }
}
@Composable
fun SettingGroup(content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxWidth().animateContentSize().clip(RoundedCornerShape(24.dp)),verticalArrangement=Arrangement.spacedBy(2.dp),content=content)}
@Composable
fun SectionLabel(label:String){LText(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(start=5.dp,top=24.dp,bottom=12.dp))}
@Composable
fun Helper(text:String,icon:ImageVector=Icons.Outlined.Info){Row(Modifier.padding(horizontal=4.dp,vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)){
    LIcon(icon,null,modifier=Modifier.size(17.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    LText(text,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}}
@Composable
fun EmptyState(title:String,text:String,icon:ImageVector=Icons.Outlined.CloudDone,action:(@Composable ()->Unit)?=null) {
    Column(Modifier.fillMaxWidth().animateContentSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(35.dp),modifier=Modifier.size(106.dp)){Box(contentAlignment=Alignment.Center){
            Crossfade(icon,animationSpec=tween(HarborMotion.MEDIUM),label="status-icon") { current -> LIcon(current,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(53.dp)) }
        }}
        AnimatedContent(title,label="status-title") { current -> LText(current,style=MaterialTheme.typography.headlineSmall) }
        LText(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
fun backupHeading(vm:HarborViewModel,data:HarborUiData):Pair<String,String> = when {
    !vm.engineReady->"暂时无法启动备份" to "请检查安装包或重新安装"
    data.settings.account.isEmpty()||vm.loginRequired->"登录后开始安心备份" to "连接你的 Google 账号"
    data.activeJob.isNotEmpty()->(if(data.latestJob?.state=="pausing")"正在暂停备份" else "正在备份你的照片") to data.transfer.name.ifEmpty{"正在准备文件"}
    data.latestJob?.state=="paused"->"备份已暂停" to "已完成的内容会保留，随时可以继续"
    data.latestJob?.state=="waiting"->"等待网络条件满足" to "连接 Wi-Fi 后会继续未完成的任务"
    data.latestJob?.state=="auth_required"->"账号需要重新登录" to "重新连接后继续未完成的备份"
    data.latestJob?.state=="needs_attention"->"有项目需要处理" to "已成功备份的内容不会重复上传"
    !data.settings.allFolders&&data.settings.folders.isEmpty()->"选择备份文件夹" to "只备份你想留住的内容"
    data.pending.isNotEmpty()->"还有 ${data.pending.size} 项待检查或备份" to "原始画质 · 备份到 Google 相册"
    else->"备份已完成" to "已选文件夹中当前可访问的内容均已备份"
}

@Composable
fun Avatar(account:String,onClick:()->Unit) {
    Surface(onClick=onClick,color=MaterialTheme.colorScheme.surfaceContainerLow,shape=CircleShape,modifier=Modifier.size(40.dp)) {
        AccountAvatar(account)
    }
}

@Composable
fun AccountAvatar(account:String,modifier:Modifier=Modifier.size(40.dp)) {
    val accounts by (LocalContext.current.applicationContext as PicHarborApp).graph.credentials.accounts.collectAsState()
    val profile=accounts.firstOrNull{it.email==account}
    Box(modifier.clip(CircleShape),contentAlignment=Alignment.Center) {
        LIcon(Icons.Outlined.AccountCircle,null,Modifier.fillMaxSize(),MaterialTheme.colorScheme.primary)
        if(!profile?.avatarPath.isNullOrBlank())AsyncImage(ImageRequest.Builder(LocalContext.current).data(java.io.File(profile!!.avatarPath))
            .memoryCacheKey("avatar:$account:${profile.profileCheckedAt}").diskCachePolicy(coil.request.CachePolicy.DISABLED).build(),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}

fun phaseLabel(phase:String)=when(phase){
    "done"->"已上传";"exists"->"云端已有相同内容";"queued"->"等待处理"
    "paused"->"已暂停";"cancelled"->"已取消";"failed"->"可重试"
    "waiting_confirmation"->"等待云端确认";"waiting_network"->"等待网络"
    "interrupted"->"任务已中断";"album_pending"->"相册归类待重试"
    "filename_skipped"->"仅同名跳过，内容待确认"
    "hashing"->"检查本地文件";"checking","cloud_check"->"核对云端内容"
    "uploading"->"上传中";"finalizing"->"完成上传";"retrying"->"稍后重试"
    else->"正在准备"
}
