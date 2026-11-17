@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun TaskProxyScreen(vm:HarborViewModel,data:HarborUiData) {
    val id by vm.inspectedJob.collectAsState()
    val job=data.jobs.firstOrNull{it.id==id}
    val global by vm.graph.proxy.library.collectAsState()
    val saved=remember(job?.proxyPolicy){runCatching{org.json.JSONObject(job?.proxyPolicy.orEmpty())}.getOrDefault(org.json.JSONObject())}
    var follow by rememberSaveable(job?.id){mutableStateOf(job?.proxyPolicy.isNullOrBlank())}
    var automatic by rememberSaveable(job?.id){mutableStateOf(saved.optString("selection","automatic")=="automatic")}
    var primary by rememberSaveable(job?.id){mutableStateOf(saved.optString("primary",global.optString("primary")))}
    var direct by rememberSaveable(job?.id){mutableStateOf(saved.optString("exhaustion","wait")=="direct")}
    var backups by rememberSaveable(job?.id){mutableStateOf(ArrayList(saved.optJSONArray("backups")?.let{array->(0 until array.length()).map{array.getString(it)}}?:emptyList()))}
    ProductPage("任务连接策略",vm) {
        ProductSection {
            ToggleRow("跟随全局","关闭后仅更改此任务",follow,enabled=!vm.busy){follow=it}
        }
        if(!follow) {
            ProductSection("节点选择与切换") {
                ToggleRow("自动选择节点","保持正常连接，失败时切换",automatic,enabled=!vm.busy){automatic=it}
                if(!automatic)vm.graph.proxy.profile()?.getJSONArray("nodes")?.let{nodes->
                    for(i in 0 until nodes.length()) {
                        val name=nodes.getJSONObject(i).getString("name")
                        NativeChoiceRow(name,primary==name,enabled=!vm.busy,localized=false){primary=name}
                        if(primary!=name)NativeCheckRow("备用节点",name in backups,enabled=!vm.busy){checked->backups=ArrayList(if(checked)backups+name else backups-name)}
                    }
                }
                ToggleRow("所有节点不可用时允许直连","关闭时等待连接",direct,enabled=!vm.busy){direct=it}
            }
        }
        ProductSection {
            Button(onClick={
                val policy=if(follow)"" else org.json.JSONObject().put("selection",if(automatic)"automatic" else "manual").put("primary",primary)
                    .put("backups",org.json.JSONArray(backups.filter{it!=primary})).put("exhaustion",if(direct)"direct" else "wait").toString()
                vm.taskProxy(policy)
            },enabled=job!=null&&!vm.busy){LText("保存")}
            Helper("只用于此任务，不会改变全局设置。修改后重新连接，手动暂停的任务保持暂停。")
        }
    }
}
