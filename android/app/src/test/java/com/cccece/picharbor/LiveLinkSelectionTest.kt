package com.cccece.picharbor

import org.junit.Assert.*
import org.junit.Test

class LiveLinkSelectionTest {
    private val photo=MediaEntry("photo","same.jpg","image/jpeg",100,1,1,"camera","Camera",pairingID="pair")
    private val video=MediaEntry("video","same.mov","video/quicktime",200,1,1,"import","Import",pairingID="pair")
    private val other=photo.copy(id="other",pairingID="")
    private val library=listOf(photo,video,other)
    @Test fun selectingEitherSideIncludesOneCompleteLogicalPair() {
        assertEquals(listOf(photo),selectedLinkMedia(library,setOf("video")))
        assertEquals(listOf(photo),selectedLinkMedia(library,setOf("photo","video")))
    }
    @Test fun emptyOrUnavailableSelectionNeverFallsBackToEntireLibrary() {
        assertTrue(selectedLinkMedia(library,emptySet()).isEmpty())
        assertTrue(selectedLinkMedia(library,setOf("removed")).isEmpty())
    }
    @Test fun selectionIsIndependentOfBackupScopeAndDoesNotIncludeOtherMedia() {
        val settings=HarborSettings(allFolders=false,folders=setOf("unrelated"))
        assertFalse(settings.includes(other))
        assertEquals(listOf(other),selectedLinkMedia(library,setOf("other")))
        assertEquals(setOf("unrelated"),settings.folders)
    }
}
