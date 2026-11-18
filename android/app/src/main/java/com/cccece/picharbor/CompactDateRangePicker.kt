@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Separate labeled rows keep unselected placeholders and long localized dates readable. */
@Composable
fun CompactDateRangePicker(state:DateRangePickerState,modifier:Modifier=Modifier) {
    val locale=LocalConfiguration.current.locales[0]
    val format=remember(locale){DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).withZone(ZoneOffset.UTC)}
    DateRangePicker(state,modifier=modifier,title={
        LText("选择起止日期",style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(start=24.dp,top=16.dp,bottom=8.dp))
    },headline={
        Column(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("开始日期" to state.selectedStartDateMillis,"结束日期" to state.selectedEndDateMillis).forEach{(label,value)->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    LText(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value?.let{format.format(Instant.ofEpochMilli(it))}?:localize("未选择"),
                        modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    })
}
