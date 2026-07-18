package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.ui.BrowsePageKey
import com.linxyi.lsmusic.ui.BrowseViewState
import com.linxyi.lsmusic.ui.LibraryBrowseStore
import com.linxyi.lsmusic.ui.LibraryPageKind
import com.linxyi.lsmusic.ui.directionalPrefetchRange
import com.linxyi.lsmusic.ui.resolveLibraryPageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBrowseStoreTest {
    @Test
    fun pageKind_keepsKnownAlbumWhileItsTracksAreLoading() {
        assertEquals(
            LibraryPageKind.ALBUM,
            resolveLibraryPageKind(LibraryPageKind.ALBUM, entries = null),
        )
    }

    @Test
    fun pageKind_resolvesUnknownTrackContainerAsAlbum() {
        assertEquals(
            LibraryPageKind.ALBUM,
            resolveLibraryPageKind(
                LibraryPageKind.RESOLVING,
                listOf(track("track-1", "album"), track("track-2", "album")),
            ),
        )
    }

    @Test
    fun pageKind_resolvesUnknownMixedContainerAsDirectory() {
        assertEquals(
            LibraryPageKind.DIRECTORY,
            resolveLibraryPageKind(
                LibraryPageKind.RESOLVING,
                listOf(folder("disc-1", "album"), track("track-1", "album")),
            ),
        )
    }

    @Test
    fun pageKind_keepsExplicitEmptyAlbumAsAlbum() {
        assertEquals(
            LibraryPageKind.ALBUM,
            resolveLibraryPageKind(LibraryPageKind.ALBUM, emptyList()),
        )
        assertEquals(
            LibraryPageKind.DIRECTORY,
            resolveLibraryPageKind(LibraryPageKind.RESOLVING, emptyList()),
        )
    }

    @Test
    fun directionalPrefetchRange_prefetchesAheadAndStopsAtLibraryEnd() {
        assertEquals(
            20..29,
            directionalPrefetchRange(
                firstVisibleIndex = 10,
                lastVisibleIndex = 19,
                lastEntryIndex = 99,
                prefetchCount = 10,
                forward = true,
            ),
        )
        assertTrue(
            directionalPrefetchRange(
                firstVisibleIndex = 95,
                lastVisibleIndex = 99,
                lastEntryIndex = 99,
                prefetchCount = 10,
                forward = true,
            ).isEmpty(),
        )
    }

    @Test
    fun directionalPrefetchRange_prefetchesBehindAndStopsAtLibraryStart() {
        assertEquals(
            5..9,
            directionalPrefetchRange(
                firstVisibleIndex = 10,
                lastVisibleIndex = 19,
                lastEntryIndex = 99,
                prefetchCount = 5,
                forward = false,
            ),
        )
        assertTrue(
            directionalPrefetchRange(
                firstVisibleIndex = 0,
                lastVisibleIndex = 4,
                lastEntryIndex = 99,
                prefetchCount = 10,
                forward = false,
            ).isEmpty(),
        )
    }

    @Test
    fun emptyDirectory_isStoredAsLoadedContent() {
        val store = LibraryBrowseStore()
        val key = BrowsePageKey("server", "empty")

        assertNull(store.page(key)?.entries)

        store.storeEntries(key, emptyList())

        assertEquals(emptyList<MediaEntry>(), store.page(key)?.entries)
    }

    @Test
    fun entriesAndViewState_areUpdatedWithoutDiscardingEachOther() {
        val store = LibraryBrowseStore()
        val key = BrowsePageKey("server", "albums")
        val entries = listOf(folder("album-1", "albums"))
        val viewState = BrowseViewState(
            query = "Miles",
            useGrid = false,
            anchorEntryKey = "media:albums:album-1",
            fallbackItemIndex = 18,
            scrollOffset = 42,
        )

        store.storeEntries(key, entries)
        store.storeViewState(key, viewState)

        assertEquals(entries, store.page(key)?.entries)
        assertEquals(viewState, store.page(key)?.viewState)

        val refreshedEntries = entries + folder("album-2", "albums")
        store.storeEntries(key, refreshedEntries)

        assertEquals(refreshedEntries, store.page(key)?.entries)
        assertEquals(viewState, store.page(key)?.viewState)
    }

    @Test
    fun requestGeneration_rejectsResultsFromAnOlderBrowse() {
        val store = LibraryBrowseStore()
        val key = BrowsePageKey("server", "albums")

        val first = store.beginRequest(key)
        val second = store.beginRequest(key)

        assertFalse(store.isLatestRequest(key, first))
        assertTrue(store.isLatestRequest(key, second))
    }

    @Test
    fun trim_keepsAncestorsAndMostRecentUnpinnedPages() {
        val store = LibraryBrowseStore(maxUnpinnedPages = 1)
        val root = BrowsePageKey("server", "0")
        val parent = BrowsePageKey("server", "parent")
        val oldSibling = BrowsePageKey("server", "old")
        val recentSibling = BrowsePageKey("server", "recent")
        listOf(root, parent, oldSibling, recentSibling).forEach {
            store.storeEntries(it, emptyList())
        }

        store.trim(setOf(root, parent))

        assertEquals(setOf(root, parent, recentSibling), store.cachedKeys())
    }

    private fun folder(id: String, parentId: String) = MediaEntry(
        id = id,
        parentId = parentId,
        title = id,
        isContainer = true,
    )

    private fun track(id: String, parentId: String) = MediaEntry(
        id = id,
        parentId = parentId,
        title = id,
        isContainer = false,
    )
}
