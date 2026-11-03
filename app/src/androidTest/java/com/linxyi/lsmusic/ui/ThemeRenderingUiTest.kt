package com.linxyi.lsmusic.ui

import android.graphics.Bitmap
import android.util.TypedValue
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import com.linxyi.lsmusic.ui.theme.presetColorScheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ThemeRenderingUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ThemeTestActivity>()

    @Test
    fun statusIconColorFollowsTheAppThemeInsteadOfTheDeviceTheme() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        var darkTheme by mutableStateOf(false)
        compose.setContent {
            LsMusicTheme(darkTheme = darkTheme, dynamicColor = false) { SystemBarAppearance() }
        }
        val controller = WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView)
        compose.runOnIdle { assertTrue("Light app theme should use dark status icons", controller.isAppearanceLightStatusBars) }
        compose.runOnIdle { darkTheme = true }
        compose.runOnIdle { assertFalse("Dark app theme should use light status icons", controller.isAppearanceLightStatusBars) }
    }

    @Test
    fun restoredLibraryUsesTheRealStatusInsetBeforeItsFirstFrame() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        val server = DlnaDevice("server", "Test server", "Test", "Test", DlnaDeviceKind.MEDIA_SERVER)
        val albums = List(60) { index ->
            MediaEntry("album-$index", "0", "Album $index", isContainer = true, isAlbum = true)
        }
        val state = LsMusicUiState(
            servers = listOf(server),
            selectedServerId = server.id,
            entries = albums,
            path = listOf(BrowseLocation(id = "0", title = "Music")),
            browsePageKey = BrowsePageKey(server.id, "0"),
            browseViewState = BrowseViewState(
                useGrid = false,
                anchorEntryKey = mediaEntryKey(albums[27]),
                fallbackItemIndex = 30,
                scrollOffset = 16,
            ),
            preferences = darkPreferences(),
            isSearching = false,
            browseLoadStatus = BrowseLoadStatus.LOADED,
        )
        compose.setContent { TestApp(state) }
        compose.waitForIdle()
        val statusHeight = WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets)
            .getInsets(WindowInsetsCompat.Type.statusBars()).top
        val list = compose.onNodeWithTag("library-directory-entries").fetchSemanticsNode().boundsInRoot
        val card = compose.onNodeWithTag("library-card:album-27").fetchSemanticsNode().boundsInRoot
        val search = compose.onNodeWithTag("library-floating-search").fetchSemanticsNode().boundsInRoot
        assertTrue("The fixture requires a visible status bar", statusHeight > 0)
        assertFalse("Dark app themes need light status bar icons",
            WindowCompat.getInsetsController(compose.activity.window, compose.activity.window.decorView).isAppearanceLightStatusBars)
        assertTrue("The list viewport should extend behind the status bar: $list", list.top < statusHeight)
        assertTrue("The search control must stay below the status bar: $search", search.top >= statusHeight)
        val expectedTop = statusHeight + with(compose.density) { 24.dp.toPx() } - 16f
        assertTrue("The restored card should keep its first-frame position: expected $expectedTop, was ${card.top}",
            kotlin.math.abs(card.top - expectedTop) < 3f)
        saveStatusFrame("restored-library")
    }

    @Test
    fun queueSettingsAndPendingListsExtendBehindTheRealStatusBar() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        val tracks = List(40) { index ->
            MediaEntry("track-$index", "album", "Track $index", creator = "Artist", isContainer = false)
        }
        val pending = tracks.mapIndexed { index, track ->
            PendingListen("listen-$index", track, 1_700_000_000L, 180_000, 120_000, 1_700_000_100L)
        }
        var state by mutableStateOf(
            LsMusicUiState(
                queue = tracks.mapIndexed { index, track -> QueueItem("queue-$index", track) },
                currentQueueIndex = 0,
                pendingListens = pending,
                preferences = darkPreferences(),
                destination = AppDestination.QUEUE,
                isSearching = false,
            ),
        )
        compose.setContent { TestApp(state) }
        compose.waitForIdle()
        val statusHeight = WindowInsetsCompat.toWindowInsetsCompat(compose.activity.window.decorView.rootWindowInsets)
            .getInsets(WindowInsetsCompat.Type.statusBars()).top
        listOf(
            Triple(AppDestination.QUEUE, "queue-list", 12),
            Triple(AppDestination.SETTINGS, "settings-list", 3),
            Triple(AppDestination.PENDING_LISTENS, "pending-listens-list", 12),
        ).forEach { (destination, tag, index) ->
            compose.runOnIdle { state = state.copy(destination = destination) }
            compose.waitForIdle()
            val list = compose.onNodeWithTag(tag).assertIsDisplayed()
            assertTrue("$tag should extend behind the status bar", list.fetchSemanticsNode().boundsInRoot.top < statusHeight)
            list.performScrollToIndex(index)
            assertTrue("$tag should stay behind the status bar after scrolling", list.fetchSemanticsNode().boundsInRoot.top < statusHeight)
            saveStatusFrame(destination.name.lowercase())
        }
    }

    private fun saveStatusFrame(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("captureStatusEdgeFrames") != "true") return
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "status-edge-review")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun darkTestActivityUsesDarkPlatformThemeWithoutForceDark() {
        val isLightTheme = TypedValue()
        assertTrue(
            compose.activity.theme.resolveAttribute(android.R.attr.isLightTheme, isLightTheme, true),
        )
        assertFalse(isLightTheme.data != 0)
        assertFalse(compose.activity.window.decorView.isForceDarkAllowed)
    }

    @Test
    fun openingAlbumWithLargeGradientKeepsDarkTextAndNavigationColors() {
        verifyAlbumColors(dynamicColor = false)
    }

    @Test
    fun openingAlbumWithDynamicColorKeepsDarkTextAndNavigationColors() {
        verifyAlbumColors(dynamicColor = true)
    }

    private fun verifyAlbumColors(dynamicColor: Boolean) {
        val albumTitle = "Theme regression album"
        val album = MediaEntry(
            id = "album",
            parentId = "0",
            title = albumTitle,
            creator = "Artist",
            isContainer = true,
            isAlbum = true,
        )
        val track = MediaEntry(
            id = "track",
            parentId = album.id,
            title = "Theme regression track",
            creator = "Artist",
            isContainer = false,
        )
        val server = DlnaDevice(
            id = "server",
            name = "Test server",
            manufacturer = "Test",
            model = "Test",
            kind = DlnaDeviceKind.MEDIA_SERVER,
        )
        val preferences = darkPreferences()
        var state by mutableStateOf(
            LsMusicUiState(
                servers = listOf(server),
                selectedServerId = server.id,
                entries = listOf(album),
                path = listOf(BrowseLocation(id = "0", title = "Music")),
                browsePageKey = BrowsePageKey(server.id, "0"),
                browseViewState = BrowseViewState(useGrid = false),
                preferences = preferences,
                isSearching = false,
                browseLoadStatus = BrowseLoadStatus.LOADED,
            ),
        )
        val expectedScheme = if (dynamicColor) {
            dynamicDarkColorScheme(compose.activity)
        } else {
            presetColorScheme(PresetPalette.MIST, darkTheme = true)
        }

        compose.setContent {
            TestApp(
                state = state,
                dynamicColor = dynamicColor,
                onOpen = { entry ->
                    state = state.copy(
                        entries = listOf(track),
                        path = state.path + BrowseLocation(
                            id = entry.id,
                            title = entry.title,
                            albumArtist = entry.creator,
                            pageKind = LibraryPageKind.ALBUM,
                        ),
                        browsePageKey = BrowsePageKey(server.id, entry.id),
                        browseViewState = BrowseViewState(),
                    )
                },
            )
        }

        compose.onNodeWithText(albumTitle).performClick()
        compose.waitForIdle()

        val titleNode = compose.onNodeWithText(albumTitle).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(200L)
        compose.waitForIdle()
        val title = titleNode.captureToImage().asAndroidBitmap()
        assertMaximumContrastAtLeast(
            bitmap = title,
            backgroundArgb = expectedScheme.background.toArgb(),
            minimumContrast = 4.5,
        )

        val navigation = compose.onNodeWithTag("app-navigation-bar").captureToImage().asAndroidBitmap()
        assertDominantColor(
            bitmap = navigation,
            expectedArgb = expectedScheme.surfaceContainer.toArgb(),
        )
    }

    @Test
    fun largeNowPlayingGradientKeepsDarkTextReadable() {
        val track = MediaEntry(
            id = "track",
            parentId = "album",
            title = "Theme regression track",
            creator = "Artist",
            album = "Album",
            isContainer = false,
        )
        val state = LsMusicUiState(
            queue = listOf(com.linxyi.lsmusic.ui.QueueItem.create(track)),
            currentQueueIndex = 0,
            durationMs = 180_000L,
            preferences = darkPreferences(),
            destination = AppDestination.NOW_PLAYING,
            isSearching = false,
        )
        val expectedBackground = presetColorScheme(PresetPalette.MIST, darkTheme = true).background.toArgb()

        compose.setContent { TestApp(state) }

        val title = compose.onNodeWithText(track.title).captureToImage().asAndroidBitmap()
        assertMaximumContrastAtLeast(title, expectedBackground, 4.5)
    }

    @Composable
    private fun TestApp(
        state: LsMusicUiState,
        dynamicColor: Boolean = false,
        onOpen: (MediaEntry) -> Unit = {},
    ) {
        LsMusicTheme(
            darkTheme = true,
            dynamicColor = dynamicColor,
            presetPalette = PresetPalette.MIST,
        ) {
            SystemBarAppearance()
            LsMusicContent(
                state = state,
                snackbar = remember { SnackbarHostState() },
                onDestination = {},
                onOpenNowPlayingFromMiniPlayer = {},
                onRefresh = {},
                onSelectServer = {},
                onSelectRenderer = {},
                onOpen = onOpen,
                onNavigateTo = {},
                onPlay = {},
                onQueue = {},
                onPlayAll = {},
                onShufflePlay = {},
                onQueueAll = {},
                onAlbumSort = {},
                onSaveBrowseViewState = { _, _ -> },
                onResolveAlbumArtwork = { _, _ -> },
                onTogglePlayback = {},
                onPrevious = {},
                onNext = {},
                onCycleRepeat = {},
                onToggleShuffle = {},
                onSeek = {},
                onRemoveQueue = {},
                onMoveQueue = { _, _ -> },
                onClearQueue = {},
                onGallerySize = {},
                onDefaultGridLayout = {},
                onThemeMode = {},
                onDynamicColor = {},
                onPresetPalette = {},
                onLoadLyrics = {},
                onRetryLyrics = {},
                onLyricsEnabled = {},
                onLyricsProviderOrder = {},
                onLyricsTranslationMode = {},
                onLyricsSourceVisible = {},
                onLyricsEffectsEnabled = {},
                onLyricsFontSizeSp = {},
                onClearLyricsCache = {},
                onListenBrainzEnabled = {},
                onListenBrainzToken = {},
                onListenBrainzMinimumSeconds = {},
                onListenBrainzMinimumPercent = {},
                onRetryPendingListens = {},
                onRemovePendingListen = {},
                onClearPendingListens = {},
            )
        }
    }

    private fun darkPreferences() = AppPreferences(
        useGridByDefault = false,
        themeMode = ThemeMode.DARK,
        useDynamicColor = false,
        presetPalette = PresetPalette.MIST,
    )

    private fun assertMaximumContrastAtLeast(
        bitmap: Bitmap,
        backgroundArgb: Int,
        minimumContrast: Double,
    ) {
        var maximumContrast = 1.0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                maximumContrast = max(
                    maximumContrast,
                    contrastRatio(bitmap.getPixel(x, y), backgroundArgb),
                )
            }
        }
        assertTrue("maximum rendered contrast was $maximumContrast", maximumContrast >= minimumContrast)
    }

    private fun assertDominantColor(bitmap: Bitmap, expectedArgb: Int) {
        var matchingPixels = 0
        val totalPixels = bitmap.width * bitmap.height
        val colors = mutableMapOf<Int, Int>()
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                colors[pixel] = colors.getOrDefault(pixel, 0) + 1
                if (colorsAreNear(pixel, expectedArgb)) matchingPixels += 1
            }
        }
        val matchingFraction = matchingPixels.toDouble() / totalPixels
        val dominantColors = colors.entries.sortedByDescending(Map.Entry<Int, Int>::value).take(5)
            .joinToString { "${it.key.toUInt().toString(16)}=${it.value}" }
        assertTrue(
            "expected ${expectedArgb.toUInt().toString(16)} covered only $matchingFraction; " +
                "dominant colors: $dominantColors",
            matchingFraction >= 0.5,
        )
    }

    private fun colorsAreNear(first: Int, second: Int): Boolean =
        kotlin.math.abs(android.graphics.Color.red(first) - android.graphics.Color.red(second)) <= 2 &&
            kotlin.math.abs(android.graphics.Color.green(first) - android.graphics.Color.green(second)) <= 2 &&
            kotlin.math.abs(android.graphics.Color.blue(first) - android.graphics.Color.blue(second)) <= 2

    private fun contrastRatio(first: Int, second: Int): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92
            else ((normalized + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(android.graphics.Color.red(argb)) +
            0.7152 * channel(android.graphics.Color.green(argb)) +
            0.0722 * channel(android.graphics.Color.blue(argb))
    }
}
