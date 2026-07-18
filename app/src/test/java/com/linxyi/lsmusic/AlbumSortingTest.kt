package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.parseAlbumYear
import com.linxyi.lsmusic.ui.AlbumSort
import com.linxyi.lsmusic.ui.sortedAlbums
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AlbumSortingTest {
    @Test
    fun parseAlbumYear_readsYearFromDlnaDateAndRejectsInvalidValues() {
        assertEquals(2024, parseAlbumYear("2024-07-18"))
        assertEquals(1988, parseAlbumYear("released 1988"))
        assertEquals(null, parseAlbumYear("unknown"))
        assertEquals(null, parseAlbumYear("3024-01-01"))
    }

    @Test
    fun serverDefault_preservesServerOrderAndListInstance() {
        val albums = listOf(album("2", "Second"), album("1", "First"))

        val sorted = albums.sortedAlbums(AlbumSort.SERVER_DEFAULT)

        assertSame(albums, sorted)
        assertEquals(listOf("Second", "First"), sorted.map { it.title })
    }

    @Test
    fun yearSort_supportsBothDirectionsAndKeepsMissingYearsLast() {
        val albums = listOf(
            album("unknown", "Unknown year"),
            album("new", "New", year = 2024),
            album("old", "Old", year = 1988),
        )

        assertEquals(
            listOf("Old", "New", "Unknown year"),
            albums.sortedAlbums(AlbumSort.YEAR_ASCENDING).map { it.title },
        )
        assertEquals(
            listOf("New", "Old", "Unknown year"),
            albums.sortedAlbums(AlbumSort.YEAR_DESCENDING).map { it.title },
        )
    }

    @Test
    fun albumArtistSort_usesAlbumArtistAndKeepsMissingArtistsLast() {
        val albums = listOf(
            album("missing", "Unknown artist"),
            album("beta", "Beta album", albumArtist = "Beta"),
            album("alpha", "Alpha album", albumArtist = "Alpha"),
        )

        assertEquals(
            listOf("Alpha album", "Beta album", "Unknown artist"),
            albums.sortedAlbums(AlbumSort.ALBUM_ARTIST).map { it.title },
        )
    }

    @Test
    fun titleSort_groupsPrefixesAndUsesPinyinForChinese() {
        val albums = listOf(
            album("ja", "あさ"),
            album("zh-b", "北京"),
            album("other", "Élan"),
            album("en-b", "beta"),
            album("number", "12 Songs"),
            album("zh-a", "阿里"),
            album("symbol", "# Hits"),
            album("en-a", "Apple"),
        ).sortedAlbums(AlbumSort.TITLE)

        assertEquals(setOf("12 Songs", "# Hits"), albums.take(2).map { it.title }.toSet())
        assertEquals(listOf("Apple", "beta"), albums.slice(2..3).map { it.title })
        assertEquals(listOf("阿里", "北京"), albums.slice(4..5).map { it.title })
        assertEquals(setOf("Élan", "あさ"), albums.takeLast(2).map { it.title }.toSet())
    }

    private fun album(
        id: String,
        title: String,
        albumArtist: String = "",
        year: Int? = null,
    ) = MediaEntry(
        id = id,
        parentId = "albums",
        title = title,
        albumArtist = albumArtist,
        year = year,
        isContainer = true,
        isAlbum = true,
    )
}
