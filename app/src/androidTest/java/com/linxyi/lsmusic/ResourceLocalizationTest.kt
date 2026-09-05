package com.linxyi.lsmusic

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ResourceLocalizationTest {
    private val baseContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun supportedLocalesProvideEnglishAndSimplifiedChinese() {
        val english = localized(Locale.ENGLISH)
        val chinese = localized(Locale.SIMPLIFIED_CHINESE)

        assertEquals("Music library", english.getString(R.string.library_root))
        assertEquals("音乐库", chinese.getString(R.string.library_root))
        assertNotEquals(
            english.getString(R.string.pending_listens_title),
            chinese.getString(R.string.pending_listens_title),
        )
    }

    @Test
    fun unsupportedLocaleFallsBackToEnglish() {
        val english = localized(Locale.ENGLISH)
        val unsupported = localized(Locale.JAPANESE)

        assertEquals(
            english.getString(R.string.library_root),
            unsupported.getString(R.string.library_root),
        )
        assertEquals(
            english.resources.getQuantityString(R.plurals.album_count, 2, 2),
            unsupported.resources.getQuantityString(R.plurals.album_count, 2, 2),
        )
    }

    private fun localized(locale: Locale): Context {
        val configuration = Configuration(baseContext.resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return baseContext.createConfigurationContext(configuration)
    }
}
