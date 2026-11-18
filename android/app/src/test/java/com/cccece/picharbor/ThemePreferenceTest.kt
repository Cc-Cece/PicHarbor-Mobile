package com.cccece.picharbor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class ThemePreferenceTest {
    @Test fun manualPaletteIsReadBackAndAutomaticCanBeRestored():Unit=runBlocking {
        val preferences=Preferences(RuntimeEnvironment.getApplication())
        val original=preferences.flow.first().palette
        try {
            preferences.string("palette","blue")
            assertEquals("blue",Preferences(RuntimeEnvironment.getApplication()).flow.first().palette)
            preferences.string("palette","system")
            assertEquals("system",preferences.flow.first().palette)
        } finally {preferences.string("palette",original)}
    }
}
