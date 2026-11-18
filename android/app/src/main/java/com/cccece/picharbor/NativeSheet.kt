@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.cccece.picharbor

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class SheetLifecycle {
    var mounted by mutableStateOf(false)
        private set
    var closing by mutableStateOf(false)
        private set
    private var generation=0
    fun present(){generation++;mounted=true;closing=false}
    fun finishNativeExit():Boolean {
        if(!mounted)return false
        generation++;mounted=false;closing=false
        return true
    }
    suspend fun close(animate:suspend ()->Unit,commit:()->Unit):Boolean {
        if(!mounted||closing)return false
        val attempt=generation
        closing=true
        try {
            animate()
            if(attempt!=generation)return false
            mounted=false
            closing=false
            commit()
            return true
        } finally {if(attempt==generation)closing=false}
    }
}

val LocalSheetClosing=staticCompositionLocalOf{false}
private val LocalSheetDismiss=staticCompositionLocalOf<((()->Unit)->Unit)?>{null}

@Composable
fun sheetDismissAction():(()->Unit)->Unit=LocalSheetDismiss.current?:{action->action()}

private class SheetInput {var clear:()->Unit={}}

internal fun commitSheetExit(requested:Boolean,onDismiss:()->Unit,action:(()->Unit)?,onHidden:()->Unit) {
    // Clear the owner's visible request even when a selection has an action.
    // Apply the action afterwards so it can intentionally open another panel.
    if(requested)onDismiss()
    action?.invoke()
    onHidden()
}

/** Keeps the native dialog composed until hide() finishes, including owner-driven closes. */
@Composable
fun NativeModalSheet(
    visible:Boolean=true,
    onDismiss:()->Unit,
    onHidden:()->Unit={},
    contentKey:Any?=Unit,
    content:@Composable ColumnScope.(close:()->Unit)->Unit
) {
    val state=rememberModalBottomSheetState(skipPartiallyExpanded=true)
    val lifecycle=remember{SheetLifecycle()}
    val scope=rememberCoroutineScope()
    val input=remember{SheetInput()}
    var closingJob by remember{mutableStateOf<Job?>(null)}
    var pendingAction by remember{mutableStateOf<(() ->Unit)?>(null)}
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestHidden by rememberUpdatedState(onHidden)
    val requested by rememberUpdatedState(visible)
    fun commitExit() {
        val action=pendingAction
        pendingAction=null
        commitSheetExit(requested,{latestDismiss()},action,{latestHidden()})
    }
    fun close(after:(()->Unit)?=null) {
        if(!lifecycle.mounted||lifecycle.closing||!requested)return
        pendingAction=after
        input.clear()
        closingJob=scope.launch(start=CoroutineStart.UNDISPATCHED) {
            lifecycle.close({state.hide()}){commitExit()}
        }
    }
    LaunchedEffect(visible,contentKey) {
        if(visible) {
            closingJob?.cancel()
            pendingAction=null
            val retained=lifecycle.mounted
            lifecycle.present()
            // A fresh ModalBottomSheet shows itself after its anchors are measured.
            // A reopened, retained dialog needs to reverse the interrupted hide.
            if(retained)state.show()
        } else {
            input.clear()
            if(lifecycle.mounted)lifecycle.close({state.hide()}){latestHidden()}
            else latestHidden()
        }
    }
    if(lifecycle.mounted)ModalBottomSheet(onDismissRequest={
        // Material may finish its own back/scrim/drag animation while an owner-
        // driven hide is cancelled. Release that already-hidden window exactly once.
        if(!state.isVisible) {
            closingJob?.cancel()
            if(lifecycle.finishNativeExit())commitExit()
        } else close()
    },sheetState=state,sheetGesturesEnabled=visible&&!lifecycle.closing) {
        val focus=LocalFocusManager.current
        val keyboard=LocalSoftwareKeyboardController.current
        SideEffect{input.clear={focus.clearFocus(force=true);keyboard?.hide()}}
        DisposableEffect(Unit){onDispose{input.clear={}}}
        CompositionLocalProvider(LocalSheetClosing provides (lifecycle.closing||!visible),LocalSheetDismiss provides {after->close(after)}) {
            content {close()}
        }
    }
}
