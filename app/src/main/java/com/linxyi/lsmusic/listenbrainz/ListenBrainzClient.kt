package com.linxyi.lsmusic.listenbrainz

import com.linxyi.lsmusic.dlna.MediaEntry
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

private const val CLIENT_NAME = "L's Music"

internal fun buildClearNowPlayingPayload(): JSONObject = JSONObject().put("client", CLIENT_NAME)

internal data class ListenBrainzTrackPayloadFields(
    val artistName: String,
    val trackName: String,
    val releaseName: String?,
    val additionalInfo: Map<String, Any>,
)

internal fun buildListenBrainzTrackPayloadFields(
    track: MediaEntry,
    durationMs: Long,
    listenedMs: Long?,
): ListenBrainzTrackPayloadFields {
    val additionalInfo = linkedMapOf<String, Any>(
        "media_player" to CLIENT_NAME,
        "submission_client" to CLIENT_NAME,
    )
    if (durationMs > 0L) additionalInfo["duration_ms"] = durationMs
    listenedMs?.takeIf { it > 0L }?.let { additionalInfo["duration_played"] = it / 1_000L }
    track.recordingMbid?.let { additionalInfo["recording_mbid"] = it }
    track.releaseMbid?.let { additionalInfo["release_mbid"] = it }
    track.releaseGroupMbid?.let { additionalInfo["release_group_mbid"] = it }
    track.trackMbid?.let { additionalInfo["track_mbid"] = it }
    if (track.artistMbids.isNotEmpty()) additionalInfo["artist_mbids"] = track.artistMbids
    track.trackNumber?.let { additionalInfo["tracknumber"] = it.toString() }
    return ListenBrainzTrackPayloadFields(
        artistName = track.creator.ifBlank { "Unknown artist" },
        trackName = track.title.ifBlank { "Unknown track" },
        releaseName = track.album.takeIf { it.isNotBlank() },
        additionalInfo = additionalInfo,
    )
}

class ListenBrainzClient(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    suspend fun validateToken(token: String): ListenBrainzTokenValidationResult = cancellableNetworkIo { cancellation ->
        val connection = openConnection(VALIDATE_TOKEN_URL, "GET", token)
        try {
            cancellation.attach(connection)
            val status = connection.responseCode
            if (status !in 200..299) throw connection.httpException(status)
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            if (!json.has("valid")) {
                throw ListenBrainzResponseException("validation response is missing valid")
            }
            ListenBrainzTokenValidationResult(
                valid = json.optBoolean("valid", false),
                userName = json.optString("user_name").takeIf { it.isNotBlank() },
            )
        } finally {
            cancellation.detach(connection)
            connection.disconnect()
        }
    }

    suspend fun submitNowPlaying(token: String, track: MediaEntry, durationMs: Long) {
        submit(token, "playing_now", trackPayload(track, durationMs, listenedMs = null))
    }

    suspend fun clearNowPlaying(token: String) = cancellableNetworkIo { cancellation ->
        val connection = openConnection("https://api.listenbrainz.org/1/playing-now/delete", "POST", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            cancellation.attach(connection)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(buildClearNowPlayingPayload().toString())
            }
            val status = connection.responseCode
            // 404 means another client owns the status. Do not clear that client's playback.
            if (status !in 200..299 && status != 404) throw connection.httpException(status)
        } finally {
            cancellation.detach(connection)
            connection.disconnect()
        }
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

    private suspend fun submit(token: String, listenType: String, listen: JSONObject) = cancellableNetworkIo { cancellation ->
        val body = JSONObject()
            .put("listen_type", listenType)
            .put("payload", JSONArray().put(listen))
            .toString()
        val connection = openConnection(SUBMIT_URL, "POST", token).apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            cancellation.attach(connection)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) throw connection.httpException(status)
            connection.inputStream?.close()
        } finally {
            cancellation.detach(connection)
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, method: String, token: String) =
        connectionFactory(URL(url)).apply {
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
        val fields = buildListenBrainzTrackPayloadFields(track, durationMs, listenedMs)
        val additionalInfo = JSONObject()
        fields.additionalInfo.forEach { (name, value) ->
            additionalInfo.put(name, if (value is List<*>) JSONArray(value) else value)
        }

        val metadata = JSONObject()
            .put("artist_name", fields.artistName)
            .put("track_name", fields.trackName)
            .put("additional_info", additionalInfo)
        fields.releaseName?.let { metadata.put("release_name", it) }
        return JSONObject().put("track_metadata", metadata)
    }

    private companion object {
        const val SUBMIT_URL = "https://api.listenbrainz.org/1/submit-listens"
        const val VALIDATE_TOKEN_URL = "https://api.listenbrainz.org/1/validate-token"
        const val TIMEOUT_MS = 15_000
    }
}

enum class ListenBrainzValidationFailureKind {
    UNKNOWN_HOST,
    TIMEOUT,
    CONNECTION,
    SSL,
    UNRECOGNIZED_RESPONSE,
    HTTP_STATUS,
    REQUEST,
}

data class ListenBrainzValidationFailure(
    val kind: ListenBrainzValidationFailureKind,
    val statusCode: Int? = null,
    val detail: String? = null,
)

fun describeListenBrainzValidationFailure(error: Throwable): ListenBrainzValidationFailure {
    val causes = generateSequence(error) { it.cause }.toList()
    val httpError = causes.filterIsInstance<ListenBrainzHttpException>().firstOrNull()
    return when {
        causes.any { it is UnknownHostException } -> ListenBrainzValidationFailure(ListenBrainzValidationFailureKind.UNKNOWN_HOST)
        causes.any { it is SocketTimeoutException } -> ListenBrainzValidationFailure(ListenBrainzValidationFailureKind.TIMEOUT)
        causes.any { it is ConnectException || it is NoRouteToHostException } ->
            ListenBrainzValidationFailure(ListenBrainzValidationFailureKind.CONNECTION)
        causes.any { it is SSLException } -> ListenBrainzValidationFailure(ListenBrainzValidationFailureKind.SSL)
        causes.any { it is ListenBrainzResponseException } ->
            ListenBrainzValidationFailure(ListenBrainzValidationFailureKind.UNRECOGNIZED_RESPONSE)
        httpError != null -> ListenBrainzValidationFailure(
            kind = ListenBrainzValidationFailureKind.HTTP_STATUS,
            statusCode = httpError.statusCode,
        )
        else -> ListenBrainzValidationFailure(
            kind = ListenBrainzValidationFailureKind.REQUEST,
            detail = error.localizedMessage,
        )
    }
}
