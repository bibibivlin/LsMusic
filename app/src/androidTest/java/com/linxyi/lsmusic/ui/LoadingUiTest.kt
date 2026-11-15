package com.linxyi.lsmusic.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.DlnaDevice
import com.linxyi.lsmusic.dlna.DlnaDeviceKind
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.lyrics.LyricsLoadState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.UUID

/** Offline fixtures render the actual screens without touching the user's library or account. */
class LoadingUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val chineseContext = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) },
    )
    private val dummyCredential = UUID.randomUUID().toString()
    private val server = DlnaDevice("fixture-server", "示例音乐库", "Fixture", "Server", DlnaDeviceKind.MEDIA_SERVER)
    private val track = MediaEntry(
        id = "track", parentId = "album", title = "示例曲目", creator = "示例艺术家",
        album = "示例专辑", duration = "03:00", isContainer = false,
    )

    @Test
    fun albumShowsLoadingUntilBrowseFinishesAndDistinguishesFailureFromEmpty() {
        val pageKey = BrowsePageKey(server.id, "album")
        var status by mutableStateOf(BrowseLoadStatus.LOADING)
        var entries by mutableStateOf(emptyList<MediaEntry>())
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 780.dp))) {
                LsMusicTheme(dynamicColor = false) {
                    AlbumDetailScreen(
                        state = libraryUiState(pageKey, status, entries),
                        pageKey = pageKey,
                        initialViewState = BrowseViewState(),
                        onSaveBrowseViewState = { _, _ -> }, onResolveAlbumArtwork = { _, _ -> },
                        onPlay = {}, onQueue = {}, onPlayAll = {}, onShufflePlay = {}, onQueueAll = {},
                        bottomContentPadding = 32.dp,
                    )
                }
            }
        }
        compose.onNodeWithTag("loading-indicator").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.album_no_playable_tracks_title)).assertDoesNotExist()
        compose.onNodeWithTag("album-detail-actions").assertDoesNotExist()

        compose.runOnIdle { entries = listOf(track); status = BrowseLoadStatus.LOADED }
        compose.onNodeWithTag("loading-indicator").assertDoesNotExist()
        compose.onNodeWithText(track.title).assertIsDisplayed()
        compose.onNodeWithTag("album-detail-actions").assertIsDisplayed()

        compose.runOnIdle { entries = emptyList(); status = BrowseLoadStatus.FAILED }
        compose.onNodeWithText(context.getString(R.string.library_load_failed_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.album_no_playable_tracks_title)).assertDoesNotExist()

        compose.runOnIdle { status = BrowseLoadStatus.LOADED }
        compose.onNodeWithText(context.getString(R.string.album_no_playable_tracks_title)).assertIsDisplayed()
    }

    @Test fun narrowLoadingScreens() = verifyViewport(Viewport.NARROW)
    @Test fun portraitLoadingScreens() = verifyViewport(Viewport.PORTRAIT)
    @Test fun squareLoadingScreens() = verifyViewport(Viewport.SQUARE)
    @Test fun landscapeLoadingScreens() = verifyViewport(Viewport.LANDSCAPE)
    @Test fun largeFontLoadingScreens() = verifyViewport(Viewport.LARGE_FONT)

    private fun verifyViewport(size: Viewport) {
        val requestedScene = InstrumentationRegistry.getArguments().getString("reviewScene")
        var scene by mutableStateOf(requestedScene?.let(Scene::valueOf) ?: Scene.LIBRARY)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides chineseContext) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size.size)) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, size.fontScale)) {
                        key(scene) {
                            LsMusicTheme(dynamicColor = false, darkTheme = false) {
                                Box(Modifier.fillMaxSize().testTag("review-root")) {
                                    val state = sceneState(scene)
                                    ReviewApp(state)
                                    if (scene == Scene.BACKUP) {
                                        DataBackupDialogs(
                                            state = DataBackupUiState(phase = DataBackupPhase.READING),
                                            showExport = false, onExport = {}, onCloseExport = {},
                                            onImport = {}, onDismiss = {},
                                        )
                                    }
                                    if (scene == Scene.EXIT) ExitProgressDialog(ExitStatus.STOPPING, null) {}
                                }
                            }
                        }
                    }
                }
            }
        }

        for (screen in Scene.entries) {
            if (requestedScene != null && requestedScene != screen.name) continue
            compose.runOnIdle { scene = screen }
            if (screen == Scene.LYRICS) {
                val artworkLabel = chineseContext.getString(R.string.album_cover_open_lyrics, track.title)
                compose.waitUntil(5_000) { compose.onNodeWithContentDescription(artworkLabel).isDisplayed() }
                compose.mainClock.autoAdvance = false
                compose.onNodeWithContentDescription(artworkLabel).performClick()
                compose.mainClock.advanceTimeByFrame()
                compose.mainClock.advanceTimeBy(400L)
                compose.mainClock.autoAdvance = true
            }
            if (screen == Scene.TOKEN) {
                compose.onNodeWithTag("settings-list").performScrollToNode(
                    hasText(chineseContext.getString(R.string.listenbrainz_api)),
                )
                compose.onNodeWithTag("loading-indicator").performScrollTo()
            }
            val screenshotTag = when (screen) {
                Scene.BACKUP -> "backup-progress-dialog"
                Scene.EXIT -> "exit-progress-dialog"
                else -> "review-root"
            }
            try {
                compose.waitUntil(5_000) { compose.onNodeWithTag("loading-indicator").isDisplayed() }
                compose.onNodeWithTag("loading-indicator").assertIsDisplayed()
            } catch (error: AssertionError) {
                saveScreenshot("diagnostic-${screen.name.lowercase()}-${size.name.lowercase()}", "review-root")
                throw AssertionError("$screen at $size: indicator is not visible", error)
            }
            if (screen == Scene.LIBRARY || screen == Scene.DIRECTORY) {
                val panel = compose.onNodeWithTag("loading-panel").fetchSemanticsNode().boundsInRoot
                val search = compose.onNodeWithTag("library-inline-search-input").fetchSemanticsNode().boundsInRoot
                val breadcrumbs = compose.onNodeWithTag("library-breadcrumbs").fetchSemanticsNode().boundsInRoot
                assertTrue("$screen at $size: loading must be below search", panel.top >= search.bottom)
                assertTrue("$screen at $size: loading must be below breadcrumbs", panel.top >= breadcrumbs.bottom)
            }
            if (screen == Scene.ALBUM || screen == Scene.ALBUM_MINI) {
                compose.onNodeWithText(chineseContext.getString(R.string.loading_music_content)).assertDoesNotExist()
                compose.onNodeWithTag("loading-indicator").assert(
                    hasContentDescription(chineseContext.getString(R.string.loading_music_content)),
                )
                compose.onNodeWithText(chineseContext.getString(R.string.album_no_playable_tracks_title)).assertDoesNotExist()
            }
            if (screen == Scene.LYRICS) {
                compose.onNodeWithText(chineseContext.getString(R.string.lyrics_loading)).assertDoesNotExist()
                compose.onNodeWithTag("loading-indicator").assert(
                    hasContentDescription(chineseContext.getString(R.string.lyrics_loading)),
                )
            }
            saveScreenshot("${screen.name.lowercase()}-${size.name.lowercase()}", screenshotTag)
        }
    }

    private fun saveScreenshot(name: String, tag: String) {
        val directory = File(context.getExternalFilesDir(null), "loading-review")
        check(directory.mkdirs() || directory.isDirectory)
        File(directory, "$name.png").outputStream().use { output ->
            check(compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun libraryUiState(key: BrowsePageKey, status: BrowseLoadStatus, entries: List<MediaEntry>) =
        LibraryUiState(
            entries = entries, albumSort = AlbumSort.SERVER_DEFAULT,
            path = listOf(BrowseLocation("0", "音乐库"), BrowseLocation("album", "示例专辑", albumArtist = "示例艺术家", pageKind = LibraryPageKind.ALBUM)),
            browsePageKey = key, browseViewState = BrowseViewState(), preferences = AppPreferences(),
            isSearching = false, servers = listOf(server), selectedServerId = server.id,
            browseLoadStatus = status, albumArtwork = null, currentTrackId = null,
            playbackState = RemotePlaybackState.STOPPED,
        )

    private fun sceneState(scene: Scene): LsMusicUiState {
        val base = LsMusicUiState(
            isSearching = false, servers = listOf(server), selectedServerId = server.id,
            browsePageKey = BrowsePageKey(server.id, "0"), browseLoadStatus = BrowseLoadStatus.LOADING,
        )
        return when (scene) {
            Scene.LIBRARY -> base.copy(isSearching = true, servers = emptyList(), browseLoadStatus = BrowseLoadStatus.WAITING_FOR_DEVICE)
            Scene.DIRECTORY -> base.copy(
                path = listOf(BrowseLocation("0", "音乐库"), BrowseLocation("directory", "专辑目录")),
                browsePageKey = BrowsePageKey(server.id, "directory"),
            )
            Scene.RESOLVING -> base.copy(
                path = listOf(BrowseLocation("0", "音乐库"), BrowseLocation("unknown", "音乐内容", pageKind = LibraryPageKind.RESOLVING)),
                browsePageKey = BrowsePageKey(server.id, "unknown"),
            )
            Scene.ALBUM, Scene.ALBUM_MINI -> base.copy(
                path = listOf(BrowseLocation("0", "音乐库"), BrowseLocation("album", "示例专辑", albumArtist = "示例艺术家", pageKind = LibraryPageKind.ALBUM)),
                browsePageKey = BrowsePageKey(server.id, "album"),
                queue = if (scene == Scene.ALBUM_MINI) listOf(QueueItem.create(track)) else emptyList(),
                currentQueueIndex = if (scene == Scene.ALBUM_MINI) 0 else -1,
            )
            Scene.LYRICS -> base.copy(
                destination = AppDestination.NOW_PLAYING, queue = listOf(QueueItem.create(track)), currentQueueIndex = 0,
                durationMs = 180_000L, lyricsLoadState = LyricsLoadState.Loading,
                preferences = AppPreferences(lyricsEnabled = true),
            )
            Scene.SCAN -> base.copy(destination = AppDestination.SETTINGS, isSearching = true)
            Scene.TOKEN -> base.copy(
                destination = AppDestination.SETTINGS_NETWORK,
                preferences = AppPreferences(listenBrainzToken = dummyCredential),
                listenBrainzTokenValidation = ListenBrainzTokenValidationUiState(
                    status = ListenBrainzTokenValidationStatus.CHECKING,
                    checkedToken = dummyCredential, message = UiText.Resource(R.string.token_checking),
                ),
            )
            Scene.UPLOAD -> base.copy(
                destination = AppDestination.PENDING_LISTENS,
                preferences = AppPreferences(listenBrainzEnabled = true, listenBrainzToken = dummyCredential),
                isPendingListensUploading = true,
                pendingListens = listOf(PendingListen("fixture", track, 1_700_000_000, 180_000, 100_000, 1_700_000_180)),
            )
            Scene.BACKUP, Scene.EXIT -> base.copy(destination = AppDestination.SETTINGS_ABOUT)
        }
    }

    @Composable
    private fun ReviewApp(state: LsMusicUiState) {
        LsMusicContent(
            state = state, snackbar = remember { SnackbarHostState() },
            onDestination = {}, onOpenNowPlayingFromMiniPlayer = {}, onRefresh = {}, onSelectServer = {}, onSelectRenderer = {},
            onOpen = {}, onNavigateTo = {}, onPlay = {}, onQueue = {}, onPlayAll = {}, onShufflePlay = {}, onQueueAll = {},
            onAlbumSort = {}, onSaveBrowseViewState = { _, _ -> }, onResolveAlbumArtwork = { _, _ -> },
            onTogglePlayback = {}, onPrevious = {}, onNext = {}, onCycleRepeat = {}, onToggleShuffle = {}, onSeek = {},
            onRemoveQueue = {}, onMoveQueue = { _, _ -> }, onClearQueue = {}, onGallerySize = {}, onDefaultGridLayout = {},
            onThemeMode = {}, onDynamicColor = {}, onPresetPalette = {}, onLoadLyrics = {}, onRetryLyrics = {},
            onLyricsEnabled = {}, onLyricsProviderOrder = {}, onLyricsTranslationMode = {}, onLyricsSourceVisible = {},
            onLyricsEffectsEnabled = {}, onLyricsFontSizeSp = {}, onClearLyricsCache = {}, onListenBrainzEnabled = {},
            onListenBrainzToken = {}, onListenBrainzMinimumSeconds = {}, onListenBrainzMinimumPercent = {},
            onRetryPendingListens = {}, onRemovePendingListen = {}, onClearPendingListens = {},
        )
    }

    private enum class Scene { LIBRARY, DIRECTORY, RESOLVING, ALBUM, ALBUM_MINI, LYRICS, SCAN, TOKEN, UPLOAD, BACKUP, EXIT }

    private enum class Viewport(val size: DpSize, val fontScale: Float = 1f) {
        NARROW(DpSize(320.dp, 640.dp)),
        PORTRAIT(DpSize(412.dp, 892.dp)),
        SQUARE(DpSize(700.dp, 700.dp)),
        LANDSCAPE(DpSize(900.dp, 420.dp)),
        LARGE_FONT(DpSize(360.dp, 780.dp), 1.6f),
    }
}
