package com.linxyi.lsmusic

import com.linxyi.lsmusic.listenbrainz.EmbeddedAudioMetadataParser
import com.linxyi.lsmusic.listenbrainz.RandomAccessAudioSource
import com.linxyi.lsmusic.listenbrainz.calculateRangeRequestLength
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class EmbeddedAudioMetadataTest {
    @Test
    fun parser_readsMusicBrainzIdsAndCreditsFromMp3Id3() {
        val metadata = EmbeddedAudioMetadataParser.parse(bytes(id3Tag()))

        assertExampleMetadata(metadata)
    }

    @Test
    fun parser_readsMusicBrainzIdsAndCreditsFromFlacVorbisComments() {
        val comments = listOf(
            "TITLE=God knows…",
            "ARTIST=小暮奈々子（CV愛美）",
            "ALBUM=God knows…",
            "TRACKNUMBER=1",
            "MUSICBRAINZ_TRACKID=$RECORDING_MBID",
            "MUSICBRAINZ_ALBUMID=$RELEASE_MBID",
            "MUSICBRAINZ_RELEASEGROUPID=$RELEASE_GROUP_MBID",
            "MUSICBRAINZ_RELEASETRACKID=$TRACK_MBID",
            "MUSICBRAINZ_ARTISTID=$ARTIST_MBID_1; $ARTIST_MBID_2",
        )
        val block = ByteArrayOutputStream().apply {
            writeLe32(0)
            writeLe32(comments.size)
            comments.forEach { comment ->
                val encoded = comment.toByteArray(StandardCharsets.UTF_8)
                writeLe32(encoded.size)
                write(encoded)
            }
        }.toByteArray()
        val flac = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(StandardCharsets.US_ASCII))
            write(0x84)
            write((block.size ushr 16) and 0xff)
            write((block.size ushr 8) and 0xff)
            write(block.size and 0xff)
            write(block)
        }.toByteArray()

        val metadata = EmbeddedAudioMetadataParser.parse(bytes(flac))

        assertExampleMetadata(metadata)
    }

    @Test
    fun parser_jumpsToId3MetadataAtEndOfDsf() {
        val tag = id3Tag()
        val dsf = ByteArrayOutputStream().apply {
            write("DSD ".toByteArray(StandardCharsets.US_ASCII))
            writeLe64(28L)
            writeLe64(28L + tag.size)
            writeLe64(28L)
            write(tag)
        }.toByteArray()

        val metadata = EmbeddedAudioMetadataParser.parse(bytes(dsf))

        assertExampleMetadata(metadata)
    }

    @Test
    fun rangePrefetch_stopsAtDsfEndWhenId3IsSmallerThanWindow() {
        val metadataOffset = 1_000_000L
        val fileSize = metadataOffset + 2_048L

        assertEquals(
            2_048,
            calculateRangeRequestLength(metadataOffset, requestedLength = 10, knownLength = fileSize),
        )
    }

    @Test
    fun parser_skipsDffChunksAndReadsId3Chunk() {
        val tag = id3Tag()
        val paddedTagSize = tag.size + (tag.size and 1)
        val formSize = 4L + 12L + paddedTagSize
        val dff = ByteArrayOutputStream().apply {
            write("FRM8".toByteArray(StandardCharsets.US_ASCII))
            writeBe64(formSize)
            write("DSD ".toByteArray(StandardCharsets.US_ASCII))
            write("ID3 ".toByteArray(StandardCharsets.US_ASCII))
            writeBe64(tag.size.toLong())
            write(tag)
            if (tag.size and 1 != 0) write(0)
        }.toByteArray()

        val metadata = EmbeddedAudioMetadataParser.parse(bytes(dff))

        assertExampleMetadata(metadata)
    }

    private fun assertExampleMetadata(metadata: com.linxyi.lsmusic.listenbrainz.EmbeddedAudioMetadata) {
        assertEquals("God knows…", metadata.title)
        assertEquals("小暮奈々子（CV愛美）", metadata.artist)
        assertEquals("God knows…", metadata.album)
        assertEquals(1, metadata.trackNumber)
        assertEquals(RECORDING_MBID, metadata.musicBrainzIds.recordingMbid)
        assertEquals(RELEASE_MBID, metadata.musicBrainzIds.releaseMbid)
        assertEquals(RELEASE_GROUP_MBID, metadata.musicBrainzIds.releaseGroupMbid)
        assertEquals(TRACK_MBID, metadata.musicBrainzIds.trackMbid)
        assertEquals(
            listOf(ARTIST_MBID_1, ARTIST_MBID_2),
            metadata.musicBrainzIds.artistMbids,
        )
    }

    private fun id3Tag(): ByteArray {
        val frames = ByteArrayOutputStream().apply {
            write(id3TextFrame("TIT2", "God knows…"))
            write(id3TextFrame("TPE1", "小暮奈々子（CV愛美）"))
            write(id3TextFrame("TALB", "God knows…"))
            write(id3TextFrame("TRCK", "1"))
            write(id3UserTextFrame("MusicBrainz Album Id", RELEASE_MBID))
            write(id3UserTextFrame("MusicBrainz Release Group Id", RELEASE_GROUP_MBID))
            write(id3UserTextFrame("MusicBrainz Release Track Id", TRACK_MBID))
            write(id3UserTextFrame("MusicBrainz Artist Id", "$ARTIST_MBID_1; $ARTIST_MBID_2"))
            write(id3UniqueFileId(RECORDING_MBID))
        }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.US_ASCII))
            write(byteArrayOf(3, 0, 0))
            writeSyncSafe(frames.size)
            write(frames)
        }.toByteArray()
    }

    private fun id3TextFrame(id: String, value: String): ByteArray = id3Frame(
        id,
        byteArrayOf(3) + value.toByteArray(StandardCharsets.UTF_8),
    )

    private fun id3UserTextFrame(description: String, value: String): ByteArray = id3Frame(
        "TXXX",
        byteArrayOf(3) + description.toByteArray(StandardCharsets.UTF_8) + byteArrayOf(0) +
            value.toByteArray(StandardCharsets.UTF_8),
    )

    private fun id3UniqueFileId(value: String): ByteArray = id3Frame(
        "UFID",
        "http://musicbrainz.org".toByteArray(StandardCharsets.ISO_8859_1) + byteArrayOf(0) +
            value.toByteArray(StandardCharsets.ISO_8859_1),
    )

    private fun id3Frame(id: String, payload: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(id.toByteArray(StandardCharsets.US_ASCII))
        writeBe32(payload.size)
        write(byteArrayOf(0, 0))
        write(payload)
    }.toByteArray()

    private fun bytes(data: ByteArray) = RandomAccessAudioSource { position, length ->
        if (position >= data.size) {
            ByteArray(0)
        } else {
            data.copyOfRange(position.toInt(), minOf(data.size, position.toInt() + length))
        }
    }

    private fun ByteArrayOutputStream.writeSyncSafe(value: Int) {
        write((value ushr 21) and 0x7f)
        write((value ushr 14) and 0x7f)
        write((value ushr 7) and 0x7f)
        write(value and 0x7f)
    }

    private fun ByteArrayOutputStream.writeBe32(value: Int) {
        write((value ushr 24) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        repeat(4) { index -> write((value ushr (index * 8)) and 0xff) }
    }

    private fun ByteArrayOutputStream.writeLe64(value: Long) {
        repeat(8) { index -> write((value ushr (index * 8)).toInt() and 0xff) }
    }

    private fun ByteArrayOutputStream.writeBe64(value: Long) {
        repeat(8) { index -> write((value ushr ((7 - index) * 8)).toInt() and 0xff) }
    }

    private companion object {
        const val RECORDING_MBID = "dda6aa28-5ff4-4327-80b0-02439b4a16e2"
        const val RELEASE_MBID = "9e8ff159-b8a2-4fa1-bc33-7d6fbd8c29bd"
        const val RELEASE_GROUP_MBID = "d22976c6-5e1e-46b5-96a0-b31152c5b4d5"
        const val TRACK_MBID = "02f899af-2fa8-495e-bd1e-5081a5fc170e"
        const val ARTIST_MBID_1 = "cdcdc22a-19a3-44ef-bf44-db8d62983a0c"
        const val ARTIST_MBID_2 = "3f063b83-8437-44b3-a4a9-9f3267a04979"
    }
}
