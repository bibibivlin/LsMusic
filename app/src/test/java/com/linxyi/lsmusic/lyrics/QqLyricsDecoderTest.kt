package com.linxyi.lsmusic.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class QqLyricsDecoderTest {
    @Test
    fun qrcDecryptsRealQqResponseFixture() {
        val encrypted = checkNotNull(javaClass.getResource("/lyrics/qq_qrc_186016.hex"))
            .readText()
            .trim()

        val decoded = QqLyricsDecoder.decode(encrypted)

        assertTrue(decoded.startsWith("<?xml"))
        assertTrue(decoded.contains("<QrcInfos>"))
        assertTrue(decoded.contains("LyricContent=\""))
    }

    @Test
    fun providerParsesRealQqXmlAndQrcFixture() = runBlocking {
        val encrypted = fixture()
        val response = """
            <!--
            <command-lable-xwl78-qq-music>
            <cmd value="1031" verson="4"><miniversion="1" /><result>0</result><reason>success</reason>
            <lyric><content type="file"><![CDATA[$encrypted]]></content><contentts><![CDATA[]]></contentts></lyric>
            </cmd></command-lable-xwl78-qq-music>
            -->
        """.trimIndent()
        val provider = QqLyricsProvider(LyricsHttpTransport { response })
        val candidate = LyricsCandidate(
            id = "186016",
            title = "fixture",
            artists = listOf("fixture"),
            album = "fixture",
            durationMs = 180_000L,
        )

        val document = provider.fetch(candidate)

        assertNotNull(document)
        assertTrue(document?.lines?.isNotEmpty() == true)
        assertTrue(document?.hasWordTiming == true)
    }

    @Test
    fun searchFallsBackWhenMusicuReturnsEmptyForNocturne() = runBlocking {
        val transport = QueueTransport(
            """{"code":0,"req_1":{"code":0,"data":{"body":{"song":{"list":[]}}}}}""",
            """
                {"code":0,"data":{"song":{"list":[{
                  "songid":718477,"songmid":"001zMQr71F1Qo8","songname":"夜曲",
                  "albumname":"十一月的萧邦","interval":226,
                  "singer":[{"id":4558,"mid":"0025NhlN2yWrP4","name":"周杰伦"}]
                }]}}}
            """.trimIndent(),
        )

        val query = LyricsQuery("夜曲", "周杰伦", "十一月的萧邦", 226_000L)
        val candidates = QqLyricsProvider(transport).search(query)

        assertEquals(1, candidates.size)
        assertEquals("718477", candidates.single().id)
        assertEquals("夜曲", candidates.single().title)
        assertEquals("十一月的萧邦", candidates.single().album)
        assertEquals("718477", LyricsCandidateMatcher.best(query, candidates)?.id)
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[1].url.startsWith("https://c.y.qq.com/soso/fcgi-bin/client_search_cp"))
        assertFalse(transport.requests[1].headers.containsKey("Cookie"))
    }

    @Test
    fun searchFallsBackWhenMusicuCandidatesDoNotMatch() = runBlocking {
        val transport = QueueTransport(
            """{"code":0,"req_1":{"code":0,"data":{"body":{"song":{"list":[{
              "id":1,"mid":"unrelated","title":"另一首歌","interval":226,
              "album":{"name":"十一月的萧邦"},"singer":[{"name":"周杰伦"}]
            }]}}}}}""",
            """
                {"code":0,"data":{"song":{"list":[{
                  "songid":718477,"songmid":"001zMQr71F1Qo8","songname":"夜曲",
                  "albumname":"十一月的萧邦","interval":226,
                  "singer":[{"name":"周杰伦"}]
                }]}}}
            """.trimIndent(),
        )
        val query = LyricsQuery("夜曲", "周杰伦", "十一月的萧邦", 226_000L)

        val candidates = QqLyricsProvider(transport).search(query)

        assertEquals("718477", LyricsCandidateMatcher.best(query, candidates)?.id)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun decoderLeavesPlainLrcUntouched() {
        val source = "[00:01.00]plain lyrics"
        assertEquals(source, QqLyricsDecoder.decode(source))
    }

    @Test
    fun providerRejectsUnrecognizedSuccessResponseInsteadOfNegativeCachingIt() = runBlocking {
        val provider = QqLyricsProvider(LyricsHttpTransport { "<html>temporarily blocked</html>" })
        val candidate = LyricsCandidate(
            id = "186016",
            title = "fixture",
            artists = listOf("fixture"),
            album = "fixture",
            durationMs = 180_000L,
        )

        try {
            provider.fetch(candidate)
            fail("Expected an invalid response failure")
        } catch (error: java.io.IOException) {
            assertTrue(error.message.orEmpty().contains("响应格式无效"))
        }
    }

    private fun fixture(): String = checkNotNull(javaClass.getResource("/lyrics/qq_qrc_186016.hex"))
        .readText()
        .trim()

    private class QueueTransport(vararg responses: String) : LyricsHttpTransport {
        private val queuedResponses = ArrayDeque(responses.toList())
        val requests = mutableListOf<LyricsHttpRequest>()

        override suspend fun execute(request: LyricsHttpRequest): String {
            requests += request
            return queuedResponses.removeFirst()
        }
    }
}
