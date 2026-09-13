package com.linxyi.lsmusic.ui

import com.linxyi.lsmusic.dlna.RemotePlaybackState

enum class SleepTimerPhase { OFF, COUNTING_DOWN, FINISHING_TRACK, APPLYING, FAILED }

data class SleepTimerRequest(val minutes: Int, val finishCurrentTrack: Boolean)

data class SleepTimerState(
    val phase: SleepTimerPhase = SleepTimerPhase.OFF,
    val token: String? = null,
    val deadlineElapsedMs: Long = 0L,
    val finishCurrentTrack: Boolean = false,
    val queueId: String? = null,
    val playbackGeneration: Long? = null,
    val error: UiText? = null,
) {
    fun remainingSeconds(nowElapsedMs: Long): Long =
        ((deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0L) + 999L) / 1_000L
}

internal enum class SleepTimerDeadlineAction { NONE, COMPLETE, PAUSE, FINISH_TRACK }

internal fun sleepTimerDeadlineAction(
    timer: SleepTimerState,
    token: String,
    nowElapsedMs: Long,
    currentQueueId: String?,
    playbackState: RemotePlaybackState,
): SleepTimerDeadlineAction = when {
    timer.phase != SleepTimerPhase.COUNTING_DOWN || timer.token != token || nowElapsedMs < timer.deadlineElapsedMs ->
        SleepTimerDeadlineAction.NONE
    currentQueueId == null || playbackState != RemotePlaybackState.PLAYING -> SleepTimerDeadlineAction.COMPLETE
    timer.finishCurrentTrack -> SleepTimerDeadlineAction.FINISH_TRACK
    else -> SleepTimerDeadlineAction.PAUSE
}

internal fun SleepTimerState.shouldStopAfterTrack(queueId: String?, generation: Long): Boolean =
    phase == SleepTimerPhase.FINISHING_TRACK && this.queueId == queueId && playbackGeneration == generation
