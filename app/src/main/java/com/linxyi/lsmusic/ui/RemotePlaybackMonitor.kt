package com.linxyi.lsmusic.ui

internal enum class RemotePlaybackObservation { NONE, PLAYING, PAUSED, STOPPED, ENDED }

/** Interprets consecutive samples from one playback generation. */
internal class RemotePlaybackMonitor(
    val requestedTrackUri: String? = null,
    private val previousTrackUri: String? = null,
) {
    private data class Progress(val positionMs: Long, val observedAtMs: Long)

    var observedPlaying = false
        private set
    private var observedProgress = false
    private var lastProgress: Progress? = null
    var observedTrackUri: String? = null
        private set
    private var interrupted = false
    private var stoppedSamples = 0
    private var stoppedPositionMs: Long? = null
    private var plausibleEnd = false
    private var differentTrackSamples = 0

    fun connectionInterrupted() {
        interrupted = true
        clearStoppedSamples()
        differentTrackSamples = 0
    }

    fun clearStoppedSamples() {
        stoppedSamples = 0
        stoppedPositionMs = null
        plausibleEnd = false
    }

    fun seek() {
        lastProgress = null
        observedProgress = false
        clearStoppedSamples()
    }

    fun observe(
        transportState: String,
        positionMs: Long?,
        durationMs: Long,
        reportedTrackUri: String?,
        nowMs: Long,
        playRequested: Boolean,
    ): RemotePlaybackObservation {
        val uri = reportedTrackUri?.takeUnless { it.isBlank() || it == "0" || it.equals("NOT_IMPLEMENTED", ignoreCase = true) }
        val expectedUri = observedTrackUri
        val differentTrack = uri != null && if (expectedUri == null) uri == previousTrackUri else uri != expectedUri
        if (differentTrack) {
            clearStoppedSamples()
            interrupted = true
            return if (++differentTrackSamples >= 3) RemotePlaybackObservation.STOPPED else RemotePlaybackObservation.NONE
        }
        differentTrackSamples = 0
        return when (transportState.uppercase()) {
            "PLAYING" -> {
                clearStoppedSamples()
                if (observedTrackUri == null) observedTrackUri = uri
                observedPlaying = true
                interrupted = false
                if (positionMs != null) {
                    lastProgress?.let {
                        if (positionMs > it.positionMs) observedProgress = true
                    }
                    lastProgress = Progress(positionMs, nowMs)
                }
                RemotePlaybackObservation.PLAYING
            }
            "PAUSED_PLAYBACK", "PAUSED" -> {
                connectionInterrupted()
                RemotePlaybackObservation.PAUSED
            }
            "STOPPED", "NO_MEDIA_PRESENT" -> {
                val previousPosition = stoppedPositionMs
                if (positionMs != null && previousPosition != null && positionMs > previousPosition) {
                    // A stale transport state can disagree with a position that is still moving.
                    clearStoppedSamples()
                    stoppedPositionMs = positionMs
                    return RemotePlaybackObservation.NONE
                }
                stoppedPositionMs = positionMs
                if (stoppedSamples == 0) {
                    plausibleEnd = when {
                        durationMs <= 0L -> !interrupted
                        else -> lastProgress?.let {
                            val gap = (nowMs - it.observedAtMs).coerceIn(0L, MAX_END_GAP_MS)
                            durationMs - it.positionMs <= gap + POSITION_TOLERANCE_MS
                        } == true
                    }
                }
                stoppedSamples++
                val atEnd = durationMs > 0 && positionMs != null && durationMs - positionMs <= POSITION_TOLERANCE_MS
                when {
                    stoppedSamples < 2 -> RemotePlaybackObservation.NONE
                    playRequested && observedPlaying && observedProgress && (plausibleEnd || atEnd) ->
                        RemotePlaybackObservation.ENDED
                    stoppedSamples >= 3 -> RemotePlaybackObservation.STOPPED
                    else -> RemotePlaybackObservation.NONE
                }
            }
            else -> {
                clearStoppedSamples()
                RemotePlaybackObservation.NONE
            }
        }
    }

    private companion object {
        const val POSITION_TOLERANCE_MS = 2_000L
        const val MAX_END_GAP_MS = 12_000L
    }
}
