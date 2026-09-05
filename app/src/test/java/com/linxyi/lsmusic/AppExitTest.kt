package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackObservation
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackTracker
import com.linxyi.lsmusic.listenbrainz.shouldSubmitListen
import com.linxyi.lsmusic.ui.AppDestination
import com.linxyi.lsmusic.ui.AppExitCoordinator
import com.linxyi.lsmusic.ui.ExitStatus
import com.linxyi.lsmusic.ui.LsMusicUiState
import com.linxyi.lsmusic.ui.remoteRendererToStopOnExit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AppExitTest {
    private val track = MediaEntry(id = "track", parentId = "0", title = "Fixture", isContainer = false)

    @Test
    fun exitStopsOnlyTheRemoteRendererWithAnActiveApplicationTrack() {
        val selected = LsMusicUiState(selectedRendererId = "renderer")
        assertNull(remoteRendererToStopOnExit(selected))
        val playing = selected.copy(queue = listOf(track), currentQueueIndex = 0, playbackState = RemotePlaybackState.PLAYING)
        assertEquals("renderer", remoteRendererToStopOnExit(playing))
        assertEquals("renderer", remoteRendererToStopOnExit(playing.copy(playbackState = RemotePlaybackState.PAUSED)))
        assertNull(remoteRendererToStopOnExit(playing.copy(playbackState = RemotePlaybackState.STOPPED)))
        assertNull(remoteRendererToStopOnExit(playing.copy(selectedRendererId = "local-renderer")))
    }

    @Test
    fun finish_countsFinalPlayingIntervalOnceAndPreservesStartTime() {
        val tracker = ListenBrainzPlaybackTracker()
        tracker.observe(observation(RemotePlaybackState.PLAYING), 10_000L, 1_700_000_000L)
        val report = requireNotNull(tracker.finish(160_000L, true))
        assertEquals(150_000L, report.listenedMs)
        assertEquals(1_700_000_000L, report.startedAtEpochSeconds)
        assertTrue(shouldSubmitListen(report.listenedMs, report.durationMs, 240, 50))
        assertNull(tracker.finish(200_000L, true))
        assertTrue(tracker.observe(observation(RemotePlaybackState.PLAYING), 200_000L, 1_700_000_190L).isEmpty())
    }

    @Test
    fun finish_excludesPausedTimeAndDoesNotInventUnstartedPlayback() {
        val tracker = ListenBrainzPlaybackTracker()
        assertNull(tracker.finish(0L, true))
        tracker.observe(observation(RemotePlaybackState.PLAYING), 0L, 1_700_000_000L)
        tracker.observe(observation(RemotePlaybackState.PAUSED), 29_000L, 1_700_000_029L)
        val report = requireNotNull(tracker.finish(500_000L, true))
        assertEquals(29_000L, report.listenedMs)
        assertFalse(shouldSubmitListen(report.listenedMs, report.durationMs, 30, 50))
    }

    @Test
    fun finish_afterNaturalCompletionOrWithReportingDisabledProducesNoListen() {
        val tracker = ListenBrainzPlaybackTracker()
        tracker.observe(observation(RemotePlaybackState.PLAYING), 0L, 1_700_000_000L)
        tracker.observe(observation(RemotePlaybackState.PLAYING).copy(positionMs = 300_000L), 300_000L, 1_700_000_300L)
        assertNull(tracker.finish(301_000L, true))
        tracker.observe(observation(RemotePlaybackState.PLAYING).copy(playbackGeneration = 2L), 400_000L, 1_700_000_400L)
        assertNull(tracker.finish(600_000L, false))
        assertNull(tracker.finish(601_000L, true))
    }

    @Test
    fun exit_stopsAndPersistsBeforeReportingAndIgnoresRepeatedClicks() = runBlocking {
        val events = mutableListOf<String>()
        val saveGate = CompletableDeferred<Unit>()
        val coordinator = AppExitCoordinator(
            scope = this,
            begin = { events += "begin" },
            stopPlayback = { events += "stop"; true },
            persist = { events += "save"; saveGate.await() },
            finishReporting = { persisted -> assertTrue(persisted); events += "report" },
            onProgress = {},
        )
        coordinator.exit()
        coordinator.exit()
        assertEquals(ExitStatus.STOPPING, coordinator.progress.status)
        yield()
        yield()
        assertEquals(1, events.count { it == "begin" })
        assertTrue("stop" in events)
        assertFalse("report" in events)
        saveGate.complete(Unit)
        awaitStatus(coordinator, ExitStatus.COMPLETE)
        assertTrue(events.indexOf("save") < events.indexOf("report"))
        coordinator.exit()
        assertEquals(1, events.count { it == "save" })
    }

    @Test
    fun exit_failedWriteStillStopsButRetryDoesNotFinalizePlaybackTwice() = runBlocking {
        var begins = 0
        var stops = 0
        var writes = 0
        val reporting = mutableListOf<Boolean>()
        val coordinator = AppExitCoordinator(
            scope = this,
            begin = { begins++ },
            stopPlayback = { stops++; true },
            persist = { if (++writes == 1) throw IOException("fixture write failure") },
            finishReporting = { reporting += it },
            onProgress = {},
        )
        coordinator.exit()
        awaitStatus(coordinator, ExitStatus.SAVE_FAILED)
        assertNotNull(coordinator.progress.error)
        coordinator.exit()
        awaitStatus(coordinator, ExitStatus.COMPLETE)
        assertEquals(1, begins)
        assertEquals(1, stops)
        assertEquals(listOf(false, true), reporting)
    }

    @Test
    fun exit_cancelsHungReportingAndRemoteStopAtDeadline() = runBlocking {
        var reportCancelled = false
        var stopCancelled = false
        val coordinator = AppExitCoordinator(
            scope = this,
            begin = {},
            stopPlayback = { try { awaitCancellation() } finally { stopCancelled = true } },
            persist = {},
            finishReporting = { try { awaitCancellation() } finally { reportCancelled = true } },
            onProgress = {},
            timeoutMs = 50L,
        )
        coordinator.exit()
        awaitStatus(coordinator, ExitStatus.COMPLETE)
        assertTrue(reportCancelled)
        assertTrue(stopCancelled)
        assertEquals("未能确认远程设备已停止", coordinator.progress.warning)
    }

    @Test
    fun settingsNavigation_returnsOneLevelAndKeepsSettingsSelected() {
        listOf(AppDestination.SETTINGS_APPEARANCE, AppDestination.SETTINGS_LYRICS,
            AppDestination.SETTINGS_NETWORK, AppDestination.SETTINGS_ABOUT).forEach {
            assertEquals(AppDestination.SETTINGS, it.settingsParent)
            assertEquals(AppDestination.SETTINGS, it.navigationDestination)
        }
        assertEquals(AppDestination.SETTINGS_NETWORK, AppDestination.PENDING_LISTENS.settingsParent)
        assertEquals(AppDestination.SETTINGS, AppDestination.PENDING_LISTENS.navigationDestination)
        assertNull(AppDestination.SETTINGS.settingsParent)
        assertEquals(AppDestination.LIBRARY, AppDestination.LIBRARY.navigationDestination)
    }

    private fun observation(state: RemotePlaybackState) = ListenBrainzPlaybackObservation(
        track, 1L, state, 0L, 300_000L, true,
    )

    private suspend fun awaitStatus(coordinator: AppExitCoordinator, status: ExitStatus) {
        withTimeout(2_000L) { while (coordinator.progress.status != status) delay(1L) }
    }
}
