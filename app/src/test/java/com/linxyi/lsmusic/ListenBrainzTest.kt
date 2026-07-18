package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackObservation
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackReport
import com.linxyi.lsmusic.listenbrainz.ListenBrainzPlaybackTracker
import com.linxyi.lsmusic.listenbrainz.ListenBrainzHttpException
import com.linxyi.lsmusic.listenbrainz.MusicBrainzMetadataParser
import com.linxyi.lsmusic.listenbrainz.describeListenBrainzValidationFailure
import com.linxyi.lsmusic.listenbrainz.shouldSubmitListen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                <meta name="musicbrainz_artistid">db92a151-1ac2-438b-bc43-b82e149ddd50</meta>
              </item>
            </DIDL-Lite>
            """.trimIndent(),
        )

        assertEquals("98255a8c-017a-4bc7-8dd6-1fa36124572b", ids.recordingMbid)
        assertEquals("bf9e91ea-8029-4a04-a26a-224e00a83266", ids.releaseMbid)
        assertEquals(listOf("db92a151-1ac2-438b-bc43-b82e149ddd50"), ids.artistMbids)
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

    private fun observation(state: RemotePlaybackState, positionMs: Long) = ListenBrainzPlaybackObservation(
        track = track,
        playbackGeneration = 1L,
        playbackState = state,
        positionMs = positionMs,
        durationMs = 300_000L,
        reportingEnabled = true,
    )
}
