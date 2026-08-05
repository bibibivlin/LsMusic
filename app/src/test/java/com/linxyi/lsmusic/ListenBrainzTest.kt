package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackObservation
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackReport
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackTracker
import com.linxyi.lsmusic.listenbrainz.ListenBrainzHttpException
import com.linxyi.lsmusic.listenbrainz.MusicBrainzMetadataParser
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.listenbrainz.PendingListenJsonCodec
import com.linxyi.lsmusic.listenbrainz.describeListenBrainzValidationFailure
import com.linxyi.lsmusic.listenbrainz.buildListenBrainzTrackPayloadFields
import com.linxyi.lsmusic.listenbrainz.processPendingListens
import com.linxyi.lsmusic.listenbrainz.shouldContinuePendingListenBatchAfter
import com.linxyi.lsmusic.listenbrainz.shouldSubmitListen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ListenBrainzTest {
    private val track = MediaEntry(
        id = "track-1",
        parentId = "0",
        title = "Soft Focus",
        creator = "Noon Atlas",
        album = "Daylight",
        resourceUri = "http://media.example/soft-focus.flac",
        isContainer = false,
    )

    @Test
    fun uploadRule_acceptsMinimumDurationOrPercentage() {
        assertFalse(shouldSubmitListen(149_000L, 300_000L, 240, 50))
        assertTrue(shouldSubmitListen(150_000L, 300_000L, 240, 50))
        assertTrue(shouldSubmitListen(240_000L, 600_000L, 240, 50))
        assertFalse(shouldSubmitListen(239_000L, 600_000L, 240, 50))
    }

    @Test
    fun uploadRule_usesDurationWhenTrackLengthIsUnknown() {
        assertFalse(shouldSubmitListen(239_999L, 0L, 240, 50))
        assertTrue(shouldSubmitListen(240_000L, 0L, 240, 50))
    }

    @Test
    fun metadataParser_readsPicardIdsFromVendorDidlFields() {
        val ids = MusicBrainzMetadataParser.parse(
            """
            <DIDL-Lite xmlns:custom="urn:vendor">
              <item id="1">
                <custom:musicbrainz_recordingid>98255a8c-017a-4bc7-8dd6-1fa36124572b</custom:musicbrainz_recordingid>
                <desc id="musicbrainz_albumid">bf9e91ea-8029-4a04-a26a-224e00a83266</desc>
                <meta name="musicbrainz_releasegroupid">1d98b0bc-5832-49d2-a93e-463032631a2f</meta>
                <meta name="musicbrainz_trackid">02f899af-2fa8-495e-bd1e-5081a5fc170e</meta>
                <meta name="musicbrainz_artistid">db92a151-1ac2-438b-bc43-b82e149ddd50</meta>
              </item>
            </DIDL-Lite>
            """.trimIndent(),
        )

        assertEquals("98255a8c-017a-4bc7-8dd6-1fa36124572b", ids.recordingMbid)
        assertEquals("bf9e91ea-8029-4a04-a26a-224e00a83266", ids.releaseMbid)
        assertEquals("1d98b0bc-5832-49d2-a93e-463032631a2f", ids.releaseGroupMbid)
        assertEquals("02f899af-2fa8-495e-bd1e-5081a5fc170e", ids.trackMbid)
        assertEquals(listOf("db92a151-1ac2-438b-bc43-b82e149ddd50"), ids.artistMbids)
    }

    @Test
    fun metadataParser_doesNotUseReleaseArtistAsTrackArtist() {
        val ids = MusicBrainzMetadataParser.parse(
            """<meta name="musicbrainz_albumartistid">db92a151-1ac2-438b-bc43-b82e149ddd50</meta>""",
        )

        assertTrue(ids.artistMbids.isEmpty())
    }

    @Test
    fun payload_keepsEachMusicBrainzEntityInItsOwnField() {
        val fields = buildListenBrainzTrackPayloadFields(
            track.copy(
                creator = "小暮奈々子（CV愛美）",
                recordingMbid = "dda6aa28-5ff4-4327-80b0-02439b4a16e2",
                releaseMbid = "9e8ff159-b8a2-4fa1-bc33-7d6fbd8c29bd",
                releaseGroupMbid = "d22976c6-5e1e-46b5-96a0-b31152c5b4d5",
                trackMbid = "02f899af-2fa8-495e-bd1e-5081a5fc170e",
                artistMbids = listOf(
                    "cdcdc22a-19a3-44ef-bf44-db8d62983a0c",
                    "3f063b83-8437-44b3-a4a9-9f3267a04979",
                ),
                trackNumber = 1,
            ),
            durationMs = 280_000L,
            listenedMs = 282_000L,
        )

        assertEquals("小暮奈々子（CV愛美）", fields.artistName)
        assertEquals("dda6aa28-5ff4-4327-80b0-02439b4a16e2", fields.additionalInfo["recording_mbid"])
        assertEquals("9e8ff159-b8a2-4fa1-bc33-7d6fbd8c29bd", fields.additionalInfo["release_mbid"])
        assertEquals("d22976c6-5e1e-46b5-96a0-b31152c5b4d5", fields.additionalInfo["release_group_mbid"])
        assertEquals("02f899af-2fa8-495e-bd1e-5081a5fc170e", fields.additionalInfo["track_mbid"])
        assertEquals("1", fields.additionalInfo["tracknumber"])
    }

    @Test
    fun playbackTracker_countsOnlyTimeSpentPlaying() {
        val tracker = ListenBrainzPlaybackTracker()

        val started = tracker.observe(observation(RemotePlaybackState.PLAYING, 0L), 0L, 1_700_000_000L)
        tracker.observe(observation(RemotePlaybackState.PAUSED, 30_000L), 30_000L, 1_700_000_030L)
        tracker.observe(observation(RemotePlaybackState.PLAYING, 30_000L), 60_000L, 1_700_000_060L)
        val stopped = tracker.observe(observation(RemotePlaybackState.STOPPED, 60_000L), 90_000L, 1_700_000_090L)

        assertTrue(started.single() is ListenBrainzPlaybackReport.NowPlaying)
        val finished = stopped.single() as ListenBrainzPlaybackReport.Finished
        assertEquals(60_000L, finished.listenedMs)
        assertEquals(1_700_000_000L, finished.startedAtEpochSeconds)
    }

    @Test
    fun playbackTracker_doesNotRestartWhenRemoteRendererResetsPositionAtEnd() {
        val tracker = ListenBrainzPlaybackTracker()
        tracker.observe(observation(RemotePlaybackState.PLAYING, 0L), 0L, 1_700_000_000L)
        val almostCompleted = tracker.observe(
            observation(RemotePlaybackState.PLAYING, 299_000L),
            299_000L,
            1_700_000_299L,
        )
        val completed = tracker.observe(
            observation(RemotePlaybackState.PLAYING, 0L),
            300_000L,
            1_700_000_300L,
        )
        val resetPosition = tracker.observe(
            observation(RemotePlaybackState.PLAYING, 0L),
            301_000L,
            1_700_000_301L,
        )
        val replayed = tracker.observe(
            observation(RemotePlaybackState.PLAYING, 0L).copy(playbackGeneration = 2L),
            302_000L,
            1_700_000_302L,
        )

        assertTrue(almostCompleted.isEmpty())
        assertTrue(completed.single() is ListenBrainzPlaybackReport.Finished)
        assertTrue(resetPosition.isEmpty())
        assertTrue(replayed.single() is ListenBrainzPlaybackReport.NowPlaying)
    }

    @Test
    fun validationFailure_distinguishesNetworkAndServiceErrors() {
        assertTrue(
            describeListenBrainzValidationFailure(UnknownHostException()).contains("DNS"),
        )
        assertTrue(
            describeListenBrainzValidationFailure(SocketTimeoutException()).contains("超时"),
        )
        assertTrue(
            describeListenBrainzValidationFailure(
                ListenBrainzHttpException(503, "unavailable"),
            ).contains("HTTP 503"),
        )
    }

    @Test
    fun pendingListenJson_roundTripPreservesPlaybackTimeAndReportingMetadata() {
        val pending = PendingListen(
            id = "pending-1",
            track = track.copy(
                trackNumber = 7,
                recordingMbid = "dda6aa28-5ff4-4327-80b0-02439b4a16e2",
                releaseMbid = "9e8ff159-b8a2-4fa1-bc33-7d6fbd8c29bd",
                releaseGroupMbid = "d22976c6-5e1e-46b5-96a0-b31152c5b4d5",
                trackMbid = "02f899af-2fa8-495e-bd1e-5081a5fc170e",
                artistMbids = listOf("cdcdc22a-19a3-44ef-bf44-db8d62983a0c"),
            ),
            startedAtEpochSeconds = 1_700_000_000L,
            durationMs = 300_000L,
            listenedMs = 180_000L,
            queuedAtEpochSeconds = 1_700_000_300L,
            attemptCount = 2,
            lastAttemptAtEpochSeconds = 1_700_000_500L,
            lastError = "offline",
        )

        val restored = PendingListenJsonCodec.decode(PendingListenJsonCodec.encode(listOf(pending))).single()

        assertEquals(pending, restored)
        assertEquals(1_700_000_000L, restored.startedAtEpochSeconds)
    }

    @Test
    fun pendingListenProcessing_removesOnlyAfterSuccessfulUploadAndUsesOriginalPlaybackTime() = runBlocking {
        val pending = PendingListen(
            id = "pending-1",
            track = track,
            startedAtEpochSeconds = 1_700_000_000L,
            durationMs = 300_000L,
            listenedMs = 180_000L,
            queuedAtEpochSeconds = 1_700_000_300L,
        )
        val stored = linkedMapOf(pending.id to pending)
        var submittedStartedAt: Long? = null
        var submittedArtist: String? = null

        val result = processPendingListens(
            pending = stored.values.toList(),
            attemptedAtEpochSeconds = { 1_700_000_600L },
            enrich = { it.copy(creator = "Embedded Artist") },
            onAttempt = { id, attemptedAt ->
                stored[id] = requireNotNull(stored[id]).copy(
                    attemptCount = requireNotNull(stored[id]).attemptCount + 1,
                    lastAttemptAtEpochSeconds = attemptedAt,
                )
            },
            onEnriched = { id, enriched -> stored[id] = requireNotNull(stored[id]).copy(track = enriched) },
            submit = { record, enriched ->
                submittedStartedAt = record.startedAtEpochSeconds
                submittedArtist = enriched.creator
            },
            onUploaded = stored::remove,
            onFailed = { id, message -> stored[id] = requireNotNull(stored[id]).copy(lastError = message) },
        )

        assertEquals(1, result.uploadedCount)
        assertEquals(1_700_000_000L, submittedStartedAt)
        assertEquals("Embedded Artist", submittedArtist)
        assertTrue(stored.isEmpty())
    }

    @Test
    fun pendingListenProcessing_keepsFailedRecordForLaterRetry() = runBlocking {
        val pending = PendingListen(
            id = "pending-1",
            track = track,
            startedAtEpochSeconds = 1_700_000_000L,
            durationMs = 300_000L,
            listenedMs = 180_000L,
            queuedAtEpochSeconds = 1_700_000_300L,
        )
        val stored = linkedMapOf(pending.id to pending)

        val result = processPendingListens(
            pending = stored.values.toList(),
            attemptedAtEpochSeconds = { 1_700_000_600L },
            enrich = { it },
            onAttempt = { id, attemptedAt ->
                stored[id] = requireNotNull(stored[id]).copy(
                    attemptCount = requireNotNull(stored[id]).attemptCount + 1,
                    lastAttemptAtEpochSeconds = attemptedAt,
                    lastError = null,
                )
            },
            onEnriched = { _, _ -> },
            submit = { _, _ -> throw UnknownHostException("offline") },
            onUploaded = stored::remove,
            onFailed = { id, message -> stored[id] = requireNotNull(stored[id]).copy(lastError = message) },
        )

        assertEquals(0, result.uploadedCount)
        assertEquals(1, stored.size)
        assertEquals(1, stored.getValue(pending.id).attemptCount)
        assertEquals("offline", stored.getValue(pending.id).lastError)
        assertNull(stored.getValue(pending.id).track.recordingMbid)
    }

    @Test
    fun pendingListenProcessing_continuesPastRecordSpecificBadRequest() = runBlocking {
        val first = pendingListen("pending-1")
        val second = pendingListen("pending-2")
        val stored = linkedMapOf(first.id to first, second.id to second)

        val result = processPendingListens(
            pending = stored.values.toList(),
            attemptedAtEpochSeconds = { 1_700_000_600L },
            enrich = { it },
            onAttempt = { _, _ -> },
            onEnriched = { _, _ -> },
            submit = { record, _ ->
                if (record.id == first.id) throw ListenBrainzHttpException(400, "bad payload")
            },
            onUploaded = stored::remove,
            onFailed = { id, message -> stored[id] = requireNotNull(stored[id]).copy(lastError = message) },
            continueAfterFailure = ::shouldContinuePendingListenBatchAfter,
        )

        assertEquals(2, result.attemptedCount)
        assertEquals(1, result.uploadedCount)
        assertEquals(setOf(first.id), stored.keys)
        assertEquals("ListenBrainz HTTP 400", stored.getValue(first.id).lastError)
    }

    @Test
    fun pendingListenProcessing_stopsBatchAfterConnectivityFailure() = runBlocking {
        val first = pendingListen("pending-1")
        val second = pendingListen("pending-2")
        val attempted = mutableListOf<String>()

        val result = processPendingListens(
            pending = listOf(first, second),
            attemptedAtEpochSeconds = { 1_700_000_600L },
            enrich = { it },
            onAttempt = { id, _ -> attempted += id },
            onEnriched = { _, _ -> },
            submit = { _, _ -> throw UnknownHostException("offline") },
            onUploaded = {},
            onFailed = { _, _ -> },
            continueAfterFailure = ::shouldContinuePendingListenBatchAfter,
        )

        assertEquals(listOf(first.id), attempted)
        assertEquals(1, result.attemptedCount)
        assertEquals(0, result.uploadedCount)
    }

    @Test
    fun pendingListenBatchPolicy_onlyContinuesForRecordSpecificClientErrors() {
        assertTrue(shouldContinuePendingListenBatchAfter(ListenBrainzHttpException(400, "bad payload")))
        assertTrue(shouldContinuePendingListenBatchAfter(ListenBrainzHttpException(422, "invalid metadata")))
        assertFalse(shouldContinuePendingListenBatchAfter(ListenBrainzHttpException(401, "invalid token")))
        assertFalse(shouldContinuePendingListenBatchAfter(ListenBrainzHttpException(503, "unavailable")))
        assertFalse(shouldContinuePendingListenBatchAfter(UnknownHostException("offline")))
    }

    private fun pendingListen(id: String) = PendingListen(
        id = id,
        track = track.copy(id = "track-$id"),
        startedAtEpochSeconds = 1_700_000_000L,
        durationMs = 300_000L,
        listenedMs = 180_000L,
        queuedAtEpochSeconds = 1_700_000_300L,
    )

    private fun observation(state: RemotePlaybackState, positionMs: Long) = ListenBrainzPlaybackObservation(
        track = track,
        playbackGeneration = 1L,
        playbackState = state,
        positionMs = positionMs,
        durationMs = 300_000L,
        reportingEnabled = true,
    )
}
