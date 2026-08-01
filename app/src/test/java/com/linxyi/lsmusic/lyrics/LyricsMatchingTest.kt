package com.linxyi.lsmusic.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsMatchingTest {
    private val query = LyricsQuery("Song（Live）", "Artist A", "Album", 180_000L)

    @Test
    fun normalizationHandlesUnicodeCaseSpacingAndPunctuation() {
        assertEquals("song1", "Ｓｏｎｇ １".let(::normalizeLyricsMatchText))
        assertEquals("helloworld", normalizeLyricsMatchText("Hello， WORLD!"))
    }

    @Test
    fun exactTitleRequiresArtistAndRejectsDurationOutsideTolerance() {
        assertTrue(LyricsCandidateMatcher.score(query, candidate("Song（Live）", 183_999L)) != null)
        assertNull(LyricsCandidateMatcher.score(query, candidate("Song（Live）", 184_001L)))
        assertNull(LyricsCandidateMatcher.score(query, candidate("Song（Live）", 180_000L, artists = listOf("Other"))))
    }

    @Test
    fun versionStrippedTitleAlsoRequiresAlbumAndComparableDuration() {
        assertTrue(LyricsCandidateMatcher.score(query, candidate("Song", 180_000L)) != null)
        assertNull(LyricsCandidateMatcher.score(query, candidate("Song", 180_000L, album = "Different")))
        assertNull(LyricsCandidateMatcher.score(query, candidate("Song", 0L)))
    }

    @Test
    fun missingArtistRequiresTitleAlbumAndDurationTogether() {
        val noArtist = LyricsQuery("Song", "", "Album", 180_000L)
        assertTrue(LyricsCandidateMatcher.score(noArtist, candidate("Song", 180_000L, artists = emptyList())) != null)
        assertNull(LyricsCandidateMatcher.score(noArtist, candidate("Song (Live)", 180_000L)))
        assertNull(LyricsCandidateMatcher.score(noArtist.copy(album = ""), candidate("Song", 180_000L)))
        assertNull(LyricsCandidateMatcher.score(noArtist.copy(durationMs = 0L), candidate("Song", 180_000L)))
    }

    @Test
    fun bestKeepsProviderResultOrderWhenConfidenceTies() {
        val first = candidate("Song（Live）", 180_000L).copy(id = "first")
        val second = first.copy(id = "second")
        assertEquals("first", LyricsCandidateMatcher.best(query, listOf(first, second))?.id)
    }

    private fun candidate(
        title: String,
        durationMs: Long,
        artists: List<String> = listOf("Artist A"),
        album: String = "Album",
    ) = LyricsCandidate("id", title = title, artists = artists, album = album, durationMs = durationMs)
}
