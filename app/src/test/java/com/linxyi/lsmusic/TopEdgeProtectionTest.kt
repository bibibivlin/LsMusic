package com.linxyi.lsmusic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.linxyi.lsmusic.ui.topEdgeProtectionStrength
import com.linxyi.lsmusic.ui.TopEdgeRegion
import com.linxyi.lsmusic.ui.mergeTopEdgeRegions
import com.linxyi.lsmusic.ui.topEdgeListStrength
import com.linxyi.lsmusic.ui.topEdgeBlurLayerWeight
import com.linxyi.lsmusic.ui.topEdgeVisualBlurStrength
import com.linxyi.lsmusic.ui.topEdgeScrimAlpha
import com.linxyi.lsmusic.ui.useDarkStatusBarIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopEdgeProtectionTest {
    @Test
    fun overlappingPagesUseOneProtectionLayerWhileSplitPanesStayIndependent() {
        assertEquals(
            listOf(TopEdgeRegion(0f, 40f, .5f), TopEdgeRegion(40f, 60f, .8f), TopEdgeRegion(60f, 100f, .8f)),
            mergeTopEdgeRegions(listOf(TopEdgeRegion(0f, 60f, .5f), TopEdgeRegion(40f, 100f, .8f))),
        )
    }

    @Test
    fun flyingCoverProtectsTheStatusBarWithoutExtendingAcrossTheWholeFade() {
        assertEquals(1f, topEdgeVisualBlurStrength(.2f, 1f, 0f, .375f), 0f)
        assertEquals(.5f, topEdgeVisualBlurStrength(.2f, 1f, .1875f, .375f), .0001f)
        assertEquals(.125f, topEdgeVisualBlurStrength(.2f, 1f, .375f, .375f), .0001f)
        assertEquals(0f, topEdgeVisualBlurStrength(0f, 1f, .375f, .375f), 0f)
        assertEquals(0f, topEdgeVisualBlurStrength(.2f, 1f, 1f, .375f), 0f)
        assertEquals(
            listOf(TopEdgeRegion(0f, 40f, .5f, 1f), TopEdgeRegion(40f, 100f, .8f, 1f)),
            mergeTopEdgeRegions(listOf(
                TopEdgeRegion(0f, 40f, .5f), TopEdgeRegion(40f, 100f, .8f), TopEdgeRegion(0f, 100f, 0f, 1f),
            )),
        )
    }

    @Test
    fun strengthIncreasesAsContentReachesTheStatusBar() {
        assertEquals(0f, topEdgeProtectionStrength(itemTop = 60f, safeTop = 40f, fadeHeight = 20f), 0f)
        assertEquals(.5f, topEdgeProtectionStrength(itemTop = 50f, safeTop = 40f, fadeHeight = 20f), 0f)
        assertEquals(1f, topEdgeProtectionStrength(itemTop = 40f, safeTop = 40f, fadeHeight = 20f), 0f)
        assertEquals(1f, topEdgeProtectionStrength(itemTop = -50f, safeTop = 40f, fadeHeight = 20f), 0f)
        assertEquals(0f, topEdgeProtectionStrength(itemTop = -50f, safeTop = 0f, fadeHeight = 20f), 0f)
    }

    @Test
    fun shortTopPaddingStillReachesFullProtectionBeforeContentEntersStatusBar() {
        assertEquals(0f, topEdgeListStrength(50f, 40f, 20f, firstIndex = 0, scrollOffset = 0), 0f)
        assertEquals(.5f, topEdgeListStrength(45f, 40f, 20f, firstIndex = 0, scrollOffset = 5), 0f)
        assertEquals(1f, topEdgeListStrength(40f, 40f, 20f, firstIndex = 0, scrollOffset = 10), 0f)
        assertEquals(.5f, topEdgeListStrength(45f, 40f, 64f, firstIndex = 0, scrollOffset = 5), 0f)
    }

    @Test
    fun blurGradientMixesOnlyNeighboringRadiiAndAlwaysKeepsFullOpacity() {
        listOf(0f, .1f, .4f, .75f, 1f).forEach { strength ->
            listOf(0f, .1f, .375f, .5f, .9f, 1f).forEach { progress ->
                val weights = (0..4).map { topEdgeBlurLayerWeight(it, strength, progress) }
                assertEquals(1f, weights.sum(), .0001f)
                assertTrue(weights.count { it > 0f } <= 2)
                assertEquals(4f * strength * (1f - progress), weights.indices.sumOf { it * weights[it].toDouble() }.toFloat(), .0001f)
            }
        }
        assertEquals(.5f, topEdgeBlurLayerWeight(2, 1f, .375f), 0f)
        assertEquals(.5f, topEdgeBlurLayerWeight(3, 1f, .375f), 0f)
        assertEquals(0f, topEdgeBlurLayerWeight(0, 1f, .375f), 0f)
    }

    @Test
    fun statusTintProtectsBothIconColorsAgainstOppositeArtwork() {
        listOf(Color(0xFFFFF8F7), Color(0xFF171315)).forEach { background ->
            val darkIcons = useDarkStatusBarIcons(background)
            val artwork = if (darkIcons) Color.Black else Color.White
            val alpha = topEdgeScrimAlpha(background, darkIcons)
            val luminance = background.copy(alpha = alpha).compositeOver(artwork).luminance()
            val contrast = if (darkIcons) (luminance + .05f) / .05f else 1.05f / (luminance + .05f)
            assertTrue("Contrast was $contrast for $background", contrast >= 4.5f)
        }
    }
}
