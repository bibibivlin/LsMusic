package com.linxyi.lsmusic.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.InflaterInputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class LyricsHttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
)

internal fun interface LyricsHttpTransport {
    suspend fun execute(request: LyricsHttpRequest): String
}

internal class UrlConnectionLyricsTransport : LyricsHttpTransport {
    override suspend fun execute(request: LyricsHttpRequest): String = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            request.headers.forEach(connection::setRequestProperty)
            request.body?.let { body ->
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val source = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = source?.use { readBounded(it) } ?: ByteArray(0)
            val text = bytes.toString(Charsets.UTF_8)
            if (status !in 200..299) throw IOException("HTTP $status${text.take(160).takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}")
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("歌词响应超过 2 MiB 限制")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}

internal class NetEaseLyricsProvider(
    private val transport: LyricsHttpTransport = UrlConnectionLyricsTransport(),
) : LyricsProvider {
    override val id = LyricsProviderId.NETEASE

    override suspend fun search(query: LyricsQuery): List<LyricsCandidate> {
        val keyword = listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" ")
        val payload = JSONObject()
            .put("s", keyword)
            .put("type", "1")
            .put("limit", "20")
            .put("offset", "0")
            .put("total", "true")
            .put("csrf_token", "")
            .toString()
        return requestWithFallback(
            primary = {
                parseSearchCandidates(JSONObject(postWeApi(SEARCH_URL, payload)).requireSuccess("网易云搜索"))
            },
            isUsable = { LyricsCandidateMatcher.best(query, it) != null },
            fallback = { parseSearchCandidates(searchLegacy(keyword)) },
        )
    }

    override suspend fun fetch(candidate: LyricsCandidate): LyricsDocument? {
        val payload = JSONObject()
            .put("id", candidate.id)
            .put("OS", "pc")
            .put("lv", "-1")
            .put("kv", "-1")
            .put("tv", "-1")
            .put("rv", "-1")
            .put("yv", "-1")
            .put("ytv", "-1")
            .put("yrv", "-1")
            .put("csrf_token", "")
            .toString()
        return requestWithFallback(
            primary = {
                parseLyricsDocument(JSONObject(postWeApi(LYRIC_URL, payload)).requireSuccess("网易云歌词"))
            },
            isUsable = { it != null },
            fallback = { parseLyricsDocument(fetchLegacy(candidate.id)) },
        )
    }

    private fun parseSearchCandidates(json: JSONObject): List<LyricsCandidate> {
        val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        return songs.objects().mapNotNull(::parseCandidate).toList()
    }

    private fun parseLyricsDocument(json: JSONObject): LyricsDocument? = LyricsParser.parse(
        provider = id,
        original = json.optJSONObject("lrc")?.optString("lyric"),
        translation = json.optJSONObject("tlyric")?.optString("lyric"),
        verbatim = json.optJSONObject("yrc")?.optString("lyric"),
    )

    private suspend fun searchLegacy(keyword: String): JSONObject {
        val url = "$LEGACY_SEARCH_URL?s=${keyword.urlEncode()}&type=1&offset=0&total=true&limit=20"
        return JSONObject(
            transport.execute(
                LyricsHttpRequest(
                    url = url,
                    method = "GET",
                    headers = commonHeaders("https://music.163.com/"),
                ),
            ),
        ).requireSuccess("网易云兼容搜索")
    }

    private suspend fun fetchLegacy(songId: String): JSONObject {
        val url = "$LEGACY_LYRIC_URL?os=pc&id=${songId.urlEncode()}&lv=-1&kv=-1&tv=-1&rv=-1"
        return JSONObject(
            transport.execute(
                LyricsHttpRequest(
                    url = url,
                    method = "GET",
                    headers = commonHeaders("https://music.163.com/"),
                ),
            ),
        ).requireSuccess("网易云兼容歌词")
    }

    private fun JSONObject.requireSuccess(operation: String): JSONObject {
        val code = optInt("code", -1)
        if (code != 200) throw IOException("${operation}返回错误码 $code")
        return this
    }

