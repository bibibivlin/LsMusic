package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.AlbumSort
import com.linxyi.lsmusic.ui.ServerAlbumSortPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAlbumSortPreferencesTest {
    @Test
    fun serverAlbumSortPreferences_keepsIndependentSortsForEachServer() {
        val fixture = fixture()

        fixture.store.save("uuid:server-a", AlbumSort.YEAR_DESCENDING)
        fixture.store.save("uuid:server-b", AlbumSort.TITLE)

        assertEquals(AlbumSort.YEAR_DESCENDING, fixture.store.load("uuid:server-a"))
        assertEquals(AlbumSort.TITLE, fixture.store.load("uuid:server-b"))
    }

    @Test
    fun serverAlbumSortPreferences_serverDefaultOverwritesPreviousSort() {
        val fixture = fixture()

        fixture.store.save("uuid:server", AlbumSort.ALBUM_ARTIST)
        fixture.store.save("uuid:server", AlbumSort.SERVER_DEFAULT)

        assertEquals(AlbumSort.SERVER_DEFAULT, fixture.store.load("uuid:server"))
    }

    @Test
    fun serverAlbumSortPreferences_normalizesUdnCaseAndWhitespace() {
        val fixture = fixture()

        fixture.store.save("  UUID:ABCD-1234  ", AlbumSort.YEAR_ASCENDING)

        assertEquals(AlbumSort.YEAR_ASCENDING, fixture.store.load("uuid:abcd-1234"))
    }

    @Test
    fun serverAlbumSortPreferences_missingUnknownOrBlankValuesUseServerDefault() {
        val fixture = fixture()
        assertEquals(AlbumSort.SERVER_DEFAULT, fixture.store.load("uuid:missing"))

        fixture.store.save("uuid:corrupt", AlbumSort.TITLE)
        fixture.values[fixture.values.keys.single()] = "NOT_A_SORT"

        assertEquals(AlbumSort.SERVER_DEFAULT, fixture.store.load("uuid:corrupt"))
        assertEquals(AlbumSort.SERVER_DEFAULT, fixture.store.load("  "))

        fixture.store.save("", AlbumSort.TITLE)
        assertTrue(fixture.values.size == 1)
    }

    private fun fixture(): Fixture {
        val values = mutableMapOf<String, String>()
        return Fixture(
            values = values,
            store = ServerAlbumSortPreferences(
                readValue = values::get,
                writeValue = values::set,
            ),
        )
    }

    private data class Fixture(
        val values: MutableMap<String, String>,
        val store: ServerAlbumSortPreferences,
    )
}
