package com.linxyi.lsmusic.artwork

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.listenbrainz.HttpRangeAudioSource
import com.linxyi.lsmusic.listenbrainz.RandomAccessAudioSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal data class EmbeddedArtwork(
    val bytes: ByteArray,
    val mimeType: String?,
    val pictureType: Int,
)

internal class EmbeddedArtworkReader {
    suspend fun read(track: MediaEntry): EmbeddedArtwork? {
        val uri = track.resourceUri?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                EmbeddedArtworkParser.parse(
                    HttpRangeAudioSource(uri, track.resourceSize),
                )
            }.getOrNull()
        }
    }
}

internal object EmbeddedArtworkParser {
    fun parse(source: RandomAccessAudioSource): EmbeddedArtwork? {
        val header = source.read(0L, 28)
        return when {
            header.startsWithAscii("fLaC") -> parseFlac(source)
            header.startsWithAscii("DSD ") -> parseDsf(source, header)
            header.startsWithAscii("FRM8") && header.ascii(12, 4) == "DSD " -> parseDff(source, header)
            header.startsWithAscii("ID3") -> parseId3(source, 0L)
            else -> null
        }
    }

    private fun parseFlac(source: RandomAccessAudioSource): EmbeddedArtwork? {
        var position = 4L
        var fallback: EmbeddedArtwork? = null
        var pictureBlocks = 0
        repeat(MAX_FLAC_BLOCKS) {
            val blockHeader = source.read(position, 4)
            if (blockHeader.size < 4) return fallback
            val isLast = blockHeader[0].toInt() and 0x80 != 0
            val type = blockHeader[0].toInt() and 0x7f
            val length = blockHeader.uint24Be(1)
            position += 4L
            if (type == FLAC_PICTURE_BLOCK && ++pictureBlocks <= MAX_PICTURE_BLOCKS) {
                val artwork = parseFlacPicture(source, position, length)
                if (artwork?.pictureType == FRONT_COVER_TYPE) return artwork
                if (fallback == null) fallback = artwork
            }
            position += length
            if (isLast || position > MAX_METADATA_OFFSET) return fallback
        }
        return fallback
    }

    private fun parseFlacPicture(
        source: RandomAccessAudioSource,
        position: Long,
        blockLength: Int,
    ): EmbeddedArtwork? {
        if (blockLength !in FLAC_PICTURE_MIN_BYTES..MAX_PICTURE_BLOCK_BYTES) return null
        val block = source.readFully(position, blockLength) ?: return null
        var offset = 0
        val pictureType = block.readInt32Be(offset) ?: return null
        offset += 4
        val mimeLength = block.readInt32Be(offset)?.takeIf { it in 0..MAX_TEXT_FIELD_BYTES } ?: return null
        offset += 4
        if (offset + mimeLength > block.size) return null
        val mimeType = block.ascii(offset, mimeLength).takeIf { it.isNotBlank() }
        offset += mimeLength
        val descriptionLength = block.readInt32Be(offset)?.takeIf { it in 0..MAX_TEXT_FIELD_BYTES } ?: return null
        offset += 4
        if (offset + descriptionLength + FLAC_PICTURE_FIXED_TAIL_BYTES > block.size) return null
        offset += descriptionLength
        offset += 16 // width, height, depth, indexed-color count
        val imageLength = block.readInt32Be(offset)?.takeIf { it in 1..MAX_ARTWORK_BYTES } ?: return null
        offset += 4
        if (offset + imageLength != block.size) return null
        return EmbeddedArtwork(
            bytes = block.copyOfRange(offset, offset + imageLength),
            mimeType = mimeType,
            pictureType = pictureType,
        )
    }

    private fun parseDsf(source: RandomAccessAudioSource, header: ByteArray): EmbeddedArtwork? {
        if (header.size < 28 || header.uint64Le(4) != 28L) return null
        val fileSize = header.uint64Le(12)
        val metadataOffset = header.uint64Le(20)
        if (fileSize <= 28L || fileSize > MAX_SOURCE_SIZE || metadataOffset <= 0L || metadataOffset >= fileSize) {
            return null
        }
        source.constrainToLength(fileSize)
        return parseId3(source, metadataOffset)
    }

