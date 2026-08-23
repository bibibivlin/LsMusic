package com.linxyi.lsmusic

import com.linxyi.lsmusic.artwork.EmbeddedArtworkParser
import com.linxyi.lsmusic.listenbrainz.RandomAccessAudioSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class EmbeddedArtworkTest {
    @Test
    fun id3v23_prefersFrontCoverOverEarlierPicture() {
        val tag = id3Tag(
            majorVersion = 3,
            frames = listOf(
                id3v23PictureFrame(pictureType = 4, image = byteArrayOf(1, 2)),
                id3v23PictureFrame(pictureType = 3, image = byteArrayOf(3, 4, 5)),
            ),
        )

        val artwork = EmbeddedArtworkParser.parse(bytes(tag))!!

        assertEquals(3, artwork.pictureType)
        assertEquals("image/jpeg", artwork.mimeType)
        assertArrayEquals(byteArrayOf(3, 4, 5), artwork.bytes)
    }

    @Test
    fun id3v22_readsPicFrame() {
        val payload = byteArrayOf(0) + "PNG".toByteArray(StandardCharsets.ISO_8859_1) +
            byteArrayOf(3, 0, 9, 8, 7)
        val frame = ByteArrayOutputStream().apply {
            write("PIC".toByteArray(StandardCharsets.US_ASCII))
            writeBe24(payload.size)
            write(payload)
        }.toByteArray()

        val artwork = EmbeddedArtworkParser.parse(bytes(id3Tag(2, listOf(frame))))!!

        assertEquals("image/png", artwork.mimeType)
        assertArrayEquals(byteArrayOf(9, 8, 7), artwork.bytes)
    }

    @Test
    fun id3v24_removesFrameUnsynchronisation() {
        val rawImage = byteArrayOf(0xff.toByte(), 0xe0.toByte(), 1)
        val storedImage = byteArrayOf(0xff.toByte(), 0, 0xe0.toByte(), 1)
        val payload = byteArrayOf(0) + "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1) +
            byteArrayOf(0, 3, 0) + storedImage
        val frame = ByteArrayOutputStream().apply {
            write("APIC".toByteArray(StandardCharsets.US_ASCII))
            writeSyncSafe(payload.size)
            write(byteArrayOf(0, 2))
            write(payload)
        }.toByteArray()

        val artwork = EmbeddedArtworkParser.parse(bytes(id3Tag(4, listOf(frame))))!!

        assertArrayEquals(rawImage, artwork.bytes)
    }

    @Test
    fun flac_readsPictureBlock() {
        val image = byteArrayOf(11, 12, 13)
        val block = flacPictureBlock(image)
        val flac = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(StandardCharsets.US_ASCII))
            write(0x86)
            writeBe24(block.size)
            write(block)
        }.toByteArray()

        val artwork = EmbeddedArtworkParser.parse(bytes(flac))!!

        assertEquals(3, artwork.pictureType)
        assertArrayEquals(image, artwork.bytes)
    }

    @Test
    fun dsfAndDff_delegateToId3PictureParser() {
        val image = byteArrayOf(21, 22)
        val tag = id3Tag(3, listOf(id3v23PictureFrame(3, image)))
        val dsf = ByteArrayOutputStream().apply {
            write("DSD ".toByteArray(StandardCharsets.US_ASCII))
            writeLe64(28L)
            writeLe64(28L + tag.size)
            writeLe64(28L)
            write(tag)
        }.toByteArray()
        val paddedTagSize = tag.size + (tag.size and 1)
        val dff = ByteArrayOutputStream().apply {
            write("FRM8".toByteArray(StandardCharsets.US_ASCII))
            writeBe64(4L + 12L + paddedTagSize)
            write("DSD ".toByteArray(StandardCharsets.US_ASCII))
            write("ID3 ".toByteArray(StandardCharsets.US_ASCII))
            writeBe64(tag.size.toLong())
            write(tag)
            if (tag.size and 1 != 0) write(0)
        }.toByteArray()

        assertArrayEquals(image, EmbeddedArtworkParser.parse(bytes(dsf))!!.bytes)
        assertArrayEquals(image, EmbeddedArtworkParser.parse(bytes(dff))!!.bytes)
    }

    @Test
    fun parser_rejectsMalformedPicturePayload() {
        val malformed = id3Tag(3, listOf(id3v23PictureFrame(3, ByteArray(0))))

        assertNull(EmbeddedArtworkParser.parse(bytes(malformed)))
    }

    @Test
    fun parser_readsLargePictureAcrossBoundedTwoMiBRanges() {
        val image = ByteArray(2 * 1024 * 1024 + 17) { (it and 0x7f).toByte() }
        val tag = id3Tag(3, listOf(id3v23PictureFrame(3, image)))
        var largestRead = 0
        val source = RandomAccessAudioSource { position, length ->
            largestRead = maxOf(largestRead, length)
            if (position >= tag.size) ByteArray(0)
            else tag.copyOfRange(position.toInt(), minOf(tag.size, position.toInt() + length))
        }

        val artwork = EmbeddedArtworkParser.parse(source)!!

        assertEquals(image.size, artwork.bytes.size)
        assertEquals(2 * 1024 * 1024, largestRead)
    }

    private fun id3v23PictureFrame(pictureType: Int, image: ByteArray): ByteArray {
        val payload = byteArrayOf(0) + "image/jpeg".toByteArray(StandardCharsets.ISO_8859_1) +
            byteArrayOf(0, pictureType.toByte(), 0) + image
        return ByteArrayOutputStream().apply {
            write("APIC".toByteArray(StandardCharsets.US_ASCII))
            writeBe32(payload.size)
            write(byteArrayOf(0, 0))
            write(payload)
        }.toByteArray()
    }

    private fun id3Tag(majorVersion: Int, frames: List<ByteArray>): ByteArray {
        val body = frames.fold(ByteArray(0), ByteArray::plus)
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.US_ASCII))
            write(byteArrayOf(majorVersion.toByte(), 0, 0))
            writeSyncSafe(body.size)
            write(body)
        }.toByteArray()
    }

    private fun flacPictureBlock(image: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        writeBe32(3)
        val mime = "image/jpeg".toByteArray(StandardCharsets.US_ASCII)
        writeBe32(mime.size)
        write(mime)
        writeBe32(0)
        writeBe32(1500)
        writeBe32(1500)
        writeBe32(24)
        writeBe32(0)
        writeBe32(image.size)
        write(image)
    }.toByteArray()

    private fun bytes(data: ByteArray) = RandomAccessAudioSource { position, length ->
        if (position >= data.size) ByteArray(0)
        else data.copyOfRange(position.toInt(), minOf(data.size, position.toInt() + length))
    }

    private fun ByteArrayOutputStream.writeSyncSafe(value: Int) {
        write((value ushr 21) and 0x7f)
        write((value ushr 14) and 0x7f)
        write((value ushr 7) and 0x7f)
        write(value and 0x7f)
    }

    private fun ByteArrayOutputStream.writeBe24(value: Int) {
        write((value ushr 16) and 0xff)
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }

    private fun ByteArrayOutputStream.writeBe32(value: Int) {
        write((value ushr 24) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }

    private fun ByteArrayOutputStream.writeLe64(value: Long) {
        repeat(8) { index -> write((value ushr (index * 8)).toInt() and 0xff) }
    }

    private fun ByteArrayOutputStream.writeBe64(value: Long) {
        repeat(8) { index -> write((value ushr ((7 - index) * 8)).toInt() and 0xff) }
    }
}
