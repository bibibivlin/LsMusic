package com.linxyi.lsmusic.listenbrainz

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState

data class ListenBrainzPlaybackObservation(
    val track: MediaEntry?,
    val playbackGeneration: Long,
    val playbackState: RemotePlaybackState,
    val positionMs: Long,
    val durationMs: Long,
    val reportingEnabled: Boolean,
)

sealed interface ListenBrainzPlaybackReport {
    val track: MediaEntry

    data class NowPlaying(
        override val track: MediaEntry,
        val durationMs: Long,
    ) : ListenBrainzPlaybackReport

    data class Finished(
        override val track: MediaEntry,
        val startedAtEpochSeconds: Long,
        val durationMs: Long,
        val listenedMs: Long,
    ) : ListenBrainzPlaybackReport
}

class ListenBrainzPlaybackTracker {
    private data class Session(
        val key: String,
        val track: MediaEntry,
        val startedAtEpochSeconds: Long,
        var durationMs: Long,
        var listenedMs: Long,
        var lastObservedAtMs: Long,
        var lastPositionMs: Long,
        var wasPlaying: Boolean,
        var nowPlayingSent: Boolean,
    )

    private var session: Session? = null
    private var naturallyCompletedKey: String? = null

    fun observe(
        observation: ListenBrainzPlaybackObservation,
        elapsedRealtimeMs: Long,
        epochSeconds: Long,
    ): List<ListenBrainzPlaybackReport> {
        val reports = mutableListOf<ListenBrainzPlaybackReport>()
        val observedKey = observation.track?.sessionKey(observation.playbackGeneration)

        naturallyCompletedKey?.let { completedKey ->
            if (observedKey != completedKey || observation.playbackState == RemotePlaybackState.STOPPED) {
                naturallyCompletedKey = null
            }
        }

        session?.let { active ->
            active.accumulateUntil(elapsedRealtimeMs)
            active.durationMs = observation.durationMs.takeIf { it > 0L } ?: active.durationMs
            val trackChanged = observedKey != active.key
            val stopped = observation.playbackState == RemotePlaybackState.STOPPED
            if (trackChanged || stopped) {
                if (observation.reportingEnabled) reports += active.finishedReport()
                session = null
            }
        }

        if (
            session == null &&
            observation.track != null &&
            observation.playbackState == RemotePlaybackState.PLAYING &&
            observedKey != naturallyCompletedKey
        ) {
            session = Session(
                key = requireNotNull(observedKey),
                track = observation.track,
                startedAtEpochSeconds = epochSeconds,
                durationMs = observation.durationMs.coerceAtLeast(0L),
                listenedMs = 0L,
                lastObservedAtMs = elapsedRealtimeMs,
                lastPositionMs = observation.positionMs,
                wasPlaying = true,
                nowPlayingSent = false,
            )
        }

        session?.let { active ->
            if (!active.nowPlayingSent && observation.reportingEnabled && observation.playbackState == RemotePlaybackState.PLAYING) {
                reports += ListenBrainzPlaybackReport.NowPlaying(active.track, active.durationMs)
                active.nowPlayingSent = true
            }
            active.wasPlaying = observation.playbackState == RemotePlaybackState.PLAYING
            active.lastObservedAtMs = elapsedRealtimeMs

            val resetAfterEnd = active.durationMs > 0L &&
                active.lastPositionMs * 100L >= active.durationMs * 98L &&
                observation.positionMs < POSITION_RESET_WINDOW_MS
            val reachedEnd = active.durationMs > 0L && (
                observation.positionMs >= active.durationMs - COMPLETION_TOLERANCE_MS || resetAfterEnd
            )
            active.lastPositionMs = observation.positionMs
            if (reachedEnd) {
                if (observation.reportingEnabled) reports += active.finishedReport()
                naturallyCompletedKey = active.key
                session = null
            }
        }
        return reports
    }

    private fun Session.accumulateUntil(elapsedRealtimeMs: Long) {
        if (wasPlaying) listenedMs += (elapsedRealtimeMs - lastObservedAtMs).coerceAtLeast(0L)
        lastObservedAtMs = elapsedRealtimeMs
    }

    private fun Session.finishedReport() = ListenBrainzPlaybackReport.Finished(
        track = track,
        startedAtEpochSeconds = startedAtEpochSeconds,
        durationMs = durationMs,
        listenedMs = listenedMs,
    )

    private fun MediaEntry.sessionKey(playbackGeneration: Long): String =
        "$playbackGeneration\u0000$id\u0000${resourceUri.orEmpty()}"

    private companion object {
        const val COMPLETION_TOLERANCE_MS = 750L
        const val POSITION_RESET_WINDOW_MS = 2_000L
    }
}

fun shouldSubmitListen(
    listenedMs: Long,
    durationMs: Long,
    minimumSeconds: Int,
    minimumPercent: Int,
): Boolean {
    val meetsDuration = listenedMs >= minimumSeconds.coerceAtLeast(0) * 1_000L
    val meetsPercent = durationMs > 0L &&
        listenedMs * 100L >= durationMs * minimumPercent.coerceIn(0, 100)
    return meetsDuration || meetsPercent
}
