package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.PlaybackProgressInterpolator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressInterpolatorTest {
    @Test
    fun playbackMovesBetweenSamplesAndCorrectsSmallErrorsGradually() {
        val progress = PlaybackProgressInterpolator(20_000L, 0L)
        assertEquals(20_000L, progress.frame(20_000L, 100_000L, 0L, false, 0L))
        assertEquals(20_200L, progress.frame(20_000L, 100_000L, 0L, false, nanos(200)))
        assertEquals(20_500L, progress.frame(20_485L, 100_000L, 0L, false, nanos(500)))
        assertEquals(20_585L, progress.frame(20_485L, 100_000L, 0L, false, nanos(600)))
    }

    @Test
    fun wholeSecondRemoteSamplesDoNotCauseVisibleBacktracking() {
        val progress = PlaybackProgressInterpolator(20_000L, 0L)
        progress.frame(20_000L, 100_000L, 0L, true, 0L)
        val beforeUpdate = progress.frame(20_000L, 100_000L, 0L, true, nanos(900))
        val onUpdate = progress.frame(21_000L, 100_000L, 0L, true, nanos(1_000))
        val afterUpdate = progress.frame(21_000L, 100_000L, 0L, true, nanos(1_050))
        val lateUpdate = progress.frame(22_000L, 100_000L, 0L, true, nanos(2_300))
        val afterLateUpdate = progress.frame(22_000L, 100_000L, 0L, true, nanos(2_350))

        assertEquals(20_900L, beforeUpdate)
        assertEquals(21_000L, onUpdate)
        assertTrue(afterUpdate > onUpdate)
        assertTrue(afterUpdate - onUpdate <= 63L)
        assertTrue(afterLateUpdate > lateUpdate)
        assertTrue(afterLateUpdate - lateUpdate <= 63L)
    }

    @Test
    fun seekTakesEffectImmediatelyAndOlderSamplesCannotUndoIt() {
        val progress = PlaybackProgressInterpolator(20_000L, 0L)
        progress.frame(20_000L, 100_000L, 0L, false, 0L)
        progress.frame(20_000L, 100_000L, 0L, false, nanos(500))

        assertEquals(75_000L, progress.seekImmediately(75_000L, 100_000L))
        assertEquals(75_000L, progress.frame(20_000L, 100_000L, 0L, false, nanos(600)))
        assertEquals(75_100L, progress.frame(20_000L, 100_000L, 0L, false, nanos(700)))
        assertEquals(75_000L, progress.frame(75_000L, 100_000L, 1L, false, nanos(716)))
        assertEquals(74_500L, progress.frame(74_500L, 100_000L, 2L, false, nanos(732)))
    }

    @Test
    fun pauseFreezesUntilAnUpdatedPositionArrivesAndResumeDoesNotCountPausedTime() {
        val progress = PlaybackProgressInterpolator(20_000L, 0L)
        progress.frame(20_000L, 100_000L, 0L, false, 0L)
        progress.frame(20_000L, 100_000L, 0L, false, nanos(500))

        assertEquals(20_500L, progress.pause(20_000L, 100_000L, 0L))
        assertEquals(20_500L, progress.pause(20_000L, 100_000L, 0L))
        assertEquals(20_580L, progress.pause(20_580L, 100_000L, 0L))
        assertEquals(20_580L, progress.frame(20_580L, 100_000L, 0L, false, nanos(10_000)))
        assertEquals(20_680L, progress.frame(20_580L, 100_000L, 0L, false, nanos(10_100)))
    }

    @Test
    fun staleSamplesAndTrackDurationBoundExtrapolation() {
        val stale = PlaybackProgressInterpolator(20_000L, 0L)
        stale.frame(20_000L, 100_000L, 0L, false, 0L)
        assertEquals(25_000L, stale.frame(20_000L, 100_000L, 0L, false, nanos(10_000)))
        assertEquals(25_000L, stale.frame(20_000L, 100_000L, 0L, false, nanos(11_000)))

        val nearEnd = PlaybackProgressInterpolator(99_800L, 0L)
        nearEnd.frame(99_800L, 100_000L, 0L, false, 0L)
        assertEquals(100_000L, nearEnd.frame(99_800L, 100_000L, 0L, false, nanos(500)))
    }

    @Test
    fun backwardsAndLargeDiscontinuousSamplesAreAppliedImmediately() {
        val progress = PlaybackProgressInterpolator(20_000L, 0L)
        progress.frame(20_000L, 100_000L, 0L, true, 0L)
        assertEquals(21_000L, progress.frame(21_000L, 100_000L, 0L, true, nanos(1_000)))
        assertEquals(19_000L, progress.frame(19_000L, 100_000L, 0L, true, nanos(1_100)))
        assertEquals(45_000L, progress.frame(45_000L, 100_000L, 0L, true, nanos(1_200)))
    }

    private fun nanos(millis: Long): Long = millis * 1_000_000L
}
