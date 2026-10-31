package com.linxyi.lsmusic

import com.linxyi.lsmusic.backup.AppBackupCodec
import com.linxyi.lsmusic.backup.AppBackupData
import com.linxyi.lsmusic.backup.BackupSession
import com.linxyi.lsmusic.backup.BackupTokenValidation
import com.linxyi.lsmusic.backup.validateBackupToken
import com.linxyi.lsmusic.backup.UnsupportedBackupVersion
import com.linxyi.lsmusic.dlna.ArtworkCandidate
import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.listenbrainz.PendingListenJsonCodec
import com.linxyi.lsmusic.listenbrainz.ListenBrainzHttpException
import com.linxyi.lsmusic.listenbrainz.PendingListenRepository
import com.linxyi.lsmusic.ui.PlaybackOrder
import com.linxyi.lsmusic.ui.QueueItem
import com.linxyi.lsmusic.ui.RepeatMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.net.UnknownHostException

class AppBackupTest {
    private val track = MediaEntry(
        id = "track", parentId = "album", title = "测试 🎵", creator = "Artist", album = "Album",
        albumArtist = "Album artist", year = 2024, genre = "Classical", trackNumber = 7,
        artworkUri = "http://media.example/cover.jpg", artworkCandidates = listOf(ArtworkCandidate("http://media.example/large.jpg", "JPEG_LRG")),
        resourceUri = "http://media.example/audio.flac", resourceSize = 123456789L, duration = "0:04:00",
        mimeType = "audio/flac", protocolInfo = "http-get:*:audio/flac:*", didlMetadata = "<DIDL-Lite>original metadata</DIDL-Lite>",
        recordingMbid = "recording", releaseMbid = "release", releaseGroupMbid = "group", trackMbid = "release-track",
        artistMbids = listOf("artist"), isContainer = false, childCount = 0,
    )
    private val pending = PendingListen("listen", track, 1700000000L, 240000L, 180000L, 1700000180L, 2, 1700000200L, "offline")
    private val preferences = mapOf(
        "theme_mode" to "DARK", "listenbrainz_enabled" to true, "listenbrainz_minimum_seconds" to 150,
        "listenbrainz_minimum_percent" to 75, "album_sort_by_server_uuid:server" to "TITLE_ASCENDING",
        "future_option" to setOf("a", "b"), "future_long" to 9007199254740993L, "future_float" to 1.25f,
    )

    private fun data(secrets: Map<String, Any>? = null) = AppBackupData(
        preferences, secrets,
        BackupSession(listOf(QueueItem("first", track), QueueItem("duplicate", track)), 1, PlaybackOrder(RepeatMode.ALL, true, setOf("duplicate"))),
        listOf(pending),
    )

    @Test fun roundTrip_preservesEveryPreferenceTypeFullMetadataDuplicateTracksAndOriginalListenTimes() {
        val original = data()
        val output = ByteArrayOutputStream()
        AppBackupCodec.write(original, output)
        val restored = AppBackupCodec.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(original.preferences, restored.preferences)
        assertEquals(original.session, restored.session)
        assertEquals(original.pendingListens, restored.pendingListens)
        assertNull(restored.secrets)
        assertEquals(listOf(pending), PendingListenJsonCodec.decode(PendingListenJsonCodec.encode(listOf(pending))))
    }

    @Test fun withoutSecrets_retainsAllListenBrainzSettingsAndPendingRecords() {
        val restored = AppBackupCodec.decode(AppBackupCodec.encode(data()))
        assertNull(restored.secrets)
        assertEquals(true, restored.preferences["listenbrainz_enabled"])
        assertEquals(150, restored.preferences["listenbrainz_minimum_seconds"])
        assertEquals(75, restored.preferences["listenbrainz_minimum_percent"])
        assertEquals(listOf(pending), restored.pendingListens)
    }

    @Test fun optionalSecrets_roundTripFutureCredentialsWithoutPrintingThem() {
        val generated = UUID.randomUUID().toString()
        val original = data(mapOf("listenbrainz_token" to generated, "future_api_key" to generated.reversed()))
        val restored = AppBackupCodec.decode(AppBackupCodec.encode(original))
        assertTrue(original.secrets == restored.secrets)
        assertFalse(original.toString().contains(generated))
        assertFalse(AppBackupCodec.encode(data()).contains(generated))
    }

