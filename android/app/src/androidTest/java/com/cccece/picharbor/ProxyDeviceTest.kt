package com.cccece.picharbor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.*

/** Explicit opt-in: exercises the saved proxy without exporting its configuration or uploading media. */
@RunWith(AndroidJUnit4::class)
class ProxyDeviceTest {
    @Test fun savedProxyConnectivity():Unit=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("phase")=="proxy_acceptance")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val graph=(instrumentation.targetContext.applicationContext as PicHarborApp).graph
        graph.startup.await()
        val proxy=graph.proxy
        check(graph.preferences.get().proxyEnabled){"Enable the in-app proxy before this opt-in test"}
        // Wait for encrypted configuration loading; no account or preference changes.
        withTimeout(15_000){while(proxy.profile()==null)delay(100)}
        proxy.testAll()
        withTimeout(500_000){while(proxy.testing.value)delay(100)}
        val profile=checkNotNull(proxy.profile())
        val nodes=profile.getJSONArray("nodes")
        val controller=ProxyManager::class.java.getDeclaredMethod("controller",String::class.java,String::class.java,String::class.java).apply{isAccessible=true}
        var encodedSuccess=0
        for(i in 0 until nodes.length()) {
            val name=nodes.getJSONObject(i).getString("name")
            if(runCatching{controller.invoke(proxy,"GET","/proxies/${android.net.Uri.encode(name)}/delay?timeout=5000&url=https%3A%2F%2Fwww.google.com%2Fgenerate_204",null)}.isSuccess)encodedSuccess++
        }
        var status=0
        var failure=""
        try {
            // UI callers must also be safe: proxy startup/controller I/O belongs off the main thread.
            val route=withContext(Dispatchers.Main){URI(proxy.url())}
            check(route.host=="127.0.0.1"&&route.port>0){"Expected in-app loopback proxy; direct fallback is not acceptance"}
            val connection=URL("https://www.google.com/generate_204").openConnection(Proxy(Proxy.Type.HTTP,InetSocketAddress(route.host,route.port))) as HttpURLConnection
            try {connection.connectTimeout=15_000;connection.readTimeout=15_000;status=connection.responseCode}
            finally{connection.disconnect()}
        } catch(e:Exception){failure=e.javaClass.simpleName}
        val report=JSONObject().put("nodes",nodes.length()).put("names_with_spaces",(0 until nodes.length()).count{nodes.getJSONObject(it).getString("name").contains(' ')})
            .put("healthy_after_test_all",proxy.health.value.values.count{it.healthy}).put("correctly_encoded_delay_success",encodedSuccess)
            .put("proxied_https_status",status).put("failure_type",failure)
        instrumentation.sendStatus(0,android.os.Bundle().apply{putString("proxy_acceptance",report.toString())})
        check(proxy.health.value.values.any{it.healthy}){"No saved node passed latency test; sanitized report=$report"}
        check(status==204){"HTTPS through the in-app loopback proxy failed; sanitized report=$report"}
    }
}
