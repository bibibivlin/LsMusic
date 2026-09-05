package com.linxyi.lsmusic.listenbrainz

import android.content.Context
import android.util.AtomicFile
import com.linxyi.lsmusic.dlna.MediaEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID

data class PendingListen(
    val id: String,
    val track: MediaEntry,
    val startedAtEpochSeconds: Long,
    val durationMs: Long,
    val listenedMs: Long,
    val queuedAtEpochSeconds: Long,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochSeconds: Long? = null,
    val lastError: String? = null,
) {
    companion object {
        fun fromReport(
            report: ListenBrainzPlaybackReport.Finished,
            queuedAtEpochSeconds: Long,
            id: String = UUID.randomUUID().toString(),
        ) = PendingListen(
            id = id,
            track = report.track,
            startedAtEpochSeconds = report.startedAtEpochSeconds,
            durationMs = report.durationMs,
            listenedMs = report.listenedMs,
            queuedAtEpochSeconds = queuedAtEpochSeconds,
        )
    }
}

data class PendingListenUploadResult(
    val attemptedCount: Int,
    val uploadedCount: Int,
    val remainingCount: Int,
    val errorMessage: String? = null,
)

internal data class PendingListenProcessingResult(
    val attemptedCount: Int,
    val uploadedCount: Int,
    val errorMessage: String? = null,
)

internal suspend fun processPendingListens(
    pending: List<PendingListen>,
    attemptedAtEpochSeconds: () -> Long,
    enrich: suspend (MediaEntry) -> MediaEntry,
    onAttempt: (id: String, attemptedAtEpochSeconds: Long) -> Unit,
    onEnriched: (id: String, track: MediaEntry) -> Unit,
    submit: suspend (PendingListen, MediaEntry) -> Unit,
    onUploaded: (id: String) -> Unit,
    onFailed: (id: String, message: String) -> Unit,
    continueAfterFailure: (Throwable) -> Boolean = { false },
): PendingListenProcessingResult {
    var attemptedCount = 0
    var uploadedCount = 0
    var firstErrorMessage: String? = null
    for (record in pending) {
        val attemptTime = attemptedAtEpochSeconds()
        try {
            onAttempt(record.id, attemptTime)
            attemptedCount += 1
            val enrichedTrack = enrich(record.track)
            if (enrichedTrack != record.track) onEnriched(record.id, enrichedTrack)
            submit(record, enrichedTrack)
            onUploaded(record.id)
            uploadedCount += 1
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.pendingListenErrorMessage()
            if (firstErrorMessage == null) firstErrorMessage = message
            runCatching { onFailed(record.id, message) }
            if (!continueAfterFailure(error)) {
                return PendingListenProcessingResult(
                    attemptedCount = attemptedCount,
                    uploadedCount = uploadedCount,
                    errorMessage = message,
                )
            }
        }
    }
    return PendingListenProcessingResult(attemptedCount, uploadedCount, firstErrorMessage)
}

internal fun shouldContinuePendingListenBatchAfter(error: Throwable): Boolean =
    error is ListenBrainzHttpException && error.statusCode in RECORD_SPECIFIC_HTTP_STATUS_CODES

class PendingListenRepository internal constructor(
    initialRecords: List<PendingListen>,
    private val saveRecords: (List<PendingListen>) -> Unit,
) {
    private constructor(store: PendingListenDiskStore) : this(store.load(), store::save)
    private constructor(context: Context) : this(PendingListenDiskStore(context.applicationContext))
    private val mutationLock = Any()
    private val uploadMutex = Mutex()
    private val _records = MutableStateFlow(initialRecords)
    private val _isUploading = MutableStateFlow(false)

    val records: StateFlow<List<PendingListen>> = _records.asStateFlow()
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    suspend fun enqueue(record: PendingListen) = withContext(Dispatchers.IO) {
        mutate { current -> if (current.any { it.id == record.id }) current else current + record }
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        uploadMutex.withLock { removeImmediately(id) }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        uploadMutex.withLock { mutate { emptyList() } }
    }

    suspend fun upload(
        token: String,
        ids: Set<String>? = null,
        client: ListenBrainzClient = ListenBrainzClient(),
        metadataReader: EmbeddedAudioMetadataReader = EmbeddedAudioMetadataReader(),
        nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    ): PendingListenUploadResult = withContext(Dispatchers.IO) {
        uploadMutex.withLock {
            if (token.isBlank()) {
                return@withLock PendingListenUploadResult(
                    attemptedCount = 0,
                    uploadedCount = 0,
                    remainingCount = records.value.size,
                    errorMessage = "请先保存有效的 ListenBrainz 令牌",
                )
            }
            val snapshot = records.value.filter { ids == null || it.id in ids }
            if (snapshot.isEmpty()) {
                return@withLock PendingListenUploadResult(0, 0, records.value.size)
            }

            _isUploading.value = true
            try {
                val processed = processPendingListens(
                    pending = snapshot,
                    attemptedAtEpochSeconds = nowEpochSeconds,
                    enrich = metadataReader::enrich,
                    onAttempt = { id, attemptedAt ->
                        update(id) {
                            it.copy(
                                attemptCount = it.attemptCount + 1,
                                lastAttemptAtEpochSeconds = attemptedAt,
                                lastError = null,
                            )
                        }
                    },
                    onEnriched = { id, track -> update(id) { it.copy(track = track) } },
                    submit = { record, track ->
                        client.submitListen(
                            token = token,
                            track = track,
                            startedAtEpochSeconds = record.startedAtEpochSeconds,
                            durationMs = record.durationMs,
                            listenedMs = record.listenedMs,
                        )
                    },
                    onUploaded = { id -> removeImmediately(id) },
                    onFailed = { id, message -> update(id) { it.copy(lastError = message) } },
                    continueAfterFailure = ::shouldContinuePendingListenBatchAfter,
                )
                PendingListenUploadResult(
                    attemptedCount = processed.attemptedCount,
                    uploadedCount = processed.uploadedCount,
                    remainingCount = records.value.size,
                    errorMessage = processed.errorMessage,
                )
            } finally {
                _isUploading.value = false
            }
        }
    }

    private fun update(id: String, transform: (PendingListen) -> PendingListen) {
        mutate { current -> current.map { if (it.id == id) transform(it) else it } }
    }

    private fun removeImmediately(id: String) {
        mutate { current -> current.filterNot { it.id == id } }
    }

    private fun mutate(transform: (List<PendingListen>) -> List<PendingListen>) {
        synchronized(mutationLock) {
            val updated = transform(_records.value)
            if (updated == _records.value) return
            saveRecords(updated)
            _records.value = updated
        }
    }

    companion object {
        @Volatile
        private var instance: PendingListenRepository? = null

        fun get(context: Context): PendingListenRepository = instance ?: synchronized(this) {
            instance ?: PendingListenRepository(context.applicationContext).also { instance = it }
        }
    }
}

