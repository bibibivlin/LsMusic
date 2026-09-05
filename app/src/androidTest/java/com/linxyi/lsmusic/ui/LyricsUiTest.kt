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
import androidx.test.platform.app.InstrumentationRegistry
import com.linxyi.lsmusic.R
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

        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertDoesNotExist()
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).performClick()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed().performClick()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertDoesNotExist()
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

        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).performClick()
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed()
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

        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).performClick()
        compose.onNodeWithContentDescription(text(R.string.album_cover_open_lyrics, "Track")).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.lyrics_not_found)).assertIsDisplayed()
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

    private fun text(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)
}
