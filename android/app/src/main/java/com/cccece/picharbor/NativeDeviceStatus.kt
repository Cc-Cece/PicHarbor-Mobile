package com.cccece.picharbor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

data class NativeDeviceStatus(val network:String,val validated:Boolean,val powerSaving:Boolean)

/** Observes system state only; never probes the Internet or changes backup policy. */
@Composable
fun rememberNativeDeviceStatus():NativeDeviceStatus {
    val context=LocalContext.current.applicationContext
    val connectivity=remember(context){context.getSystemService(ConnectivityManager::class.java)}
    val power=remember(context){context.getSystemService(PowerManager::class.java)}
    fun read():NativeDeviceStatus {
        val caps=runCatching{connectivity.getNetworkCapabilities(connectivity.activeNetwork)}.getOrNull()
        val name=when {
            caps==null->"未连接"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)->"Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)->"蜂窝网络"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)->"以太网"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)->"VPN"
            else->"其他网络"
        }
        return NativeDeviceStatus(name,caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true,power.isPowerSaveMode)
    }
    val status by produceState(read(),context) {
        val callback=object:ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network:Network){value=read()}
            override fun onLost(network:Network){value=read()}
            override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){value=read()}
        }
        val receiver=object:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){value=read()}}
        val networkRegistered=runCatching{connectivity.registerDefaultNetworkCallback(callback)}.isSuccess
        val powerRegistered=runCatching {
            val filter=IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(receiver,filter)
        }.isSuccess
        value=read()
        awaitDispose {
            if(networkRegistered)runCatching{connectivity.unregisterNetworkCallback(callback)}
            if(powerRegistered)runCatching{context.unregisterReceiver(receiver)}
        }
    }
    return status
}
