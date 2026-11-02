package com.linxyi.lsmusic

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import com.linxyi.lsmusic.ui.COVER_OPEN_DURATION_MILLIS
import com.linxyi.lsmusic.ui.coverFlightSpring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoverFlightSpringTest {
    private val spec = coverFlightSpring(COVER_OPEN_DURATION_MILLIS, .00001f)
        .vectorize(Float.VectorConverter)
    private val start = AnimationVector1D(0f)
    private val end = AnimationVector1D(1f)
    private val resting = AnimationVector1D(0f)

    @Test
    fun flightHasOneSmallNaturalOvershootAndSettles() {
        val samples = (0..1000).map { ms ->
            spec.getValueFromNanos(ms * 1_000_000L, start, end, resting).value
        }
        val peakIndex = samples.indices.maxBy { samples[it] }
        val crossing = samples.indexOfFirst { it > 1f }
        assertEquals(0f, samples.first(), .00001f)
        assertTrue("The cover should ease out distinctly", samples[160] > .55f && samples[272] > .85f)
        assertTrue("The settling overshoot should be small", samples[peakIndex] in 1.002f..1.008f)
        assertTrue("The spring should settle after crossing the target", peakIndex in 400..600)
        assertTrue("The cover must still be moving as it crosses its destination", crossing in 1 until peakIndex)
        val crossingVelocity = spec.getVelocityFromNanos(crossing * 1_000_000L, start, end, resting).value
        assertTrue("Overshoot must continue the flight, not restart from rest", crossingVelocity > .05f)
        assertTrue("The cover should land close to its target", abs(samples[800] - 1f) < .001f)
    }

    @Test
    fun retargetingPreservesCurrentVelocity() {
        val time = 120_000_000L
        val position = spec.getValueFromNanos(time, start, end, resting).value
        val velocity = spec.getVelocityFromNanos(time, start, end, resting).value
        val reversedVelocity = spec.getVelocityFromNanos(
            0L, AnimationVector1D(position), start, AnimationVector1D(velocity),
        ).value
        assertTrue("The cover should be moving when interrupted", velocity > 0f)
        assertEquals("Retargeting should not reset its velocity", velocity, reversedVelocity, .0001f)
    }
}
