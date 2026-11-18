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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
    var days by rememberSaveable(data.settings.account,data.settings.archiveDays){mutableStateOf(data.settings.archiveDays.toString())}
    ProductPage("活动记录",vm) {
        ProductSection(footer="归档只改变活动列表，不删除照片或备份记录。") {
            ToggleRow("自动归档","仅归档已完成的任务",data.settings.archiveEnabled,enabled=!vm.busy){vm.setBoolean("archiveEnabled",it)}
            OutlinedTextField(value=days,onValueChange={days=it},label={LText("保留天数")},isError=days.toIntOrNull() !in 1..90,supportingText={LText("1–90",localize=false)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),keyboardActions=KeyboardActions(onDone={focus.clearFocus()}),modifier=Modifier.fillMaxWidth().padding(16.dp))
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
        ProductSection("主题色") {
            val automatic=data.settings.palette=="system"
            ProductPicker("配色方式",if(automatic)"automatic" else "manual",listOf("automatic" to "自动","manual" to "手动"),description="自动使用系统动态配色（Android 12 及以上）；手动可选择主题色。"){mode->
                vm.setString("palette",if(mode=="automatic")"system" else if(automatic)"lavender" else data.settings.palette)
            }
            if(!automatic)MaterialPalettePicker(data.settings.palette,vm)
        }
    }
}

@Composable
private fun MaterialPalettePicker(value:String,vm:HarborViewModel) {
    val options=listOf(Triple("lavender","薰衣草",Color(0xFF67518E)),Triple("sage","鼠尾草",Color(0xFF386652)),Triple("peach","暖杏",Color(0xFF8C503B)),
        Triple("blue","晴空蓝",Color(0xFF365E9D)),Triple("rose","玫瑰红",Color(0xFF984061)),Triple("teal","青绿色",Color(0xFF006B5E)),Triple("amber","琥珀色",Color(0xFF795900)))
    var open by rememberSaveable{mutableStateOf(false)}
    SettingsRow("主题色",options.firstOrNull{it.first==value}?.second.orEmpty(),onClick={open=true}){LIcon(Icons.Outlined.ExpandMore,null)}
    ProductBottomSheet("主题色",{open=false},visible=open) {
        val dismiss=sheetDismissAction();val closing=LocalSheetClosing.current
        options.forEach{(key,label,color)->
            Row(Modifier.fillMaxWidth().heightIn(min=56.dp).selectable(value==key,enabled=!closing,role=Role.RadioButton,onClick={dismiss{open=false;vm.setString("palette",key)}}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(26.dp).background(color,CircleShape))
                LText(label,Modifier.weight(1f));RadioButton(value==key,onClick=null,enabled=!closing)
            }
        }
    }
}
