@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DetailScreen(vm:HarborViewModel,data:HarborUiData) {
    val locale=androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val selected=vm.activeMedia
    if(selected==null) {
        ProductPage("照片详情",vm){EmptyState("没有可显示的项目","调整筛选条件或相册访问权限。")}
        return
    }
    val media=data.media.firstOrNull{it.id==selected.id}?:selected
    val status=data.status(media)
    val pair=remember(media,data.media){if(media.video)null else LivePhotoPairing.findPair(media,data.media)}
    var playingLive by remember(media.id){mutableStateOf(false)}
    var filename by remember{mutableStateOf(false)}
    val context=LocalView.current.context
    val clipboard=androidx.compose.ui.platform.LocalClipboardManager.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle("照片详情",vm,action={
            IconButton(onClick={vm.navigate("information")}){LIcon(Icons.Outlined.Info,"详情")}
            IconButton(onClick={
                val sources=arrayListOf(Uri.parse(media.id)).apply{if(pair!=null)add(Uri.parse(pair.id))}
                val intent=if(sources.size>1)android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM,sources)
                    else android.content.Intent(android.content.Intent.ACTION_SEND).setType(media.mime).putExtra(android.content.Intent.EXTRA_STREAM,sources.first())
                context.openProductIntent(android.content.Intent.createChooser(intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),""))
            }){LIcon(Icons.Outlined.Share,"分享")}
        })
        if(media.video||playingLive)VideoPlayer(if(playingLive)pair?.id?:media.id else media.id,autoplay=playingLive) else {
            var scale by remember(media.id){mutableFloatStateOf(1f)}
            var x by remember(media.id){mutableFloatStateOf(0f)};var y by remember(media.id){mutableFloatStateOf(0f)}
            Box(Modifier.fillMaxWidth().height(370.dp).background(Color.Black).clipToBounds().pointerInput(media.id){detectTransformGestures{_,pan,zoom,_->
                scale=(scale*zoom).coerceIn(1f,5f);x=if(scale>1f)(x+pan.x).coerceIn(-size.width*scale/2,size.width*scale/2) else 0f;y=if(scale>1f)(y+pan.y).coerceIn(-size.height*scale/2,size.height*scale/2) else 0f
            }}) {
                AsyncImage(Uri.parse(media.id),media.name,modifier=Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=x;translationY=y},contentScale=ContentScale.Fit)
            }
        }
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            TextButton(onClick={filename=true}){LText(media.name,localize=false,style=MaterialTheme.typography.titleMedium,maxLines=1)}
            LText(Instant.ofEpochMilli(media.captured).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM).withLocale(locale)),localize=false)
            LText(formatSize(media.size+(pair?.size?:0))+" · "+if(pair!=null||media.pairingID==LivePhotoPairing.EMBEDDED_MOTION)"Live Photo" else if(media.video)localize("视频") else localize("照片"),localize=false,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(pair!=null||media.pairingID==LivePhotoPairing.EMBEDDED_MOTION)TextButton(onClick={playingLive=!playingLive}){LText(if(playingLive)"显示静态照片" else "播放实况")}
            Button(onClick={vm.start(listOf(media.id))},enabled=!vm.busy&&data.settings.account.isNotEmpty(),modifier=Modifier.fillMaxWidth()){LIcon(Icons.Outlined.CloudUpload,null);LText("备份此项目")}
            data.itemMap[media.id]?.let{item->SettingsRow(phaseLabel(item.state),icon=Icons.Outlined.History,onClick={vm.showItem(item)})}

        }
    }
    if(filename)AlertDialog(onDismissRequest={filename=false},title={LText("完整文件名")},text={androidx.compose.foundation.text.selection.SelectionContainer{LText(media.name,localize=false)}},confirmButton={TextButton(onClick={filename=false}){LText("完成")}},dismissButton={TextButton(onClick={clipboard.setText(androidx.compose.ui.text.AnnotatedString(media.name))}){LText("复制文件名")}})

}
@Composable
fun DetailRow(label:String,value:String,valueLocalized:Boolean=true){Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.SpaceBetween){LText(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);LText(value,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(start=15.dp),localize=valueLocalized)};HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)}
@Composable
fun VideoPlayer(uri:String,autoplay:Boolean=false) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var error by remember(uri){mutableStateOf(false)}
    val player=remember(uri){ExoPlayer.Builder(context).build().apply{
        addListener(object:androidx.media3.common.Player.Listener{override fun onPlayerError(value:androidx.media3.common.PlaybackException){error=true}})
        setMediaItem(MediaItem.fromUri(uri));playWhenReady=autoplay;prepare()
    }}
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP)player.pause()}
        lifecycle.addObserver(observer)
        onDispose{lifecycle.removeObserver(observer);player.release()}
    }
    if(error)Helper("无法预览动态内容，请检查文件与系统格式支持。")
    AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true}},modifier=Modifier.fillMaxWidth().height(350.dp))
}
