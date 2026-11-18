package com.cccece.picharbor

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SheetLifecycleTest {
    @Test fun selectingAnOptionClearsOwnerAndAllowsRepeatedOpening():Unit=runBlocking {
        val sheet=SheetLifecycle();var mode="live-photo";var selections=0;var hidden=0
        repeat(3) {
            mode="live-photo";sheet.present()
            sheet.close({}) {commitSheetExit(mode.isNotEmpty(),{mode=""},{selections++},{hidden++})}
            assertEquals("",mode);assertFalse(sheet.mounted);assertFalse(sheet.closing)
        }
        assertEquals(3,selections);assertEquals(3,hidden)
    }
    @Test fun deferredActionCanOpenAnotherPanelAfterOldOwnerIsDismissed() {
        var mode="old";var dismissed=0;var retained="old"
        commitSheetExit(true,{dismissed++;mode=""},{mode="replacement"},{if(mode.isEmpty())retained=""})
        assertEquals(1,dismissed);assertEquals("replacement",mode);assertEquals("old",retained)
        commitSheetExit(false,{fail("Already closed owner must not be dismissed again")},null,{retained=""})
        assertEquals("",retained)
    }
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
