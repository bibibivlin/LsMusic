package com.linxyi.lsmusic.listenbrainz

import android.util.Log
import com.linxyi.lsmusic.dlna.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap

private const val RANGE_PREFETCH_SIZE = 8 * 1024

data class EmbeddedAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val musicBrainzIds: MusicBrainzIds = MusicBrainzIds(),
) {
    fun applyTo(track: MediaEntry): MediaEntry = track.copy(
        title = title?.takeIf { it.isNotBlank() } ?: track.title,
        creator = artist?.takeIf { it.isNotBlank() } ?: track.creator,
        album = album?.takeIf { it.isNotBlank() } ?: track.album,
        trackNumber = trackNumber ?: track.trackNumber,
        recordingMbid = musicBrainzIds.recordingMbid ?: track.recordingMbid,
        releaseMbid = musicBrainzIds.releaseMbid ?: track.releaseMbid,
        releaseGroupMbid = musicBrainzIds.releaseGroupMbid ?: track.releaseGroupMbid,
        trackMbid = musicBrainzIds.trackMbid ?: track.trackMbid,
        artistMbids = musicBrainzIds.artistMbids.ifEmpty { track.artistMbids },
    )
}

/** Reads only the byte ranges that contain tags; it never downloads the complete audio resource. */
class EmbeddedAudioMetadataReader {
    private val cache = object : LinkedHashMap<String, MediaEntry>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaEntry>?): Boolean =
            size > CACHE_SIZE
    }

    suspend fun enrich(track: MediaEntry): MediaEntry {
        val resourceUri = track.resourceUri?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return track
        synchronized(cache) { cache[resourceUri] }?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                EmbeddedAudioMetadataParser.parse(HttpRangeAudioSource(resourceUri)).applyTo(track)
            }.onSuccess { enriched ->
                synchronized(cache) { cache[resourceUri] = enriched }
            }.onFailure { error ->
                Log.w(TAG, "Unable to read embedded metadata for ${track.id}", error)
            }.getOrDefault(track)
        }
    }

    private companion object {
        const val CACHE_SIZE = 64
        const val TAG = "EmbeddedMetadata"
    }
}

internal fun interface RandomAccessAudioSource {
    @Throws(IOException::class)
    fun read(position: Long, length: Int): ByteArray

    /** Restricts future reads to a container-declared file length when it is available. */
    fun constrainToLength(length: Long) = Unit
}

