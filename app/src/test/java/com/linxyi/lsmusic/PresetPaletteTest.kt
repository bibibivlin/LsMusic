package com.linxyi.lsmusic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.linxyi.lsmusic.ui.PresetPalette
import com.linxyi.lsmusic.ui.theme.presetColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class PresetPaletteTest {
    @Test
    fun presetPalettes_generateDistinctAccentColorsForBothModes() {
        listOf(false, true).forEach { darkTheme ->
            val accents = PresetPalette.entries.map { palette ->
                presetColorScheme(palette, darkTheme).primary.value
            }

            assertEquals(PresetPalette.entries.size, accents.distinct().size)
        }
    }

    @Test
    fun presetPalettes_keepMaterialRolePairsAccessible() {
        PresetPalette.entries.forEach { palette ->
            listOf(false, true).forEach { darkTheme ->
                val scheme = presetColorScheme(palette, darkTheme)
                val rolePairs = listOf(
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.secondary to scheme.onSecondary,
                    scheme.secondaryContainer to scheme.onSecondaryContainer,
                    scheme.tertiary to scheme.onTertiary,
                    scheme.tertiaryContainer to scheme.onTertiaryContainer,
                    scheme.surface to scheme.onSurface,
                    scheme.surfaceVariant to scheme.onSurfaceVariant,
                    scheme.error to scheme.onError,
                    scheme.errorContainer to scheme.onErrorContainer,
                )

                rolePairs.forEach { (background, foreground) ->
                    assertTrue(
                        "${palette.name} dark=$darkTheme contrast=${contrastRatio(background, foreground)}",
                        contrastRatio(background, foreground) >= MIN_TEXT_CONTRAST,
                    )
                }
            }
        }
    }

    private fun contrastRatio(first: Color, second: Color): Float {
        val firstLuminance = first.luminance()
        val secondLuminance = second.luminance()
        return (max(firstLuminance, secondLuminance) + 0.05f) /
            (min(firstLuminance, secondLuminance) + 0.05f)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5f
    }
}
