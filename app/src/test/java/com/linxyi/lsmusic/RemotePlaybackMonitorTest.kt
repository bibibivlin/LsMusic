package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.RemotePlaybackMonitor
import com.linxyi.lsmusic.ui.RemotePlaybackObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePlaybackMonitorTest {
    @Test
    fun playingAfterAnOutOfOrderStopKeepsTheCurrentTrack() {
        val monitor = playingAt(98_000)

        assertEquals(RemotePlaybackObservation.NONE, monitor.stopped(2_000))
        assertEquals(RemotePlaybackObservation.PLAYING, monitor.sample("PLAYING", 99_000, 3_000))
        assertEquals(RemotePlaybackObservation.NONE, monitor.stopped(4_000))
        assertEquals(RemotePlaybackObservation.ENDED, monitor.stopped(5_000))
    }

    @Test
    fun confirmedEndAfterHotspotRecoveryAdvancesEvenIfRendererClearedItsPosition() {
        val monitor = playingAt(94_000)
        monitor.connectionInterrupted()

        assertEquals(RemotePlaybackObservation.NONE, monitor.stopped(7_000))
        assertEquals(RemotePlaybackObservation.ENDED, monitor.stopped(8_000))
    }

    @Test
    fun interruptedPlaybackFarFromTheEndDoesNotAdvance() {
        val monitor = playingAt(20_000)
        monitor.connectionInterrupted()

        assertEquals(RemotePlaybackObservation.NONE, monitor.stopped(85_000))
        assertEquals(RemotePlaybackObservation.NONE, monitor.stopped(86_000))
        assertEquals(RemotePlaybackObservation.STOPPED, monitor.stopped(87_000))
    }

    @Test
    fun rendererReportingStoppedWhileItsPositionIncreasesDoesNotAdvance() {
        val monitor = playingAt(98_000)

        assertEquals(RemotePlaybackObservation.NONE, monitor.sample("STOPPED", 99_000, 2_000))
        assertEquals(RemotePlaybackObservation.NONE, monitor.sample("STOPPED", 100_000, 3_000))
        assertEquals(RemotePlaybackObservation.NONE, monitor.sample("STOPPED", 100_000, 4_000))
        assertEquals(RemotePlaybackObservation.ENDED, monitor.sample("STOPPED", 100_000, 5_000))
    }

    @Test
    fun startupAndSeekRequireObservedPlaybackProgressBeforeAdvancing() {
        val starting = RemotePlaybackMonitor()
        assertEquals(RemotePlaybackObservation.NONE, starting.stopped(2_000))
        assertEquals(RemotePlaybackObservation.NONE, starting.stopped(3_000))
        assertEquals(RemotePlaybackObservation.STOPPED, starting.stopped(4_000))

        val seeking = playingAt(98_000)
        seeking.seek()
        assertEquals(RemotePlaybackObservation.NONE, seeking.stopped(2_000))
        assertEquals(RemotePlaybackObservation.NONE, seeking.stopped(3_000))
    }

    @Test
    fun reportsForDifferentTracksDoNotAdvanceTheQueue() {
        val monitor = playingAt(98_000)

        repeat(2) {
            assertEquals(
                RemotePlaybackObservation.NONE,
                monitor.sample("STOPPED", 100_000, 2_000L + it * 1_000, "http://player/other-track"),
            )
        }
    }

    @Test
    fun previousTrackResponsesDoNotConfirmPlaybackAfterAChange() {
        val previousUri = "http://player/previous-track"
        val monitor = RemotePlaybackMonitor("http://player/current-track", previousUri)

        assertEquals(RemotePlaybackObservation.NONE, monitor.sample("PLAYING", 98_000, 0, previousUri))
        assertEquals(RemotePlaybackObservation.NONE, monitor.sample("PLAYING", 99_000, 1_000, previousUri))
        assertEquals(RemotePlaybackObservation.NONE, monitor.stopped(2_000))
        assertEquals(RemotePlaybackObservation.NONE, monitor.stopped(3_000))
    }

    private fun playingAt(position: Long) = RemotePlaybackMonitor().apply {
        sample("PLAYING", position - 1_000, 0)
        sample("PLAYING", position, 1_000)
    }

    private fun RemotePlaybackMonitor.stopped(at: Long) = sample("NO_MEDIA_PRESENT", 0, at, uri = null)

    private fun RemotePlaybackMonitor.sample(
        state: String,
        position: Long,
        at: Long,
        uri: String? = "http://player/current-track",
    ) = observe(state, position, 100_000, uri, at, playRequested = true)
}
