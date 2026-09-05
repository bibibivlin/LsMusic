package com.linxyi.lsmusic.ui

import android.graphics.Bitmap
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Renders production routes with in-memory state. Never starts discovery or contacts an account. */
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LsMusicUiState(destination = AppDestination.SETTINGS, isSearching = false))
    private var exits = 0

    @Test
    fun homeKeepsDevicesAndOrdersCategoriesBeforeIndependentExit() {
        render()
        compose.onNodeWithText(text(R.string.settings_online_lyrics)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_devices_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scan_local_network)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_gallery_size)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_online_lyrics)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.listenbrainz_api)).assertDoesNotExist()
        listOf("appearance", "lyrics", "network", "about").forEachIndexed { index, key ->
            compose.onNodeWithTag("settings-list").performScrollToIndex(index + 2)
            compose.waitUntil(5_000L) { compose.onNodeWithTag("settings-link-$key").isDisplayed() }
        }
        compose.onNodeWithText(text(R.string.exit)).performScrollTo().assertIsDisplayed()
        screenshot("settings-home")
    }

    @Test
    fun appearanceAndNetworkRestorePreferencesDraftAndPagePosition() {
        render()
        open("appearance")
        compose.onNodeWithText(text(R.string.gallery_large)).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(GallerySize.LARGE, state.preferences.gallerySize) }
        screenshot("settings-appearance")
        back()
        open("appearance")
        compose.onNodeWithText(text(R.string.gallery_large)).assertIsSelected()
        back()
        open("network")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("offline draft")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(text(R.string.upload_rules)))
        val offset = compose.onNodeWithText(text(R.string.upload_rules)).fetchSemanticsNode().boundsInRoot.top
        // Bottom navigation deliberately returns to the root, and the saved network page restores.
        compose.onNode(hasText(text(R.string.nav_settings)) and hasClickAction()).performClick()
        open("network")
        assertEquals(offset, compose.onNodeWithText(text(R.string.upload_rules)).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("settings-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assertTextContains("offline draft")
    }

    @Test
    fun pendingRecordsReturnToNetworkAndEntryIsConditional() {
        render()
        open("network")
        compose.onNodeWithText(text(R.string.pending_listens_title)).assertDoesNotExist()
        compose.runOnIdle {
            state = state.copy(pendingListens = listOf(PendingListen(
                id = "fixture", track = track(), startedAtEpochSeconds = 1_700_000_000L,
                durationMs = 300_000L, listenedMs = 180_000L, queuedAtEpochSeconds = 1_700_000_180L,
            )))
        }
        compose.onNodeWithText(text(R.string.view_and_manage)).performScrollTo().performClick()
        compose.onNodeWithContentDescription(text(R.string.back_to_network_settings)).performClick()
        compose.runOnIdle { assertEquals(AppDestination.SETTINGS_NETWORK, state.destination) }
        back()
        compose.runOnIdle { assertEquals(AppDestination.SETTINGS, state.destination) }
    }

    @Test
    fun lyricsAndAboutHaveSeparateContentAndProjectInformation() {
        render()
        open("lyrics")
        compose.onNodeWithText(text(R.string.settings_online_lyrics)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.listenbrainz_api)).assertDoesNotExist()
        screenshot("settings-lyrics")
        back()
        open("about")
        compose.onNodeWithText(text(R.string.app_name)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.mit_license)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_devices_title)).assertDoesNotExist()
        screenshot("settings-about")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-link-third-party-notices"))
        compose.onNodeWithTag("settings-link-third-party-notices").assertIsDisplayed()
    }

    @Test
    fun exitScrollsAboveMiniPlayerAndImmediatelyShowsProgress() {
        state = state.copy(queue = listOf(track()), currentQueueIndex = 0, playbackState = RemotePlaybackState.PAUSED)
        render()
        compose.onNodeWithTag("settings-list").performScrollToIndex(6)
        compose.onNodeWithText(text(R.string.exit)).assertIsDisplayed()
        val exitBottom = compose.onNodeWithText(text(R.string.exit)).fetchSemanticsNode().boundsInRoot.bottom
        val playerTop = compose.onNodeWithText("Offline track").fetchSemanticsNode().boundsInRoot.top
        assertTrue(exitBottom < playerTop)
        screenshot("settings-mini-player")
        compose.onNodeWithText(text(R.string.exit)).performClick()
        compose.onNodeWithText(text(R.string.exiting)).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    fun secondaryPageSlidesInWhilePreviousPageFadesOut() {
        render()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings-link-appearance").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64L)
        val enteringLeft = compose.onNodeWithText(text(R.string.settings_gallery_size)).fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithText(text(R.string.scan_local_network)).assertExists()
        compose.mainClock.advanceTimeBy(240L)
        val settledLeft = compose.onNodeWithText(text(R.string.settings_gallery_size)).fetchSemanticsNode().boundsInRoot.left
        assertTrue(enteringLeft > settledLeft)
        compose.onNodeWithText(text(R.string.scan_local_network)).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun largeFontKeepsExitAndAboutLinksReachable() {
        render(fontScale = 1.5f)
        compose.onNodeWithTag("settings-list").performScrollToIndex(6)
        compose.onNodeWithText(text(R.string.exit)).assertIsDisplayed()
        screenshot("settings-large-font")
        open("about")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-link-third-party-notices"))
        compose.onNodeWithTag("settings-link-third-party-notices").assertIsDisplayed()
    }

    private fun open(key: String) {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-link-$key"))
        compose.onNodeWithTag("settings-link-$key").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }

    private fun back() {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasContentDescription(text(R.string.back_to_settings)))
        compose.onNodeWithContentDescription(text(R.string.back_to_settings)).performClick()
        compose.waitForIdle()
    }

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun render(fontScale: Float? = null) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
            LsMusicTheme(dynamicColor = false) {
                ExitProgressDialog(state.exitStatus, state.exitError) {}
                LsMusicContent(
                    state = state,
                    snackbar = remember { SnackbarHostState() },
                    onDestination = { state = state.copy(destination = it) },
                    onRefresh = {}, onSelectServer = {}, onSelectRenderer = {},
                    onOpen = {}, onNavigateTo = {}, onPlay = {}, onQueue = {}, onPlayAll = {},
                    onShufflePlay = {}, onQueueAll = {}, onAlbumSort = {},
                    onSaveBrowseViewState = { _, _ -> }, onResolveAlbumArtwork = { _, _ -> },
                    onTogglePlayback = {}, onPrevious = {}, onNext = {}, onCycleRepeat = {},
                    onToggleShuffle = {}, onSeek = {}, onRemoveQueue = {}, onMoveQueue = { _, _ -> }, onClearQueue = {},
                    onGallerySize = { state = state.copy(preferences = state.preferences.copy(gallerySize = it)) },
                    onDefaultGridLayout = {}, onThemeMode = {}, onDynamicColor = {}, onPresetPalette = {},
                    onLoadLyrics = {}, onRetryLyrics = {}, onLyricsEnabled = {}, onLyricsProviderOrder = {},
                    onLyricsTranslationMode = {}, onLyricsSourceVisible = {}, onLyricsEffectsEnabled = {},
                    onLyricsFontSizeSp = {}, onClearLyricsCache = {}, onListenBrainzEnabled = {},
                    onListenBrainzToken = {}, onListenBrainzMinimumSeconds = {}, onListenBrainzMinimumPercent = {},
                    onRetryPendingListens = {}, onRemovePendingListen = {}, onClearPendingListens = {},
                    onExit = { exits++; state = state.copy(exitStatus = ExitStatus.STOPPING, queue = emptyList()) },
                )
            }
            }
        }
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun track() = MediaEntry(id = "offline", parentId = "0", title = "Offline track", isContainer = false)
}
