package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.dlna.isPauseRejected
import com.linxyi.lsmusic.ui.*
import org.junit.Assert.*
import org.junit.Test

class SleepTimerTest {
    private val timer = SleepTimerState(
        phase = SleepTimerPhase.COUNTING_DOWN, token = "new-timer", deadlineElapsedMs = 60_000L,
    )

    @Test
    fun deadline_usesElapsedTimeAndIgnoresEarlyOrObsoleteDelivery() {
        assertEquals(SleepTimerDeadlineAction.NONE, action(now = 59_999L))
        assertEquals(SleepTimerDeadlineAction.NONE, action(token = "old-timer"))
        assertEquals(SleepTimerDeadlineAction.PAUSE, action())
        assertEquals(SleepTimerDeadlineAction.PAUSE, action(now = 180_000L))
    }

    @Test
    fun cancelledAndConsumedTimers_doNotActAgain() {
        for (phase in SleepTimerPhase.entries.filter { it != SleepTimerPhase.COUNTING_DOWN }) {
            assertEquals(SleepTimerDeadlineAction.NONE, action(timer.copy(phase = phase)))
        }
    }

    @Test
    fun deadlineWhilePausedStoppedOrEmpty_completesWithoutStartingMusic() {
        for (finish in listOf(false, true)) {
            val configured = timer.copy(finishCurrentTrack = finish)
            assertEquals(SleepTimerDeadlineAction.COMPLETE, action(configured, state = RemotePlaybackState.PAUSED))
            assertEquals(SleepTimerDeadlineAction.COMPLETE, action(configured, state = RemotePlaybackState.STOPPED))
            assertEquals(SleepTimerDeadlineAction.COMPLETE, action(configured, queueId = null))
        }
    }

    @Test
    fun finishTrackMode_locksPlaybackGenerationAndQueueOccurrence() {
        assertEquals(SleepTimerDeadlineAction.FINISH_TRACK, action(timer.copy(finishCurrentTrack = true)))
        val finishing = timer.copy(phase = SleepTimerPhase.FINISHING_TRACK, queueId = "copy-2", playbackGeneration = 8L)
        assertTrue(finishing.shouldStopAfterTrack("copy-2", 8L))
        assertFalse(finishing.shouldStopAfterTrack("copy-1", 8L))
        assertFalse(finishing.shouldStopAfterTrack("copy-2", 9L))
        assertFalse(finishing.copy(phase = SleepTimerPhase.OFF).shouldStopAfterTrack("copy-2", 8L))
    }

    @Test
    fun countdown_roundsUpAndNeverBecomesNegativeEvenAfterLongSleep() {
        assertEquals(60L, timer.remainingSeconds(0L))
        assertEquals(1L, timer.remainingSeconds(59_001L))
        assertEquals(0L, timer.remainingSeconds(60_000L))
        assertEquals(0L, timer.remainingSeconds(86_400_000L))
    }

    @Test
    fun pauseFallback_requiresAnExplicitDeviceRejection() {
        assertTrue(isPauseRejected(UiText.Resource(R.string.error_unsupported_action, listOf("Pause"))))
        assertTrue(isPauseRejected(UiText.Raw("UPnP error: 701 Transition not available")))
        assertTrue(isPauseRejected(UiText.Resource(R.string.error_transport_action, listOf("Pause", "Error code: 401"))))
        assertTrue(isPauseRejected(UiText.Raw("HTTP 500: UPnPError 401")))
        assertTrue(isPauseRejected(UiText.Raw("<UPnPError><errorCode>701</errorCode></UPnPError>")))
        assertFalse(isPauseRejected(UiText.Raw("Connection refused")))
        assertFalse(isPauseRejected(UiText.Raw("Read timed out after 701 milliseconds")))
        assertFalse(isPauseRejected(UiText.Raw("HTTP 501")))
    }

    @Test
    fun commandIdentity_rejectsSameSongFromDifferentOccurrencesGenerationsAndRenderers() {
        val identity = PlaybackCommandIdentity("renderer", "copy-1", 1L, 3L)
        assertNotEquals(identity, identity.copy(queueId = "copy-2"))
        assertNotEquals(identity, identity.copy(playbackGeneration = 2L))
        assertNotEquals(identity, identity.copy(commandGeneration = 4L))
        assertNotEquals(identity, identity.copy(rendererId = "other"))
    }

    private fun action(
        timer: SleepTimerState = this.timer,
        token: String = "new-timer",
        now: Long = 60_000L,
        queueId: String? = "track",
        state: RemotePlaybackState = RemotePlaybackState.PLAYING,
    ) = sleepTimerDeadlineAction(timer, token, now, queueId, state)
}
