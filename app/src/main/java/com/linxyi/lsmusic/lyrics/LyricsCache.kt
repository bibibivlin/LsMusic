package com.linxyi.lsmusic.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.security.MessageDigest

internal sealed interface LyricsCacheEntry {
    data class Document(val value: LyricsDocument) : LyricsCacheEntry
    data object NotFound : LyricsCacheEntry
}

internal class LyricsDiskCache(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maximumBytes: Long = DEFAULT_MAXIMUM_BYTES,
) {
    suspend fun get(query: LyricsQuery, provider: LyricsProviderId): LyricsCacheEntry? = withContext(Dispatchers.IO) {
        val file = fileFor(query, provider)
        if (!file.isFile) return@withContext null
        val entry = runCatching { read(file, provider) }.getOrNull()
        if (entry == null) {
            file.delete()
            return@withContext null
        }
        file.setLastModified(clock())
        entry
    }

    suspend fun put(query: LyricsQuery, provider: LyricsProviderId, document: LyricsDocument?) =
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            val target = fileFor(query, provider)
            val temporary = File(directory, "${target.name}.tmp")
            runCatching {
                DataOutputStream(BufferedOutputStream(temporary.outputStream())).use { output ->
                    output.writeInt(MAGIC)
                    output.writeInt(FORMAT_VERSION)
                    output.writeLong(clock())
                    output.writeBoolean(document == null)
                    output.writeString(provider.name)
                    if (document != null) writeDocument(output, document)
                }
                if (!temporary.renameTo(target)) {
                    target.delete()
                    check(temporary.renameTo(target)) { "无法提交歌词缓存" }
                }
            }.onFailure { temporary.delete() }
            prune()
        }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val failedCount = directory.listFiles()
            ?.filter(File::isFile)
            ?.count { !it.delete() }
            ?: 0
        check(failedCount == 0) { "有 $failedCount 个歌词缓存文件无法删除" }
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter(File::isFile)?.sumOf(File::length) ?: 0L
    }

    private fun read(file: File, expectedProvider: LyricsProviderId): LyricsCacheEntry? {
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) return null
            val storedAt = input.readLong()
            val negative = input.readBoolean()
            val provider = runCatching { LyricsProviderId.valueOf(input.readString()) }.getOrNull() ?: return null
            if (provider != expectedProvider) return null
            val maximumAge = if (negative) NEGATIVE_TTL_MS else POSITIVE_TTL_MS
            if (clock() - storedAt !in 0..maximumAge) return null
            if (negative) return LyricsCacheEntry.NotFound
            return LyricsCacheEntry.Document(readDocument(input, provider))
        }
    }

    private fun writeDocument(output: DataOutputStream, document: LyricsDocument) {
        output.writeInt(document.lines.size)
        document.lines.forEach { line ->
            output.writeString(line.stableId)
            output.writeNullableLong(line.startMs)
            output.writeNullableLong(line.durationMs)
            output.writeString(line.original)
            output.writeNullableString(line.translation)
            output.writeInt(line.words.size)
            line.words.forEach { word ->
                output.writeString(word.text)
                output.writeLong(word.startMs)
                output.writeLong(word.durationMs)
            }
        }
    }

    private fun readDocument(input: DataInputStream, provider: LyricsProviderId): LyricsDocument {
        val lineCount = input.readInt().takeIf { it in 0..MAX_LINES } ?: throw EOFException("无效歌词行数")
        val lines = List(lineCount) {
            LyricsLine(
                stableId = input.readString(),
                startMs = input.readNullableLong(),
                durationMs = input.readNullableLong(),
                original = input.readString(),
                translation = input.readNullableString(),
                words = List(input.readInt().takeIf { count -> count in 0..MAX_WORDS_PER_LINE }
                    ?: throw EOFException("无效逐字歌词数量")) {
                    TimedWord(input.readString(), input.readLong(), input.readLong())
                },
            )
        }
        return LyricsDocument(provider, lines)
    }

    private fun prune() {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
        var total = files.sumOf(File::length)
        if (total <= maximumBytes) return
        files.sortedBy(File::lastModified).forEach { file ->
            if (total <= maximumBytes) return
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun fileFor(query: LyricsQuery, provider: LyricsProviderId): File {
        val identity = listOf(
            CACHE_KEY_VERSION,
            provider.name,
            normalizeLyricsMatchText(query.title),
            normalizeLyricsMatchText(query.artist),
            normalizeLyricsMatchText(query.album),
            (query.durationMs / 1_000L).toString(),
        ).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
        val name = digest.joinToString("") { byte -> "%02x".format(byte) }
        return File(directory, "$name.lyrics-cache")
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt().takeIf { it in 0..MAX_STRING_BYTES } ?: throw EOFException("无效字符串长度")
        return ByteArray(size).also { readFully(it) }.toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataInputStream.readNullableString(): String? = if (readBoolean()) readString() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong() else null

    companion object {
        private const val MAGIC = 0x4C534C59
        private const val FORMAT_VERSION = 1
        private const val CACHE_KEY_VERSION = "lyrics-v3"
        private const val MAX_LINES = 10_000
        private const val MAX_WORDS_PER_LINE = 2_000
        private const val MAX_STRING_BYTES = 2 * 1024 * 1024
        private const val POSITIVE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        private const val NEGATIVE_TTL_MS = 24L * 60L * 60L * 1_000L
        const val DEFAULT_MAXIMUM_BYTES = 25L * 1024L * 1024L
    }
}
