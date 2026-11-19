package com.cccece.picharbor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Persisted native overlay. The ordinary DataStore continues to hold personal preferences. */
object RemotePolicies {
    val revision=MutableStateFlow(0)
    private fun store(c:Context)=c.getSharedPreferences("remote_policies",Context.MODE_PRIVATE)
    fun data(c:Context,account:String)=runCatching{JSONObject(store(c).getString("scope:$account","{}")!!)}.getOrDefault(JSONObject())
    fun locked(c:Context,key:String,account:String=""):Boolean {
        val root=data(c,account.ifEmpty{store(c).getString("active","").orEmpty()})
        return root.optJSONArray("forced")?.let{(0 until it.length()).any{i->it.optString(i)==key}}?:false
    }
    fun save(c:Context,account:String,value:JSONObject){store(c).edit().putString("scope:$account",value.toString()).putString("active",account).apply();revision.value++}
    fun overlay(c:Context,s:HarborSettings):HarborSettings {
        val p=data(c,s.account);val values=p.optJSONObject("values")?:return s
        fun text(k:String,d:String)=if(locked(c,k,s.account))values.optString(k,d) else d
        fun bool(k:String,d:Boolean)=if(locked(c,k,s.account))values.optBoolean(k,d) else d
        fun number(k:String,d:Int)=if(locked(c,k,s.account))values.optInt(k,d) else d
        return s.copy(auto=bool("auto",s.auto),wifi=bool("wifi",s.wifi),restart=bool("restart",s.restart),quality=text("quality",s.quality),
            theme=text("theme",s.theme),palette=text("palette",s.palette),language=text("language",s.language),albumMode=text("albumMode",s.albumMode),
            livePhotoPairing=text("livePhotoPairing",s.livePhotoPairing),pipelineMode=text("pipelineMode",s.pipelineMode),pipelineLimits=text("pipelineLimits",s.pipelineLimits),executionPolicy=text("executionPolicy",s.executionPolicy),
            concurrency=number("concurrency",s.concurrency),timeout=number("timeout",s.timeout),retries=number("retries",s.retries),
            archiveEnabled=bool("archiveEnabled",s.archiveEnabled),archiveDays=number("archiveDays",s.archiveDays),activitySource=text("activitySource",s.activitySource),activityStatus=text("activityStatus",s.activityStatus),activityGrouping=text("activityGrouping",s.activityGrouping),archiveSource=text("archiveSource",s.archiveSource))
    }
    val labels=mapOf("上传画质" to "quality","备份方式" to "livePhotoPairing","并发模式" to "pipelineMode","主题" to "theme","语言" to "language","主题颜色" to "palette","自动归档" to "archiveEnabled","归档时间" to "archiveDays","归档天数" to "archiveDays","保留天数" to "archiveDays","保留相册分类" to "albumMode","仅 Wi-Fi" to "wifi","仅通过 Wi-Fi 备份" to "wifi","开启自动备份" to "auto","自动备份" to "auto","网络条件" to "wifi")
}

