@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@Composable
fun DiagnosticExportDialog(visible:Boolean=true,onDismiss:()->Unit,onExport:(LogRange)->Unit) {
    val zone=remember{ZoneId.systemDefault()}
    var custom by rememberSaveable{mutableStateOf(false)}
    var calendar by rememberSaveable{mutableStateOf(false)}
    var calendarVisible by remember{mutableStateOf(false)}
    LaunchedEffect(visible){if(!visible){calendar=false;calendarVisible=false}}
    var startDate by rememberSaveable{mutableStateOf<Long?>(null)}
    var endDate by rememberSaveable{mutableStateOf<Long?>(null)}
    val dates=rememberDateRangePickerState()
    val range=if(startDate!=null&&endDate!=null)LogRange.dates(
        Instant.ofEpochMilli(startDate!!).atZone(ZoneOffset.UTC).toLocalDate(),
        Instant.ofEpochMilli(endDate!!).atZone(ZoneOffset.UTC).toLocalDate(),zone) else null
    val configuration=LocalConfiguration.current
    val format=remember(configuration){java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(configuration.locales[0]).withZone(ZoneOffset.UTC)}
    val label=if(startDate!=null&&endDate!=null)format.format(Instant.ofEpochMilli(startDate!!))+" – "+format.format(Instant.ofEpochMilli(endDate!!)) else localize("选择起止日期")
    if(calendarVisible&&visible)DatePickerDialog(onDismissRequest={calendarVisible=false;calendar=false},confirmButton={TextButton(
        enabled=dates.selectedStartDateMillis!=null&&dates.selectedEndDateMillis!=null,
        onClick={
            val first=dates.selectedStartDateMillis;val last=dates.selectedEndDateMillis
            if(first!=null&&last!=null&&last>=first){startDate=first;endDate=last;calendarVisible=false;calendar=false}
        }){LText("确定")}},dismissButton={TextButton(onClick={calendarVisible=false;calendar=false}){LText("取消")}}) {
        CompactDateRangePicker(dates,modifier=Modifier.heightIn(max=minOf(480.dp,configuration.screenHeightDp.dp*.70f)))
    }
    ProductBottomSheet("导出日志",onDismiss,visible=visible&&!calendar,onHidden={if(visible&&calendar)calendarVisible=true}) {
        val dismiss=sheetDismissAction()
        val closing=LocalSheetClosing.current
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Choice("全部",!custom,"当前仍保留的全部日志"){custom=false}
            Choice("自定义日期",custom,"包含起止日期当天"){custom=true}
            if(custom)TextButton(onClick={calendar=true},enabled=!closing){LText(label,localize=false)}
            Helper("日期按 ${zone.id} 计算。日志最多保留 14 天或 64 MiB，较早记录可能已自动覆盖。")
            Helper("已脱敏；包含运行信息和文件名，不包含照片或视频本体、账号、密码及登录凭据。")
        }
        Button(enabled=!closing&&(!custom||range!=null),onClick={val selected=if(custom)range!! else LogRange(zone=zone.id);dismiss{onExport(selected)}},modifier=Modifier.fillMaxWidth()){LText("导出")}
    }
}
