package com.linxyi.lsmusic.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class NavigationInsetsUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    @Before
    fun prepareLandscapeWindow() {
        compose.activityRule.scenario.onActivity {
            it.setShowWhenLocked(true)
            it.setTurnScreenOn(true)
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(10_000) {
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        // Rotation can recreate the activity, so enable edge-to-edge on the current window.
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.waitForIdle()
        assumeTrue("This test requires a wide landscape window", compose.activity.resources.configuration.screenWidthDp >= 720)
    }

    @After
    fun restoreOrientation() {
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test
    fun libraryListAndGridScrollBehindNavigationAndKeepTheirLastItemVisible() {
        val albums = List(60) { index ->
            MediaEntry("album-$index", "0", "Album $index", isContainer = true, isAlbum = true)
        }
        val server = DlnaDevice("server", "Test server", "Test", "Test", DlnaDeviceKind.MEDIA_SERVER)
        val state = LsMusicUiState(
            servers = listOf(server),
            selectedServerId = server.id,
            entries = albums,
            path = listOf(BrowseLocation("0", "Music")),
            browsePageKey = BrowsePageKey(server.id, "0"),
            browseViewState = BrowseViewState(useGrid = false),
            preferences = AppPreferences(useGridByDefault = false, miniPlayerEnabled = false),
            isSearching = false,
            browseLoadStatus = BrowseLoadStatus.LOADED,
        )
        compose.setContent { TestApp(state) }
        compose.onNodeWithTag("app-navigation-bar").assertDoesNotExist()

        repeat(2) { layout ->
            val list = compose.onNodeWithTag("library-directory-entries")
            assertExtendsToWindowBottom("library-directory-entries")
            list.performScrollToIndex(10)
            assertAlbumCanCrossNavigationBoundary()
            saveReviewFrame(if (layout == 0) "list" else "grid")

            list.performScrollToNode(hasTestTag("library-card:album-59"))
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
            val last = compose.onNodeWithTag("library-card:album-59").fetchSemanticsNode().boundsInRoot
            assertTrue("The last album must clear the system navigation area: $last", last.bottom <= navigationTop() + 1f)

            if (layout == 0) {
                list.performScrollToIndex(0)
                compose.onNodeWithContentDescription(compose.activity.getString(R.string.switch_to_grid)).performClick()
            }
        }
    }

    @Test
    fun widePagesAndFloatingControlsRespectTheSystemNavigationArea() {
        val tracks = List(40) { index ->
            MediaEntry("track-$index", "album", "Track $index", creator = "Artist", isContainer = false)
        }
        var state by mutableStateOf(
            LsMusicUiState(
                destination = AppDestination.QUEUE,
                queue = tracks.mapIndexed { index, track -> QueueItem("queue-$index", track) },
                currentQueueIndex = 0,
                pendingListens = tracks.mapIndexed { index, track ->
                    PendingListen("listen-$index", track, 1_700_000_000L, 180_000, 120_000, 1_700_000_100L)
                },
                preferences = AppPreferences(miniPlayerEnabled = true),
                isSearching = false,
            ),
        )
        compose.setContent { TestApp(state) }

        listOf(
            AppDestination.QUEUE to "queue-list",
            AppDestination.SETTINGS to "settings-list",
            AppDestination.PENDING_LISTENS to "pending-listens-list",
        ).forEach { (destination, tag) ->
            compose.runOnIdle { state = state.copy(destination = destination) }
            assertExtendsToWindowBottom(tag)
            val player = compose.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
            assertTrue("The mini player must clear the system navigation area: $player", player.bottom <= navigationTop() + 1f)
            if (destination == AppDestination.QUEUE) {
                val list = compose.onNodeWithTag(tag)
                list.performScrollToNode(hasTestTag("queue-item-queue-39"))
                list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
                val last = compose.onNodeWithTag("queue-item-queue-39").fetchSemanticsNode().boundsInRoot
                assertTrue("The last queue item must clear the mini player: $last, $player", last.bottom <= player.top + 1f)
            }
        }

        compose.runOnIdle {
            state = state.copy(
                destination = AppDestination.LIBRARY,
                entries = tracks,
                path = listOf(BrowseLocation("album", "Album", pageKind = LibraryPageKind.ALBUM)),
                browsePageKey = BrowsePageKey("server", "album"),
                browseLoadStatus = BrowseLoadStatus.LOADED,
            )
        }
        assertExtendsToWindowBottom("album-detail-tracks")
        val actions = compose.onNodeWithTag("album-detail-floating-actions").fetchSemanticsNode().boundsInRoot
        val player = compose.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        assertTrue("Album actions must clear the mini player: $actions, $player", actions.bottom <= player.top + 1f)

        compose.runOnIdle { state = state.copy(destination = AppDestination.NOW_PLAYING) }
        val repeat = compose.onNodeWithContentDescription(compose.activity.getString(R.string.repeat_off_accessibility))
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Playback controls must clear the system navigation area: $repeat", repeat.bottom <= navigationTop() + 1f)
    }

    @Test
    fun switchingBetweenRailAndBottomTabsUpdatesInsetsAndSystemContrast() {
        var narrow by mutableStateOf(false)
        compose.setContent {
            Box(if (narrow) Modifier.width(412.dp).fillMaxHeight() else Modifier.fillMaxSize()) {
                TestApp(LsMusicUiState(destination = AppDestination.SETTINGS, isSearching = false))
            }
        }
        assertExtendsToWindowBottom("settings-list")
        compose.runOnIdle { assertTrue(compose.activity.window.isNavigationBarContrastEnforced) }

        compose.runOnIdle { narrow = true }
        val list = compose.onNodeWithTag("settings-list").fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag("app-navigation-bar").fetchSemanticsNode().boundsInRoot
        assertEquals("Compact pages stop above the bottom tabs", tabs.top, list.bottom, 1f)
        compose.runOnIdle { assertFalse(compose.activity.window.isNavigationBarContrastEnforced) }
    }

    private fun assertAlbumCanCrossNavigationBoundary() {
        val list = compose.onNodeWithTag("library-directory-entries")
        var crossed = false
        repeat(24) {
            if (!crossed) {
                val boundary = navigationTop()
                crossed = (0 until 60).any { index ->
                    compose.onAllNodesWithTag("library-card:album-$index").fetchSemanticsNodes().any { node ->
                        node.boundsInRoot.top < boundary && node.boundsInRoot.bottom > boundary
                    }
                }
                if (!crossed) list.performSemanticsAction(SemanticsActions.ScrollBy) {
                    it(0f, with(compose.density) { 10.dp.toPx() })
                }
            }
        }
        assertTrue("A scrolling album must be able to draw behind the navigation area", crossed)
    }

    private fun saveReviewFrame(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureNavigationInsetsFrames") != "true") return
        val directory = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "navigation-insets-review",
        )
        directory.mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertExtendsToWindowBottom(tag: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertEquals("$tag should extend behind the navigation area", root.bottom, list.bottom, 1f)
    }

    private fun navigationInset(): Int = maxOf(
        WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets)
            .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
        with(compose.density) { 24.dp.roundToPx() },
    )

    private fun navigationTop(): Float = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom - navigationInset()

    @Composable
    private fun TestApp(state: LsMusicUiState) {
        LsMusicTheme(dynamicColor = false) {
            LsMusicContent(
                state = state, snackbar = remember { SnackbarHostState() },
                contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets(bottom = 24.dp)),
                onDestination = {}, onOpenNowPlayingFromMiniPlayer = {},
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
