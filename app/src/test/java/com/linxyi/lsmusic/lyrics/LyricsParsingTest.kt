package com.linxyi.lsmusic.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParsingTest {
    @Test
    fun lrc_supportsMultipleTimestampsOffsetAndMetadata() {
        val document = requireNotNull(
            LyricsParser.parse(
                provider = LyricsProviderId.NETEASE,
                original = """
                    [ar:Artist]
                    [offset:+120]
                    [00:01.50][00:03.250]同一句
                    [00:05.0]下一句
                """.trimIndent(),
            ),
        )

        assertEquals(listOf(1_620L, 3_370L, 5_120L), document.lines.map { it.startMs })
        assertEquals(listOf("同一句", "同一句", "下一句"), document.lines.map { it.original })
        assertEquals(1_750L, document.lines.first().durationMs)
    }

    @Test
    fun lrc_withoutTimestampsRemainsStaticAndIgnoresMalformedRows() {
        val document = requireNotNull(
            LyricsParser.parse(
                provider = LyricsProviderId.QQ,
                original = "第一行\n[broken:metadata]\n第二行\n[00:xx]坏行",
            ),
        )

        assertFalse(document.isSynced)
        assertEquals(listOf("第一行", "第二行"), document.lines.map { it.original })
        assertTrue(document.lines.all { it.startMs == null })
        assertNull(LyricsParser.parse(LyricsProviderId.QQ, "[ar:x]\n[00:01.00]"))
    }

    @Test
    fun yrc_parsesWordTimingAndAlignsTranslation() {
        val document = requireNotNull(
            LyricsParser.parse(
                provider = LyricsProviderId.NETEASE,
                original = "[00:01.00]Hello world",
                translation = "[00:01.20]你好世界",
                verbatim = "[1000,1800](1000,500,0)Hello (1500,700,0)world",
            ),
        )

        val line = document.lines.single()
        assertEquals(1_000L, line.startMs)
        assertEquals(listOf("Hello ", "world"), line.words.map { it.text })
        assertEquals("你好世界", line.translation)
        assertTrue(document.hasWordTiming)
    }

    @Test
    fun qrc_supportsTimestampsAfterEachWord() {
        val document = requireNotNull(
            LyricsParser.parse(
                provider = LyricsProviderId.QQ,
                original = null,
                verbatim = "[4000,1200]你(4000,500)好(4500,700)",
            ),
        )

        assertEquals("你好", document.lines.single().original)
        assertEquals(listOf(4_000L, 4_500L), document.lines.single().words.map { it.startMs })
    }

    @Test
    fun activeLineUsesLastTimestampAtOrBeforePosition() {
        val lines = listOf(
            LyricsLine("a", 1_000L, original = "a"),
            LyricsLine("b", 2_000L, original = "b"),
            LyricsLine("c", 4_000L, original = "c"),
        )

        assertEquals(-1, activeLyricsLineIndex(lines, 999L))
        assertEquals(1, activeLyricsLineIndex(lines, 3_500L))
        assertEquals(2, activeLyricsLineIndex(lines, 9_000L))
    }

    @Test
    fun wordSweepAndPlaybackInterpolationRespectTimingPauseAndDuration() {
        val line = LyricsLine(
            stableId = "line",
            startMs = 1_000L,
            original = "abcd",
            words = listOf(
                TimedWord("ab", 1_000L, 1_000L),
                TimedWord("cd", 2_000L, 1_000L),
            ),
        )
        assertEquals(.25f, wordSweepProgress(line, 1_500L), .001f)
        assertEquals(.75f, wordSweepProgress(line, 2_500L), .001f)
        assertEquals(5_000L, interpolatedLyricsPosition(5_000L, 900L, false, 10_000L))
        assertEquals(5_900L, interpolatedLyricsPosition(5_000L, 900L, true, 10_000L))
        assertEquals(10_000L, interpolatedLyricsPosition(9_800L, 900L, true, 10_000L))
    }

    @Test
    fun translationModesApplyFallbackRulesWithoutReloading() {
        val translated = LyricsLine("a", 0L, original = "Original", translation = "中文")
        val originalOnly = LyricsLine("b", 0L, original = "Original")

        assertEquals("Original" to null, translated.displayTexts(LyricsTranslationMode.ORIGINAL))
        assertEquals("Original" to "中文", translated.displayTexts(LyricsTranslationMode.BILINGUAL))
        assertEquals("中文" to null, translated.displayTexts(LyricsTranslationMode.CHINESE_ONLY))
        assertEquals("Original" to null, originalOnly.displayTexts(LyricsTranslationMode.CHINESE_ONLY))
    }

    @Test
    fun duplicateTimestampRowsStillReceiveStableUniqueKeys() {
        val document = requireNotNull(
            LyricsParser.parse(
                LyricsProviderId.NETEASE,
                "[00:01.00]Repeat\n[00:01.00]Repeat",
            ),
        )
        assertEquals(2, document.lines.map { it.stableId }.distinct().size)
    }
}
