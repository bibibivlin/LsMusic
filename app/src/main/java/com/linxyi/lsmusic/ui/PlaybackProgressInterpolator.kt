package com.linxyi.lsmusic.ui

import kotlin.math.abs

/** Keeps the visible playback position moving between player updates. */
internal class PlaybackProgressInterpolator(
    initialPositionMs: Long,
    initialSeekGeneration: Long,
) {
    private var positionMs = initialPositionMs.coerceAtLeast(0L).toDouble()
    private var lastReportedPositionMs = initialPositionMs.coerceAtLeast(0L)
    private var lastSeekGeneration = initialSeekGeneration
    private var lastFrameNanos: Long? = null
    private var correctionMs = 0.0
    private var pendingSeekPositionMs: Long? = null

    fun seekImmediately(targetMs: Long, durationMs: Long): Long {
        val target = boundedPosition(targetMs, durationMs)
        positionMs = target.toDouble()
        pendingSeekPositionMs = target
        correctionMs = 0.0
        lastFrameNanos = null
        return target
    }

    fun pause(reportedPositionMs: Long, durationMs: Long, seekGeneration: Long): Long {
        val reported = boundedPosition(reportedPositionMs, durationMs)
        if (seekGeneration != lastSeekGeneration ||
            (pendingSeekPositionMs == null && reported != lastReportedPositionMs)
        ) {
            positionMs = reported.toDouble()
            pendingSeekPositionMs = null
        }
        lastReportedPositionMs = reported
        lastSeekGeneration = seekGeneration
        lastFrameNanos = null
        correctionMs = 0.0
        return boundedDisplayPosition(durationMs)
    }

    fun frame(
        reportedPositionMs: Long,
        durationMs: Long,
        seekGeneration: Long,
        coarsePosition: Boolean,
        frameNanos: Long,
    ): Long {
        val elapsedMs = lastFrameNanos?.let { (frameNanos - it).coerceAtLeast(0L) / 1_000_000.0 } ?: 0.0
        val adjustment = correctionMs.coerceIn(-elapsedMs * MAX_CORRECTION_RATE, elapsedMs * MAX_CORRECTION_RATE)
        positionMs += elapsedMs + adjustment
        correctionMs -= adjustment
        lastFrameNanos = frameNanos

        val reported = boundedPosition(reportedPositionMs, durationMs)
        if (seekGeneration != lastSeekGeneration) {
            positionMs = reported.toDouble()
            pendingSeekPositionMs = null
            correctionMs = 0.0
        } else if (pendingSeekPositionMs == null && reported != lastReportedPositionMs) {
            // DLNA commonly reports whole seconds. The actual position can be anywhere
            // within that second, so steer toward its midpoint without moving backwards.
            val target = (reported.toDouble() + if (coarsePosition && reported % 1_000L == 0L) 500.0 else 0.0)
                .coerceAtMost(durationLimit(durationMs))
            val difference = target - positionMs
            if (reported < lastReportedPositionMs || abs(difference) > MAX_SMOOTH_CORRECTION_MS) {
                positionMs = reported.toDouble()
                correctionMs = 0.0
            } else {
                correctionMs = difference
            }
        }
        lastReportedPositionMs = reported
        lastSeekGeneration = seekGeneration

        // Do not extrapolate indefinitely if the player stops providing updates.
        val lastKnownPosition = pendingSeekPositionMs ?: reported
        positionMs = positionMs.coerceAtMost(lastKnownPosition.toDouble() + MAX_EXTRAPOLATION_MS)
        return boundedDisplayPosition(durationMs)
    }

    private fun boundedDisplayPosition(durationMs: Long): Long {
        positionMs = positionMs.coerceIn(0.0, durationLimit(durationMs))
        return positionMs.toLong()
    }

    private fun boundedPosition(positionMs: Long, durationMs: Long): Long =
        positionMs.coerceIn(0L, durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)

    private fun durationLimit(durationMs: Long): Double =
        (durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE).toDouble()
}

private const val MAX_CORRECTION_RATE = 0.25
private const val MAX_SMOOTH_CORRECTION_MS = 3_000.0
private const val MAX_EXTRAPOLATION_MS = 5_000.0
