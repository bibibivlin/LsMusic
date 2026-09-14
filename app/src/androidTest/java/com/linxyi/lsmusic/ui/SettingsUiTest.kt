package com.linxyi.lsmusic.ui

import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.getOrNull
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
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Renders production routes with in-memory state. Never starts discovery or contacts an account. */
class SettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private var state by mutableStateOf(LsMusicUiState(destination = AppDestination.SETTINGS, isSearching = false))
    private var exits = 0
    private var libraryPlayClicks = 0
    private var timerRequest: SleepTimerRequest? = null
    private var resourceContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun albumDoubleTapOpensImmersiveArtworkAndTapClosesIt() {
        val fixture = File(resourceContext.cacheDir, "fullscreen-artwork-test.png")
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        fixture.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val pageKey = BrowsePageKey("offline", "album")
        state = state.copy(
            destination = AppDestination.LIBRARY,
            path = listOf(BrowseLocation("0"), BrowseLocation(
                "album", "Offline album", artworkUri = fixture.toURI().toString(),
                pageKind = LibraryPageKind.ALBUM,
            )),
            browsePageKey = pageKey,
            browseLoadStatus = BrowseLoadStatus.LOADED,
            entries = listOf(track()),
        )
        render()
        val opener = hasClickLabel(text(R.string.open_full_screen_artwork))
        compose.onNode(opener).performTouchInput { doubleClick() }
        val closer = hasClickLabel(text(R.string.close_full_screen_artwork))
        compose.onNode(closer).assertIsDisplayed()
        compose.runOnIdle {
            val windows = android.view.inspector.WindowInspector.getGlobalWindowViews()
            val dialog = windows.last()
            val insets = requireNotNull(dialog.rootWindowInsets)
            assertFalse(insets.isVisible(android.view.WindowInsets.Type.statusBars()))
            assertFalse(insets.isVisible(android.view.WindowInsets.Type.navigationBars()))
        }
        val dispatcher = compose.runOnIdle {
            requireNotNull(android.view.inspector.WindowInspector.getGlobalWindowViews().last()
                .findViewTreeOnBackPressedDispatcherOwner()).onBackPressedDispatcher
        }
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 200f, 0f, 0))
            dispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(100f, 200f, .6f, 0))
        }
        compose.waitForIdle()
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.onNode(closer).assertIsDisplayed()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNode(closer).assertDoesNotExist()
        compose.onNode(opener).performTouchInput { doubleClick() }
        compose.onNode(closer).performClick()
        compose.onNode(closer).assertDoesNotExist()
        compose.onNode(opener).assertIsDisplayed()
        fixture.delete()
    }

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label $label") {
        it.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick)?.label == label
    }

    @Test
    fun emptyCardsKeepTheSameBoundsAcrossMainDestinations() {
        state = state.copy(destination = AppDestination.LIBRARY)
        render(locale = Locale.SIMPLIFIED_CHINESE)
        val expected = compose.onNodeWithTag("empty-status-card").fetchSemanticsNode().boundsInRoot
        screenshot("empty-library")
        listOf(AppDestination.QUEUE, AppDestination.NOW_PLAYING).forEach { destination ->
            compose.runOnIdle { state = state.copy(destination = destination) }
            val actual = compose.onNodeWithTag("empty-status-card").fetchSemanticsNode().boundsInRoot
            assertEquals(expected.top, actual.top, 1f)
            assertEquals(expected.bottom, actual.bottom, 1f)
            assertEquals(expected.left, actual.left, 1f)
            screenshot("empty-${destination.name.lowercase()}")
        }
    }

    @Test
    fun homeKeepsDevicesAndOrdersCategoriesBeforeIndependentExit() {
        render()
        compose.onNodeWithText(text(R.string.settings_online_lyrics)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_devices_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.scan_local_network)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_gallery_size)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.settings_online_lyrics)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.listenbrainz_api)).assertDoesNotExist()
        listOf("appearance", "playback", "lyrics", "network", "about").forEachIndexed { index, key ->
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
        compose.onNodeWithText(text(R.string.open_in_browser)).assertDoesNotExist()
        screenshot("settings-about")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-link-third-party-notices"))
        compose.onNodeWithTag("settings-link-third-party-notices").assertIsDisplayed()
    }

    @Test
    fun exitScrollsAboveMiniPlayerAndImmediatelyShowsProgress() {
        state = state.copy(queue = listOf(QueueItem.create(track())), currentQueueIndex = 0, playbackState = RemotePlaybackState.PAUSED)
        render()
        compose.onNodeWithTag("settings-list").performScrollToIndex(7)
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
        compose.onNodeWithTag("settings-list").performScrollToIndex(7)
        compose.onNodeWithText(text(R.string.exit)).assertIsDisplayed()
        screenshot("settings-large-font")
        open("about")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-link-third-party-notices"))
        compose.onNodeWithTag("settings-link-third-party-notices").assertIsDisplayed()
    }

    @Test
    fun playbackSwitchesHaveDefaultsAndMiniPlayerCanBeHiddenImmediately() {
        state = state.copy(queue = listOf(QueueItem("current", track())), currentQueueIndex = 0)
        render()
        open("playback")
        switch("enqueue-while-playing").assertIsOff().performClick()
        switch("mini-player").assertIsOn().performClick()
        compose.onNodeWithText("Offline track").assertDoesNotExist()
        switch("clear-queue-on-play").performScrollTo().assertIsOn().performClick()
        compose.runOnIdle {
            assertTrue(state.preferences.enqueueWhilePlaying)
            assertFalse(state.preferences.miniPlayerEnabled)
            assertFalse(state.preferences.clearQueueOnPlay)
        }
        screenshot("settings-playback")
        back()
        open("playback")
        switch("enqueue-while-playing").assertIsOn()
        switch("mini-player").assertIsOff()
        switch("clear-queue-on-play").performScrollTo().assertIsOff()
    }

    @Test
    fun sleepTimerValidatesCustomDurationAndOffersFinishTrackAndCancellation() {
        render()
        open("playback")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(text(R.string.sleep_timer_custom)))
        compose.onNodeWithText(text(R.string.sleep_timer_custom)).performScrollTo().performClick()
        compose.onNodeWithTag("sleep-timer-minutes").performScrollTo().performTextReplacement("0")
        compose.onNodeWithTag("sleep-timer-start").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("sleep-timer-minutes").performScrollTo().performTextReplacement("181")
        compose.onNodeWithTag("sleep-timer-start").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("sleep-timer-minutes").performScrollTo().performTextReplacement("12")
        compose.onNodeWithTag("sleep-timer-finish-track").performScrollTo().performClick()
        compose.onNodeWithTag("sleep-timer-start").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SleepTimerRequest(12, true), timerRequest) }
        compose.onNodeWithTag("sleep-timer-cancel").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(SleepTimerPhase.OFF, state.sleepTimer.phase) }
        compose.onNodeWithTag("sleep-timer-status").performScrollTo()
        screenshot("settings-sleep-timer")
    }

    @Test
    fun queueSelectionUsesOccurrenceIdentityAndBypassesLibraryAction() {
        state = state.copy(
            destination = AppDestination.QUEUE,
            queue = List(3) { QueueItem("copy-$it", track()) },
            currentQueueIndex = 0,
            preferences = AppPreferences(enqueueWhilePlaying = true, clearQueueOnPlay = true, miniPlayerEnabled = false),
        )
        render()
        compose.onNode(hasClickAction() and hasText("Offline track") and hasAnyAncestor(hasTestTag("queue-item-copy-2"))).performClick()
        compose.runOnIdle {
            assertEquals("copy-2", state.currentQueueItem?.queueId)
            assertEquals(3, state.queue.size)
            assertEquals(0, libraryPlayClicks)
        }
        compose.onAllNodesWithContentDescription(text(R.string.playing)).assertCountEquals(1)
    }

    @Test
    fun playbackSettingsRemainReachableInChineseWithLargeFont() {
        render(fontScale = 1.5f, locale = Locale.SIMPLIFIED_CHINESE)
        open("playback")
        assertEquals("迷你播放器", text(R.string.mini_player))
        switch("enqueue-while-playing").performScrollTo().assertIsOff()
        screenshot("settings-playback-chinese-large-font")
        switch("mini-player").performScrollTo().assertIsOn()
        switch("clear-queue-on-play").performScrollTo().assertIsOn()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("sleep-timer-start"))
        compose.onNodeWithTag("sleep-timer-finish-track").performScrollTo().assertIsOff()
        compose.onNodeWithTag("sleep-timer-start").performScrollTo().assertIsDisplayed().assertIsEnabled()
        screenshot("settings-sleep-timer-chinese-large-font")
        back()
        compose.runOnIdle { assertEquals(AppDestination.SETTINGS, state.destination) }
    }

    private fun switch(key: String) = compose.onNode(isToggleable() and hasAnyAncestor(hasTestTag("setting-$key")))

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

    private fun text(id: Int): String = resourceContext.getString(id)

    private fun render(fontScale: Float? = null, locale: Locale? = null) {
        if (locale != null) {
            resourceContext = resourceContext.createConfigurationContext(
                Configuration(resourceContext.resources.configuration).apply { setLocale(locale) },
            )
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides resourceContext,
                LocalConfiguration provides resourceContext.resources.configuration,
                LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
            ) {
            LsMusicTheme(dynamicColor = false) {
                ExitProgressDialog(state.exitStatus, state.exitError) {}
                LsMusicContent(
                    state = state,
                    snackbar = remember { SnackbarHostState() },
                    onDestination = { state = state.copy(destination = it) },
                    onRefresh = {}, onSelectServer = {}, onSelectRenderer = {},
                    onOpen = {}, onNavigateTo = {}, onPlay = { libraryPlayClicks++ }, onQueue = {}, onPlayAll = {},
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
                    onPlayQueueItem = { queueId -> state = state.copy(currentQueueIndex = state.queue.indexOfFirst { it.queueId == queueId }) },
                    onEnqueueWhilePlaying = { state = state.copy(preferences = state.preferences.copy(enqueueWhilePlaying = it)) },
                    onMiniPlayerEnabled = { state = state.copy(preferences = state.preferences.copy(miniPlayerEnabled = it)) },
                    onClearQueueOnPlay = { state = state.copy(preferences = state.preferences.copy(clearQueueOnPlay = it)) },
                    onStartSleepTimer = { minutes, finish ->
                        timerRequest = SleepTimerRequest(minutes, finish)
                        state = state.copy(sleepTimer = SleepTimerState(
                            phase = SleepTimerPhase.COUNTING_DOWN, token = "fixture",
                            deadlineElapsedMs = android.os.SystemClock.elapsedRealtime() + minutes * 60_000L,
                        ))
                    },
                    onCancelSleepTimer = { state = state.copy(sleepTimer = SleepTimerState()) },
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