internal class HttpRangeAudioSource(
    private val uri: String,
    declaredLength: Long? = null,
) : RandomAccessAudioSource {
    private var cachedPosition = -1L
    private var cachedBytes = ByteArray(0)
    private var knownLength: Long? = declaredLength?.takeIf { it > 0L }

    override fun constrainToLength(length: Long) {
        if (length > 0L) knownLength = minOf(knownLength ?: length, length)
    }

    override fun read(position: Long, length: Int): ByteArray {
        require(position >= 0L && length in 1..MAX_READ_SIZE)
        val effectiveLength = knownLength?.let { fileLength ->
            (fileLength - position).coerceIn(0L, length.toLong()).toInt()
        } ?: length
        if (effectiveLength == 0) return ByteArray(0)
        if (position >= cachedPosition && position + effectiveLength <= cachedPosition + cachedBytes.size) {
            val start = (position - cachedPosition).toInt()
            return cachedBytes.copyOfRange(start, start + effectiveLength)
        }
        val requestLength = calculateRangeRequestLength(position, effectiveLength, knownLength)
        cachedBytes = readRange(position, requestLength, minimumLength = effectiveLength)
        cachedPosition = position
        return cachedBytes.copyOfRange(0, minOf(effectiveLength, cachedBytes.size))
    }

    private fun readRange(position: Long, length: Int, minimumLength: Int): ByteArray {
        val end = position + length - 1L
        val connection = (URL(uri).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Range", "bytes=$position-$end")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Audio source HTTP $status")
            val partial = status == HttpURLConnection.HTTP_PARTIAL
            updateKnownLength(connection, partial)
            if (!partial && position > MAX_FALLBACK_SKIP) {
                throw IOException("Audio source does not support byte ranges")
            }
            return connection.inputStream.use { input ->
                if (!partial && position > 0L) input.skipFully(position)
                val output = ByteArrayOutputStream(length)
                val buffer = ByteArray(minOf(8_192, length))
                var remaining = length
                while (remaining > 0) {
                    val read = try {
                        input.read(buffer, 0, minOf(buffer.size, remaining))
                    } catch (error: IOException) {
                        // Prefetching is opportunistic. If the bytes the parser actually asked for
                        // have arrived, keep them even when this DLNA server stalls before sending
                        // the rest of the advertised range.
                        if (output.size() >= minimumLength) break else throw error
                    }
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun updateKnownLength(connection: HttpURLConnection, partial: Boolean) {
        val reportedLength = if (partial) {
            connection.getHeaderField("Content-Range")
                ?.substringAfterLast('/', "")
                ?.toLongOrNull()
        } else {
            connection.contentLengthLong.takeIf { it > 0L }
        }
        reportedLength?.let(::constrainToLength)
    }

    private fun java.io.InputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        val scratch = ByteArray(8_192)
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (read < 0) throw IOException("Audio source ended before requested range")
                remaining -= read
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000
        const val MAX_READ_SIZE = 2 * 1024 * 1024
        const val MAX_FALLBACK_SKIP = 1024L * 1024L
    }
}

internal fun calculateRangeRequestLength(position: Long, requestedLength: Int, knownLength: Long?): Int {
    val prefetchedLength = maxOf(requestedLength, RANGE_PREFETCH_SIZE)
    return knownLength?.let { fileLength ->
        minOf(prefetchedLength.toLong(), (fileLength - position).coerceAtLeast(0L)).toInt()
    } ?: prefetchedLength
}

internal object EmbeddedAudioMetadataParser {
    private val uuid = Regex(
        "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
    )

    fun parse(source: RandomAccessAudioSource): EmbeddedAudioMetadata {
        val header = source.read(0L, 28)
        return when {
            header.startsWithAscii("fLaC") -> parseFlac(source)
            header.startsWithAscii("DSD ") -> parseDsf(source, header)
            header.startsWithAscii("FRM8") && header.ascii(12, 4) == "DSD " -> parseDff(source, header)
            header.startsWithAscii("ID3") -> parseId3(source, 0L)
            else -> EmbeddedAudioMetadata()
        }
    }

    private fun parseFlac(source: RandomAccessAudioSource): EmbeddedAudioMetadata {
        var position = 4L
        repeat(MAX_FLAC_BLOCKS) {
            val blockHeader = source.read(position, 4)
            if (blockHeader.size < 4) return EmbeddedAudioMetadata()
            val isLast = blockHeader[0].toInt() and 0x80 != 0
            val type = blockHeader[0].toInt() and 0x7f
            val length = blockHeader.uint24Be(1)
            position += 4L
            if (type == FLAC_VORBIS_COMMENT_BLOCK && length in 1..MAX_METADATA_BLOCK_SIZE) {
                return parseVorbisComments(source.read(position, length))
            }
            position += length
            if (isLast || position > MAX_METADATA_OFFSET) return EmbeddedAudioMetadata()
        }
        return EmbeddedAudioMetadata()
    }

    private fun parseVorbisComments(data: ByteArray): EmbeddedAudioMetadata {
        if (data.size < 8) return EmbeddedAudioMetadata()
        var position = 0
        val vendorLength = data.int32Le(position)
        position += 4
        if (vendorLength < 0 || position + vendorLength + 4 > data.size) return EmbeddedAudioMetadata()
        position += vendorLength
        val count = data.int32Le(position)
        position += 4
        if (count !in 0..MAX_COMMENT_COUNT) return EmbeddedAudioMetadata()
        val fields = linkedMapOf<String, MutableList<String>>()
        repeat(count) {
            if (position + 4 > data.size) return EmbeddedAudioMetadata()
            val length = data.int32Le(position)
            position += 4
            if (length < 0 || position + length > data.size) return EmbeddedAudioMetadata()
            val comment = data.copyOfRange(position, position + length).toString(StandardCharsets.UTF_8)
            position += length
            val separator = comment.indexOf('=')
            if (separator > 0) {
                val key = comment.substring(0, separator).uppercase()
                fields.getOrPut(key) { mutableListOf() } += comment.substring(separator + 1)
            }
        }
        return EmbeddedAudioMetadata(
            title = fields["TITLE"]?.firstOrNull(),
            artist = fields["ARTIST"]?.firstOrNull(),
            album = fields["ALBUM"]?.firstOrNull(),
            trackNumber = fields["TRACKNUMBER"]?.firstOrNull()?.substringBefore('/')?.toIntOrNull(),
            musicBrainzIds = MusicBrainzIds(
                // Picard stores recording IDs in MUSICBRAINZ_TRACKID for Vorbis comments.
                recordingMbid = fields.uuids("MUSICBRAINZ_TRACKID", "MUSICBRAINZ_RECORDINGID").firstOrNull(),
                releaseMbid = fields.uuids("MUSICBRAINZ_ALBUMID", "MUSICBRAINZ_RELEASEID").firstOrNull(),
                releaseGroupMbid = fields.uuids("MUSICBRAINZ_RELEASEGROUPID").firstOrNull(),
                trackMbid = fields.uuids("MUSICBRAINZ_RELEASETRACKID").firstOrNull(),
                artistMbids = fields.uuids("MUSICBRAINZ_ARTISTID"),
            ),
        )
    }

    private fun parseDsf(source: RandomAccessAudioSource, header: ByteArray): EmbeddedAudioMetadata {
        if (header.size < 28 || header.uint64Le(4) != 28L) return EmbeddedAudioMetadata()
        val fileSize = header.uint64Le(12)
        val metadataOffset = header.uint64Le(20)
        if (
            fileSize <= 28L || fileSize > MAX_SOURCE_SIZE ||
            metadataOffset <= 0L || metadataOffset >= fileSize
        ) {
            return EmbeddedAudioMetadata()
        }
        source.constrainToLength(fileSize)
        return parseId3(source, metadataOffset)
    }

    private fun parseDff(source: RandomAccessAudioSource, header: ByteArray): EmbeddedAudioMetadata {
        if (header.size < 16) return EmbeddedAudioMetadata()
        val formSize = header.uint64Be(4)
        val formEnd = (12L + formSize).coerceAtMost(MAX_SOURCE_SIZE)
        source.constrainToLength(formEnd)
        var position = 16L
        repeat(MAX_DFF_CHUNKS) {
            if (position + 12L > formEnd) return EmbeddedAudioMetadata()
            val chunkHeader = source.read(position, 12)
            if (chunkHeader.size < 12) return EmbeddedAudioMetadata()
            val id = chunkHeader.ascii(0, 4)
            val size = chunkHeader.uint64Be(4)
            val dataPosition = position + 12L
            if (id == "ID3 " || id == "ID3\u0000") return parseId3(source, dataPosition)
            if (size < 0L || dataPosition + size > formEnd) return EmbeddedAudioMetadata()
            position = dataPosition + size + (size and 1L)
        }
        return EmbeddedAudioMetadata()
    }

    private fun parseId3(source: RandomAccessAudioSource, tagOffset: Long): EmbeddedAudioMetadata {
        val header = source.read(tagOffset, 10)
        if (header.size < 10 || !header.startsWithAscii("ID3")) return EmbeddedAudioMetadata()
        val majorVersion = header[3].toInt() and 0xff
        if (majorVersion !in 2..4) return EmbeddedAudioMetadata()
        val tagSize = header.syncSafeInt(6)
        if (tagSize <= 0 || tagSize > MAX_ID3_TAG_SIZE) return EmbeddedAudioMetadata()
        val tagEnd = tagOffset + 10L + tagSize
        var position = tagOffset + 10L
        val tagUnsynchronised = header[5].toInt() and 0x80 != 0
        if (header[5].toInt() and 0x40 != 0) {
            val extendedSizeBytes = source.read(position, 4)
            if (extendedSizeBytes.size < 4) return EmbeddedAudioMetadata()
            position += if (majorVersion == 3) {
                4L + extendedSizeBytes.uint32Be(0)
            } else {
                extendedSizeBytes.syncSafeInt(0).toLong()
            }
        }

        val builder = MetadataBuilder()
        repeat(MAX_ID3_FRAMES) {
            val frameHeaderSize = if (majorVersion == 2) 6 else 10
            if (position + frameHeaderSize > tagEnd) return builder.build()
            val frameHeader = source.read(position, frameHeaderSize)
            if (frameHeader.size < frameHeaderSize || frameHeader[0] == 0.toByte()) return builder.build()
            val id = frameHeader.ascii(0, if (majorVersion == 2) 3 else 4)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) return builder.build()
            val frameSize = when (majorVersion) {
                2 -> frameHeader.uint24Be(3).toLong()
                3 -> frameHeader.uint32Be(4)
                else -> frameHeader.syncSafeInt(4).toLong()
            }
            if (frameSize <= 0L || position + frameHeaderSize + frameSize > tagEnd) return builder.build()
            val relevant = id in RELEVANT_ID3_FRAMES
            if (relevant && frameSize <= MAX_ID3_TEXT_FRAME_SIZE) {
                var payload = source.read(position + frameHeaderSize, frameSize.toInt())
                val frameUnsynchronised = majorVersion == 4 && frameHeader[9].toInt() and 0x02 != 0
                if (tagUnsynchronised || frameUnsynchronised) payload = payload.removeUnsynchronisation()
                builder.consumeId3Frame(id, payload)
            }
            position += frameHeaderSize + frameSize
        }
        return builder.build()
    }

    private class MetadataBuilder {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var trackNumber: Int? = null
        var recordingMbid: String? = null
        var releaseMbid: String? = null
        var releaseGroupMbid: String? = null
        var trackMbid: String? = null
        val artistMbids = linkedSetOf<String>()

        fun consumeId3Frame(id: String, payload: ByteArray) {
            when (id) {
                "TIT2", "TT2" -> title = payload.decodeId3Text().firstOrNull()
                "TPE1", "TP1" -> artist = payload.decodeId3Text().firstOrNull()
                "TALB", "TAL" -> album = payload.decodeId3Text().firstOrNull()
                "TRCK", "TRK" -> trackNumber = payload.decodeId3Text().firstOrNull()
                    ?.substringBefore('/')?.toIntOrNull()
                "TXXX", "TXX" -> consumeUserText(payload)
                "UFID", "UFI" -> consumeUniqueFileId(payload)
            }
        }

        private fun consumeUserText(payload: ByteArray) {
            val (description, values) = payload.decodeId3UserText() ?: return
            val ids = values.flatMap(::extractUuids)
            when (description.filter(Char::isLetterOrDigit).lowercase()) {
                "musicbrainzrecordingid", "musicbrainztrackid" -> recordingMbid = ids.firstOrNull()
                "musicbrainzalbumid", "musicbrainzreleaseid" -> releaseMbid = ids.firstOrNull()
                "musicbrainzreleasegroupid" -> releaseGroupMbid = ids.firstOrNull()
                "musicbrainzreleasetrackid" -> trackMbid = ids.firstOrNull()
                "musicbrainzartistid" -> artistMbids += ids
            }
        }

        private fun consumeUniqueFileId(payload: ByteArray) {
            val separator = payload.indexOf(0.toByte())
            if (separator <= 0) return
            val owner = payload.copyOfRange(0, separator).toString(StandardCharsets.ISO_8859_1)
            if (owner.equals("http://musicbrainz.org", ignoreCase = true)) {
                recordingMbid = extractUuids(
                    payload.copyOfRange(separator + 1, payload.size).toString(StandardCharsets.ISO_8859_1),
                ).firstOrNull()
            }
        }

        fun build() = EmbeddedAudioMetadata(
            title = title,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            musicBrainzIds = MusicBrainzIds(
                recordingMbid = recordingMbid,
                releaseMbid = releaseMbid,
                releaseGroupMbid = releaseGroupMbid,
                trackMbid = trackMbid,
                artistMbids = artistMbids.toList(),
            ),
        )
    }

    private fun ByteArray.decodeId3UserText(): Pair<String, List<String>>? {
        if (isEmpty()) return null
        val encoding = this[0].toInt() and 0xff
        val terminator = findTextTerminator(1, encoding)
        val width = if (encoding == 1 || encoding == 2) 2 else 1
        val description = decodeTextRange(1, terminator, encoding)
        val valuesStart = (terminator + width).coerceAtMost(size)
        val values = decodeTextRange(valuesStart, size, encoding).split('\u0000').filter(String::isNotBlank)
        return description to values
    }

    private fun ByteArray.decodeId3Text(): List<String> {
        if (isEmpty()) return emptyList()
        val encoding = this[0].toInt() and 0xff
        return decodeTextRange(1, size, encoding).split('\u0000').filter(String::isNotBlank)
    }

    private fun ByteArray.findTextTerminator(start: Int, encoding: Int): Int {
        val width = if (encoding == 1 || encoding == 2) 2 else 1
        var index = start
        while (index + width <= size) {
            if (this[index] == 0.toByte() && (width == 1 || this[index + 1] == 0.toByte())) return index
            index += width
        }
        return size
    }

    private fun ByteArray.decodeTextRange(start: Int, end: Int, encoding: Int): String {
        if (start >= end || start !in indices) return ""
        val charset = when (encoding) {
            0 -> StandardCharsets.ISO_8859_1
            1 -> Charset.forName("UTF-16")
            2 -> StandardCharsets.UTF_16BE
            else -> StandardCharsets.UTF_8
        }
        return copyOfRange(start, end.coerceAtMost(size)).toString(charset).trimEnd('\u0000')
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

    private fun extractUuids(value: String): List<String> = uuid.findAll(value)
        .map { it.value.lowercase() }
        .distinct()
        .toList()

    private fun Map<String, List<String>>.uuids(vararg keys: String): List<String> = keys
        .flatMap { get(it).orEmpty() }
        .flatMap(::extractUuids)
        .distinct()

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && ascii(0, value.length) == value

    private fun ByteArray.ascii(offset: Int, length: Int): String =
        copyOfRange(offset, (offset + length).coerceAtMost(size)).toString(StandardCharsets.ISO_8859_1)

    private fun ByteArray.uint24Be(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 16) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            (this[offset + 2].toInt() and 0xff)

    private fun ByteArray.uint32Be(offset: Int): Long =
        ((this[offset].toLong() and 0xff) shl 24) or
            ((this[offset + 1].toLong() and 0xff) shl 16) or
            ((this[offset + 2].toLong() and 0xff) shl 8) or
            (this[offset + 3].toLong() and 0xff)

    private fun ByteArray.int32Le(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

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

    private val RELEVANT_ID3_FRAMES = setOf(
        "TIT2", "TT2", "TPE1", "TP1", "TALB", "TAL", "TRCK", "TRK", "TXXX", "TXX", "UFID", "UFI",
    )
    private const val FLAC_VORBIS_COMMENT_BLOCK = 4
    private const val MAX_FLAC_BLOCKS = 64
    private const val MAX_DFF_CHUNKS = 128
    private const val MAX_ID3_FRAMES = 512
    private const val MAX_COMMENT_COUNT = 4_096
    private const val MAX_METADATA_BLOCK_SIZE = 2 * 1024 * 1024
    private const val MAX_ID3_TAG_SIZE = 16 * 1024 * 1024
    private const val MAX_ID3_TEXT_FRAME_SIZE = 256 * 1024L
    private const val MAX_METADATA_OFFSET = 32L * 1024L * 1024L
    private const val MAX_SOURCE_SIZE = 16L * 1024L * 1024L * 1024L * 1024L
}