class RemoteServices(private val graph:AppGraph) {
    private val gate=Mutex()
    private val started=java.util.concurrent.atomic.AtomicBoolean(false)
    val startupUi=MutableStateFlow<JSONObject?>(null)
    val dialogUi=MutableStateFlow<JSONObject?>(null)
    private val dialogGate=Mutex()
    private val dialogs=java.util.ArrayDeque<JSONObject>()
    val hintUi=MutableStateFlow("")
    val update=MutableStateFlow<JSONObject?>(null)
    val revision=MutableStateFlow(0)
    private val context get()=graph.app
    private fun metadata()=JSONObject().put("platform","android").put("version",BuildConfig.VERSION_NAME).put("build",BuildConfig.VERSION_CODE.toString()).put("channel",if(BuildConfig.DEBUG)"debug" else "release").put("language",java.util.Locale.getDefault().toLanguageTag()).put("system",android.os.Build.VERSION.RELEASE)
    suspend fun call(action:String,payload:JSONObject=JSONObject()):JSONObject=gate.withLock {
        withContext(Dispatchers.IO){
            if(!graph.engineReady)return@withContext JSONObject().put("ok",false)
            val language=graph.preferences.get().language
            payload.put("context",metadata().put("language",if(language=="system")java.util.Locale.getDefault().toLanguageTag() else language).put("device_class","phone"))
            runCatching{JSONObject(Python.getInstance().getModule("harbor_remote").callAttr("dispatch",File(context.noBackupFilesDir,"services").absolutePath,action,payload.toString()).toString())}.getOrDefault(JSONObject().put("ok",false))
        }
    }
    fun start(){if(!started.compareAndSet(false,true))return;graph.scope.launch {
        graph.startup.await()
        call("sync")
        val result=call("startup");result.optJSONObject("ui")?.let{if(it.optString("type")=="dialog")showDialog(it) else startupUi.value=it}
        resolve()
        resolve();update.value=call("update").optJSONObject("update");revision.value++
        graph.scope.launch { while(isActive) { runCatching { snapshotFacts(); if(graph.visible.get())call("lifecycle",JSONObject().put("phase","heartbeat")); call("flush") }; delay(60_000) } }
        val work=androidx.work.PeriodicWorkRequestBuilder<RemoteFeedbackWorker>(15,java.util.concurrent.TimeUnit.MINUTES).setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build()
        androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork("remote-feedback",androidx.work.ExistingPeriodicWorkPolicy.KEEP,work)
        graph.preferences.flow.map{it.account}.distinctUntilChanged().drop(1).collect{resolve()}
    }}
    suspend fun showDialog(value:JSONObject)=dialogGate.withLock {
        if(dialogs.none{it.optString("id")==value.optString("id")&&it.optInt("revision",1)==value.optInt("revision",1)})dialogs.addLast(value)
        if(dialogUi.value==null)advanceDialog()
    }
    private suspend fun advanceDialog(){
        while(dialogs.isNotEmpty()){
            val result=call("dialog_prepare",JSONObject().put("dialog",dialogs.first))
            if(result.optString("status")=="already_seen"){dialogs.removeFirst();continue}
            dialogUi.value=result.optJSONObject("dialog");return
        }
        dialogUi.value=null
    }
    suspend fun completeDialog(dialog:JSONObject,button:JSONObject)=dialogGate.withLock {
        if(dialogUi.value!==dialog)return@withLock
        val result=call("dialog_complete",JSONObject().put("dialog",dialog).put("button",button.optString("id")))
        if(!result.optBoolean("ok"))return@withLock
        dialogUi.value=null
        if(dialogs.isNotEmpty())dialogs.removeFirst()
        val url=button.optString("url")
        if(url.startsWith("https://"))withContext(Dispatchers.Main){context.openProductIntent(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
        advanceDialog()
    }
    suspend fun resolve(){
        val settings=graph.preferences.rawGet();val values=JSONObject()
        settings.javaClass.declaredFields.filter{!java.lang.reflect.Modifier.isStatic(it.modifiers)}.forEach{f->runCatching{f.isAccessible=true;values.put(f.name,JSONObject.wrap(f.get(settings)))}}
        val result=call("resolve",JSONObject().put("scope",settings.account).put("values",values))
        if(!result.optBoolean("ok"))return
        RemotePolicies.save(context,settings.account,result)
        graph.preferences.remoteApply(result)
        graph.scheduler.configure()
    }
    fun intercept(key:String):Boolean {
        val account=context.getSharedPreferences("remote_policies",Context.MODE_PRIVATE).getString("active","").orEmpty()
        val data=RemotePolicies.data(context,account);val hints=data.optJSONObject("hints");val hint=hints?.optJSONObject(key)
        if(hint!=null){
            hints.remove(key);RemotePolicies.save(context,account,data);hintUi.value=hint.optString("message","此设置已由应用配置调整。")
            graph.scope.launch{call("hint",JSONObject().put("scope",hint.optString("scope",data.optString("scope"))).put("receipt",hint.optString("id")))}
        }
        return hint!=null||RemotePolicies.locked(context,key,account)
    }
    fun event(name:String,id:String="",durationMs:Long?=null,bytes:Long?=null){graph.scope.launch{call("event",JSONObject().put("name",name).put("id",id).put("duration_ms",durationMs).put("bytes",bytes))}}
    /** Only bounded, explicit facts cross this bridge; remote code chooses aggregation. */
    fun observe(name:String,fields:JSONObject=JSONObject(),id:String=""){graph.scope.launch{call("observe",JSONObject().put("name",name).put("fields",fields).put("id",id))}}
    fun lifecycle(phase:String){graph.scope.launch{graph.startup.await();if(started.get())call("lifecycle",JSONObject().put("phase",phase))}}
    private suspend fun snapshotFacts(){
        val settings=graph.preferences.get()
        val power=context.getSystemService(android.os.PowerManager::class.java)
        val memory=android.app.ActivityManager.MemoryInfo().also{context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it)}
        val connectivity=context.getSystemService(android.net.ConnectivityManager::class.java)
        val caps=connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val network=when {caps==null->"offline";caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)->"wifi";caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)->"cellular";else->"other"}
        val environment=JSONObject().put("os_version",android.os.Build.VERSION.RELEASE).put("brand",android.os.Build.BRAND.take(80)).put("model_family",android.os.Build.MODEL.take(80))
            .put("abi",android.os.Build.SUPPORTED_ABIS.firstOrNull()?:"unknown").put("ram_bucket",when{memory.totalMem<3L*1024*1024*1024->"under3gb";memory.totalMem<6L*1024*1024*1024->"3to6gb";else->"6gbplus"})
            .put("network",network).put("low_memory",memory.lowMemory).put("power_save",power.isPowerSaveMode).put("battery_optimization",if(power.isIgnoringBatteryOptimizations(context.packageName))"unrestricted" else "optimized")
            .put("font_scale",context.resources.configuration.fontScale.toDouble()).put("orientation",if(context.resources.configuration.orientation==2)"landscape" else "portrait").put("auth_state",if(settings.account.isBlank())"signed_out" else "selected")
        call("observe",JSONObject().put("name","environment.snapshot").put("fields",environment))
        call("observe",JSONObject().put("name","preferences.snapshot").put("fields",JSONObject().put("auto_backup",settings.auto).put("wifi_only",settings.wifi).put("quality",settings.quality).put("live_mode",settings.livePhotoPairing).put("album_mode",settings.albumMode).put("concurrency_mode",settings.pipelineMode).put("theme",settings.theme).put("proxy_enabled",settings.proxyEnabled)))
        call("observe",JSONObject().put("name","backup.conditions").put("fields",JSONObject().put("blocked_reason",when{settings.account.isBlank()->"signed_out";!settings.auto->"auto_off";network=="offline"->"offline";settings.wifi&&network!="wifi"->"wifi_required";else->"ready"})))
    }
    suspend fun batchFact(job:BackupJob,items:List<BackupItem>){
        val fields=JSONObject().put("status",when(job.state){"needs_attention"->"failed";"ended"->"cancelled";"auth_required","queued"->"waiting";else->job.state})
            .put("source",if(job.automatic)"automatic" else "manual").put("quality",job.quality).put("live_mode",job.liveStrategy)
            .put("media_total",items.size).put("media_success",items.count{it.state in setOf("done","exists")}).put("media_failed",items.count{it.state in setOf("failed","album_pending")}).put("media_skipped",items.count{it.state=="filename_skipped"})
            .put("bytes",items.filter{it.state in setOf("done","exists")}.sumOf{it.bytes}).put("retries",items.sumOf{maxOf(0,it.attempt-1)}).put("confirm_ms",items.sumOf{it.confirmationElapsedMs})
        call("observe",JSONObject().put("name","backup.batch").put("id",job.id).put("fields",fields))
    }
    suspend fun feedback(values:Map<String,String>,files:List<RemoteFile>):JSONObject {
        val account=graph.preferences.get().account
        val ref=MessageDigest.getInstance("SHA-256").digest((account+context.noBackupFilesDir.path).toByteArray()).joinToString(""){"%02x".format(it)}
        return call("feedback",JSONObject().put("values",JSONObject(values).put("email",account).put("account_ref",ref)).put("files",JSONArray(files.map{JSONObject().put("path",it.path).put("kind",it.kind).put("mime",it.mime)})))
    }
}
data class RemoteFile(val path:String,val kind:String,val mime:String)
val LocalRemotePolicyEnabled=staticCompositionLocalOf{true}
@Composable
fun RemoteSettingGuard(key:String,content:@Composable ()->Unit){
    val context=LocalContext.current;val revision by RemotePolicies.revision.collectAsState()
    val settings=(context.applicationContext as PicHarborApp).graph
    val account=context.getSharedPreferences("remote_policies",Context.MODE_PRIVATE).getString("active","").orEmpty()
    val data=remember(revision,account){RemotePolicies.data(context,account)}
    val intercept=RemotePolicies.locked(context,key,account)||data.optJSONObject("hints")?.has(key)==true
    Box{content();if(intercept)Box(Modifier.matchParentSize().clickable{settings.remote.intercept(key)})}
}
class RemoteFeedbackWorker(context:Context,parameters:androidx.work.WorkerParameters):androidx.work.CoroutineWorker(context,parameters){
    override suspend fun doWork():Result {
        val graph=(applicationContext as PicHarborApp).graph;graph.startup.await()
        return if(graph.remote.call("flush").optBoolean("ok"))Result.success() else Result.retry()
    }
}

