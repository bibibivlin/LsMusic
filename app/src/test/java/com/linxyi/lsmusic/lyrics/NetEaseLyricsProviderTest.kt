package com.linxyi.lsmusic.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetEaseLyricsProviderTest {
    private val query = LyricsQuery("晴天", "周杰伦", "叶惠美", 269_000L)

    @Test
    fun searchUsesAnonymousSearchEndpoint() = runBlocking {
        val transport = QueueTransport(
            """
                {"code":200,"result":{"songCount":1,"songs":[
                  {"id":186016,"name":"晴天","artists":[{"name":"周杰伦"}],
                   "album":{"name":"叶惠美"},"duration":269000}
                ]}}
            """.trimIndent(),
        )

        val candidates = NetEaseLyricsProvider(transport).search(query)

        assertEquals(1, candidates.size)
        assertEquals("186016", candidates.single().id)
        assertTrue(transport.requests.single().url.endsWith("/weapi/search/get"))
        assertFalse(transport.requests.single().headers.containsKey("Cookie"))
    }

    @Test
    fun searchFallsBackToHttpsLegacyEndpointWhenPrimaryRequiresLogin() = runBlocking {
        val transport = QueueTransport(
            """{"code":50000005}""",
            """
                {"code":200,"result":{"songCount":1,"songs":[
                  {"id":186016,"name":"晴天","artists":[{"name":"周杰伦"}],
                   "album":{"name":"叶惠美"},"duration":269000}
                ]}}
            """.trimIndent(),
        )

        val candidates = NetEaseLyricsProvider(transport).search(query)

        assertEquals(1, candidates.size)
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[1].url.startsWith("https://music.163.com/api/search/get/"))
        assertFalse(transport.requests[1].headers.containsKey("Cookie"))
    }

    @Test
    fun searchFallsBackWhenPrimarySuccessfullyReturnsNoMatch() = runBlocking {
        val transport = QueueTransport(
            """{"code":200,"result":{"songCount":0,"songs":[]}}""",
            """
                {"code":200,"result":{"songCount":1,"songs":[
                  {"id":186016,"name":"晴天","artists":[{"name":"周杰伦"}],
                   "album":{"name":"叶惠美"},"duration":269000}
                ]}}
            """.trimIndent(),
        )

        val candidates = NetEaseLyricsProvider(transport).search(query)

        assertEquals("186016", candidates.single().id)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun lyricRequestWorksWithoutAccountCookie() = runBlocking {
        val transport = QueueTransport(
            """{"code":200,"lrc":{"lyric":"[00:01.00]晴天"},"tlyric":{"lyric":"[00:01.00]Sunny day"}}""",
        )
        val candidate = LyricsCandidate(
            id = "186016",
            title = "晴天",
            artists = listOf("周杰伦"),
            album = "叶惠美",
            durationMs = 269_000L,
        )

        val document = NetEaseLyricsProvider(transport).fetch(candidate)

        assertNotNull(document)
        assertEquals("晴天", document?.lines?.single()?.original)
        assertEquals("Sunny day", document?.lines?.single()?.translation)
        assertTrue(transport.requests.single().url.contains("/weapi/song/lyric"))
        assertFalse(transport.requests.single().headers.containsKey("Cookie"))
    }

    @Test
    fun lyricFallsBackWithoutAnyCookie() = runBlocking {
        val transport = QueueTransport(
            """{"code":50000005}""",
            """{"code":200,"lrc":{"lyric":"[00:01.00]晴天"}}""",
        )
        val candidate = LyricsCandidate(
            id = "186016",
            title = "晴天",
            artists = listOf("周杰伦"),
            album = "叶惠美",
            durationMs = 269_000L,
        )

        val document = NetEaseLyricsProvider(transport).fetch(candidate)

        assertNotNull(document)
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[1].url.startsWith("https://music.163.com/api/song/lyric"))
        assertFalse(transport.requests[1].headers.containsKey("Cookie"))
    }

    @Test
    fun lyricFallsBackWhenPrimaryReturnsNoUsableText() = runBlocking {
        val transport = QueueTransport(
            """{"code":200,"lrc":{"lyric":""}}""",
            """{"code":200,"lrc":{"lyric":"[00:01.00]晴天"}}""",
        )
        val candidate = LyricsCandidate(
            id = "186016",
            title = "晴天",
            artists = listOf("周杰伦"),
            album = "叶惠美",
            durationMs = 269_000L,
        )

        val document = NetEaseLyricsProvider(transport).fetch(candidate)

        assertEquals("晴天", document?.lines?.single()?.original)
        assertEquals(2, transport.requests.size)
    }

    private class QueueTransport(vararg responses: String) : LyricsHttpTransport {
        private val queuedResponses = ArrayDeque(responses.toList())
        val requests = mutableListOf<LyricsHttpRequest>()

        override suspend fun execute(request: LyricsHttpRequest): String {
            requests += request
            return queuedResponses.removeFirst()
        }
    }
}
