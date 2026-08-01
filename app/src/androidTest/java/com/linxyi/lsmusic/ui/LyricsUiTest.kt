package com.linxyi.lsmusic.ui

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.dp
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.lyrics.LyricsDocument
import com.linxyi.lsmusic.lyrics.LyricsLine
import com.linxyi.lsmusic.lyrics.LyricsLoadState
import com.linxyi.lsmusic.lyrics.LyricsProviderId
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LyricsUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun artworkOpensLyricsAndLyricsTapReturnsToArtwork() {
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = playingState(lyricsEnabled = true, lyricsLoadState = LyricsLoadState.NotFound),
                    modifier = Modifier.requiredSize(width = 412.dp, height = 700.dp),
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

        compose.onNodeWithText("无歌词").assertDoesNotExist()
        compose.onNodeWithContentDescription("专辑封面，点击打开歌词").performClick()
        compose.onNodeWithText("无歌词").assertIsDisplayed().performClick()
        compose.onNodeWithText("无歌词").assertDoesNotExist()
    }

    @Test
    fun squareFoldableLayoutReplacesArtworkWithLyrics() {
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = playingState(lyricsEnabled = true, lyricsLoadState = LyricsLoadState.NotFound),
                    modifier = Modifier.requiredSize(width = 800.dp, height = 800.dp),
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

        compose.onNodeWithContentDescription("专辑封面，点击打开歌词").performClick()
        compose.onNodeWithContentDescription("专辑封面，点击打开歌词").assertDoesNotExist()
        compose.onNodeWithText("无歌词").assertIsDisplayed()
    }

    @Test
    fun wideLandscapeLayoutKeepsArtworkBesideOpenedLyrics() {
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = playingState(lyricsEnabled = true, lyricsLoadState = LyricsLoadState.NotFound),
                    modifier = Modifier.requiredSize(width = 1000.dp, height = 700.dp),
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

        compose.onNodeWithContentDescription("专辑封面，点击打开歌词").performClick()
        compose.onNodeWithContentDescription("专辑封面，点击打开歌词").assertIsDisplayed()
        compose.onNodeWithText("无歌词").assertIsDisplayed()
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

        compose.onNodeWithContentDescription("专辑封面").assertHasNoClickAction()
        compose.onNodeWithText("无歌词").assertDoesNotExist()
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

        compose.onNodeWithText("点击重试").performClick()
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
                                    original = "测试歌词",
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

        compose.onNodeWithText("测试歌词").assertIsDisplayed()
        compose.onNodeWithText("歌词来源：QQ音乐").assertDoesNotExist()
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
        ),
        currentQueueIndex = 0,
        durationMs = 180_000L,
        lyricsLoadState = lyricsLoadState,
        preferences = AppPreferences(lyricsEnabled = lyricsEnabled),
    )
}
