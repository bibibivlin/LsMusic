package com.linxyi.lsmusic.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class MiniPlayerTransitionUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    private var state by mutableStateOf(LsMusicUiState())
    private var playerInsetVisible by mutableStateOf(false)
    private var density = 1f
    private val artworkRequests = mutableListOf<Int>()

    @Before
    fun prepareWindow() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
    }

    @Test
    fun startingAnAlbumTrackMovesThePlayerAndWideLayoutTogether() {
        render(DpSize(920.dp, 650.dp), trackCount = 3)
        val actionsBefore = bounds("album-detail-floating-actions")
        val trackBefore = textBounds("Track 2")

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Track 1").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        val earlyActions = bounds("album-detail-floating-actions")
        val earlyPlayer = bounds("mini-player")
        val earlyTrack = textBounds("Track 2")
        compose.mainClock.advanceTimeBy(96)
        val middleActions = bounds("album-detail-floating-actions")
        compose.mainClock.advanceTimeBy(600)
        val finalActions = bounds("album-detail-floating-actions")
        val finalPlayer = bounds("mini-player")
        val finalTrack = textBounds("Track 2")

        val distance = actionsBefore.bottom - finalActions.bottom
        assertEquals("actions: $actionsBefore -> $earlyActions -> $middleActions -> $finalActions", 80f * density, distance, 3f)
        assertTrue("actions: $actionsBefore -> $earlyActions -> $middleActions -> $finalActions", earlyActions.bottom < actionsBefore.bottom && earlyActions.bottom > middleActions.bottom)
        assertTrue("actions: $actionsBefore -> $earlyActions -> $middleActions -> $finalActions", middleActions.bottom > finalActions.bottom)
        val middleFraction = (actionsBefore.bottom - middleActions.bottom) / distance
        assertTrue("The motion should ease out: $middleFraction", middleFraction > .6f && middleFraction < 1f)
        assertEquals(
            "The player and floating actions should move in step",
            earlyActions.bottom - finalActions.bottom,
            earlyPlayer.top - finalPlayer.top,
            4f,
        )
        assertTrue("tracks: $trackBefore -> $earlyTrack -> $finalTrack", trackBefore.top > earlyTrack.top && earlyTrack.top > finalTrack.top)
        assertEquals("track-1", state.currentTrack?.id)
    }

    @Test
    fun trackHighlightSweepsAcrossTheRowWhileTheMiniPlayerMovesIt() {
        render(DpSize(920.dp, 650.dp), trackCount = 3)
        val before = trackBackground("track-1")
        val initialTrack = bounds("album-track-track-1")

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Track 1").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(144)
        val early = trackBackground("track-1")
        val movingTrack = bounds("album-track-track-1")
        compose.mainClock.advanceTimeBy(112)
        val middle = trackBackground("track-1")
        compose.mainClock.advanceTimeBy(600)
        val finished = trackBackground("track-1")
        val finalTrack = bounds("album-track-track-1")

        assertTrue("The selected background should differ from the page", colorDistance(before.first, finished.first) > 15)
        assertTrue(
            "The beginning of the row should be filled before its end: $early",
            colorDistance(early.first, finished.first) < colorDistance(early.second, finished.second),
        )
        assertTrue(
            "The far edge should still be mostly unfilled early in the sweep: " +
                "before=$before early=$early finished=$finished",
            colorDistance(early.second, finished.second) > colorDistance(before.second, finished.second) * .45f,
        )
        assertTrue(
            "The sweep should continue toward the far edge",
            colorDistance(middle.second, finished.second) < colorDistance(early.second, finished.second),
        )
        assertTrue(
            "The row should move up during the same animation",
            initialTrack.top > movingTrack.top && movingTrack.top > finalTrack.top,
        )
        assertTrue("The completed background should cover both ends", colorDistance(finished.first, finished.second) < 8)
    }

    @Test
    fun changingTracksDuringTheSweepSettlesOnTheLatestSelection() {
        render(DpSize(920.dp, 650.dp), trackCount = 3)
        val unselected = trackBackground("track-1")
        val otherUnselected = trackBackground("track-2")

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Track 1").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(144)
        compose.onNodeWithText("Track 2").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val fading = trackBackground("track-1")
        compose.onNodeWithText("Track 1").performClick()
        compose.mainClock.advanceTimeByFrame()
        val resumed = trackBackground("track-1")
        compose.mainClock.advanceTimeBy(80)
        val recovering = trackBackground("track-1")
        compose.mainClock.advanceTimeBy(700)
        val selected = trackBackground("track-1")
        val other = trackBackground("track-2")

        assertTrue(
            "Reselecting a fading row should remain continuous: " +
                "fading=$fading resumed=$resumed selected=$selected",
            colorDistance(fading.first, resumed.first) < colorDistance(unselected.first, selected.first) * .3f,
        )
        assertTrue(
            "The selected background should recover after the interrupted fade",
            colorDistance(recovering.first, selected.first) < colorDistance(resumed.first, selected.first),
        )
        assertTrue("The latest track should end with the highlight", colorDistance(selected.first, unselected.first) > 15)
        assertTrue(
            "The other track should return to its normal background: before=$otherUnselected after=$other",
            // A click or focus indication can still lightly tint the row after the highlight fades.
            colorDistance(other.first, otherUnselected.first) < colorDistance(other.first, selected.first),
        )
        assertEquals("track-1", state.currentTrack?.id)
    }

    @Test
    fun returningToAnAlreadyPlayingTrackShowsItsSettledBackground() {
        render(DpSize(920.dp, 650.dp), trackCount = 30, initiallyPlaying = true)
        val selected = trackBackground("track-1")
        compose.onNodeWithTag("album-detail-tracks").performScrollToIndex(30)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("album-detail-tracks").performScrollToIndex(0)
        compose.mainClock.advanceTimeByFrame()
        val returned = trackBackground("track-1")

        assertTrue(
            "An already-playing track should not replay the sweep after scrolling back",
            colorDistance(returned.first, selected.first) < 8 && colorDistance(returned.second, selected.second) < 8,
        )
    }

    @Test
    fun phoneAlbumContentMovesSmoothlyAndDoesNotReloadArtworkEachFrame() {
        render(DpSize(412.dp, 600.dp), trackCount = 2)
        compose.onNodeWithTag("album-detail-tracks").performScrollToNode(hasText("Track 1"))
        val start = bounds("album-detail-actions").top
        artworkRequests.clear()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Track 1").performClick()
        compose.mainClock.advanceTimeByFrame()
        val positions = mutableListOf(start)
        repeat(25) {
            compose.mainClock.advanceTimeByFrame()
            val position = bounds("album-detail-actions").top
            if (position > 0f) positions += position
        }
        compose.mainClock.advanceTimeBy(400)
        positions += bounds("album-detail-actions").top

        assertTrue("The animation should produce intermediate layout samples", positions.size >= 20)
        val distance = positions.first() - positions.last()
        assertTrue("The album controls should move up: $positions", distance > 10f * density)
        positions.zipWithNext().forEach { (previous, current) ->
            assertTrue("The controls should not jump: $positions", abs(current - previous) < distance * .35f + 3f)
        }
        assertTrue("Artwork resolution should wait for resizing to settle: $artworkRequests", artworkRequests.size <= 1)
    }

    @Test
    fun landscapeControlsDoNotJumpWhenTheCompactLayoutBecomesNecessary() {
        renderCompactBoundary()
        val initialActions = bounds("album-detail-floating-actions")
        val list = bounds("album-detail-tracks")
        val positions = mutableListOf(bounds("album-action-shuffle").center)
        val tracks = mutableListOf(textBounds("Track 2").center)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Track 1").performClick()
        compose.mainClock.advanceTimeByFrame()
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            val action = bounds("album-action-shuffle").center
            val track = textBounds("Track 2").center
            if (action != Offset.Zero) positions += action
            if (track != Offset.Zero) tracks += track
        }
        val finalActions = bounds("album-detail-floating-actions")
        assertTrue("The fixture should change from vertical to compact controls: $initialActions -> $finalActions; list=$list density=$density",
            initialActions.height > initialActions.width && finalActions.width > finalActions.height)
        assertTrue("The animation should produce intermediate layout samples", positions.size >= 25 && tracks.size >= 25)
        positions.zipWithNext().forEach { (previous, current) ->
            assertTrue("The actions should move continuously: $positions", (current - previous).getDistance() < 32f * density + 3f)
        }
        tracks.zipWithNext().forEach { (previous, current) ->
            assertTrue("The tracks should move continuously: $tracks", (current - previous).getDistance() < 32f * density + 3f)
        }
    }

    @Test
    fun portraitCoverResizesContinuouslyAcrossTheCompactSpacingThreshold() {
        val library = albumLibraryState(trackCount = 1)
        val pageKey = requireNotNull(library.browsePageKey)
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                TestViewport(DpSize(412.dp, 390.dp)) {
                    val padding by animateDpAsState(
                        if (playerInsetVisible) 112.dp else 32.dp,
                        tween(400, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)),
                        label = "test-inset",
                    )
                    AlbumDetailScreen(
                        state = library, pageKey = pageKey, initialViewState = BrowseViewState(),
                        onSaveBrowseViewState = { _, _ -> }, onResolveAlbumArtwork = { _, _ -> },
                        onPlay = {}, onQueue = {}, onPlayAll = {}, onShufflePlay = {}, onQueueAll = {},
                        bottomContentPadding = padding,
                    )
                }
            }
        }
        compose.waitForIdle()
        val widths = mutableListOf(bounds("album-detail-cover").width)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { playerInsetVisible = true }
        compose.mainClock.advanceTimeByFrame()
        repeat(30) {
            compose.mainClock.advanceTimeByFrame()
            val width = bounds("album-detail-cover").width
            if (width > 0f) widths += width
        }
        assertTrue("The animation should produce intermediate layout samples", widths.size >= 25)
        assertTrue("The cover should become smaller: $widths", widths.first() - widths.last() > 10f * density)
        widths.zipWithNext().forEach { (previous, current) ->
            assertTrue("The cover should not grow or jump during the resize: $widths",
                current <= previous + 2f && previous - current < 20f * density + 3f)
        }
    }

    @Test
    fun disabledPlayerStaysHiddenAndClearingTheQueueAnimatesItsExit() {
        render(DpSize(920.dp, 650.dp), trackCount = 3, miniPlayerEnabled = false)
        val start = bounds("album-detail-floating-actions")
        compose.onNodeWithText("Track 1").performClick()
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
        assertEquals(start.bottom, bounds("album-detail-floating-actions").bottom, 1f)

        compose.runOnIdle { state = state.copy(preferences = state.preferences.copy(miniPlayerEnabled = true)) }
        compose.waitForIdle()
        val visible = bounds("mini-player")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state = state.copy(queue = emptyList(), currentQueueIndex = -1) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(96)
        val exiting = bounds("mini-player")
        assertTrue("The cleared player should slide out before it is removed", exiting.top > visible.top)
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("mini-player").assertDoesNotExist()
        assertEquals(start.bottom, bounds("album-detail-floating-actions").bottom, 2f)
    }

    private fun render(
        viewport: DpSize,
        trackCount: Int,
        miniPlayerEnabled: Boolean = true,
        initiallyPlaying: Boolean = false,
    ) {
        val tracks = List(trackCount) { index ->
            MediaEntry(
                id = "track-${index + 1}",
                parentId = "album",
                title = "Track ${index + 1}",
                creator = "Artist",
                isContainer = false,
            )
        }
        state = LsMusicUiState(
            entries = tracks,
            path = listOf(
                BrowseLocation("root", "Library"),
                BrowseLocation("album", "Album", albumArtist = "Artist", pageKind = LibraryPageKind.ALBUM),
            ),
            browsePageKey = BrowsePageKey("server", "album"),
            selectedServerId = "server",
            isSearching = false,
            browseLoadStatus = BrowseLoadStatus.LOADED,
            preferences = AppPreferences(miniPlayerEnabled = miniPlayerEnabled),
            queue = if (initiallyPlaying) listOf(QueueItem.create(tracks.first())) else emptyList(),
            currentQueueIndex = if (initiallyPlaying) 0 else -1,
            playbackState = if (initiallyPlaying) RemotePlaybackState.PLAYING else RemotePlaybackState.STOPPED,
        )
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                TestViewport(viewport) {
                    LsMusicContent(
                        state = state,
                        snackbar = remember { SnackbarHostState() },
                        onDestination = {}, onOpenNowPlayingFromMiniPlayer = {}, onRefresh = {},
                        onSelectServer = {}, onSelectRenderer = {}, onOpen = {}, onNavigateTo = {},
                        onPlay = { track ->
                            state = state.copy(
                                queue = listOf(QueueItem.create(track)),
                                currentQueueIndex = 0,
                                playbackState = RemotePlaybackState.PLAYING,
                            )
                        },
                        onQueue = {}, onPlayAll = {}, onShufflePlay = {}, onQueueAll = {}, onAlbumSort = {},
                        onSaveBrowseViewState = { _, _ -> },
                        onResolveAlbumArtwork = { _, size -> artworkRequests += size },
                        onTogglePlayback = {}, onPrevious = {}, onNext = {}, onCycleRepeat = {},
                        onToggleShuffle = {}, onSeek = {}, onRemoveQueue = {}, onMoveQueue = { _, _ -> },
                        onClearQueue = {}, onGallerySize = {}, onDefaultGridLayout = {}, onThemeMode = {},
                        onDynamicColor = {}, onPresetPalette = {}, onLoadLyrics = {}, onRetryLyrics = {},
                        onLyricsEnabled = {}, onLyricsProviderOrder = {}, onLyricsTranslationMode = {},
                        onLyricsSourceVisible = {}, onLyricsEffectsEnabled = {}, onLyricsFontSizeSp = {},
                        onClearLyricsCache = {}, onListenBrainzEnabled = {}, onListenBrainzToken = {},
                        onListenBrainzMinimumSeconds = {}, onListenBrainzMinimumPercent = {},
                        onRetryPendingListens = {}, onRemovePendingListen = {}, onClearPendingListens = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun renderCompactBoundary() {
        val library = albumLibraryState(trackCount = 2)
        val pageKey = requireNotNull(library.browsePageKey)
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                TestViewport(DpSize(700.dp, 370.dp)) {
                    var showingPlayer by remember { mutableStateOf(false) }
                    val padding by animateDpAsState(
                        if (showingPlayer) 112.dp else 32.dp,
                        tween(400, easing = CubicBezierEasing(.2f, 0f, 0f, 1f)),
                        label = "test-inset",
                    )
                    AlbumDetailScreen(
                        state = library,
                        pageKey = pageKey,
                        initialViewState = BrowseViewState(),
                        onSaveBrowseViewState = { _, _ -> },
                        onResolveAlbumArtwork = { _, _ -> },
                        onPlay = { showingPlayer = true },
                        onQueue = {}, onPlayAll = {}, onShufflePlay = {}, onQueueAll = {},
                        bottomContentPadding = padding,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun albumLibraryState(trackCount: Int): LibraryUiState {
        val tracks = List(trackCount) { index ->
            MediaEntry("track-${index + 1}", "album", "Track ${index + 1}", creator = "Artist", isContainer = false)
        }
        return LibraryUiState(
            entries = tracks,
            albumSort = AlbumSort.SERVER_DEFAULT,
            path = listOf(BrowseLocation("root"), BrowseLocation("album", "Album", pageKind = LibraryPageKind.ALBUM)),
            browsePageKey = BrowsePageKey("server", "album"),
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
    }

    @Composable
    private fun TestViewport(size: DpSize, content: @Composable () -> Unit) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val deviceDensity = LocalDensity.current
            val scale = minOf(1f, maxWidth / size.width, maxHeight / size.height)
            val testDensity = remember(deviceDensity, scale) { Density(deviceDensity.density * scale, deviceDensity.fontScale) }
            density = testDensity.density
            CompositionLocalProvider(LocalDensity provides testDensity) {
                Box(Modifier.requiredSize(size)) { content() }
            }
        }
    }

    private fun bounds(tag: String): Rect = nodeBounds(
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode(),
    )

    private fun textBounds(text: String): Rect = nodeBounds(compose.onNodeWithText(text).fetchSemanticsNode())

    private fun trackBackground(trackId: String): Pair<Int, Int> {
        val image = compose.onNodeWithTag("album-track-$trackId").captureToImage().asAndroidBitmap()
        val y = (image.height * .12f).toInt().coerceIn(0, image.height - 1)
        return image.getPixel((image.width * .15f).toInt(), y) to
            image.getPixel((image.width * .85f).toInt(), y)
    }

    private fun colorDistance(first: Int, second: Int): Int =
        abs(android.graphics.Color.red(first) - android.graphics.Color.red(second)) +
            abs(android.graphics.Color.green(first) - android.graphics.Color.green(second)) +
            abs(android.graphics.Color.blue(first) - android.graphics.Color.blue(second))

    private fun nodeBounds(node: SemanticsNode): Rect {
        var result = Rect.Zero
        compose.runOnIdle { result = node.boundsInRoot }
        return result
    }
}
