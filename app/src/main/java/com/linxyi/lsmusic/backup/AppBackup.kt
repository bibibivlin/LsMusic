package com.linxyi.lsmusic.backup

import com.linxyi.lsmusic.dlna.MediaEntryJsonCodec
import com.linxyi.lsmusic.listenbrainz.PendingListen
import com.linxyi.lsmusic.listenbrainz.PendingListenJsonCodec
import com.linxyi.lsmusic.ui.PlaybackOrder
import com.linxyi.lsmusic.ui.QueueItem
import com.linxyi.lsmusic.ui.RepeatMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** Logical app data only: no downloaded media, caches, permissions or running timers. */
internal data class BackupSession(
    val queue: List<QueueItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val playbackOrder: PlaybackOrder = PlaybackOrder(),
)

// Deliberately not a data class: generated toString() must never expose credentials.
internal class AppBackupData(
    val preferences: Map<String, Any>,
    val secrets: Map<String, Any>?,
    val session: BackupSession,
    val pendingListens: List<PendingListen>,
)

internal class UnsupportedBackupVersion : IllegalArgumentException()

internal fun nonEmptyBackupSecrets(values: Map<String, Any>?): Map<String, Any> =
    values.orEmpty().filterValues { it !is String || it.isNotBlank() }

internal object AppBackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    private const val FORMAT = "com.linxyi.lsmusic.backup"
    private const val VERSION = 1

    fun write(data: AppBackupData, output: OutputStream) {
        val bytes = encode(data).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "Backup is too large" }
        output.write(bytes)
    }

    fun read(input: InputStream): AppBackupData {
        val bytes = input.readBytesBounded(MAX_BYTES)
        return decode(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString())
    }

    fun encode(data: AppBackupData): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("preferences", encodePreferences(data.preferences))
        .put("secrets", data.secrets?.let(::encodePreferences) ?: JSONObject.NULL)
        .put("session", encodeSession(data.session))
        .put("pendingListens", JSONObject(PendingListenJsonCodec.encode(data.pendingListens)))
        .toString()

    fun decode(value: String): AppBackupData {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        requireSafeJsonNesting(value)
        val root = JSONObject(value)
        require(root.getString("format") == FORMAT) { "Not an L's Music backup" }
        if (root.getInt("version") != VERSION) throw UnsupportedBackupVersion()
        val records = PendingListenJsonCodec.decode(root.getJSONObject("pendingListens").toString())
        require(records.map { it.id }.toSet().size == records.size)
        require(records.all {
            it.id.isNotBlank() && it.startedAtEpochSeconds > 0 && it.queuedAtEpochSeconds > 0 &&
                it.durationMs >= 0 && it.listenedMs >= 0 && it.attemptCount >= 0
        })
        return AppBackupData(
            preferences = decodePreferences(root.getJSONObject("preferences")),
            secrets = if (root.isNull("secrets")) null else decodePreferences(root.getJSONObject("secrets")),
            session = decodeSession(root.getJSONObject("session")),
            pendingListens = records,
        )
    }

    // Store types explicitly; JSON numbers alone cannot preserve SharedPreferences Int/Long/Float.
    private fun encodePreferences(values: Map<String, Any>) = JSONObject().apply {
        values.toSortedMap().forEach { (key, value) ->
            val type = when (value) {
                is String -> "string"
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                is Set<*> -> "strings"
                else -> error("Unsupported preference type")
            }
            put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) {
                require(value.all { it is String })
                JSONArray(value.toList().sortedBy { it as String })
            } else value))
        }
    }

    private fun decodePreferences(root: JSONObject): Map<String, Any> = root.keys().asSequence().associateWith { key ->
        require(key.length in 1..1024)
        val entry = root.getJSONObject(key)
        val value = entry.get("value")
        when (entry.getString("type")) {
            "string" -> (value as? String) ?: error("Invalid string preference")
            "boolean" -> (value as? Boolean) ?: error("Invalid boolean preference")
            "int" -> {
                require(value is Int || value is Long)
                (value as Number).toLong().also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
            }
            "long" -> {
                require(value is Int || value is Long)
                (value as Number).toLong()
            }
            "float" -> (value as Number).toFloat().also { require(it.isFinite()) }
            "strings" -> (value as JSONArray).strings().toSet()
            else -> error("Unsupported preference type")
        }
    }

    fun encodeSession(session: BackupSession): JSONObject = JSONObject()
        .put("queue", JSONArray().apply {
            session.queue.forEach { item -> put(JSONObject().put("queueId", item.queueId).put("track", MediaEntryJsonCodec.encode(item.track))) }
        })
        .put("currentQueueIndex", session.currentQueueIndex)
        .put("repeatMode", session.playbackOrder.repeatMode.name)
        .put("shuffleEnabled", session.playbackOrder.shuffleEnabled)
        .put("shuffledQueueIds", JSONArray(session.playbackOrder.shuffledQueueIds.toList()))

    fun decodeSession(root: JSONObject): BackupSession {
        val array = root.getJSONArray("queue")
        val queue = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            QueueItem(item.getString("queueId"), MediaEntryJsonCodec.decode(item.getJSONObject("track")))
        }
        val ids = queue.mapTo(mutableSetOf()) { it.queueId }
        require(ids.size == queue.size && ids.all { it.isNotBlank() })
        require(queue.all { !it.track.isContainer })
        val current = root.getInt("currentQueueIndex")
        require(current == -1 || current in queue.indices)
        val shuffled = root.getJSONArray("shuffledQueueIds").strings().toSet()
        require(ids.containsAll(shuffled))
        return BackupSession(queue, current, PlaybackOrder(
            repeatMode = RepeatMode.valueOf(root.getString("repeatMode")),
            shuffleEnabled = root.getBoolean("shuffleEnabled"),
            shuffledQueueIds = shuffled,
        ))
    }

    private fun JSONArray.strings() = List(length()) { get(it) as String }
}

/** Reject pathological nesting before handing untrusted input to the recursive JSON parser. */
private fun requireSafeJsonNesting(value: String) {
    var depth = 0
    var quoted = false
    var escaped = false
    value.forEach { character ->
        if (quoted) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> quoted = false
            }
        } else when (character) {
            '"' -> quoted = true
            '{', '[' -> { depth++; require(depth <= 32) }
            '}', ']' -> { depth--; require(depth >= 0) }
        }
    }
    require(!quoted && depth == 0)
}

private fun InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size().toLong() + count <= limit) { "Backup is too large" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
