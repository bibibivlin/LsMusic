package com.linxyi.lsmusic.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.ui.theme.LsMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlaybackSliderUiTest {
    @get:Rule val compose = createAndroidComposeRule<ThemeTestActivity>()

    private val track = MediaEntry("track", "album", "Track", isContainer = false)
    private var state by mutableStateOf(
        LsMusicUiState(
            queue = listOf(QueueItem.create(track)),
            currentQueueIndex = 0,
            positionMs = 20_000L,
            durationMs = 100_000L,
        ),
    )
    private val seeks = mutableListOf<Long>()

    @Test
    fun accessibilitySeekUsesRequestedPositionAndTracksPlaybackUpdates() {
        render()
        assertEquals(.2f, progress(), .001f)
        compose.runOnIdle { state = state.copy(positionMs = 40_000L) }
        assertEquals(.4f, progress(), .001f)

        slider().performSemanticsAction(SemanticsActions.SetProgress) { action ->
            assertTrue(action(.75f))
        }
        compose.runOnIdle { assertEquals(listOf(75_000L), seeks) }
        compose.runOnIdle { state = state.copy(positionMs = 75_000L) }
        assertEquals(.75f, progress(), .001f)
    }

    @Test
    fun dragKeepsSelectedPositionDuringPlaybackUpdatesAndSeeksOnRelease() {
        render()
        slider().performTouchInput {
            down(Offset(width * .2f, centerY))
            moveTo(Offset(width * .65f, centerY))
        }
        compose.runOnIdle {
            assertTrue(seeks.isEmpty())
            state = state.copy(positionMs = 30_000L)
        }
        assertTrue(progress() in 0.60f..0.70f)

        slider().performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, seeks.size)
            assertTrue(seeks.single() in 60_000L..70_000L)
        }
    }

    @Test
    fun unknownDurationDisablesSeeking() {
        state = state.copy(durationMs = 0L)
        render()
        slider().assertIsNotEnabled()
    }

    @Test
    fun playingProgressMovesBetweenUpdatesAndStopsWhenPaused() {
        state = state.copy(
            playbackState = RemotePlaybackState.PLAYING,
            selectedRendererId = LsMusicViewModel.LOCAL_RENDERER_ID,
        )
        compose.mainClock.autoAdvance = false
        render()
        compose.mainClock.advanceTimeByFrame()
        val start = progress()
        compose.mainClock.advanceTimeBy(250L)
        val moving = progress()
        assertTrue(moving > start + .001f)

        compose.runOnIdle { state = state.copy(playbackState = RemotePlaybackState.PAUSED) }
        compose.mainClock.advanceTimeByFrame()
        val paused = progress()
        compose.mainClock.advanceTimeBy(500L)
        assertEquals(paused, progress(), .0001f)

        compose.runOnIdle { state = state.copy(positionMs = 60_000L, seekGeneration = 1L) }
        compose.mainClock.advanceTimeBy(80L)
        assertEquals(.6f, progress(), .001f)
    }

    @Test
    fun remoteWholeSecondUpdatesKeepProgressMovingContinuously() {
        state = state.copy(playbackState = RemotePlaybackState.PLAYING, selectedRendererId = "remote")
        compose.mainClock.autoAdvance = false
        render()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(900L)
        val beforeUpdate = progress()

        compose.runOnIdle { state = state.copy(positionMs = 21_000L) }
        compose.mainClock.advanceTimeBy(64L)
        val afterUpdate = progress()
        assertTrue(afterUpdate > beforeUpdate)
        assertTrue(afterUpdate - beforeUpdate < .002f)
    }

    @Test
    fun seekWhilePlayingIsImmediateAndTrackRestartResetsProgress() {
        state = state.copy(playbackState = RemotePlaybackState.PLAYING)
        compose.mainClock.autoAdvance = false
        render()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(200L)

        slider().performSemanticsAction(SemanticsActions.SetProgress) { action ->
            assertTrue(action(.75f))
        }
        compose.mainClock.advanceTimeBy(64L)
        assertEquals(.75f, progress(), .001f)
        compose.runOnIdle {
            assertEquals(listOf(75_000L), seeks)
            state = state.copy(positionMs = 75_000L, seekGeneration = 1L)
        }
        compose.mainClock.advanceTimeBy(64L)
        assertEquals(.75f, progress(), .001f)

        compose.runOnIdle { state = state.copy(positionMs = 0L, playbackGeneration = 1L) }
        compose.mainClock.advanceTimeBy(80L)
        assertTrue(progress() < .002f)
    }

    private fun slider() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))

    private fun progress(): Float =
        slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun render() {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        compose.setContent {
            LsMusicTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = state,
                    onTogglePlayback = {},
                    onPrevious = {},
                    onNext = {},
                    onCycleRepeat = {},
                    onToggleShuffle = {},
                    onSeek = { seeks += it },
                    onLoadLyrics = {},
                    onRetryLyrics = {},
                )
            }
        }
    }
}
