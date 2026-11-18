@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.*

/** Native screen around the existing decoder; camera follows the Activity lifecycle. */
class QrScannerActivity: ComponentActivity() {
    companion object { const val CONTENT = "qr_content" }
    private var preview: DecoratedBarcodeView? = null
    private var permitted by mutableStateOf(false)
    private var denied by mutableStateOf(false)
    private var failed by mutableStateOf(false)
    private var delivered = false
    private var torch by mutableStateOf(false)
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        permitted = it; denied = !it
    }
    private fun hasPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(); permitted = hasPermission()
        setContent {
            val graph = (application as PicHarborApp).graph
            val settings by graph.preferences.flow.collectAsState(initial=HarborSettings())
            MobileLocale(settings.language) { PicHarborTheme(settings) {
                Scaffold(topBar={CenterAlignedTopAppBar(title={LText("扫描二维码")},navigationIcon={
                    IconButton(onClick={finish()}){LIcon(Icons.Outlined.Close,"关闭")}
                },actions={
                    if(permitted&&!failed&&packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))IconToggleButton(checked=torch,onCheckedChange={
                        torch=it; if(it)preview?.setTorchOn() else preview?.setTorchOff()
                    }){LIcon(if(torch)Icons.Outlined.FlashlightOff else Icons.Outlined.FlashlightOn,if(torch)"关闭手电筒" else "打开手电筒")}
                })},bottomBar={
                    Surface { Column(Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Helper("将二维码放入取景框，识别后自动返回。也可以从图片导入。")
                        OutlinedButton(onClick={setResult(Activity.RESULT_FIRST_USER);finish()},modifier=Modifier.fillMaxWidth()){LText("从图片识别二维码")}
                    } }
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.Center) {
                        if(permitted&&!failed)AndroidView(factory={context ->
                            DecoratedBarcodeView(context).also { view ->
                                preview=view; view.setStatusText("")
                                view.barcodeView.decoderFactory=DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                                view.barcodeView.addStateListener(object:CameraPreview.StateListener {
                                    override fun previewSized() {}
                                    override fun previewStarted() {}
                                    override fun previewStopped() {}
                                    override fun cameraClosed() {}
                                    override fun cameraError(error:Exception) { failed=true }
                                })
                                view.decodeContinuous(object:BarcodeCallback {
                                    override fun barcodeResult(result:BarcodeResult?) {
                                        val value=result?.text?.takeIf{it.isNotBlank()}?:return
                                        if(delivered||isFinishing)return
                                        delivered=true;view.pause()
                                        setResult(Activity.RESULT_OK,Intent().putExtra(CONTENT,value));finish()
                                    }
                                    override fun possibleResultPoints(points:MutableList<ResultPoint>?) {}
                                })
                                if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))view.resume()
                            }
                        },modifier=Modifier.fillMaxSize())
                        else Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            LIcon(Icons.Outlined.QrCodeScanner,null,Modifier.size(56.dp))
                            LText(if(failed)"相机暂不可用，请重试或从图片导入。" else "允许使用相机以扫描二维码。")
                            Button(onClick={
                                if(failed){failed=false;torch=false}
                                else if(denied&&!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName")))
                                } else permission.launch(Manifest.permission.CAMERA)
                            }) {LText(if(failed)"重试" else if(denied&&!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))"打开系统设置" else "允许使用相机")}
                        }
                    }
                }
                LaunchedEffect(Unit){if(!permitted)permission.launch(Manifest.permission.CAMERA)}
                DisposableEffect(Unit){onDispose{preview?.pause();preview=null}}
            } }
        }
    }
    override fun onResume() { super.onResume();permitted=hasPermission();if(permitted&&!failed)preview?.resume() }
    override fun onPause() { preview?.pause();torch=false;preview?.setTorchOff();super.onPause() }
}
