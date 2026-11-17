@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Presentation filters never change batch identity or the underlying task set. */
object ActivityPresentation {
    fun visible(jobs:List<BackupJob>,source:String,status:String,archived:Boolean):List<BackupJob> = jobs.filter {
        !it.hidden && (it.archivedAt>0)==archived &&
            (source=="all" || it.automatic==(source=="automatic")) &&
            (status=="all" || (it.state=="completed")== (status=="completed"))
    }
}

@Composable
fun ConfirmationSettingsScreen(vm:HarborViewModel,data:HarborUiData) {
    ProductPage("高级备份检查",vm) {
        ProductSection(footer="默认关闭。开启后，已保存的内容仍会显示已备份，任务会继续等待动态处理，不会重新上传。") {
            ToggleRow("等待云端完成动态处理","",data.settings.strictConfirmation,enabled=!vm.busy){vm.setBoolean("strictConfirmation",it)}
        }
        ProductSection(footer="只影响等待确认的任务，保留手动暂停，不改变已完成任务。") {
            ProductAction("对等待确认的任务使用此设置",enabled=!vm.busy&&data.items.any{it.state=="waiting_confirmation"},onClick=vm::applyConfirmationPolicy)
        }
    }
}

@Composable
fun ArchiveSettingsScreen(vm:HarborViewModel,data:HarborUiData) {
    val focus=LocalFocusManager.current
    var days by remember(data.settings.archiveDays){mutableStateOf(data.settings.archiveDays.toString())}
    ProductPage("活动记录",vm) {
        ProductSection(footer="归档只改变活动列表，不删除照片或备份记录。") {
            ToggleRow("自动归档","仅归档已完成的任务",data.settings.archiveEnabled,enabled=!vm.busy){vm.setBoolean("archiveEnabled",it)}
            OutlinedTextField(value=days,onValueChange={days=it},label={LText("保留天数")},isError=days.toIntOrNull() !in 1..90,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),modifier=Modifier.fillMaxWidth().padding(16.dp))
            ProductAction("保存",enabled=!vm.busy&&days.toIntOrNull() in 1..90){days.toIntOrNull()?.let{vm.setInteger("archiveDays",it)}}
        }
        ProductSection {SettingsRow("归档记录",onClick={vm.navigate("archives")})}
    }
}

@Composable
fun InterfaceScreen(vm:HarborViewModel,data:HarborUiData) {
    ProductPage("界面",vm) {
        ProductSection("语言") {
            ProductPicker("语言",data.settings.language,listOf("system" to "跟随系统","zh-Hans" to "简体中文","en" to "English","yue-Hant-HK" to "粵語（香港）")){vm.setString("language",it)}
        }
        ProductSection("外观") {
            ProductPicker("外观",data.settings.theme,listOf("system" to "跟随系统","light" to "浅色","dark" to "深色")){vm.setString("theme",it)}
        }
    }
}