    private suspend fun postWeApi(url: String, payload: String): String {
        val parameters = NetEaseWeApiEncryption.encrypt(payload)
        return transport.execute(
            LyricsHttpRequest(
                url = url,
                method = "POST",
                headers = commonHeaders("https://music.163.com/") +
                    ("Content-Type" to "application/x-www-form-urlencoded; charset=utf-8"),
                body = formBody(parameters),
            ),
        )
    }

    private fun parseCandidate(json: JSONObject): LyricsCandidate? {
        val songId = json.optLong("id", -1L).takeIf { it >= 0L }?.toString() ?: return null
        val artistsJson = json.optJSONArray("ar") ?: json.optJSONArray("artists") ?: JSONArray()
        val albumJson = json.optJSONObject("al") ?: json.optJSONObject("album")
        return LyricsCandidate(
            id = songId,
            title = json.optString("name"),
            artists = artistsJson.objects().map { it.optString("name") }.filter(String::isNotBlank).toList(),
            album = albumJson?.optString("name").orEmpty(),
            durationMs = json.optLong("dt", json.optLong("duration", 0L)),
        )
    }

    private companion object {
        const val SEARCH_URL = "https://music.163.com/weapi/search/get"
        const val LYRIC_URL = "https://music.163.com/weapi/song/lyric?csrf_token="
        const val LEGACY_SEARCH_URL = "https://music.163.com/api/search/get/"
        const val LEGACY_LYRIC_URL = "https://music.163.com/api/song/lyric"
    }
}

internal class QqLyricsProvider(
    private val transport: LyricsHttpTransport = UrlConnectionLyricsTransport(),
) : LyricsProvider {
    override val id = LyricsProviderId.QQ

    override suspend fun search(query: LyricsQuery): List<LyricsCandidate> = requestWithFallback(
        primary = { searchMusicu(query) },
        isUsable = { LyricsCandidateMatcher.best(query, it) != null },
        fallback = { searchLegacy(query) },
    )

    private suspend fun searchMusicu(query: LyricsQuery): List<LyricsCandidate> {
        val parameters = JSONObject()
            .put("num_per_page", "20")
            .put("page_num", "1")
            .put("query", listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" "))
            .put("search_type", 0)
        val body = JSONObject()
            .put(
                "req_1",
                JSONObject()
                    .put("method", "DoSearchForQQMusicDesktop")
                    .put("module", "music.search.SearchCgiService")
                    .put("param", parameters),
            )
            .put("comm", JSONObject().put("ct", 24).put("cv", 0).put("format", "json"))
            .toString()
            .toByteArray(Charsets.UTF_8)
        val response = transport.execute(
            LyricsHttpRequest(
                url = SEARCH_URL,
                method = "POST",
                headers = commonHeaders("https://c.y.qq.com/") +
                    ("Content-Type" to "application/json; charset=utf-8"),
                body = body,
            ),
        )
        val responseJson = JSONObject(response)
        val responseCode = responseJson.optInt("code", -1)
        val request = responseJson.optJSONObject("req_1")
            ?: throw IOException("QQ音乐搜索响应缺少 req_1")
        val requestCode = request.optInt("code", -1)
        if (responseCode != 0 || requestCode != 0) {
            throw IOException("QQ音乐搜索返回错误码 $responseCode/$requestCode")
        }
        val song = request.optJSONObject("data")?.optJSONObject("body")?.optJSONObject("song")
            ?: request.optJSONObject("data")?.optJSONObject("song")
            ?: return emptyList()
        val songs = song.optJSONArray("list") ?: return emptyList()
        return songs.objects().mapNotNull(::parseCandidate).toList()
    }

    private suspend fun searchLegacy(query: LyricsQuery): List<LyricsCandidate> {
        val keyword = listOf(query.title, query.artist).filter(String::isNotBlank).joinToString(" ")
        val url = "$LEGACY_SEARCH_URL?format=json&n=20&p=1&w=${keyword.urlEncode()}&cr=1&g_tk=5381"
        val response = JSONObject(
            transport.execute(
                LyricsHttpRequest(
                    url = url,
                    method = "GET",
                    headers = commonHeaders("https://y.qq.com/"),
                ),
            ),
        )
        val code = response.optInt("code", -1)
        if (code != 0) throw IOException("QQ音乐兼容搜索返回错误码 $code")
        val songs = response.optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list")
            ?: return emptyList()
        return songs.objects().mapNotNull(::parseCandidate).toList()
    }

    override suspend fun fetch(candidate: LyricsCandidate): LyricsDocument? {
        val response = transport.execute(
            LyricsHttpRequest(
                url = LYRIC_URL,
                method = "POST",
                headers = commonHeaders("https://c.y.qq.com/") +
                    ("Content-Type" to "application/x-www-form-urlencoded; charset=utf-8"),
                body = formBody(
                    mapOf(
                        "version" to "15",
                        "miniversion" to "82",
                        "lrctype" to "4",
                        "musicid" to candidate.id,
                    ),
                ),
            ),
        )
        val resultCode = qqResultCode.find(response)?.groupValues?.get(1)?.toIntOrNull()
        if (resultCode != null && resultCode != 0) throw IOException("QQ音乐歌词返回错误码 $resultCode")
        val payloads = extractQqPayloads(response)
        if (resultCode == null && payloads.isEmpty()) throw IOException("QQ音乐歌词响应格式无效")
        val original = payloads["content"]?.let(::decodeQqPayload)
            ?: payloads["Lyric_1"]?.let(::decodeQqPayload)
        val translation = payloads["contentts"]?.let(::decodeQqPayload)
        return LyricsParser.parse(id, original, translation, original)
    }

    private fun parseCandidate(json: JSONObject): LyricsCandidate? {
        val numericId = json.optLong("id", json.optLong("songid", -1L)).takeIf { it >= 0L } ?: return null
        val singers = json.optJSONArray("singer") ?: JSONArray()
        val album = json.optJSONObject("album")
        return LyricsCandidate(
            id = numericId.toString(),
            title = json.optString("title")
                .ifBlank { json.optString("name") }
                .ifBlank { json.optString("songname") },
            artists = singers.objects().map { it.optString("name") }.filter(String::isNotBlank).toList(),
            album = album?.optString("name").orEmpty().ifBlank { json.optString("albumname") },
            durationMs = json.optLong("interval", 0L) * 1_000L,
        )
    }

    private companion object {
        const val SEARCH_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg"
        const val LEGACY_SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
        const val LYRIC_URL = "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg"
        val qqResultCode = Regex("<result>\\s*(-?\\d+)\\s*</result>")
    }
}

