package com.cccece.picharbor

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistryOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29],application=Application::class)
class MobileLocaleOwnerTest {
    @Test fun localizedContextsRetainTheActivityResultOwnerAndLeaveOriginalLocaleUntouched() {
        val controller=Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity=controller.get()
            val original=activity.resources.configuration.locales[0]
            for(tag in listOf("zh-Hans","en","yue-Hant-HK")) {
                val configuration=Configuration(activity.resources.configuration).apply{setLocale(Locale.forLanguageTag(tag))}
                val localized=mobileLocaleContext(activity,configuration)
                var owner:Context=localized
                while(owner !is ActivityResultRegistryOwner && owner is ContextWrapper)owner=owner.baseContext
                assertSame(activity,owner)
                assertEquals(Locale.forLanguageTag(tag),localized.resources.configuration.locales[0])
                assertEquals(original,activity.resources.configuration.locales[0])
            }
        } finally {controller.pause().stop().destroy()}
    }
}