@Composable
fun RemoteAlerts(vm:HarborViewModel){
    val dialog by vm.graph.remote.dialogUi.collectAsState()
    val dialogScope=rememberCoroutineScope()
    var completing by remember(dialog){mutableStateOf(false)}
    dialog?.let{value->
        val buttons=value.getJSONArray("buttons")
        fun finish(button:JSONObject){if(!completing){completing=true;dialogScope.launch{try{vm.graph.remote.completeDialog(value,button)}finally{completing=false}}}}
        AlertDialog(onDismissRequest={},title={Text(value.optString("title"))},text={Text(value.optString("message"))},confirmButton={TextButton(onClick={finish(buttons.getJSONObject(0))},enabled=!completing){Text(buttons.getJSONObject(0).optString("title"))}},dismissButton={if(buttons.length()>1)TextButton(onClick={finish(buttons.getJSONObject(1))},enabled=!completing){Text(buttons.getJSONObject(1).optString("title"))}})
    }
    val start by vm.graph.remote.startupUi.collectAsState();val hint by vm.graph.remote.hintUi.collectAsState()
    if(start?.has("items")==true)androidx.compose.ui.window.Dialog(onDismissRequest={vm.graph.remote.startupUi.value=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)){
        Surface(Modifier.fillMaxSize()){Column{TextButton(onClick={vm.graph.remote.startupUi.value=null},modifier=Modifier.align(androidx.compose.ui.Alignment.End)){LText("关闭")};Box(Modifier.weight(1f)){RemoteMoreScreen(vm,start)}}}
    }else if(start!=null)AlertDialog(onDismissRequest={vm.graph.remote.startupUi.value=null},title={Text(start!!.optString("title","PicHarbor"))},text={Text(start!!.optString("message"))},confirmButton={TextButton(onClick={vm.graph.remote.startupUi.value=null}){LText("好")}})
    if(hint.isNotEmpty())AlertDialog(onDismissRequest={vm.graph.remote.hintUi.value=""},text={Text(hint)},confirmButton={TextButton(onClick={vm.graph.remote.hintUi.value=""}){LText("好")}})
}

