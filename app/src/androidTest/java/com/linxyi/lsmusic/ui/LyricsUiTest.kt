package com.linxyi.lsmusic.ui

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.lyrics.LyricsDocument
import com.linxyi.lsmusic.lyrics.LyricsLine
import com.linxyi.lsmusic.lyrics.LyricsLoadState
import com.linxyi.lsmusic.lyrics.LyricsProviderId
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LyricsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun artworkAndLyricsRemainVisibleUntilTheirTransitionFinishes() {
        compose.setContent {
            NowPlayingTestLayout(DpSize(412.dp, 700.dp)) {
                NowPlayingScreen(
                    state = playingState(lyricsEnabled = true, lyricsLoadState = LyricsLoadState.NotFound),
                    onTogglePlayback = {},
                    onPrevious = {},
                    onNext = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onSeek = {},
                    onLoadLyrics = {},
                    onRetryLyrics = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80L)
        compose.onNodeWithContentDescription(text(R.string.album_cover_close_lyrics, "Track")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(400L)
        compose.onNodeWithContentDescription(text(R.string.album_cover_close_lyrics, "Track")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80L)
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed()
        compose.mainClock.advanceTimeBy(400L)
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun squareFoldableLayoutReplacesArtworkWithLyrics() {
        compose.setContent {
            NowPlayingTestLayout(DpSize(800.dp, 800.dp)) {
                NowPlayingScreen(
                    state = playingState(lyricsEnabled = true, lyricsLoadState = LyricsLoadState.NotFound),
                    onTogglePlayback = {},
                    onPrevious = {},
                    onNext = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onSeek = {},
                    onLoadLyrics = {},
                    onRetryLyrics = {},
                )
            }
        }

        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).performClick()
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.album_cover_close_lyrics, "Track")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed()
    }

    @Test
    fun wideLandscapeArtworkMovesSmoothlyAndCanReverseBeforeSettling() {
        compose.setContent {
            NowPlayingTestLayout(DpSize(1000.dp, 700.dp)) {
                NowPlayingScreen(
                    state = playingState(lyricsEnabled = true, lyricsLoadState = LyricsLoadState.NotFound),
                    onTogglePlayback = {},
                    onPrevious = {},
                    onNext = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onSeek = {},
                    onLoadLyrics = {},
                    onRetryLyrics = {},
                )
            }
        }

        val centered = compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track"))
            .fetchSemanticsNode().boundsInRoot
        val titleBounds = compose.onNodeWithText("Track").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80L)
        val opening = compose.onNodeWithContentDescription(text(R.string.album_cover_close_lyrics, "Track"))
            .fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(400L)
        val beside = compose.onNodeWithContentDescription(text(R.string.album_cover_close_lyrics, "Track"))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(opening.center.x < centered.center.x)
        assertTrue(opening.center.x > beside.center.x)
        assertEquals(titleBounds, compose.onNodeWithText("Track").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.album_cover_close_lyrics, "Track")).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80L)
        val closing = compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track"))
            .fetchSemanticsNode().boundsInRoot
        assertTrue(closing.center.x > beside.center.x)
        assertTrue(closing.center.x < centered.center.x)
        // Reverse the closing animation while the artwork is still moving.
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600L)
        assertEquals(
            beside,
            compose.onNodeWithContentDescription(text(R.string.album_cover_close_lyrics, "Track"))
                .fetchSemanticsNode().boundsInRoot,
        )
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400L)
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertDoesNotExist()
        assertEquals(
            centered,
            compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track"))
                .fetchSemanticsNode().boundsInRoot,
        )
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun disabledOnlineLyricsRemovesArtworkEntryAction() {
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = playingState(lyricsEnabled = false),
                    onTogglePlayback = {},
                    onPrevious = {},
                    onNext = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onSeek = {},
                    onLoadLyrics = {},
                    onRetryLyrics = {},
                )
            }
        }

        compose.onNodeWithContentDescription(text(R.string.album_cover, "Track")).assertHasNoClickAction()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertDoesNotExist()
    }

    @Test
    fun retryButtonDoesNotCloseErrorPanel() {
        var retried = false
        var closed = false
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                LyricsPanel(
                    loadState = LyricsLoadState.Error("offline"),
                    positionMs = 0L,
                    durationMs = 0L,
                    isPlaying = false,
                    translationMode = com.linxyi.lsmusic.lyrics.LyricsTranslationMode.ORIGINAL,
                    sourceVisible = true,
                    effectsEnabled = true,
                    fontSizeSp = 28,
                    onRetry = { retried = true },
                    onClose = { closed = true },
                )
            }
        }

        compose.onNodeWithText(text(R.string.retry)).performClick()
        compose.runOnIdle {
            assertTrue(retried)
            assertFalse(closed)
        }
    }

    @Test
    fun lyricsSourceCanBeHidden() {
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                LyricsPanel(
                    loadState = LyricsLoadState.Loaded(
                        LyricsDocument(
                            provider = LyricsProviderId.QQ,
                            lines = listOf(
                                LyricsLine(
                                    stableId = "line",
                                    startMs = 0L,
                                    original = "Test lyrics",
                                ),
                            ),
                        ),
                    ),
                    positionMs = 0L,
                    durationMs = 180_000L,
                    isPlaying = false,
                    translationMode = com.linxyi.lsmusic.lyrics.LyricsTranslationMode.ORIGINAL,
                    sourceVisible = false,
                    effectsEnabled = true,
                    fontSizeSp = 28,
                    onRetry = {},
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("Test lyrics").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.lyrics_source_label, text(R.string.lyrics_provider_qq))).assertDoesNotExist()
    }

    @Test
    fun lyricsSourceUsesSeparateFooterAndReturnsItsSpaceWhenHidden() {
        var sourceVisible by mutableStateOf(true)
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                LyricsPanel(
                    loadState = LyricsLoadState.Loaded(
                        LyricsDocument(
                            provider = LyricsProviderId.QQ,
                            lines = listOf(
                                LyricsLine(
                                    stableId = "line",
                                    startMs = 0L,
                                    original = "Test lyrics",
                                ),
                            ),
                        ),
                    ),
                    positionMs = 0L,
                    durationMs = 180_000L,
                    isPlaying = false,
                    translationMode = com.linxyi.lsmusic.lyrics.LyricsTranslationMode.ORIGINAL,
                    sourceVisible = sourceVisible,
                    effectsEnabled = false,
                    fontSizeSp = 28,
                    onRetry = {},
                    onClose = {},
                    modifier = Modifier.requiredSize(width = 320.dp, height = 420.dp),
                )
            }
        }

        val viewportWithSource = compose.onNodeWithTag(
            testTag = LYRICS_VIEWPORT_TEST_TAG,
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        val sourceBounds = compose.onNodeWithTag(
            testTag = LYRICS_SOURCE_TEST_TAG,
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot

        assertTrue(viewportWithSource.bottom <= sourceBounds.top)

        compose.runOnIdle { sourceVisible = false }
        compose.onNodeWithTag(
            testTag = LYRICS_SOURCE_TEST_TAG,
            useUnmergedTree = true,
        ).assertDoesNotExist()

        val viewportWithoutSource = compose.onNodeWithTag(
            testTag = LYRICS_VIEWPORT_TEST_TAG,
            useUnmergedTree = true,
        )
            .fetchSemanticsNode().boundsInRoot
        assertTrue(viewportWithoutSource.height > viewportWithSource.height)
    }

    @Composable
    private fun NowPlayingTestLayout(size: DpSize, content: @Composable () -> Unit) {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            LsMusicTheme(dynamicColor = false, content = content)
        }
    }

    private fun playingState(
        lyricsEnabled: Boolean,
        lyricsLoadState: LyricsLoadState = LyricsLoadState.Idle,
    ): LsMusicUiState = LsMusicUiState(
        queue = listOf(
            MediaEntry(
                id = "track",
                parentId = "album",
                title = "Track",
                creator = "Artist",
                album = "Album",
                duration = "03:00",
                resourceUri = "https://example.test/track.flac",
                isContainer = false,
            ),
        ).map(QueueItem::create),
        currentQueueIndex = 0,
        durationMs = 180_000L,
        lyricsLoadState = lyricsLoadState,
        preferences = AppPreferences(lyricsEnabled = lyricsEnabled),
    )

    private fun text(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
}
