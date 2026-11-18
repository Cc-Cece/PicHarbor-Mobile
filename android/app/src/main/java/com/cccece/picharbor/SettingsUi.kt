@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun qualityName(value:String)=when(value){"unknown"->"待确认";"saver"->"节省空间（不占用配额）";"quota"->"原始画质（占用配额）";else->"原始画质（不占用配额）"}
fun livePhotoLabel(value:String)=when(value){"motion"->"动态照片封装";"stepwise"->"原始照片配对（分步上传）";else->"原始照片配对"}
fun editPolicyLabel(value:String)=when(value){"original"->"仅原始版本";"current"->"仅编辑后版本";else->"原始与编辑后均保留"}
fun albumLabel(s:HarborSettings)=when(s.albumMode){"none"->"不分类";"custom"->"自定义：${s.albumValue}";"existing"->"加入已选择的云端相册";else->"按来源文件夹"}

fun jobLabel(state:String)=when(state){"completed"->"已完成";"running"->"进行中";"queued"->"等待开始";"paused"->"已暂停";"pausing"->"正在暂停";"waiting"->"等待网络";"auth_required"->"需要登录";"ended"->"已结束";else->"需要处理"}