    @Test fun decode_rejectsForeignOrFutureBackupsAndMissingSections() {
        val valid = AppBackupCodec.encode(data())
        assertThrows(IllegalArgumentException::class.java) { AppBackupCodec.decode(JSONObject(valid).put("format", "another.app").toString()) }
        assertThrows(UnsupportedBackupVersion::class.java) { AppBackupCodec.decode(JSONObject(valid).put("version", 2).toString()) }
        val missing = JSONObject(valid).apply { remove("preferences") }
        assertThrows(Exception::class.java) { AppBackupCodec.decode(missing.toString()) }
        assertThrows(Exception::class.java) { AppBackupCodec.decode(valid.take(valid.length / 2)) }
        assertThrows(IllegalArgumentException::class.java) { AppBackupCodec.decode("[".repeat(10_000) + "]".repeat(10_000)) }
    }

    @Test fun decode_rejectsInvalidQueueIdentityAndSelection() {
        fun alteredSession(edit: (JSONObject) -> Unit): String = JSONObject(AppBackupCodec.encode(data())).apply {
            edit(getJSONObject("session"))
        }.toString()
        assertThrows(IllegalArgumentException::class.java) {
            AppBackupCodec.decode(alteredSession { it.put("currentQueueIndex", 4) })
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppBackupCodec.decode(alteredSession { it.getJSONArray("queue").getJSONObject(1).put("queueId", "first") })
        }
    }

    @Test fun decode_rejectsWrongTypedPreferencesAndInvalidListenTimes() {
        val root = JSONObject(AppBackupCodec.encode(data()))
        root.getJSONObject("preferences").getJSONObject("future_long").put("value", "not a number")
        assertThrows(Exception::class.java) { AppBackupCodec.decode(root.toString()) }
        val invalid = AppBackupData(preferences, null, BackupSession(), listOf(pending.copy(startedAtEpochSeconds = -1)))
        assertThrows(IllegalArgumentException::class.java) { AppBackupCodec.decode(AppBackupCodec.encode(invalid)) }
    }

    @Test fun read_boundsActualBytesEvenWhenStreamDoesNotDeclareItsLength() {
        var remaining = AppBackupCodec.MAX_BYTES + 1
        val oversized = object : InputStream() {
            override fun read(): Int = if (remaining-- > 0) ' '.code else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return -1
                val count = minOf(remaining, length)
                buffer.fill(' '.code.toByte(), offset, offset + count)
                remaining -= count
                return count
            }
        }
        assertThrows(IllegalArgumentException::class.java) { AppBackupCodec.read(oversized) }
    }

    @Test fun oldPendingListenSchema_remainsReadableAfterLosslessMetadataUpgrade() {
        val root = JSONObject(PendingListenJsonCodec.encode(listOf(pending)))
        root.getJSONArray("records").getJSONObject(0).getJSONObject("track").apply {
            listOf("artworkUri", "artworkCandidates", "albumArtist", "year", "resourceSize", "protocolInfo", "didlMetadata", "isContainer", "isAlbum", "childCount").forEach(::remove)
        }
        val restored = PendingListenJsonCodec.decode(root.toString()).single()
        assertEquals(track.recordingMbid, restored.track.recordingMbid)
        assertEquals(pending.startedAtEpochSeconds, restored.startedAtEpochSeconds)
        assertEquals("", restored.track.albumArtist)
        assertNull(restored.track.didlMetadata)
    }

    @Test fun tokenValidation_skipsExcludedSecretsAndSeparatesInvalidCredentialsFromNetworkFailure() = runBlocking {
        var requests = 0
        assertEquals(BackupTokenValidation.VALID, validateBackupToken(null) { requests++; false })
        assertEquals(0, requests)
        val generated = UUID.randomUUID().toString()
        assertEquals(BackupTokenValidation.INVALID, validateBackupToken(generated) { false })
        assertEquals(BackupTokenValidation.INVALID, validateBackupToken(generated) { throw ListenBrainzHttpException(401, "Unauthorized") })
        assertEquals(BackupTokenValidation.CONNECTION_FAILURE, validateBackupToken(generated) { throw UnknownHostException() })
        assertEquals(BackupTokenValidation.VALID, validateBackupToken(generated) { true })
        var cancelled = false
        try { validateBackupToken(generated) { throw CancellationException() } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun importSuspendsUploadsBeforeRecordsCanBeSubmitted() = runBlocking {
        val repository = PendingListenRepository(listOf(pending)) {}
        repository.suspendUploadsForImport()
        val result = repository.upload(UUID.randomUUID().toString())
        assertEquals(0, result.attemptedCount)
        assertEquals(listOf(pending), repository.records.value)
        assertFalse(repository.isUploading.value)
    }
}
