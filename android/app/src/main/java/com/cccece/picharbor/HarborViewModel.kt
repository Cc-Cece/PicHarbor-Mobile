package com.cccece.picharbor

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HarborViewModel(app:Application):AndroidViewModel(app) {
    val graph=(app as PicHarborApp).graph
    val settings=graph.preferences.flow.stateIn(viewModelScope,SharingStarted.Eagerly,HarborSettings())
    val accounts=graph.credentials.accounts
    val accountSwitching=graph.switching
    val media=graph.dao.mediaFlow().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val records=settings.map{it.account}.distinctUntilChanged().flatMapLatest{graph.dao.backupsFlow(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val jobs=settings.map{it.account}.distinctUntilChanged().flatMapLatest{graph.dao.jobsFlow(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val items=settings.map{it.account}.distinctUntilChanged().flatMapLatest{graph.dao.itemsFlow(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val uploadWorkload=settings.map{it.account}.distinctUntilChanged().flatMapLatest{graph.dao.uploadWorkloadFlow(it)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val statistics=settings.map{it.account}.distinctUntilChanged().flatMapLatest{graph.dao.statisticsFlow(it)}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val inspectedJob=MutableStateFlow("")
    var inspectedItem by mutableStateOf("");private set
    fun showItem(item:BackupItem){navigate("task-detail");inspectedItem=item.id;inspectedJob.value=item.jobId}
    fun itemAction(id:String,operation:String)=action{graph.backup.itemAction(id,operation)}
    fun itemActions(ids:List<String>,operation:String)=action{ids.forEach{graph.backup.itemAction(it,operation)}}
    fun archiveItem(id:String,restore:Boolean=false)=action{graph.dao.archiveItem(id,if(restore)0 else System.currentTimeMillis())}
    val jobDetails=inspectedJob.flatMapLatest{graph.dao.jobItemsFlow(it)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    var detailLimit by mutableIntStateOf(200)
    var batchStatus by mutableStateOf("all");private set
    var batchArchived by mutableStateOf(false);private set
    fun showJob(id:String,status:String="all",archived:Boolean=false){navigate("job-details");inspectedJob.value=id;detailLimit=200;batchStatus=status;batchArchived=archived}
    fun taskProxy(value:String)=action {
        val origin=pageKey
        val job=graph.dao.job(inspectedJob.value)?:return@action
        if(job.account!=settings.value.account)return@action
        val wasRunning=graph.backup.activeJob.value==job.id&&job.state=="running"
        if(wasRunning)graph.backup.stopAndWait()
        val current=graph.dao.job(job.id)?:job
        graph.dao.putJob(current.copy(proxyPolicy=value,state=if(wasRunning&&current.state=="paused")"queued" else current.state))
        if(job.state!="paused")graph.scheduler.launchManual(job.allowMobile)
        backFrom(origin)
    }
    var page by mutableStateOf("photos");private set
    var pageKey by mutableStateOf(UUID.randomUUID().toString());private set
    var scanPurpose by mutableStateOf("");private set
    var scanResults by mutableStateOf(listOf<String>());private set
    var scanner:(Boolean)->Unit={}
    var proxyFilePicker:()->Unit={}
    fun importProxyFile(uri:Uri)=proxyAction {
        val text=withContext(Dispatchers.IO){
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    val count=stream.read(buffer);if(count<0)break
                    if(output.size()+count>4*1024*1024)throw EngineFailure("PROXY_CONFIG_UNSUPPORTED")
                    output.write(buffer,0,count)
                }
                output.toString("UTF-8")
            }?:throw EngineFailure("PROXY_CONFIG_UNSUPPORTED")
        }
        graph.proxy.importText(text,"");message="已导入配置"
    }
    fun scanQr(purpose:String,image:Boolean) { scanPurpose=purpose;scanResults=emptyList();scanner(image) }
    fun acceptScan(values:List<String>) { scanResults=values.distinct();if(values.isEmpty())say("未识别到二维码，请重试") }
    fun clearScan(){scanResults=emptyList();scanPurpose=""}
    var navigationForward by mutableStateOf(true);private set
    private val stacks=MobilePage.roots.associateWith{mutableListOf<MobileNavigationEntry>()}
    private val tabEntries=mutableMapOf<MobilePage,MobileNavigationEntry>()
    var selectedTab by mutableStateOf(MobilePage.Photos);private set
    private fun entry()=MobileNavigationEntry(MobilePage.from(page),activeMediaId,inspectedItem,inspectedJob.value,inspectedProfile,batchStatus,batchArchived,pageKey)
    private fun restore(entry:MobileNavigationEntry){page=entry.page.route;pageKey=entry.key;activeMediaId=entry.media;inspectedItem=entry.item;inspectedJob.value=entry.job;inspectedProfile=entry.profile;batchStatus=entry.status;batchArchived=entry.archived}
    val activePageKeys get()=buildSet {
        add(pageKey)
        MobilePage.roots.forEach{root->stacks.getValue(root).forEach{add(it.key)};if(root!=selectedTab)tabEntries[root]?.let{add(it.key)}}
    }
    fun backFrom(key:String){if(pageKey==key)back()}
    val canGoBack get()=sheet.isNotEmpty()||(selectionMode&&page=="photos")||stacks.getValue(selectedTab).isNotEmpty()
    var selected by mutableStateOf(setOf<String>());private set
    var selectionMode by mutableStateOf(false);private set
    private var activeMediaId by mutableStateOf("")
    val activeMedia get()=media.value.firstOrNull{it.id==activeMediaId}
    var filter by mutableStateOf("all")
    var libraryAlbum by mutableStateOf("")
    var backupListKind by mutableStateOf("pending");private set
    fun showBackupList(kind:String){backupListKind=kind;navigate("backup-list")}
    var type by mutableStateOf("all")
    var fromDate by mutableStateOf(0L)
    var toDate by mutableStateOf(Long.MAX_VALUE)
    var sheet by mutableStateOf("")
    var busy by mutableStateOf(false);private set
    var message by mutableStateOf("");private set
    var loginRequired by mutableStateOf(false);private set
    var permissionRevision by mutableIntStateOf(0);private set
    var albumChoices by mutableStateOf(listOf<Pair<String,String>>());private set
    var pendingManualIds:List<String>?=null;private set
    val engineReady get()=graph.engineReady
    init {
        refresh()
        viewModelScope.launch {
            media.map { rows -> rows.map { Triple(it.id,it.modified,it.size) } }.distinctUntilChanged().collectLatest { versions ->
                if(versions.isNotEmpty()) {
                    try {
                        graph.startup.await()
                        if(graph.engineReady)graph.backup.identifiedMedia()
                    } catch(error:CancellationException){throw error}
                    catch(_:Exception){ /* A later permission or library update retries metadata indexing. */ }
                }
            }
        }
        viewModelScope.launch {
            combine(items,settings){rows,s->rows to s}.collect { (_,s) ->
                if(s.archiveEnabled) {
                    val threshold=System.currentTimeMillis()-s.archiveDays*86400000L
                    graph.dao.archiveEligible(s.account,threshold,System.currentTimeMillis())
                }
            }
        }
    }
    private var navigationRestored=false
    fun navigationSnapshot():String {
        fun json(e:MobileNavigationEntry)=JSONObject().put("page",e.page.route).put("media",e.media).put("item",e.item).put("job",e.job).put("profile",e.profile).put("status",e.status).put("archived",e.archived).put("key",e.key)
        val tabs=JSONObject();val history=JSONObject()
        MobilePage.roots.forEach { root ->
            tabs.put(root.route,json(if(root==selectedTab)entry() else tabEntries[root]?:MobileNavigationEntry(root)))
            history.put(root.route,org.json.JSONArray().apply{stacks.getValue(root).forEach{put(json(it))}})
        }
        return JSONObject().put("schema",1).put("selected",selectedTab.route).put("tabs",tabs).put("history",history)
            .put("filter",filter).put("media_type",type).put("album",libraryAlbum)
            .put("selection",org.json.JSONArray(if(selected.size<=1000)selected.toList() else emptyList<String>())).put("selecting",selectionMode&&selected.size<=1000).toString()
    }
    fun restoreNavigation(value:String?) {
        if(navigationRestored)return;navigationRestored=true
        if(value.isNullOrBlank())return
        runCatching {
            val data=JSONObject(value);if(data.optInt("schema")!=1)return
            fun entry(data:JSONObject)=MobileNavigationEntry(MobilePage.from(data.getString("page")),data.optString("media"),data.optString("item"),data.optString("job"),data.optString("profile"),data.optString("status","all"),data.optBoolean("archived"),data.optString("key").ifBlank{UUID.randomUUID().toString()})
            val restoredTabs=MobilePage.roots.associateWith{entry(data.getJSONObject("tabs").getJSONObject(it.route))}
            val restoredStacks=MobilePage.roots.associateWith{root->val a=data.getJSONObject("history").getJSONArray(root.route);(0 until a.length()).map{entry(a.getJSONObject(it))}}
            val root=MobilePage.from(data.getString("selected"));require(root.root)
            tabEntries.putAll(restoredTabs);restoredStacks.forEach{(root,rows)->stacks.getValue(root).apply{clear();addAll(rows)}}
            selectedTab=root;restore(restoredTabs.getValue(root))
            filter=data.optString("filter","all").takeIf{it in setOf("all","pending","uploaded")}?:"all"
            type=data.optString("media_type","all");libraryAlbum=data.optString("album")
            val ids=data.optJSONArray("selection");selected=if(ids==null)emptySet() else (0 until ids.length()).map{ids.getString(it)}.toSet()
            selectionMode=data.optBoolean("selecting")
        }
    }
    fun navigate(destination:String){
        val target=MobilePage.from(destination)
        if(target.route==page)return
        if(target.root){tab(destination);return}
        navigationForward=true;stacks.getValue(selectedTab).add(entry())
        page=target.route;pageKey=UUID.randomUUID().toString();sheet=""
    }
    fun tab(destination:String){
        val target=MobilePage.from(destination);require(target.root)
        if(target==selectedTab){
            if(page!=target.route){navigationForward=false;val first=stacks.getValue(target).firstOrNull{it.page==target}?:MobileNavigationEntry(target);stacks.getValue(target).clear();restore(first);sheet=""}
            return
        }
        tabEntries[selectedTab]=entry();selectedTab=target
        restore(tabEntries[target]?:MobileNavigationEntry(target));sheet=""
    }
    fun back(){
        if(sheet.isNotEmpty()){sheet="";return}
        if(selectionMode&&page=="photos"){clearSelection();return}
        navigationForward=false
        stacks.getValue(selectedTab).removeLastOrNull()?.let(::restore)
    }
    fun open(media:MediaEntry){if(selectionMode&&page=="photos"){select(media.id);return};navigate("detail");activeMediaId=media.id}
    fun select(id:String){selectionMode=true;selected=if(id in selected)selected-id else selected+id}
    fun selectAll(ids:List<String>){selectionMode=true;selected=selected+ids}
    fun toggleSelection(){if(selectionMode)clearSelection() else selectionMode=true}
    fun clearSelection(){selectionMode=false;selected=emptySet()}
    fun clearMessage(){message=""}
    fun say(value:String){message=value}
    /** System activity results must not be dropped while a refresh is finishing. */
    fun afterAction(block:()->Unit){viewModelScope.launch{while(busy)delay(50);block()}}
    fun action(block:suspend ()->Unit) {
        if(busy)return
        busy=true
        viewModelScope.launch {
            try{block()}catch(error:Exception){val code=(error as? EngineFailure)?.code?:"ENGINE_ERROR";message=Messages.error(code);if(code=="LOGIN_REQUIRED"){loginRequired=true;graph.credentials.needsLogin(settings.value.account,true)}}
            finally{busy=false}
        }
    }
    fun proxyAction(block:suspend ()->Unit)=action {
        val id=graph.backup.activeJob.value
        val running=if(id.isNotBlank())graph.dao.job(id) else null
        if(running!=null)graph.backup.stopAndWait()
        try {block()} finally {
            if(running!=null&&running.state=="running") {
                graph.dao.job(id)?.takeIf{it.state=="paused"}?.let{graph.dao.putJob(it.copy(state="queued"));graph.scheduler.launchManual(it.allowMobile)}
            }
        }
    }
    fun refresh()=action {
        withContext(Dispatchers.IO){graph.media.scan()};permissionRevision++
    }
    fun permissionsChanged(){permissionRevision++;if(busy)afterAction{refresh()} else refresh()}
    fun login(token:String)=action {
        graph.login(token);loginRequired=false;message="账号已连接"
        if(graph.dao.nextJob(graph.preferences.get().account)!=null)graph.scheduler.launchManual()
        if(page=="connection")back()
        if(!graph.preferences.get().onboarded)navigate("folders")
    }
    fun logout()=action {
        graph.logout();loginRequired=false;sheet="";message="已退出，云端备份会保留";tab("settings")
        withContext(Dispatchers.Main){android.webkit.CookieManager.getInstance().removeAllCookies(null);android.webkit.WebStorage.getInstance().deleteAllData()}
    }
    fun switchAccount(onLogin:()->Unit)=action {
        sheet="";withContext(Dispatchers.Main){onLogin()}
    }
    fun selectAccount(email:String)=action {
        graph.selectAccount(email);loginRequired=false;albumChoices=emptyList();clearSelection()
        activeMediaId="";sheet="";if(page=="detail")back()
        message="已切换备份账号"
    }
    fun removeAccount(email:String)=action {
        graph.removeAccount(email);loginRequired=false;sheet="";clearSelection();message="已从此设备移除账号，云端内容保留"
        withContext(Dispatchers.Main){android.webkit.CookieManager.getInstance().removeAllCookies(null);android.webkit.WebStorage.getInstance().deleteAllData()}
    }
    fun setProxy(enabled:Boolean,scheme:String,host:String,port:Int,username:String,password:String)=action {
        val config=ProxyConfig(enabled,scheme.lowercase(),host.trim(),port,username.trim())
        if(enabled) {
            when(val validation=ProxyPolicy.validate(graph.harborCore,config)) {
                ProxyPolicy.Validation.Valid -> {}
                is ProxyPolicy.Validation.Invalid -> {
                    // The core reports INVALID_PROXY; the UI wording lives
                    // under PROXY_INVALID (plus the TLS-specific message).
                    val code=if(validation.error=="INVALID_PROXY")"PROXY_INVALID" else validation.error
                    message="代理设置无效：${Messages.error(code)}"; return@action
                }
            }
        }
        graph.preferences.boolean("proxyEnabled",enabled)
        graph.preferences.string("proxyScheme",config.scheme)
        graph.preferences.string("proxyHost",config.host)
        graph.preferences.integer("proxyPort",config.port)
        graph.preferences.string("proxyUser",config.username)
        if(password.isNotEmpty())graph.credentials.saveProxyCredential(password)
        message=if(enabled)"代理设置已保存" else "代理已关闭"
        sheet=""
    }
    fun setBoolean(key:String,value:Boolean)=action {
        if(key=="wifi"){applyNetworkPolicy(if(value)"none" else "all");return@action}
        val active=if(key=="proxyEnabled")graph.backup.activeJob.value.takeIf{it.isNotBlank()}?.let{graph.dao.job(it)} else null
        if(active!=null)graph.backup.stopAndWait()
        if (key=="auto" && value && !settings.value.onboarded) { navigate("folders"); return@action }
        if (key=="wifi") graph.preferences.mobile(if(value) "none" else "all")
        else graph.preferences.boolean(key,value)
        if(key=="wifi") { graph.backup.stopAndWait() }
        graph.scheduler.configure()
        if(active!=null&&active.state=="running")graph.dao.job(active.id)?.takeIf{it.state=="paused"}?.let{graph.dao.putJob(it.copy(state="queued"));graph.scheduler.launchManual(it.allowMobile)}
    }
    fun restoreDefaults()=action { graph.backup.stopAndWait(); graph.preferences.restoreBackupDefaults(); graph.scheduler.configure(); message="已恢复备份默认设置，请重新确认范围" }
    fun applyConfirmationPolicy()=action {
        val settings=graph.preferences.get()
        jobs.value.filter{it.account==settings.account}.forEach { job ->
            if(graph.dao.items(job.id).any{it.state=="waiting_confirmation"})
                graph.dao.putJob(job.copy(strictConfirmation=settings.strictConfirmation))
        }
        graph.scheduler.launchManual()
        message="已更新等待确认任务的检查设置"
    }
    fun savePipeline(mode:String,limits:String,execution:String)=action {
        val origin=pageKey
        graph.preferences.string("pipelineLimits",limits)
        graph.preferences.string("pipelineMode",mode)
        graph.preferences.string("executionPolicy",execution)
        graph.scheduler.configure();backFrom(origin)
    }
    fun pauseBatch(ids:List<String>)=action { graph.backup.pauseJobs(ids) }
    fun resumeBatch(ids:List<String>)=action { graph.backup.resumeJobs(ids); graph.scheduler.launchManual() }
    fun removeBatch(ids:List<String>)=action {
        ids.forEach { id ->
            graph.dao.items(id).filter{it.state !in setOf("done","exists","cancelled")}.forEach{graph.backup.itemAction(it.id,"remove")}
            if(graph.dao.items(id).all{it.hidden})graph.dao.hideJob(id)
        }
    }
    fun archiveSelected(ids:List<String>,restore:Boolean)=action{ids.forEach{graph.dao.archiveItem(it,if(restore)0 else System.currentTimeMillis())}}
    fun archiveBatch(ids:List<String>,restore:Boolean=false)=action {
        val time=if(restore)0 else System.currentTimeMillis()
        ids.forEach { id->
            val members=graph.dao.items(id)
            members.filter{it.state in setOf("done","exists")}.forEach{graph.dao.archiveItem(it.id,time)}
            if(members.all{it.state in setOf("done","exists","cancelled")})graph.dao.archiveJob(id,time)
        }
    }

    fun setAllFolders(value:Boolean)=action {
        graph.preferences.boolean("allFolders",value)
        graph.scheduler.configure()
        if(graph.preferences.get().onboarded)graph.scheduler.checkSoon()
    }
    fun setString(key:String,value:String)=action {
        graph.preferences.string(key,value);graph.scheduler.configure()
    }
    fun setInteger(key:String,value:Int)=action {graph.preferences.integer(key,value)}
    private suspend fun applyNetworkPolicy(value:String) {
        val active=graph.backup.activeJob.value.takeIf{it.isNotBlank()}?.let{graph.dao.job(it)}
        if(active!=null)graph.backup.stopAndWait()
        graph.preferences.mobile(value)
        if(active!=null&&active.state=="running")graph.dao.job(active.id)?.takeIf{it.state=="paused"}?.let{graph.dao.putJob(it.copy(state="queued"))}
        graph.scheduler.configure();graph.scheduler.restorePending();sheet=""
    }
    fun setMobile(value:String)=action {applyNetworkPolicy(value)}
    fun folder(key:String,checked:Boolean)=action {
        val current=graph.preferences.get();var chosen=current.folders
        if(current.allFolders)return@action
        if(key=="@camera")chosen=if(checked)chosen+key else chosen-key
        else {
            val mediaFolder=media.value.find{it.folderKey==key}
            if(mediaFolder?.folderName.equals("Camera",true)&&"@camera" in chosen)chosen=chosen-"@camera"
            chosen=if(checked)chosen+key else chosen-key
        }
        graph.preferences.folders(chosen);graph.scheduler.configure()
    }
    fun addTree(uri:Uri)=action {
        withContext(Dispatchers.IO) {
            runCatching { graph.app.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }.getOrElse { graph.app.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val current=graph.preferences.get();graph.preferences.trees(current.trees+uri.toString());graph.preferences.folders(current.folders+uri.toString());graph.media.scan()
        };permissionRevision++
    }
    fun importFiles(uris:List<Uri>)=action {
        for(uri in uris)runCatching{graph.app.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        val ids=graph.media.importUris(uris);selected=ids.toSet();selectionMode=true;tab("photos");selected=ids.toSet();selectionMode=true
    }
    fun confirmScope(all:Boolean,folders:Set<String>,automatic:Boolean)=action {
        val origin=pageKey
        if(graph.preferences.get().account.isBlank()){navigate("accounts");return@action}
        graph.preferences.folders(folders);graph.preferences.boolean("allFolders",all)
        graph.preferences.boolean("onboarded",true);graph.preferences.boolean("auto",automatic)
        graph.scheduler.configure();if(automatic)graph.scheduler.checkSoon();backFrom(origin)
    }
    fun setAlbumSync(enabled:Boolean)=action {graph.preferences.string("albumMode",if(enabled)"folder" else "none");graph.scheduler.configure()}
    var inspectedProfile by mutableStateOf("");private set
    fun showProfile(id:String){navigate("proxy-profile");inspectedProfile=id}
    fun finishSetup()=action {
        graph.preferences.boolean("onboarded",true);graph.preferences.boolean("auto",true);graph.scheduler.configure();graph.scheduler.checkSoon();tab("backup");message="自动备份已开启"
    }
    fun start(ids:List<String>?=null,allowMobile:Boolean=false) {
        if(settings.value.account.isEmpty()||loginRequired){navigate("connection");return}
        if(settings.value.wifi&&!allowMobile&&!graph.backup.onWifi()){pendingManualIds=ids;sheet=if(ids!=null)"mobile-selected" else "mobile-all";return}
        action {
            val submission=graph.backup.enqueue(ids,allowMobile=allowMobile)
            if(submission==null) {
                val account=graph.preferences.get().account
                if(graph.dao.pendingManual(account)>0){graph.scheduler.launchManual(allowMobile);message="正在启动待备份队列"}
                else message="没有需要新增备份的项目"
            } else {
                graph.scheduler.launchManual(allowMobile)
                message=if(submission.created>0)"已加入备份，将在后台处理" else "这些照片正在备份中"
                watchSubmission(submission,graph.preferences.get().account)
            }
        }
    }
    private val watched=mutableSetOf<String>()
    private fun watchSubmission(submission:BackupSubmission,account:String) {
        val key=submission.itemIds.sorted().joinToString("|")
        if(!watched.add(key))return
        viewModelScope.launch {
            try {
                val chunks=submission.itemIds.chunked(800).map{graph.dao.observeItems(it)}
                val finished=combine(combine(chunks){it.flatMap{rows->rows}},settings){rows,s->rows to s.account}
                    .first{(rows,current)->current!=account||(rows.size==submission.itemIds.size&&rows.all{it.state in BackupPolicy.terminalItems||it.state=="album_pending"})}
                if(finished.second!=account)return@launch
                val rows=finished.first
                val success=rows.count{it.state in setOf("done","exists")}
                val failed=rows.count{it.state in setOf("failed","album_pending")}
                message=when {
                    failed>0->"备份结束：成功 $success 项，$failed 项需要处理，可在任务记录重试"
                    rows.all{it.state=="exists"}->"云端已有相同内容，已确认备份；可在 Google 相册中使用官方“释放空间”"
                    rows.all{it.state=="cancelled"}->"本次备份已取消"
                    else->"备份完成，共 $success 项；云端已确认的内容，可在 Google 相册中使用官方“释放空间”"
                }
            } finally {watched.remove(key)}
        }
    }
    fun pause()=action {graph.backup.pause()}
    fun resume(id:String)=action {graph.backup.resume(id)}
    fun retry(id:String)=action {graph.backup.retry(id)}
    fun retryAll(ids:List<String>)=action {ids.distinct().forEach { graph.backup.retry(it) }}
    fun end(id:String)=action {graph.backup.end(id)}
    fun albums() {
        sheet="albums"
        action {graph.ensureAccount();val response=graph.engine.call("albums");val array=response.getJSONArray("albums");albumChoices=(0 until array.length()).map{array.getJSONObject(it).let{v->v.getString("id") to v.getString("name")}}}
    }
    fun album(mode:String,value:String="")=action {
        graph.preferences.string("albumMode",mode);graph.preferences.string("albumValue",value);sheet=""
    }
    fun checkAccount()=action{graph.ensureAccount();loginRequired=false;message="账号连接正常，登录凭据会自动续期"}
    /**
     * Official free-up-space handoff: PicHarbor no longer deletes local
     * originals itself. Only cloud-confirmed backups are pointed at the
     * official Google Photos free-up-space; opening the link does not change
     * any task state and does not mean cleanup is done.
     */
    fun openOfficialFreeSpace()=action {
        val outcome=FreeSpaceGuide.launch(graph.app)
        Diagnostics.event("ui.intent",attrs=JSONObject().put("kind","free_space_open").put("outcome",outcome.name.lowercase()))
        message=when(outcome) {
            FreeSpaceGuide.Outcome.OPENED_LINK->"已打开 Google 相册释放空间，请在官方页面确认清理"
            FreeSpaceGuide.Outcome.OPENED_PHOTOS->"已打开 Google 相册，请使用官方“释放空间”"
            FreeSpaceGuide.Outcome.OPENED_STORE->"未安装 Google 相册，已打开应用商店"
            FreeSpaceGuide.Outcome.MANUAL_GUIDANCE->FreeSpaceGuide.MANUAL_STEPS
        }
    }
    fun report():String=JSONObject().put("app","PicHarbor").put("version",BuildConfig.VERSION_NAME).put("author","cc_cece")
        .put("device",android.os.Build.MODEL).put("android",android.os.Build.VERSION.SDK_INT).put("account_connected",settings.value.account.isNotBlank())
        .put("visible_media",media.value.size).put("completed_jobs",jobs.value.count{it.state=="completed"})
        .put("failed_items",items.value.count{it.state=="failed"})
        .put("saved_accounts",accounts.value.size)
        .put("task_states",JSONObject(items.value.groupingBy{it.state}.eachCount()))
        .put("task_errors",JSONObject(items.value.filter{it.error.isNotBlank()}.groupingBy{it.error}.eachCount()))
        .put("harbor_core",graph.harborCore.status())
        .put("wifi_available",graph.backup.onWifi()).put("auto_enabled",settings.value.auto)
        .put("time",System.currentTimeMillis()).toString(2)
}

fun formatSize(bytes:Long):String=when {
    bytes>=1024L*1024*1024->"%.1f GB".format(bytes.toDouble()/(1024*1024*1024))
    bytes>=1024*1024->"%.1f MB".format(bytes.toDouble()/(1024*1024))
    bytes>=1024->"%.1f KB".format(bytes/1024.0)
    else->"$bytes B"
}
