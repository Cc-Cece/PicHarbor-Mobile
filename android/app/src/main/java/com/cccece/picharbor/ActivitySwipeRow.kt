package com.cccece.picharbor

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SwipePosition { Closed, Open }

/** A trailing action is revealed, never executed merely by completing a drag. */
@Composable
fun ActivitySwipeRow(
    rowKey:String,label:String,destructive:Boolean,enabled:Boolean,
    revealed:String?,onReveal:(String?)->Unit,onAction:()->Unit,content:@Composable ()->Unit
) {
    if(label.isEmpty()){content();return}
    val density=LocalDensity.current
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    val actionWidth=104.dp
    val distance=with(density){actionWidth.toPx()}*(if(rtl)1 else -1)
    val state=remember(rowKey,label,distance){AnchoredDraggableState(SwipePosition.Closed,DraggableAnchors {
        SwipePosition.Closed at 0f;SwipePosition.Open at distance
    })}
    LaunchedEffect(state.settledValue){
        if(state.settledValue==SwipePosition.Open)onReveal(rowKey)
        else if(revealed==rowKey)onReveal(null)
    }
    LaunchedEffect(revealed){if(revealed!=rowKey&&state.settledValue==SwipePosition.Open)state.animateTo(SwipePosition.Closed)}
    val action={onReveal(null);onAction()}
    val description=localize(label)
    Box(Modifier.fillMaxWidth().clipToBounds().semantics {
        if(enabled)customActions=listOf(CustomAccessibilityAction(description){action();true})
    }) {
        if(abs(state.requireOffset())>0.5f)Box(Modifier.matchParentSize(),contentAlignment=Alignment.CenterEnd) {
            Surface(onClick=action,enabled=enabled,modifier=Modifier.width(actionWidth).fillMaxHeight(),
                color=if(destructive)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor=if(destructive)MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary) {
                Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    LIcon(if(destructive)Icons.Outlined.DeleteOutline else Icons.Outlined.Archive,null)
                    Spacer(Modifier.height(4.dp));LText(label,style=MaterialTheme.typography.labelLarge)
                }
            }
        }
        Box(Modifier.absoluteOffset{IntOffset(state.requireOffset().roundToInt(),0)}
            .background(MaterialTheme.colorScheme.background)
            .anchoredDraggable(state=state,orientation=Orientation.Horizontal,reverseDirection=false,enabled=enabled)) {content()}
    }
}
