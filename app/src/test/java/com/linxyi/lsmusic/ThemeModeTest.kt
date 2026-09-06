package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.ThemeMode
import com.linxyi.lsmusic.ui.resolvesToDarkTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun systemModeFollowsSystemWhileExplicitModesOverrideIt() {
        assertFalse(resolvesToDarkTheme(ThemeMode.SYSTEM, systemUsesDarkTheme = false))
        assertTrue(resolvesToDarkTheme(ThemeMode.SYSTEM, systemUsesDarkTheme = true))
        assertFalse(resolvesToDarkTheme(ThemeMode.LIGHT, systemUsesDarkTheme = true))
        assertTrue(resolvesToDarkTheme(ThemeMode.DARK, systemUsesDarkTheme = false))
    }
}