private fun decodeQqPayload(value: String): String = unwrapQqDecodedLyrics(QqLyricsDecoder.decode(value))

internal object NetEaseWeApiEncryption {
    private val random = SecureRandom()

    fun encrypt(value: String): Map<String, String> {
        val secret = buildString(16) { repeat(16) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
        val first = aes(value, NONCE)
        val second = aes(first, secret)
        val reversedHex = secret.reversed().toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        val encryptedSecret = BigInteger(reversedHex, 16)
            .modPow(BigInteger(PUBLIC_KEY, 16), BigInteger(MODULUS, 16))
            .toString(16)
            .padStart(256, '0')
            .takeLast(256)
        return mapOf("params" to second, "encSecKey" to encryptedSecret)
    }

    private fun aes(value: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(Charsets.UTF_8)),
        )
        return Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    private const val NONCE = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val PUBLIC_KEY = "010001"
    private const val MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7" +
            "b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280" +
            "104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932" +
            "575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b" +
            "3ece0462db0a22b8e7"
    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
}

internal object QqLyricsDecoder {
    private val hexPattern = Regex("^[0-9a-fA-F]+$")

    fun decode(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.length % 2 != 0 || !hexPattern.matches(trimmed)) return trimmed
        val encrypted = ByteArray(trimmed.length / 2) { index ->
            trimmed.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        if (encrypted.size % 8 != 0) return trimmed
        val decrypted = QqQrcCipher.decrypt(encrypted)
        return inflate(decrypted).toString(Charsets.UTF_8).trimEnd('\u0000')
    }