@Composable
fun RemoteMoreScreen(vm:HarborViewModel,initialPage:JSONObject?=null){
    val context=LocalContext.current;val scope=rememberCoroutineScope();val revision by vm.graph.remote.revision.collectAsState()
    var page by remember{mutableStateOf(JSONObject().put("title","更多").put("items",JSONArray()))}
    var parents by remember{mutableStateOf(listOf<JSONObject>())}
    val values=remember{mutableStateMapOf<String,String>()};var files by remember{mutableStateOf(listOf<RemoteFile>())}
    var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")}
    var diagnosticDialog by remember{mutableStateOf(false)}
    fun issue(text:String){message=text}
    fun generateDiagnostic(range:LogRange){
        if(busy||files.any{it.kind=="diagnostic"})return
        scope.launch {
            busy=true
            try {
                val attachment=withContext(Dispatchers.IO){
                    val target=File(context.cacheDir,"remote-diagnostic-${UUID.randomUUID()}.zip")
                    try {
                        target.outputStream().use{Diagnostics.export(it,range)}
                        require(target.length() in 1..5*1024*1024)
                        RemoteFile(target.absolutePath,"diagnostic","application/zip")
                    } catch(error:Exception){target.delete();throw error}
                }
                files=files+attachment
            }catch(_:Exception){issue("无法生成诊断文件，请缩小日期范围后重试。")}
            finally{busy=false}
        }
    }
    fun choose(uri:Uri,kind:String){scope.launch {
        try {
            val f=withContext(Dispatchers.IO){
                val bytes=context.contentResolver.openInputStream(uri)!!.use{input->
                    val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384)
                    while(true){val count=input.read(buffer);if(count<0)break;require(output.size()+count<=10*1024*1024);output.write(buffer,0,count)}
                    output.toByteArray()
                }
                require(bytes.size<=10*1024*1024)
                val output=File(context.cacheDir,"remote-${UUID.randomUUID()}${if(kind=="screenshot")".jpg" else ".zip"}")
                if(kind=="screenshot"){
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    require(bounds.outWidth>0&&bounds.outHeight>0)
                    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2
                    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample})?:error("IMAGE")
                    try{output.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,85,it)}}finally{bitmap.recycle()}
                }else output.writeBytes(bytes)
                require(output.length()<=5*1024*1024);RemoteFile(output.absolutePath,kind,if(kind=="screenshot")"image/jpeg" else "application/zip")
            }
            if(files.count{it.kind==kind}<(if(kind=="screenshot")3 else 1))files=files+f else{File(f.path).delete();issue("附件数量已达上限")}
        }catch(_:Exception){issue("无法添加附件，请选择 5 MiB 以内的图片或诊断文件。")}
    }}
    val images=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){uris->uris.take(3).forEach{choose(it,"screenshot")}}
    val logs=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{choose(it,"diagnostic")}}
    LaunchedEffect(revision){if(initialPage!=null)page=initialPage else if(parents.isEmpty()&&values.isEmpty()){val r=vm.graph.remote.call("more");r.optJSONObject("page")?.let{if(it.optString("type")=="dialog")vm.graph.remote.showDialog(it) else page=it}}}
    androidx.activity.compose.BackHandler(parents.isNotEmpty()){page=parents.last();parents=parents.dropLast(1)}
    fun activate(item:JSONObject){
        if(busy)return
        val url=item.optString("url");if(url.isNotEmpty()){if(url.startsWith("https://"))context.openProductIntent(Intent(Intent.ACTION_VIEW,Uri.parse(url)));return}
        scope.launch {
            busy=true
            try{
                if(item.optString("type")=="dialog"){vm.graph.remote.showDialog(item);return@launch}
                if(item.optString("capability")=="feedback.submit"){
                    val result=vm.graph.remote.feedback(values,files)
                    if(result.optBoolean("ok")){
                        issue(if(result.optBoolean("queued"))"反馈已保存在本机，联网后自动发送。" else "反馈已收到：${result.optString("id")}")
                        values.clear();files=emptyList()
                    }else issue(if(result.optString("error")=="INVALID_FEEDBACK")"请填写反馈内容。" else "提交失败，请检查附件后重试。")
                }else{
                    val result=vm.graph.remote.call("action",JSONObject().put("action",item.optString("action")).put("values",JSONObject(values)))
                    result.optJSONObject("page")?.let{if(it.optString("type")=="dialog")vm.graph.remote.showDialog(it) else {parents=parents+page;page=it;values.clear()}}
                }
            }finally{busy=false}
        }
    }
    CompositionLocalProvider(LocalRemotePolicyEnabled provides false){ProductPage(page.optString("title","更多"),vm,onBack={if(parents.isNotEmpty()){page=parents.last();parents=parents.dropLast(1)}else vm.back()}){
        val items=page.optJSONArray("items")?:JSONArray()
        RemoteItems(items,values,files,busy,::activate,{images.launch("image/*")},{diagnosticDialog=true},{logs.launch(arrayOf("application/zip"))},{f->files=files-f})
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }}
    DiagnosticExportDialog(visible=diagnosticDialog,onDismiss={diagnosticDialog=false},confirmLabel="生成并添加",onExport={range->diagnosticDialog=false;generateDiagnostic(range)})
    if(message.isNotEmpty())AlertDialog(onDismissRequest={message=""},text={LText(message)},confirmButton={TextButton(onClick={message=""}){LText("好")}})
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RemoteItems(items:JSONArray,values:MutableMap<String,String>,files:List<RemoteFile>,busy:Boolean,activate:(JSONObject)->Unit,images:()->Unit,diagnostics:()->Unit,logs:()->Unit,remove:(RemoteFile)->Unit){
    for(i in 0 until items.length()){
        val item=items.optJSONObject(i)?:continue;val id=item.optString("id","field$i");val title=item.optString("title")
        when(item.optString("type")){
            "group"->ProductSection(title){RemoteItems(item.optJSONArray("items")?:JSONArray(),values,files,busy,activate,images,diagnostics,logs,remove)}
            "text","result"->Text(item.optString("text"),modifier=Modifier.padding(16.dp))
            "input","multiline","number"->OutlinedTextField(value=values[id].orEmpty(),onValueChange={if(it.length<=10000)values[id]=it},label={Text(title)},singleLine=item.optString("type")!="multiline",minLines=if(item.optString("type")=="multiline")5 else 1,modifier=Modifier.fillMaxWidth().padding(vertical=6.dp),enabled=!busy)
            "toggle"->Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(title);Switch(values[id]=="true",onCheckedChange={values[id]=it.toString()},enabled=!busy)}
            "select"->ProductSection(title){val options=item.optJSONArray("options")?:JSONArray();for(j in 0 until options.length()){val option=options.getJSONObject(j);TextButton(onClick={values[id]=option.optString("value")},enabled=!busy){Text((if(values[id]==option.optString("value"))"✓ " else "")+option.optString("title"))}}}
            "attachments"->ProductSection(title){
                FlowRow{
                    TextButton(onClick=images,enabled=!busy&&files.count{it.kind=="screenshot"}<3){LText("添加截图")}
                    TextButton(onClick=diagnostics,enabled=!busy&&files.none{it.kind=="diagnostic"}){LText("诊断文件")}
                    TextButton(onClick=logs,enabled=!busy&&files.none{it.kind=="diagnostic"}){LText("从文件选择")}
                }
                files.forEach{f->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                        if(f.kind=="screenshot")coil.compose.AsyncImage(model=File(f.path),contentDescription="所选截图",modifier=Modifier.size(58.dp))
                        else Text("诊断文件",Modifier.padding(10.dp))
                        TextButton(onClick={remove(f)},enabled=!busy){LText("移除")}
                    }
                }
            }
            "button","dialog"->if(item.optString("capability")=="feedback.submit")Button(onClick={activate(item)},enabled=!busy,modifier=Modifier.fillMaxWidth().padding(vertical=10.dp)){Text(title)}else ProductSection{SettingsRow(title,onClick={activate(item)},enabled=!busy)}
            "loading"->LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