    private fun parseDff(source: RandomAccessAudioSource, header: ByteArray): EmbeddedArtwork? {
        if (header.size < 16) return null
        val formSize = header.uint64Be(4)
        val formEnd = (12L + formSize).coerceAtMost(MAX_SOURCE_SIZE)
        source.constrainToLength(formEnd)
        var position = 16L
        repeat(MAX_DFF_CHUNKS) {
            if (position + 12L > formEnd) return null
            val chunkHeader = source.read(position, 12)
            if (chunkHeader.size < 12) return null
            val id = chunkHeader.ascii(0, 4)
            val size = chunkHeader.uint64Be(4)
            val dataPosition = position + 12L
            if (id == "ID3 " || id == "ID3\u0000") return parseId3(source, dataPosition)
            if (size < 0L || dataPosition + size > formEnd) return null
            position = dataPosition + size + (size and 1L)
        }
        return null
    }

    private fun parseId3(source: RandomAccessAudioSource, tagOffset: Long): EmbeddedArtwork? {
        val header = source.read(tagOffset, 10)
        if (header.size < 10 || !header.startsWithAscii("ID3")) return null
        val majorVersion = header[3].toInt() and 0xff
        if (majorVersion !in 2..4) return null
        val tagSize = header.syncSafeInt(6)
        if (tagSize <= 0 || tagSize > MAX_ID3_TAG_SIZE) return null
        val tagEnd = tagOffset + 10L + tagSize
        var position = tagOffset + 10L
        val tagUnsynchronised = header[5].toInt() and 0x80 != 0
        if (header[5].toInt() and 0x40 != 0) {
            val extendedSizeBytes = source.read(position, 4)
            if (extendedSizeBytes.size < 4) return null
            position += if (majorVersion == 3) {
                4L + extendedSizeBytes.uint32Be(0)
            } else {
                extendedSizeBytes.syncSafeInt(0).toLong()
            }
        }

        var fallback: EmbeddedArtwork? = null
        repeat(MAX_ID3_FRAMES) {
            val frameHeaderSize = if (majorVersion == 2) 6 else 10
            if (position + frameHeaderSize > tagEnd) return fallback
            val frameHeader = source.read(position, frameHeaderSize)
            if (frameHeader.size < frameHeaderSize || frameHeader[0] == 0.toByte()) return fallback
            val id = frameHeader.ascii(0, if (majorVersion == 2) 3 else 4)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) return fallback
            val frameSize = when (majorVersion) {
                2 -> frameHeader.uint24Be(3).toLong()
                3 -> frameHeader.uint32Be(4)
                else -> frameHeader.syncSafeInt(4).toLong()
            }
            if (frameSize <= 0L || position + frameHeaderSize + frameSize > tagEnd) return fallback
            if (id == "APIC" || id == "PIC") {
                if (frameSize <= MAX_ARTWORK_FRAME_BYTES) {
                    var payload = source.readFully(position + frameHeaderSize, frameSize.toInt())
                    if (payload != null) {
                        val frameUnsynchronised = majorVersion == 4 && frameHeader[9].toInt() and 0x02 != 0
                        if (tagUnsynchronised || frameUnsynchronised) payload = payload.removeUnsynchronisation()
                        val artwork = parseId3Picture(payload, majorVersion)
                        if (artwork?.pictureType == FRONT_COVER_TYPE) return artwork
                        if (fallback == null) fallback = artwork
                    }
                }
            }
            position += frameHeaderSize + frameSize
        }
        return fallback
    }

    private fun parseId3Picture(payload: ByteArray, majorVersion: Int): EmbeddedArtwork? {
        if (payload.size < 5) return null
        val encoding = payload[0].toInt() and 0xff
        var offset = 1
        val mimeType: String?
        if (majorVersion == 2) {
            if (payload.size < 6) return null
            mimeType = when (payload.ascii(offset, 3).uppercase()) {
                "JPG" -> "image/jpeg"
                "PNG" -> "image/png"
                else -> null
            }
            offset += 3
        } else {
            val mimeEnd = payload.indexOfZero(offset)
            if (mimeEnd < offset) return null
            mimeType = payload.ascii(offset, mimeEnd - offset).takeIf { it.startsWith("image/") }
            offset = mimeEnd + 1
        }
        if (offset >= payload.size) return null
        val pictureType = payload[offset].toInt() and 0xff
        offset++
        val imageStart = payload.findTextTerminator(offset, encoding) ?: return null
        offset = imageStart.first + imageStart.second
        if (offset >= payload.size || payload.size - offset > MAX_ARTWORK_BYTES) return null
        return EmbeddedArtwork(
            bytes = payload.copyOfRange(offset, payload.size),
            mimeType = mimeType,
            pictureType = pictureType,
        )
    }

    private fun RandomAccessAudioSource.readFully(position: Long, length: Int): ByteArray? {
        if (length !in 1..MAX_ARTWORK_FRAME_BYTES) return null
        val output = ByteArrayOutputStream(length)
        var offset = 0
        while (offset < length) {
            val requested = minOf(MAX_RANGE_READ_BYTES, length - offset)
            val bytes = read(position + offset, requested)
            if (bytes.isEmpty()) return null
            output.write(bytes)
            offset += bytes.size
            if (bytes.size < requested && offset < length) return null
        }
        return output.toByteArray()
    }

    private fun ByteArray.findTextTerminator(start: Int, encoding: Int): Pair<Int, Int>? {
        val unit = if (encoding == 1 || encoding == 2) 2 else 1
        var index = start
        while (index + unit <= size) {
            if (unit == 1 && this[index] == 0.toByte()) return index to 1
            if (unit == 2 && this[index] == 0.toByte() && this[index + 1] == 0.toByte()) return index to 2
            index += unit
        }
        return null
    }

    private fun ByteArray.indexOfZero(start: Int): Int {
        for (index in start until size) if (this[index] == 0.toByte()) return index
        return -1
    }

    private fun ByteArray.removeUnsynchronisation(): ByteArray {
        val output = ByteArrayOutputStream(size)
        var index = 0
        while (index < size) {
            val value = this[index]
            output.write(value.toInt())
            if (value == 0xff.toByte() && index + 1 < size && this[index + 1] == 0.toByte()) index++
            index++
        }
        return output.toByteArray()
    }

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && ascii(0, value.length) == value

    private fun ByteArray.ascii(offset: Int, length: Int): String {
        if (offset < 0 || length < 0 || offset + length > size) return ""
        return copyOfRange(offset, offset + length).toString(StandardCharsets.ISO_8859_1)
    }

    private fun ByteArray.readInt32Be(offset: Int): Int? =
        if (offset < 0 || offset + 4 > size) null else uint32Be(offset).takeIf { it <= Int.MAX_VALUE }?.toInt()

    private fun ByteArray.uint24Be(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 16) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            (this[offset + 2].toInt() and 0xff)

    private fun ByteArray.uint32Be(offset: Int): Long =
        ((this[offset].toLong() and 0xff) shl 24) or
            ((this[offset + 1].toLong() and 0xff) shl 16) or
            ((this[offset + 2].toLong() and 0xff) shl 8) or
            (this[offset + 3].toLong() and 0xff)

    private fun ByteArray.uint64Le(offset: Int): Long = (0 until 8).fold(0L) { value, index ->
        value or ((this[offset + index].toLong() and 0xff) shl (index * 8))
    }

    private fun ByteArray.uint64Be(offset: Int): Long = (0 until 8).fold(0L) { value, index ->
        (value shl 8) or (this[offset + index].toLong() and 0xff)
    }

    private fun ByteArray.syncSafeInt(offset: Int): Int =
        ((this[offset].toInt() and 0x7f) shl 21) or
            ((this[offset + 1].toInt() and 0x7f) shl 14) or
            ((this[offset + 2].toInt() and 0x7f) shl 7) or
            (this[offset + 3].toInt() and 0x7f)

    private const val FRONT_COVER_TYPE = 3
    private const val FLAC_PICTURE_BLOCK = 6
    private const val FLAC_PICTURE_MIN_BYTES = 32
    private const val FLAC_PICTURE_FIXED_TAIL_BYTES = 20
    private const val MAX_FLAC_BLOCKS = 64
    private const val MAX_PICTURE_BLOCKS = 8
    private const val MAX_DFF_CHUNKS = 128
    private const val MAX_ID3_FRAMES = 512
    private const val MAX_ID3_TAG_SIZE = 16 * 1024 * 1024
    private const val MAX_ARTWORK_BYTES = 8 * 1024 * 1024
    private const val MAX_ARTWORK_FRAME_BYTES = MAX_ARTWORK_BYTES + 64 * 1024
    private const val MAX_PICTURE_BLOCK_BYTES = MAX_ARTWORK_FRAME_BYTES
    private const val MAX_TEXT_FIELD_BYTES = 64 * 1024
    private const val MAX_RANGE_READ_BYTES = 2 * 1024 * 1024
    private const val MAX_METADATA_OFFSET = 32L * 1024L * 1024L
    private const val MAX_SOURCE_SIZE = 16L * 1024L * 1024L * 1024L * 1024L
}