    private fun inflate(value: ByteArray): ByteArray = runCatching {
        InflaterInputStream(ByteArrayInputStream(value)).use(::readInflatedBounded)
    }.recoverCatching {
        InflaterInputStream(ByteArrayInputStream(value), java.util.zip.Inflater(true)).use(::readInflatedBounded)
    }.getOrThrow()

    private fun readInflatedBounded(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > MAX_INFLATED_BYTES) throw IOException("解压后的歌词超过 2 MiB 限制")
            output.write(buffer, 0, count)
        }
    }

    private const val MAX_INFLATED_BYTES = 2 * 1024 * 1024
}

private fun extractQqPayloads(value: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    listOf("content", "contentts", "Lyric_1").forEach { tag ->
        extractQqElementValue(value, tag)?.takeIf(String::isNotBlank)?.let { result[tag] = it }
    }
    return result
}

private fun extractQqElementValue(value: String, tag: String): String? {
    val opening = Regex("<${Regex.escape(tag)}(?:\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(value)
        ?: return null
    val attribute = Regex("""\bLyricContent\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)
        .find(opening.value)
        ?.groupValues
        ?.get(1)
        ?.takeIf(String::isNotBlank)
    if (attribute != null) return attribute.decodeXmlEntities()

    val contentStart = opening.range.last + 1
    val contentEnd = value.indexOf("</$tag>", contentStart, ignoreCase = true)
    if (contentEnd < 0) return null
    val raw = value.substring(contentStart, contentEnd).trim()
    val text = if (raw.startsWith("<![CDATA[") && raw.endsWith("]]>") && raw.length >= 12) {
        raw.substring(9, raw.length - 3)
    } else {
        raw
    }
    return text.trim().decodeXmlEntities()
}

private fun String.decodeXmlEntities(): String = replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")

private fun unwrapQqDecodedLyrics(value: String): String {
    if (!value.contains('<')) return value
    extractQqLyricContent(value)?.let { return it }
    val payloads = extractQqPayloads(value)
    return payloads["Lyric_1"] ?: payloads["content"] ?: value
}

private fun extractQqLyricContent(value: String): String? {
    val elementStart = value.indexOf("<Lyric_1")
    if (elementStart < 0) return null
    val marker = "LyricContent=\""
    val contentStart = value.indexOf(marker, elementStart).takeIf { it >= 0 }?.plus(marker.length) ?: return null
    val lyricInfoEnd = value.indexOf("</LyricInfo>", contentStart).takeIf { it >= 0 } ?: value.length
    val elementEnd = value.lastIndexOf("/>", lyricInfoEnd).takeIf { it > contentStart } ?: return null
    val contentEnd = value.lastIndexOf('"', elementEnd).takeIf { it >= contentStart } ?: return null
    return value.substring(contentStart, contentEnd)
        .decodeXmlEntities()
}

private fun commonHeaders(referer: String): Map<String, String> = mapOf(
    "Accept" to "application/json, text/plain, */*",
    "Referer" to referer,
    "User-Agent" to "Mozilla/5.0 (Linux; Android 12) LsMusic/1.0",
)

private fun formBody(values: Map<String, String>): ByteArray = values.entries.joinToString("&") { (key, value) ->
    "${key.urlEncode()}=${value.urlEncode()}"
}.toByteArray(Charsets.UTF_8)

private fun String.urlEncode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

private suspend fun <T> requestWithFallback(
    primary: suspend () -> T,
    isUsable: (T) -> Boolean,
    fallback: suspend () -> T,
): T {
    var primaryFailure: Exception? = null
    try {
        val result = primary()
        if (isUsable(result)) return result
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        primaryFailure = error
    }
    return try {
        fallback()
    } catch (fallbackError: Exception) {
        if (fallbackError is CancellationException) throw fallbackError
        primaryFailure?.let(fallbackError::addSuppressed)
        throw fallbackError
    }
}

private fun JSONArray.objects(): Sequence<JSONObject> = sequence {
    for (index in 0 until length()) optJSONObject(index)?.let { yield(it) }
}
