package com.cccece.picharbor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SheetLifecycleTest {
    @Test fun selectionWaitsForExitAndRepeatedClicksDoNotCommitTwice():Unit=runBlocking {
        val sheet=SheetLifecycle();sheet.present()
        val animation=CompletableDeferred<Unit>();var commits=0
        val first=launch(start=CoroutineStart.UNDISPATCHED){sheet.close({animation.await()}){commits++}}
        assertTrue(sheet.mounted);assertTrue(sheet.closing);assertEquals(0,commits)
        assertFalse(sheet.close({fail("Duplicate animation")}){commits++})
        animation.complete(Unit);first.join()
        assertFalse(sheet.mounted);assertFalse(sheet.closing);assertEquals(1,commits)
    }
    @Test fun interruptedAnimationKeepsSheetAndDoesNotApplySelection():Unit=runBlocking {
        val sheet=SheetLifecycle();sheet.present();var commits=0
        val first=launch(start=CoroutineStart.UNDISPATCHED){sheet.close({awaitCancellation()}){commits++}}
        first.cancelAndJoin()
        assertTrue(sheet.mounted);assertFalse(sheet.closing);assertEquals(0,commits)
        assertTrue(sheet.close({}){commits++})
        assertFalse(sheet.mounted);assertEquals(1,commits)
    }
    @Test fun reopeningCannotBeClosedByAnOlderExit():Unit=runBlocking {
        val sheet=SheetLifecycle();sheet.present();var commits=0
        val animation=CompletableDeferred<Unit>()
        val first=launch(start=CoroutineStart.UNDISPATCHED){sheet.close({animation.await()}){commits++}}
        sheet.present();animation.complete(Unit);first.join()
        assertTrue(sheet.mounted);assertFalse(sheet.closing);assertEquals(0,commits)
        sheet.close({}){commits++}
        assertFalse(sheet.mounted);assertEquals(1,commits)
    }
    @Test fun platformBackFinishingAnExitCannotLeaveAWindowOrRepeatTheOldCallback():Unit=runBlocking {
        val sheet=SheetLifecycle();sheet.present();var commits=0
        val animation=CompletableDeferred<Unit>()
        val first=launch(start=CoroutineStart.UNDISPATCHED){sheet.close({animation.await()}){commits++}}
        assertTrue(sheet.finishNativeExit());commits++
        assertFalse(sheet.finishNativeExit())
        animation.complete(Unit);first.join()
        assertFalse(sheet.mounted);assertFalse(sheet.closing);assertEquals(1,commits)
    }
}
