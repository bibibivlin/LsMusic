package com.linxyi.lsmusic.listenbrainz

import com.linxyi.lsmusic.dlna.MediaEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.SSLException

data class ListenBrainzTokenValidationResult(
    val valid: Boolean,
    val userName: String? = null,
)

class ListenBrainzHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)

class ListenBrainzResponseException(message: String) : IOException(message)

class ListenBrainzClient {
    suspend fun validateToken(token: String): ListenBrainzTokenValidationResult = withContext(Dispatchers.IO) {
        val connection = openConnection(VALIDATE_TOKEN_URL, "GET", token)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw connection.httpException(status)
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (!json.has("valid")) {
                throw ListenBrainzResponseException("令牌校验响应缺少 valid 字段")
            }
            ListenBrainzTokenValidationResult(
                valid = json.optBoolean("valid", false),
                userName = json.optString("user_name").takeIf { it.isNotBlank() },
            )
        } finally {
            connection.disconnect()
        }
    }

    suspend fun submitNowPlaying(token: String, track: MediaEntry, durationMs: Long) {
        submit(token, "playing_now", trackPayload(track, durationMs, listenedMs = null))
    }

    suspend fun submitListen(
        token: String,
        track: MediaEntry,
        startedAtEpochSeconds: Long,
        durationMs: Long,
        listenedMs: Long,
    ) {
        val listen = trackPayload(track, durationMs, listenedMs)
            .put("listened_at", startedAtEpochSeconds)
        submit(token, "single", listen)
    }

    private suspend fun submit(token: String, listenType: String, listen: JSONObject) = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("listen_type", listenType)
            .put("payload", JSONArray().put(listen))
            .toString()
        val connection = openConnection(SUBMIT_URL, "POST", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) throw connection.httpException(status)
            connection.inputStream?.close()
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String, token: String) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Token ${token.trim()}")
            setRequestProperty("Accept", "application/json")
        }

    private fun HttpURLConnection.httpException(status: Int): ListenBrainzHttpException {
        val response = errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val detail = response.takeIf { it.isNotBlank() }?.take(300)?.let { ": $it" }.orEmpty()
        return ListenBrainzHttpException(status, "ListenBrainz HTTP $status$detail")
    }

    private fun trackPayload(track: MediaEntry, durationMs: Long, listenedMs: Long?): JSONObject {
        val additionalInfo = JSONObject()
            .put("media_player", CLIENT_NAME)
            .put("submission_client", CLIENT_NAME)
        if (durationMs > 0L) additionalInfo.put("duration_ms", durationMs)
        listenedMs?.takeIf { it > 0L }?.let { additionalInfo.put("duration_played", it / 1_000L) }
        track.recordingMbid?.let { additionalInfo.put("recording_mbid", it) }
        track.releaseMbid?.let { additionalInfo.put("release_mbid", it) }
        if (track.artistMbids.isNotEmpty()) additionalInfo.put("artist_mbids", JSONArray(track.artistMbids))
        track.trackNumber?.let { additionalInfo.put("tracknumber", it.toString()) }

        val metadata = JSONObject()
            .put("artist_name", track.creator.ifBlank { "未知艺术家" })
            .put("track_name", track.title.ifBlank { "未知曲目" })
            .put("additional_info", additionalInfo)
        if (track.album.isNotBlank()) metadata.put("release_name", track.album)
        return JSONObject().put("track_metadata", metadata)
    }

    private companion object {
        const val SUBMIT_URL = "https://api.listenbrainz.org/1/submit-listens"
        const val VALIDATE_TOKEN_URL = "https://api.listenbrainz.org/1/validate-token"
        const val CLIENT_NAME = "L's Music"
        const val TIMEOUT_MS = 15_000
    }
}

fun describeListenBrainzValidationFailure(error: Throwable): String {
    val causes = generateSequence(error) { it.cause }.toList()
    val httpError = causes.filterIsInstance<ListenBrainzHttpException>().firstOrNull()
    return when {
        causes.any { it is UnknownHostException } -> "无法解析 ListenBrainz 地址，请检查网络或 DNS"
        causes.any { it is SocketTimeoutException } -> "连接 ListenBrainz 超时，请检查网络后重试"
        causes.any { it is ConnectException || it is NoRouteToHostException } ->
            "无法连接 ListenBrainz，请检查网络是否可用"
        causes.any { it is SSLException } -> "无法建立 ListenBrainz HTTPS 安全连接"
        causes.any { it is ListenBrainzResponseException } -> "ListenBrainz 返回了无法识别的校验结果"
        httpError != null -> "已连接 ListenBrainz，但服务返回 HTTP ${httpError.statusCode}"
        else -> "校验请求失败：${error.localizedMessage ?: "未知网络或服务错误"}"
    }
}
