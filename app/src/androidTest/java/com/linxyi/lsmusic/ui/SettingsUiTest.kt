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
        compose.onNodeWithText("播放与设备").assertDoesNotExist()
        compose.onNodeWithText("媒体库与播放设备").assertIsDisplayed()
        compose.onNodeWithText("扫描局域网设备").assertIsDisplayed()
        compose.onNodeWithText("封面大小").assertDoesNotExist()
        compose.onNodeWithText("在线获取歌词").assertDoesNotExist()
        compose.onNodeWithText("ListenBrainz API").assertDoesNotExist()
        listOf("界面", "歌词", "网络", "关于").forEachIndexed { index, title ->
            compose.onNodeWithTag("settings-list").performScrollToIndex(index + 2)
            compose.waitUntil(5_000L) { compose.onNodeWithTag("settings-link-$title").isDisplayed() }
        }
        compose.onNodeWithText("退出").performScrollTo().assertIsDisplayed()
        screenshot("settings-home")
    }

    @Test
    fun appearanceAndNetworkRestorePreferencesDraftAndPagePosition() {
        render()
        open("界面")
        compose.onNodeWithText("大封面").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.runOnIdle { assertEquals(GallerySize.LARGE, state.preferences.gallerySize) }
        screenshot("settings-appearance")
        back()
        open("界面")
        compose.onNodeWithText("大封面").assertIsSelected()
        back()
        open("网络")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextInput("offline draft")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("上传规则"))
        val offset = compose.onNodeWithText("上传规则").fetchSemanticsNode().boundsInRoot.top
        // Bottom navigation deliberately returns to the root, and the saved network page restores.
        compose.onNode(hasText("设置") and hasClickAction()).performClick()
        open("网络")
        assertEquals(offset, compose.onNodeWithText("上传规则").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("settings-list").performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).assertTextContains("offline draft")
    }

    @Test
    fun pendingRecordsReturnToNetworkAndEntryIsConditional() {
        render()
        open("网络")
        compose.onNodeWithText("待上传记录").assertDoesNotExist()
        compose.runOnIdle {
            state = state.copy(pendingListens = listOf(PendingListen(
                id = "fixture", track = track(), startedAtEpochSeconds = 1_700_000_000L,
                durationMs = 300_000L, listenedMs = 180_000L, queuedAtEpochSeconds = 1_700_000_180L,
            )))
        }
        compose.onNodeWithText("查看并管理").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回网络设置").performClick()
        compose.runOnIdle { assertEquals(AppDestination.SETTINGS_NETWORK, state.destination) }
        back()
        compose.runOnIdle { assertEquals(AppDestination.SETTINGS, state.destination) }
    }

    @Test
    fun lyricsAndAboutHaveSeparateContentAndProjectInformation() {
        render()
        open("歌词")
        compose.onNodeWithText("在线获取歌词").assertIsDisplayed()
        compose.onNodeWithText("ListenBrainz API").assertDoesNotExist()
        screenshot("settings-lyrics")
        back()
        open("关于")
        compose.onNodeWithText("L’s Music").assertIsDisplayed()
        compose.onNodeWithText("MIT 开源许可证").assertIsDisplayed()
        compose.onNodeWithText("你的音乐，你的选择").assertDoesNotExist()
        screenshot("settings-about")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-link-第三方声明"))
        compose.onNodeWithTag("settings-link-第三方声明").assertIsDisplayed()
    }

    @Test
    fun exitScrollsAboveMiniPlayerAndImmediatelyShowsProgress() {
        state = state.copy(queue = listOf(track()), currentQueueIndex = 0, playbackState = RemotePlaybackState.PAUSED)
        render()
        compose.onNodeWithTag("settings-list").performScrollToIndex(6)
        compose.onNodeWithText("退出").assertIsDisplayed()
        val exitBottom = compose.onNodeWithText("退出").fetchSemanticsNode().boundsInRoot.bottom
        val playerTop = compose.onNodeWithText("Offline track").fetchSemanticsNode().boundsInRoot.top
        assertTrue(exitBottom < playerTop)
        screenshot("settings-mini-player")
        compose.onNodeWithText("退出").performClick()
        compose.onNodeWithText("正在退出…").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    fun secondaryPageSlidesInWhilePreviousPageFadesOut() {
        render()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("settings-link-界面").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64L)
        val enteringLeft = compose.onNodeWithText("封面大小").fetchSemanticsNode().boundsInRoot.left
        compose.onNodeWithText("扫描局域网设备").assertExists()
        compose.mainClock.advanceTimeBy(240L)
        val settledLeft = compose.onNodeWithText("封面大小").fetchSemanticsNode().boundsInRoot.left
        assertTrue(enteringLeft > settledLeft)
        compose.onNodeWithText("扫描局域网设备").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun largeFontKeepsExitAndAboutLinksReachable() {
        render(fontScale = 1.5f)
        compose.onNodeWithTag("settings-list").performScrollToIndex(6)
        compose.onNodeWithText("退出").assertIsDisplayed()
        screenshot("settings-large-font")
        open("关于")
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-link-第三方声明"))
        compose.onNodeWithTag("settings-link-第三方声明").assertIsDisplayed()
    }

    private fun open(title: String) {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-link-$title"))
        compose.onNodeWithTag("settings-link-$title").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }

    private fun back() {
        compose.onNodeWithTag("settings-list").performScrollToNode(hasContentDescription("返回设置"))
        compose.onNodeWithContentDescription("返回设置").performClick()
        compose.waitForIdle()
    }

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
