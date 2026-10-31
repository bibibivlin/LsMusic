package com.linxyi.lsmusic

import com.linxyi.lsmusic.backup.AppBackupData
import com.linxyi.lsmusic.backup.BackupSession
import com.linxyi.lsmusic.backup.finishPlaybackForImport
import com.linxyi.lsmusic.backup.persistAndIncludeCurrentListens
import com.linxyi.lsmusic.backup.tokenChangeNeedsPlaybackStopped
import com.linxyi.lsmusic.backup.withPreservedListens
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackObservation
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackTracker
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.listenbrainz.PendingListenRepository
import com.linxyi.lsmusic.ui.AppPreferences
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.io.IOException
import kotlinx.coroutines.runBlocking

class BackupPlaybackReportingTest {
    private val track = MediaEntry(id = "track", parentId = "album", title = "Track", isContainer = false)
    private val token = UUID.randomUUID().toString()
    private val preferences = AppPreferences(
        listenBrainzEnabled = true,
        listenBrainzToken = token,
        listenBrainzMinimumSeconds = 60,
        listenBrainzMinimumPercent = 50,
    )

    private fun ListenBrainzPlaybackTracker.observe(state: RemotePlaybackState, atMs: Long) = observe(
        observation = ListenBrainzPlaybackObservation(
            track = track,
            playbackGeneration = 1,
            playbackState = state,
            positionMs = atMs,
            durationMs = 300_000,
            reportingEnabled = true,
        ),
        elapsedRealtimeMs = atMs,
        epochSeconds = 1_700_000_000L,
    )

    @Test fun importFinalizesAnEligibleSessionUsingOldRulesAndDoesNotCountPausedTime() {
        val tracker = ListenBrainzPlaybackTracker()
        tracker.observe(RemotePlaybackState.PLAYING, 0)
        tracker.observe(RemotePlaybackState.PAUSED, 60_000)
        val record = finishPlaybackForImport(tracker, preferences, 150_000, 1_700_000_150L)
        assertNotNull(record)
        assertEquals(60_000L, record!!.listenedMs)
        assertEquals(1_700_000_000L, record.startedAtEpochSeconds)
        assertEquals(1_700_000_150L, record.queuedAtEpochSeconds)
        assertFalse(tracker.hasActiveSession)
        assertNull(finishPlaybackForImport(tracker, preferences, 160_000, 1_700_000_160L))
    }

    @Test fun importDoesNotCreateAListenBelowThresholdOrWhenReportingWasDisabled() {
        val short = ListenBrainzPlaybackTracker()
        short.observe(RemotePlaybackState.PLAYING, 0)
        assertNull(finishPlaybackForImport(short, preferences, 59_000, 1_700_000_059L))
        val disabled = ListenBrainzPlaybackTracker()
        disabled.observe(RemotePlaybackState.PLAYING, 0)
        assertNull(finishPlaybackForImport(disabled, preferences.copy(listenBrainzEnabled = false), 300_000, 1_700_000_300L))
    }

    @Test fun importUsesTheOriginalPercentageThresholdWhenDurationThresholdIsNotMet() {
        val tracker = ListenBrainzPlaybackTracker()
        tracker.observe(RemotePlaybackState.PLAYING, 0)
        val rules = preferences.copy(listenBrainzMinimumSeconds = 240, listenBrainzMinimumPercent = 30)
        val record = finishPlaybackForImport(tracker, rules, 90_000, 1_700_000_090L)
        assertNotNull(record)
        assertEquals(90_000L, record!!.listenedMs)
    }

    @Test fun importingADifferentTokenWaitsForActiveOrUnpersistedOldAccountRecords() {
        val otherToken = UUID.randomUUID().toString()
        fun mustWait(candidate: String?, reporting: Boolean, active: Boolean, unsaved: Boolean) =
            tokenChangeNeedsPlaybackStopped(token, candidate, reporting, active, unsaved)
        assertTrue(mustWait(otherToken, reporting = true, active = true, unsaved = false))
        assertTrue(mustWait(otherToken, reporting = false, active = false, unsaved = true))
        assertFalse(mustWait(null, reporting = true, active = true, unsaved = true))
        assertFalse(mustWait(token, reporting = true, active = true, unsaved = true))
        assertFalse(mustWait(otherToken, reporting = true, active = false, unsaved = false))
        assertFalse(mustWait(otherToken, reporting = false, active = true, unsaved = false))
    }

    @Test fun importPreservesRecentlyFinishedRecordsWithoutDuplicatingTheirIds() {
        val imported = PendingListen("imported", track, 1_700_000_000, 300_000, 150_000, 1_700_000_150)
        val preserved = imported.copy(id = "finished", startedAtEpochSeconds = 1_700_000_300)
        val olderCopy = preserved.copy(listenedMs = 0)
        val backup = AppBackupData(emptyMap(), null, BackupSession(), listOf(imported, olderCopy))
        val restored = backup.withPreservedListens(listOf(preserved, preserved))
        assertEquals(listOf(imported, preserved), restored.pendingListens)
        assertEquals(1_700_000_300L, restored.pendingListens.last().startedAtEpochSeconds)
    }

    @Test fun retryKeepsUnsavedListensAndDoesNotReintroduceRecordsAfterRemoval() = runBlocking {
        val record = PendingListen("finished", track, 1_700_000_000, 300_000, 150_000, 1_700_000_150)
        val backup = AppBackupData(emptyMap(), null, BackupSession(), emptyList())
        val unsaved = linkedMapOf(record.id to record)
        val pendingIds = linkedSetOf<String>()
        var fail = true
        val repository = PendingListenRepository(emptyList()) {
            if (fail) throw IOException("simulated storage failure")
        }
        assertTrue(runCatching { backup.persistAndIncludeCurrentListens(repository, unsaved, pendingIds) }.isFailure)
        assertEquals(record, unsaved[record.id])
        assertTrue(repository.records.value.isEmpty())
        fail = false
        val firstAttempt = backup.persistAndIncludeCurrentListens(repository, unsaved, pendingIds)
        assertEquals(listOf(record), firstAttempt.pendingListens)
        assertTrue(unsaved.isEmpty())
        repository.remove(record.id) // As after an upload succeeds or the user deletes the record.
        val nextAttempt = backup.persistAndIncludeCurrentListens(repository, unsaved, pendingIds)
        assertTrue(nextAttempt.pendingListens.isEmpty())
    }
}