private class PendingListenDiskStore(context: Context) {
    private val directory = File(context.filesDir, STORE_DIRECTORY)
    private val file = AtomicFile(File(directory, STORE_FILE_NAME))

    fun load(): List<PendingListen> {
        return try {
            file.openRead().use { input ->
                PendingListenJsonCodec.decode(input.bufferedReader(Charsets.UTF_8).readText())
            }
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (error: Throwable) {
            throw IOException("无法读取待上传的 ListenBrainz 记录", error)
        }
    }

    fun save(records: List<PendingListen>) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("无法创建 ListenBrainz 待上传记录目录")
        }
        val output = file.startWrite()
        try {
            output.write(PendingListenJsonCodec.encode(records).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    private companion object {
        const val STORE_DIRECTORY = "listenbrainz"
        const val STORE_FILE_NAME = "pending-listens.json"
    }
}

internal object PendingListenJsonCodec {
    private const val VERSION = 1

    fun encode(records: List<PendingListen>): String = JSONObject()
        .put("version", VERSION)
        .put("records", JSONArray().apply { records.forEach { put(it.toJson()) } })
        .toString()

    fun decode(value: String): List<PendingListen> {
        val root = JSONObject(value)
        require(root.getInt("version") == VERSION) { "不支持的待上传记录版本" }
        val records = root.getJSONArray("records")
        return List(records.length()) { index -> records.getJSONObject(index).toPendingListen() }
    }

    private fun PendingListen.toJson() = JSONObject()
        .put("id", id)
        .put("track", track.toPendingJson())
        .put("startedAtEpochSeconds", startedAtEpochSeconds)
        .put("durationMs", durationMs)
        .put("listenedMs", listenedMs)
        .put("queuedAtEpochSeconds", queuedAtEpochSeconds)
        .put("attemptCount", attemptCount)
        .put("lastAttemptAtEpochSeconds", lastAttemptAtEpochSeconds)
        .put("lastError", lastError)

    private fun JSONObject.toPendingListen() = PendingListen(
        id = getString("id"),
        track = getJSONObject("track").toPendingTrack(),
        startedAtEpochSeconds = getLong("startedAtEpochSeconds"),
        durationMs = getLong("durationMs"),
        listenedMs = getLong("listenedMs"),
        queuedAtEpochSeconds = getLong("queuedAtEpochSeconds"),
        attemptCount = optInt("attemptCount", 0),
        lastAttemptAtEpochSeconds = nullableLong("lastAttemptAtEpochSeconds"),
        lastError = nullableString("lastError"),
    )

    private fun MediaEntry.toPendingJson() = JSONObject()
        .put("id", id)
        .put("parentId", parentId)
        .put("title", title)
        .put("creator", creator)
        .put("album", album)
        .put("genre", genre)
        .put("trackNumber", trackNumber)
        .put("resourceUri", resourceUri)
        .put("duration", duration)
        .put("mimeType", mimeType)
        .put("recordingMbid", recordingMbid)
        .put("releaseMbid", releaseMbid)
        .put("releaseGroupMbid", releaseGroupMbid)
        .put("trackMbid", trackMbid)
        .put("artistMbids", JSONArray(artistMbids))

    private fun JSONObject.toPendingTrack() = MediaEntry(
        id = getString("id"),
        parentId = getString("parentId"),
        title = getString("title"),
        creator = optString("creator"),
        album = optString("album"),
        genre = optString("genre"),
        trackNumber = nullableInt("trackNumber"),
        resourceUri = nullableString("resourceUri"),
        duration = nullableString("duration"),
        mimeType = nullableString("mimeType"),
        recordingMbid = nullableString("recordingMbid"),
        releaseMbid = nullableString("releaseMbid"),
        releaseGroupMbid = nullableString("releaseGroupMbid"),
        trackMbid = nullableString("trackMbid"),
        artistMbids = getJSONArray("artistMbids").let { array ->
            List(array.length()) { index -> array.getString(index) }
        },
        isContainer = false,
    )

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.nullableInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else getInt(name)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)
}

private fun Throwable.pendingListenErrorMessage(): String = when (this) {
    is ListenBrainzHttpException -> "ListenBrainz HTTP $statusCode"
    else -> localizedMessage?.takeIf { it.isNotBlank() }?.take(300) ?: "网络请求失败"
}

private val RECORD_SPECIFIC_HTTP_STATUS_CODES = setOf(400, 422)
