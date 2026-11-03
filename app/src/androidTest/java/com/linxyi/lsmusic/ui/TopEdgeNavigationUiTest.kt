package com.linxyi.lsmusic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class TopEdgeNavigationUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    @Test
    fun openingAnAlbumFadesTheOutgoingPagesBlurWithoutAJump() {
        val directory = LibraryUiState(
            entries = emptyList(),
            albumSort = AlbumSort.SERVER_DEFAULT,
            path = listOf(BrowseLocation("directory", "Directory")),
            browsePageKey = BrowsePageKey("server", "directory"),
            browseViewState = BrowseViewState(),
            preferences = AppPreferences(),
            isSearching = false,
            servers = emptyList(),
            selectedServerId = "server",
            browseLoadStatus = BrowseLoadStatus.LOADED,
            albumArtwork = null,
            currentTrackId = null,
            playbackState = RemotePlaybackState.STOPPED,
        )
        val album = directory.copy(
            path = directory.path + BrowseLocation("album", "Album", pageKind = LibraryPageKind.ALBUM),
            browsePageKey = BrowsePageKey("server", "album"),
        )
        var current by mutableStateOf(directory)
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize().background(Color.White).testTag("navigation-edge-root")) {
                    TopEdgeProtection(24.dp) {
                        LibraryPageTransition(current) { page ->
                            val isAlbum = page.pageKind == LibraryPageKind.ALBUM
                            val list = rememberLazyListState(initialFirstVisibleItemIndex = if (isAlbum) 0 else 1)
                            LazyColumn(
                                state = list,
                                modifier = Modifier.fillMaxSize().protectTopEdge(list),
                                contentPadding = PaddingValues(top = 48.dp),
                            ) {
                                items(20) {
                                    if (isAlbum) {
                                        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                                            val width = 4.dp.toPx()
                                            var x = 0f
                                            while (x < size.width) {
                                                drawRect(Color.Black, Offset(x, 0f), Size(width, size.height))
                                                x += width * 2f
                                            }
                                        }
                                    } else {
                                        Box(Modifier.fillMaxWidth().height(120.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { current = album }
        val ratios = mutableListOf<Float>()
        var elapsed = 0L
        for (time in listOf(48L, 80L, 112L, 144L, 176L, 208L, 240L, 272L)) {
            compose.mainClock.advanceTimeBy(time - elapsed)
            elapsed = time
            val frame = screenshot()
            val y = with(compose.density) { 64.dp.roundToPx() }
            val referenceY = with(compose.density) { 100.dp.roundToPx() }
            val start = with(compose.density) { 30.dp.roundToPx() }
            val end = with(compose.density) { 120.dp.roundToPx() }.coerceAtMost(frame.width - 1)
            val referenceRange = contrastRange(frame, referenceY, start, end)
            if (referenceRange > 10) ratios += contrastRange(frame, y, start, end).toFloat() / referenceRange
        }
        assertTrue("The destination should become visible during the transition: $ratios", ratios.size >= 4)
        val largestJump = ratios.zipWithNext().maxOf { (first, second) -> abs(second - first) }
        assertTrue("Blur should recede during the page fade without a sudden jump: $ratios", largestJump < .45f)
        assertTrue("The album's top position should finish sharp: $ratios", ratios.last() > .9f)

        compose.runOnIdle { current = directory }
        val returningRatios = mutableListOf<Float>()
        elapsed = 0L
        for (time in listOf(32L, 64L, 96L, 128L, 160L)) {
            compose.mainClock.advanceTimeBy(time - elapsed)
            elapsed = time
            val frame = screenshot()
            val start = with(compose.density) { 30.dp.roundToPx() }
            val end = with(compose.density) { 120.dp.roundToPx() }.coerceAtMost(frame.width - 1)
            val referenceRange = contrastRange(frame, with(compose.density) { 100.dp.roundToPx() }, start, end)
            if (referenceRange > 30) {
                returningRatios += contrastRange(frame, with(compose.density) { 64.dp.roundToPx() }, start, end).toFloat() / referenceRange
            }
        }
        assertTrue("The album should remain visible while returning: $returningRatios", returningRatios.size >= 3)
        assertTrue("Restored directory protection should appear gradually: $returningRatios",
            returningRatios.zipWithNext().all { (first, second) -> abs(second - first) < .45f })
        assertTrue("The restored directory should regain its protection: $returningRatios",
            returningRatios.last() < returningRatios.first() - .1f)
    }

    private fun screenshot(): Bitmap = compose.onNodeWithTag("navigation-edge-root").captureToImage().asAndroidBitmap()

    private fun contrastRange(bitmap: Bitmap, y: Int, start: Int, end: Int): Int {
        val values = (start..end).map { android.graphics.Color.red(bitmap.getPixel(it, y)) }
        return values.max() - values.min()
    }
}
