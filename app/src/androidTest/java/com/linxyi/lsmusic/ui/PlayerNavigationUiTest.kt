package com.linxyi.lsmusic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.lyrics.LyricsLoadState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Real navigation/layout/drawing, with offline artwork and no playback or network requests. */
class PlayerNavigationUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    private var state by mutableStateOf(LsMusicUiState())
    private var artworkFile: File? = null
    private val artworkColor = android.graphics.Color.rgb(11, 223, 183)
    private var density = 1f

    @After
    fun removeFixture() {
        artworkFile?.delete()
    }

    @Test
    fun miniPlayerCoverFliesWithoutFadingAndMorphsAllFourCorners() {
        render()
        assertOpeningFlight("mini-player", saveFrames = true)
    }

    @Test
    fun holdingMiniPlayerScalesItsCoverAndTextAndOnlyReleaseNavigates() {
        render()
        val cover = requireCover().bounds
        val title = compose.onNodeWithText("Flight track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini-player").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(250)
        assertEquals(AppDestination.SETTINGS, state.destination)
        val pressedCover = requireCover().bounds
        val pressedTitle = compose.onNodeWithText("Flight track", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(.96f, pressedCover.width / cover.width, .02f)
        assertEquals(.96f, pressedTitle.width / title.width, .01f)
        saveFrame("pressed")
        compose.onNodeWithTag("mini-player").performTouchInput { up() }
        assertEquals("Release should start navigation immediately", AppDestination.NOW_PLAYING, state.destination)
        compose.mainClock.advanceTimeUntil(500) { state.destination == AppDestination.NOW_PLAYING }
        compose.mainClock.advanceTimeByFrame()
        assertBounds(pressedCover, requireCover().bounds)
        var previousWidth = pressedCover.width
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            val width = requireCover().bounds.width
            assertTrue("The released cover must keep growing: $previousWidth -> $width", width >= previousWidth - 1f)
            previousWidth = width
        }
        assertTrue("The cover should already be flying", previousWidth > cover.width + 5f)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("now-playing-artwork", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun cancellingMiniPlayerPressRestoresItWithoutNavigation() {
        render()
        val original = requireCover().bounds
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini-player").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("mini-player").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(700)
        assertEquals(AppDestination.SETTINGS, state.destination)
        assertBounds(original, requireCover().bounds)
    }

    @Test
    fun bottomNavigationUsesTheSameFlightFromTheLibrary() {
        render(destination = AppDestination.LIBRARY)
        assertOpeningFlight("navigation-NOW_PLAYING")
    }

    @Test
    fun bottomNavigationUsesTheSameFlightFromNestedSettings() {
        render(destination = AppDestination.SETTINGS_PLAYBACK)
        assertOpeningFlight("navigation-NOW_PLAYING")
    }

    @Test
    fun squareFoldableKeepsTheFinalCoverSizeAfterLanding() {
        render(viewport = DpSize(800.dp, 800.dp))
        assertOpeningFlight("navigation-NOW_PLAYING")
    }

    @Test
    fun wideLayoutKeepsTheFinalCoverSizeAfterLanding() {
        render(viewport = DpSize(1100.dp, 740.dp))
        assertOpeningFlight("mini-player")
    }

    @Test
    fun quickReverseAndPlaybackUpdatesKeepTheFlightContinuous() {
        render()
        val original = requireCover()
        open("mini-player")
        compose.mainClock.advanceTimeBy(112)
        val moving = requireCover()
        val movingTime = compose.mainClock.currentTime
        compose.runOnIdle { state = state.copy(positionMs = 42_000L) }
        compose.mainClock.advanceTimeByFrame()
        val updated = requireCover()
        assertTrue("Playback updates must not restart the flight", updated.bounds.width >= moving.bounds.width)
        val beforeReverseTime = compose.mainClock.currentTime
        compose.onNodeWithTag("navigation-SETTINGS").performTouchInput { click() }
        compose.mainClock.advanceTimeByFrame()
        val reversed = requireCover()
        // Input injection and composing the new target each consume a frame. Compare the actual
        // elapsed clock time with the preceding frame's speed, rather than assuming one frame.
        val sampledFrameDuration = beforeReverseTime - movingTime
        val retargetFrames = (compose.mainClock.currentTime - beforeReverseTime).toFloat() / sampledFrameDuration
        val maximumFrameStep = (updated.bounds.width - moving.bounds.width) * retargetFrames + 3f
        assertTrue(
            "Reversing must remain continuous (${compose.mainClock.currentTime - beforeReverseTime}ms): " +
                "${moving.bounds} -> ${updated.bounds} -> ${reversed.bounds}",
            abs(updated.bounds.width - reversed.bounds.width) <= maximumFrameStep,
        )
        compose.mainClock.advanceTimeBy(700)
        assertBounds(original.bounds, requireCover().bounds)
        compose.onNodeWithTag("mini-player").assertIsDisplayed()
        open("mini-player")
        compose.mainClock.advanceTimeBy(700)
        assertTrue(requireCover().bounds.width > original.bounds.width * 2)
    }

    @Test
    fun reversingAFlightThatStartedPressedKeepsTheCoverContinuous() {
        render()
        val original = requireCover().bounds
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("mini-player").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("mini-player").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(112)
        val previous = requireCover().bounds
        compose.mainClock.advanceTimeByFrame()
        val moving = requireCover().bounds
        compose.runOnIdle { state = state.copy(destination = AppDestination.SETTINGS) }
        compose.mainClock.advanceTimeByFrame()
        val returning = requireCover().bounds
        val allowed = abs(moving.width - previous.width) * 1.5f + 3f
        assertTrue("Reversing must not snap the pressed cover: $previous -> $moving -> $returning",
            abs(returning.width - moving.width) <= allowed)
        compose.mainClock.advanceTimeBy(800)
        assertBounds(original, requireCover().bounds)
    }

    @Test
    fun restoredLyricsWaitUntilTheCoverLands() {
        render(lyricsEnabled = true)
        open("mini-player")
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Flight track")).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed()
        compose.onNodeWithTag("navigation-SETTINGS").performClick()
        compose.mainClock.advanceTimeBy(800)
        val source = requireCover()
        open("mini-player")
        compose.mainClock.advanceTimeBy(112)
        assertTrue("Restoring lyrics must not remove the flying cover", requireCover().bounds.width > source.bounds.width)
        compose.mainClock.advanceTimeBy(1_100)
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed()
        compose.onNodeWithTag("now-playing-artwork", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun noMiniPlayerStillOpensNowPlayingAndDoesNotInventASource() {
        render(miniPlayerEnabled = false)
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
        open("navigation-NOW_PLAYING")
        compose.mainClock.advanceTimeBy(800)
        val settled = requireCover()
        assertTrue(settled.bounds.width > 100 * density)
        compose.onNodeWithTag("navigation-SETTINGS").performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
    }

    @Test
    fun missingArtworkKeepsTheSamePlaceholderAtBothEndpoints() {
        render(withArtwork = false)
        val mini = nodeBounds("mini-player-artwork")
        val before = placeholderSample(mini)
        open("mini-player")
        compose.mainClock.advanceTimeBy(800)
        val hero = nodeBounds("now-playing-artwork")
        val after = placeholderSample(hero)
        assertTrue(hero.width > mini.width * 2)
        assertTrue("The placeholder must not switch designs during navigation", colorDifference(before, after) <= 12)
    }

    private fun assertOpeningFlight(trigger: String, saveFrames: Boolean = false) {
        val source = requireCover()
        if (saveFrames) saveFrame("mini")
        open(trigger)
        val start = requireCover()
        assertBounds(source.bounds, start.bounds)
        assertTrue("The first frame must retain the mini player's corners", abs(source.cornerInset - start.cornerInset) <= 2)
        compose.mainClock.advanceTimeBy(64)
        val early = requireCover()
        if (saveFrames) saveFrame("flight-early")
        compose.mainClock.advanceTimeBy(96)
        val middle = requireCover()
        if (saveFrames) saveFrame("flight-middle")
        compose.mainClock.advanceTimeBy(112)
        val late = requireCover()
        compose.mainClock.advanceTimeBy(208)
        val overshoot = requireCover()
        if (saveFrames) saveFrame("overshoot")
        compose.mainClock.advanceTimeBy(240)
        val settled = requireCover()
        if (saveFrames) saveFrame("expanded")
        assertTrue("The cover must grow continuously", early.bounds.width > source.bounds.width + 1)
        assertTrue("The cover must not jump directly to its destination", middle.bounds.width > early.bounds.width + 1)
        assertTrue("The cover must remain in flight", late.bounds.width > middle.bounds.width + 1)
        assertTrue("The flight must finish at the large cover", settled.bounds.width > late.bounds.width + 1)
        // A subpixel overshoot can round to the same painted width in a scaled wide viewport.
        assertTrue("The cover should reach its destination before settling", overshoot.bounds.width >= settled.bounds.width - 1f)
        if (settled.bounds.width - source.bounds.width > 350f) {
            assertTrue("The cover must overshoot before settling", overshoot.bounds.width > settled.bounds.width)
        }
        assertTrue("Overshoot must remain subtle", overshoot.bounds.width <= settled.bounds.width +
            (settled.bounds.width - source.bounds.width) * .01f + 2f)
        listOf(source, start, early, middle, late, overshoot, settled).forEach { sample ->
            assertTrue("The flying cover must stay square: $sample", abs(sample.bounds.width - sample.bounds.height) < 3)
            assertTrue("All four corners must agree: $sample", sample.cornerInsets.max() - sample.cornerInsets.min() <= 3)
        }
        assertTrue("Corners must grow smoothly, not scale with the cover", middle.cornerInset in source.cornerInset..settled.cornerInset)
        assertTrue("Final corners must stay near 28dp", settled.cornerInset < 28 * density)
        val midpointFraction = (middle.bounds.width - source.bounds.width) / (settled.bounds.width - source.bounds.width)
        assertTrue("The movement must ease out rather than interpolate linearly: $midpointFraction", midpointFraction > .37f)
        compose.mainClock.advanceTimeBy(500)
        assertBounds(settled.bounds, requireCover().bounds)
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
        compose.onNodeWithTag("now-playing-artwork", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun render(
        destination: AppDestination = AppDestination.SETTINGS,
        viewport: DpSize = DpSize(412.dp, 850.dp),
        miniPlayerEnabled: Boolean = true,
        withArtwork: Boolean = true,
        lyricsEnabled: Boolean = false,
    ) {
        compose.activityRule.scenario.onActivity { activity ->
            // This isolated fixture may draw over keyguard; the device itself stays locked.
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("player-navigation-", ".png", context.cacheDir)
        artworkFile = file
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(artworkColor)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val track = MediaEntry(
            "flight-track", "album", "Flight track", creator = "Artist", album = "Album",
            artworkUri = if (withArtwork) file.toURI().toString() else null,
            isContainer = false,
        )
        state = LsMusicUiState(
            destination = destination,
            queue = listOf(QueueItem.create(track)), currentQueueIndex = 0, durationMs = 180_000L,
            isSearching = false, lyricsLoadState = LyricsLoadState.NotFound,
            preferences = AppPreferences(miniPlayerEnabled = miniPlayerEnabled, lyricsEnabled = lyricsEnabled),
        )
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                TestViewport(viewport) {
                    LsMusicContent(
                        state = state, snackbar = remember { SnackbarHostState() },
                        onDestination = { state = state.copy(destination = it) },
                        onRefresh = {}, onSelectServer = {}, onSelectRenderer = {},
                        onOpen = {}, onNavigateTo = {}, onPlay = {}, onQueue = {}, onPlayAll = {},
                        onShufflePlay = {}, onQueueAll = {}, onAlbumSort = {},
                        onSaveBrowseViewState = { _, _ -> }, onResolveAlbumArtwork = { _, _ -> },
                        onTogglePlayback = {}, onPrevious = {}, onNext = {}, onCycleRepeat = {},
                        onToggleShuffle = {}, onSeek = {}, onRemoveQueue = {}, onMoveQueue = { _, _ -> }, onClearQueue = {},
                        onGallerySize = {}, onDefaultGridLayout = {}, onThemeMode = {}, onDynamicColor = {}, onPresetPalette = {},
                        onLoadLyrics = {}, onRetryLyrics = {}, onLyricsEnabled = {}, onLyricsProviderOrder = {},
                        onLyricsTranslationMode = {}, onLyricsSourceVisible = {}, onLyricsEffectsEnabled = {},
                        onLyricsFontSizeSp = {}, onClearLyricsCache = {}, onListenBrainzEnabled = {},
                        onListenBrainzToken = {}, onListenBrainzMinimumSeconds = {}, onListenBrainzMinimumPercent = {},
                        onRetryPendingListens = {}, onRemovePendingListen = {}, onClearPendingListens = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        if (withArtwork && miniPlayerEnabled) compose.waitUntil(10_000) { paintedCover() != null }
    }

    @Composable
    private fun TestViewport(size: DpSize, content: @Composable () -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val deviceDensity = LocalDensity.current
            val scale = minOf(1f, maxWidth / size.width, maxHeight / size.height)
            val testDensity = remember(deviceDensity, scale) { Density(deviceDensity.density * scale, deviceDensity.fontScale) }
            density = testDensity.density
            CompositionLocalProvider(LocalDensity provides testDensity) {
                Box(Modifier.requiredSize(size).testTag("player-navigation-root")) { content() }
            }
        }
    }

    private fun open(tag: String) {
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(tag).performTouchInput { click() }
        if (tag == "mini-player") {
            compose.mainClock.advanceTimeUntil(500) { state.destination == AppDestination.NOW_PLAYING }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private data class Cover(val bounds: Rect, val cornerInsets: List<Int>) {
        val cornerInset: Int get() = cornerInsets.first()
    }

    private fun requireCover(): Cover = requireNotNull(paintedCover()) { "The cover must remain painted throughout the flight" }

    private fun paintedCover(): Cover? {
        val bitmap = screenshot()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var left = bitmap.width
        var top = bitmap.height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, color ->
            if (colorDifference(color, artworkColor) <= 6) {
                val x = index % bitmap.width
                val y = index / bitmap.width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left) return null
        fun cornerInsets(y: Int): List<Int> {
            val first = (left..right).first { colorDifference(pixels[y * bitmap.width + it], artworkColor) <= 6 }
            val last = (right downTo left).first { colorDifference(pixels[y * bitmap.width + it], artworkColor) <= 6 }
            return listOf(first - left, right - last)
        }
        // Near the tangent a fraction of a pixel of movement can shift the color edge by several
        // pixels. Sample farther into the arc to compare the shape rather than raster rounding.
        val sampleInset = (density * 4f).roundToInt().coerceAtLeast(2)
        return Cover(
            Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f),
            cornerInsets(top + sampleInset) + cornerInsets(bottom - sampleInset),
        )
    }

    private fun screenshot(): Bitmap = compose.onNodeWithTag("player-navigation-root").captureToImage().asAndroidBitmap()

    private fun saveFrame(name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "player-transition-review")
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use { screenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun nodeBounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun placeholderSample(bounds: Rect): Int {
        val root = nodeBounds("player-navigation-root")
        return screenshot().getPixel(
            (bounds.left - root.left + bounds.width * .2f).toInt(),
            (bounds.top - root.top + bounds.height * .5f).toInt(),
        )
    }

    private fun colorDifference(first: Int, second: Int): Int = maxOf(
        abs(android.graphics.Color.red(first) - android.graphics.Color.red(second)),
        abs(android.graphics.Color.green(first) - android.graphics.Color.green(second)),
        abs(android.graphics.Color.blue(first) - android.graphics.Color.blue(second)),
    )

    private fun assertBounds(expected: Rect, actual: Rect) {
        assertEquals("Cover x", expected.left, actual.left, 2f)
        assertEquals("Cover y", expected.top, actual.top, 2f)
        assertEquals("Cover width", expected.width, actual.width, 2f)
        assertEquals("Cover height", expected.height, actual.height, 2f)
    }

    private fun text(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
}
