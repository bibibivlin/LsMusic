package com.linxyi.lsmusic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class TopEdgeProtectionUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    @Test
    fun restoredGridProtectsLightStatusIconsOnItsFirstFrame() {
        assertRestoredGrid(darkTheme = true)
    }

    @Test
    fun restoredGridProtectsDarkStatusIconsOnItsFirstFrame() {
        assertRestoredGrid(darkTheme = false)
    }

    @Test
    fun gradientBlursTransparentAndOpaqueContentEquallyAcrossAWiderArea() {
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        var enabled by mutableStateOf(true)
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                list = rememberLazyListState()
                scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().background(Color.White).testTag("edge-root")) {
                    TopEdgeProtection(if (enabled) 24.dp else 0.dp) {
                        LazyColumn(
                            state = list,
                            modifier = Modifier.fillMaxSize().protectTopEdge(list),
                            contentPadding = PaddingValues(top = 48.dp),
                        ) {
                            item {
                                Canvas(Modifier.fillMaxWidth().height(1200.dp)) {
                                    val halfWidth = size.width / 2f
                                    drawRect(
                                        Color.White,
                                        topLeft = androidx.compose.ui.geometry.Offset(halfWidth, 0f),
                                        size = androidx.compose.ui.geometry.Size(halfWidth, size.height),
                                    )
                                    val stripeWidth = 4.dp.toPx()
                                    repeat(2) { half ->
                                        var x = 0f
                                        while (x < halfWidth) {
                                            drawRect(
                                                Color.Black,
                                                topLeft = androidx.compose.ui.geometry.Offset(half * halfWidth + x, 0f),
                                                size = androidx.compose.ui.geometry.Size(stripeWidth, size.height),
                                            )
                                            x += stripeWidth * 2f
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { scope.launch { list.scrollToItem(0, with(compose.density) { 90.dp.roundToPx() }) } }
        compose.waitForIdle()
        val protected = screenshot()
        compose.runOnIdle { enabled = false }
        val raw = screenshot()

        val density = compose.density.density
        val halfWidth = protected.width / 2
        val startX = (30f * density).roundToInt()
        val endX = halfWidth - startX
        val textY = (34f * density).roundToInt()
        val totalDifference = (startX until endX).sumOf { x ->
            abs(android.graphics.Color.red(protected.getPixel(x, textY)) -
                android.graphics.Color.red(protected.getPixel(x + halfWidth, textY)))
        }
        val averageDifference = totalDifference.toFloat() / (endX - startX)
        assertTrue("Transparent text and opaque artwork should have the same blur: difference $averageDifference", averageDifference < 5f)

        val gradientY = (64f * density).roundToInt()
        val rawRange = contrastRange(raw, gradientY, startX, endX)
        val protectedRange = contrastRange(protected, gradientY, startX, endX)
        assertTrue("The longer transition should still soften content at 64 dp: $rawRange -> $protectedRange",
            protectedRange < rawRange * .8f)
        val samples = listOf(34f, 48f, 64f, 80f, 100f).map { y ->
            contrastRange(protected, (y * density).roundToInt(), startX, endX)
        }
        assertTrue("Contrast should gradually return below the status bar: $samples",
            samples.zipWithNext().all { (above, below) -> above <= below + 5 })
        val normalY = (100f * density).roundToInt()
        assertEquals("Content below the gradient should remain unchanged", raw.getPixel(startX, normalY), protected.getPixel(startX, normalY))
    }

    @Test
    fun splitPanesOnlyBlurThePaneThatHasScrolled() {
        lateinit var summary: ScrollState
        lateinit var tracks: LazyListState
        lateinit var scope: CoroutineScope
        var enabled by mutableStateOf(true)
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                summary = rememberScrollState()
                tracks = rememberLazyListState()
                scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().background(Color.White).testTag("edge-root")) {
                    TopEdgeProtection(if (enabled) 24.dp else 0.dp) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxHeight().protectTopEdge(summary, 24.dp)) {
                                Column(Modifier.fillMaxSize().verticalScroll(summary).padding(top = 48.dp)) {
                                    VerticalStripes()
                                }
                            }
                            LazyColumn(
                                state = tracks,
                                modifier = Modifier.weight(1f).fillMaxHeight().protectTopEdge(tracks),
                                contentPadding = PaddingValues(top = 48.dp),
                            ) {
                                item { VerticalStripes() }
                            }
                        }
                    }
                }
            }
        }
        val offset = with(compose.density) { 90.dp.roundToPx() }
        compose.runOnIdle { scope.launch { summary.scrollTo(offset) } }
        compose.waitForIdle()
        val leftProtected = screenshot()
        compose.runOnIdle { enabled = false }
        val leftRaw = screenshot()
        val margin = (30f * compose.density.density).roundToInt()
        val half = leftRaw.width / 2
        val y = (64f * compose.density.density).roundToInt()
        assertTrue(contrastRange(leftProtected, y, margin, half - margin) < contrastRange(leftRaw, y, margin, half - margin) * .8f)
        for (x in half + margin until leftRaw.width - margin) {
            assertEquals("An unscrolled track pane should remain untouched", leftRaw.getPixel(x, y), leftProtected.getPixel(x, y))
        }

        compose.runOnIdle { scope.launch { summary.scrollTo(0); tracks.scrollToItem(0, offset) } }
        compose.runOnIdle { enabled = true }
        val rightProtected = screenshot()
        compose.runOnIdle { enabled = false }
        val rightRaw = screenshot()
        assertTrue(contrastRange(rightProtected, y, half + margin, rightRaw.width - margin) <
            contrastRange(rightRaw, y, half + margin, rightRaw.width - margin) * .8f)
        for (x in margin until half - margin) {
            assertEquals("An unscrolled summary pane should remain untouched", rightRaw.getPixel(x, y), rightProtected.getPixel(x, y))
        }
    }

    @Test
    fun textOnATransparentBackgroundDoesNotKeepASharpCopyUnderTheBlur() {
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        var enabled by mutableStateOf(true)
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                list = rememberLazyListState()
                scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().background(Color.White).testTag("edge-root")) {
                    TopEdgeProtection(if (enabled) 24.dp else 0.dp) {
                        LazyColumn(
                            state = list,
                            modifier = Modifier.fillMaxSize().protectTopEdge(list),
                            contentPadding = PaddingValues(top = 48.dp),
                        ) {
                            item {
                                Row(Modifier.fillMaxWidth()) {
                                    repeat(2) { half ->
                                        Box(Modifier.weight(1f).background(if (half == 0) Color.Transparent else Color.White)) {
                                            Text("Track title 123", Modifier.padding(start = 12.dp), color = Color.Black, fontSize = 22.sp)
                                        }
                                    }
                                }
                            }
                            item { Box(Modifier.height(1200.dp)) }
                        }
                    }
                }
            }
        }
        val offset = with(compose.density) { 24.dp.roundToPx() }
        compose.runOnIdle { scope.launch { list.scrollToItem(0, offset) } }
        compose.waitForIdle()
        val protected = screenshot()
        compose.runOnIdle { enabled = false }
        val raw = screenshot()
        val density = compose.density.density
        val half = raw.width / 2
        var difference = 0L
        var foregroundPixels = 0
        for (y in (24f * density).roundToInt() until (65f * density).roundToInt()) {
            for (x in (8f * density).roundToInt() until half - (8f * density).roundToInt()) {
                if (android.graphics.Color.red(raw.getPixel(x, y)) < 200) {
                    difference += abs(android.graphics.Color.red(protected.getPixel(x, y)) -
                        android.graphics.Color.red(protected.getPixel(x + half, y)))
                    foregroundPixels++
                }
            }
        }
        assertTrue("The fixture must contain text", foregroundPixels > 100)
        val averageDifference = difference.toFloat() / foregroundPixels
        assertTrue("Text should be blurred like opaque content, without a sharp underlay: $averageDifference", averageDifference < 5f)
    }

    @Test
    fun repeatedFastSwipesKeepTheBlurAndReturnToTheOriginalTop() {
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        var enabled by mutableStateOf(true)
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                list = rememberLazyListState()
                scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().background(Color.White).testTag("edge-root")) {
                    TopEdgeProtection(if (enabled) 24.dp else 0.dp) {
                        LazyColumn(
                            state = list,
                            modifier = Modifier.fillMaxSize().protectTopEdge(list).testTag("edge-list"),
                            contentPadding = PaddingValues(top = 48.dp),
                        ) {
                            items(100) {
                                Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                                    val stripeWidth = 4.dp.toPx()
                                    var left = 0f
                                    while (left < size.width) {
                                        drawRect(
                                            Color.Black,
                                            topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                                            size = androidx.compose.ui.geometry.Size(stripeWidth, size.height),
                                        )
                                        left += stripeWidth * 2f
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val initial = screenshot()
        repeat(4) { compose.onNodeWithTag("edge-list").performTouchInput { swipeUp(durationMillis = 120) } }
        compose.runOnIdle { assertTrue("Fast swipes should move the list", list.firstVisibleItemIndex > 0) }
        val protected = screenshot()
        compose.runOnIdle { enabled = false }
        val raw = screenshot()
        val density = compose.density.density
        val y = (8f * density).roundToInt()
        val startX = (30f * density).roundToInt()
        val endX = (120f * density).roundToInt().coerceAtMost(raw.width - 1)
        assertTrue("Fast scrolling should leave the status area protected",
            contrastRange(protected, y, startX, endX) < contrastRange(raw, y, startX, endX) * .8f)
        compose.runOnIdle { enabled = true; scope.launch { list.scrollToItem(0) } }
        compose.waitForIdle()
        assertImagesEqual(initial, screenshot())
    }

    @Composable
    private fun VerticalStripes() {
        Canvas(Modifier.fillMaxWidth().height(1200.dp)) {
            val stripeWidth = 4.dp.toPx()
            var left = 0f
            while (left < size.width) {
                drawRect(
                    Color.Black,
                    topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                    size = androidx.compose.ui.geometry.Size(stripeWidth, size.height),
                )
                left += stripeWidth * 2f
            }
        }
    }

    @Test
    fun scrollingBlursOnlyTheTopAndReturningToTopRestoresTheOriginalLayout() {
        lateinit var list: LazyListState
        lateinit var scope: CoroutineScope
        var enabled by mutableStateOf(true)
        var offsetPx = 0
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                val density = LocalDensity.current
                offsetPx = with(density) { 40.dp.roundToPx() }
                list = rememberLazyListState()
                scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("edge-root")) {
                    CompositionLocalProvider(LocalContentTopInset provides 24.dp) {
                        TopEdgeProtection(if (enabled) 24.dp else 0.dp) {
                            LazyColumn(
                                state = list,
                                modifier = Modifier.fillMaxSize().protectTopEdge(list),
                                contentPadding = PaddingValues(top = 48.dp),
                            ) {
                                item { Box(Modifier.fillMaxWidth().height(70.dp)) }
                                items(20) {
                                    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                                        val stripeWidth = 4.dp.toPx()
                                        var left = 0f
                                        var dark = false
                                        while (left < size.width) {
                                            drawRect(
                                                color = if (dark) Color.Black else Color.White,
                                                topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                                                size = androidx.compose.ui.geometry.Size(stripeWidth, size.height),
                                            )
                                            left += stripeWidth
                                            dark = !dark
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val initial = screenshot()
        compose.runOnIdle { enabled = false }
        val initialWithoutEffect = screenshot()
        assertImagesEqual(initial, initialWithoutEffect)

        compose.runOnIdle { scope.launch { list.scrollToItem(1, offsetPx) } }
        compose.waitForIdle()
        val raw = screenshot()
        compose.runOnIdle { enabled = true }
        val protected = screenshot()
        val density = compose.density.density
        val statusY = (8f * density).roundToInt()
        val normalY = (90f * density).roundToInt().coerceAtMost(raw.height - 1)
        val startX = (30f * density).roundToInt()
        val endX = (120f * density).roundToInt().coerceAtMost(raw.width - 1)
        assertTrue("The fixture must show scrolling content behind the status bar", contrastRange(raw, statusY, startX, endX) > 100)
        val rawRange = contrastRange(raw, statusY, startX, endX)
        val protectedRange = contrastRange(protected, statusY, startX, endX)
        val background = Color(initial.getPixel(startX, statusY))
        val tintOnlyRange = rawRange * (1f - topEdgeScrimAlpha(background, useDarkStatusBarIcons(background)))
        assertTrue("Blur should soften the artwork beyond the tint alone: $rawRange -> $protectedRange (tint alone: $tintOnlyRange)",
            protectedRange < tintOnlyRange * .8f)
        assertEquals("Content below the top effect should stay unchanged", raw.getPixel(startX, normalY), protected.getPixel(startX, normalY))

        compose.runOnIdle { scope.launch { list.scrollToItem(0) } }
        compose.waitForIdle()
        assertImagesEqual(initial, screenshot())
    }

    private fun assertRestoredGrid(darkTheme: Boolean) {
        var enabled by mutableStateOf(true)
        compose.setContent {
            LsMusicTheme(darkTheme = darkTheme, dynamicColor = false) {
                val offset = with(LocalDensity.current) { 56.dp.roundToPx() }
                val grid = rememberLazyGridState(initialFirstVisibleItemIndex = 4, initialFirstVisibleItemScrollOffset = offset)
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("edge-root")) {
                    TopEdgeProtection(if (enabled) 24.dp else 0.dp) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = grid,
                            modifier = Modifier.fillMaxSize().protectTopEdge(grid),
                            contentPadding = PaddingValues(top = 48.dp),
                        ) {
                            items(20) {
                                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                                    val stripeWidth = 4.dp.toPx()
                                    var left = 0f
                                    while (left < size.width) {
                                        drawRect(
                                            color = if ((left / stripeWidth).toInt() % 2 == 0) Color.Black else Color.White,
                                            topLeft = androidx.compose.ui.geometry.Offset(left, 0f),
                                            size = androidx.compose.ui.geometry.Size(stripeWidth, size.height),
                                        )
                                        left += stripeWidth
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val firstFrame = screenshot()
        compose.runOnIdle { enabled = false }
        val raw = screenshot()
        val density = compose.density.density
        val y = (8f * density).roundToInt()
        val startX = (30f * density).roundToInt()
        val endX = (120f * density).roundToInt().coerceAtMost(raw.width - 1)
        assertTrue("The restored grid should already be protected", contrastRange(firstFrame, y, startX, endX) < contrastRange(raw, y, startX, endX) / 2)
        for (x in startX..endX) {
            val luminance = Color(firstFrame.getPixel(x, y)).luminance()
            val contrast = if (darkTheme) 1.05f / (luminance + .05f) else (luminance + .05f) / .05f
            assertTrue("Status icon contrast was $contrast at $x", contrast >= 4.5f)
        }
    }

    private fun screenshot(): Bitmap = compose.onNodeWithTag("edge-root").captureToImage().asAndroidBitmap()

    private fun contrastRange(bitmap: Bitmap, y: Int, startX: Int, endX: Int): Int {
        val values = (startX..endX).map { x -> android.graphics.Color.red(bitmap.getPixel(x, y)) }
        return values.max() - values.min()
    }

    private fun assertImagesEqual(expected: Bitmap, actual: Bitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        var changed = 0
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val a = expected.getPixel(x, y)
                val b = actual.getPixel(x, y)
                if (abs(android.graphics.Color.red(a) - android.graphics.Color.red(b)) > 1 ||
                    abs(android.graphics.Color.green(a) - android.graphics.Color.green(b)) > 1 ||
                    abs(android.graphics.Color.blue(a) - android.graphics.Color.blue(b)) > 1
                ) changed++
            }
        }
        assertTrue("Expected identical frames, but $changed pixels changed", changed == 0)
    }
}
