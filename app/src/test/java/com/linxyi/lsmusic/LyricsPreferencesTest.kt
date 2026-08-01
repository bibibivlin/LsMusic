package com.linxyi.lsmusic

import com.linxyi.lsmusic.lyrics.LyricsProviderId
import com.linxyi.lsmusic.lyrics.LyricsTranslationMode
import com.linxyi.lsmusic.ui.AppPreferences
import com.linxyi.lsmusic.ui.normalizedLyricsFontSizeSp
import com.linxyi.lsmusic.ui.parseLyricsProviderOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPreferencesTest {
    @Test
    fun defaultsKeepOnlineLyricsOffAndEffectsReady() {
        val preferences = AppPreferences()
        assertFalse(preferences.lyricsEnabled)
        assertEquals(LyricsProviderId.entries, preferences.lyricsProviderOrder)
        assertEquals(LyricsTranslationMode.ORIGINAL, preferences.lyricsTranslationMode)
        assertTrue(preferences.lyricsSourceVisible)
        assertTrue(preferences.lyricsEffectsEnabled)
        assertEquals(28, preferences.lyricsFontSizeSp)
    }

    @Test
    fun migratedProviderOrderDropsUnknownValuesAndRepairsDuplicates() {
        assertEquals(
            listOf(LyricsProviderId.QQ, LyricsProviderId.NETEASE),
            parseLyricsProviderOrder("QQ,UNKNOWN,QQ"),
        )
        assertEquals(LyricsProviderId.entries, parseLyricsProviderOrder(null))
    }

    @Test
    fun fontSizeMigrationClampsAndUsesTwoSpSteps() {
        assertEquals(18, normalizedLyricsFontSizeSp(1))
        assertEquals(28, normalizedLyricsFontSizeSp(29))
        assertEquals(40, normalizedLyricsFontSizeSp(99))
    }
}
