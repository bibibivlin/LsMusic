package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.ui.BrowsePageKey
import com.linxyi.lsmusic.ui.BrowseLoadStatus
import com.linxyi.lsmusic.ui.BrowseScrollPosition
import com.linxyi.lsmusic.ui.BrowseViewState
import com.linxyi.lsmusic.ui.LibraryBrowseStore
import com.linxyi.lsmusic.ui.LibraryContentStatus
import com.linxyi.lsmusic.ui.LibraryPageKind
import com.linxyi.lsmusic.ui.albumArtworkRetryDelayMillis
import com.linxyi.lsmusic.ui.browseItemIndex
import com.linxyi.lsmusic.ui.directionalPrefetchRange
import com.linxyi.lsmusic.ui.directionalPrefetchIndices
import com.linxyi.lsmusic.ui.fastScrollPositionFraction
import com.linxyi.lsmusic.ui.fastScrollTargetItemIndex
import com.linxyi.lsmusic.ui.isLibrarySearchNearTop
import com.linxyi.lsmusic.ui.librarySearchReturnDuration
import com.linxyi.lsmusic.ui.matchesLibraryQuery
import com.linxyi.lsmusic.ui.resolveLibraryContentStatus
import com.linxyi.lsmusic.ui.resolveLibraryPageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBrowseStoreTest {
    @Test
    fun libraryContentStatus_keepsColdStartPhasesInOneLoadingState() {
        assertEquals(
            LibraryContentStatus.LOADING,
            resolveLibraryContentStatus(
                browseLoadStatus = BrowseLoadStatus.WAITING_FOR_DEVICE,
                isSearching = true,
                hasSelectedServer = true,
                selectedServerAvailable = false,
                visibleEntriesEmpty = true,
            ),
        )
        assertEquals(
            LibraryContentStatus.LOADING,
            resolveLibraryContentStatus(
                browseLoadStatus = BrowseLoadStatus.LOADING,
                isSearching = true,
                hasSelectedServer = true,
                selectedServerAvailable = true,
                visibleEntriesEmpty = true,
            ),
        )
    }

    @Test
    fun libraryContentStatus_showsEmptyOnlyAfterSuccessfulEmptyBrowse() {
        assertEquals(
            LibraryContentStatus.EMPTY,
            resolveLibraryContentStatus(
                browseLoadStatus = BrowseLoadStatus.LOADED,
                isSearching = false,
                hasSelectedServer = true,
                selectedServerAvailable = true,
                visibleEntriesEmpty = true,
            ),
        )
    }

    @Test
    fun libraryContentStatus_distinguishesUnavailableServerAndBrowseFailure() {
        assertEquals(
            LibraryContentStatus.SERVER_UNAVAILABLE,
            resolveLibraryContentStatus(
                browseLoadStatus = BrowseLoadStatus.WAITING_FOR_DEVICE,
                isSearching = false,
                hasSelectedServer = true,
                selectedServerAvailable = false,
                visibleEntriesEmpty = true,
            ),
        )
        assertEquals(
            LibraryContentStatus.LOAD_FAILED,
            resolveLibraryContentStatus(
                browseLoadStatus = BrowseLoadStatus.FAILED,
                isSearching = false,
                hasSelectedServer = true,
                selectedServerAvailable = true,
                visibleEntriesEmpty = true,
            ),
        )
    }

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
    fun directionalPrefetchIndices_ordersNearestItemsFirstInEitherDirection() {
        assertEquals(
            listOf(20, 21, 22, 23, 24),
            directionalPrefetchIndices(
                firstVisibleIndex = 10,
                lastVisibleIndex = 19,
                lastEntryIndex = 99,
                prefetchCount = 5,
                forward = true,
            ),
        )
        assertEquals(
            listOf(9, 8, 7, 6, 5),
            directionalPrefetchIndices(
                firstVisibleIndex = 10,
                lastVisibleIndex = 19,
                lastEntryIndex = 99,
                prefetchCount = 5,
                forward = false,
            ),
        )
    }

    @Test
    fun albumArtworkRetryDelay_stopsAfterTwoFailures() {
        assertEquals(350L, albumArtworkRetryDelayMillis(1))
        assertEquals(1_000L, albumArtworkRetryDelayMillis(2))
        assertEquals(null, albumArtworkRetryDelayMillis(0))
        assertEquals(null, albumArtworkRetryDelayMillis(3))
    }

    @Test
    fun fastScrollPosition_tracksTheScrollableItemRange() {
        assertEquals(0f, fastScrollPositionFraction(0, 20, 100), 0f)
        assertEquals(0.5f, fastScrollPositionFraction(40, 20, 100), 0f)
        assertEquals(1f, fastScrollPositionFraction(80, 20, 100), 0f)
    }

    @Test
    fun fastScrollTarget_clampsToValidFirstVisibleItem() {
        assertEquals(0, fastScrollTargetItemIndex(-1f, 20, 100))
        assertEquals(40, fastScrollTargetItemIndex(0.5f, 20, 100))
        assertEquals(80, fastScrollTargetItemIndex(2f, 20, 100))
        assertEquals(0, fastScrollTargetItemIndex(1f, 20, 10))
    }

    @Test
    fun searchButton_usesInlineSearchOnlyWithinOneViewport() {
        assertTrue(isLibrarySearchNearTop(firstVisibleIndex = 6, lastVisibleIndex = 11))
        assertFalse(isLibrarySearchNearTop(firstVisibleIndex = 7, lastVisibleIndex = 12))
        assertTrue(isLibrarySearchNearTop(firstVisibleIndex = 14, lastVisibleIndex = 27))
        assertFalse(isLibrarySearchNearTop(firstVisibleIndex = 15, lastVisibleIndex = 28))
    }

    @Test
    fun searchReturnDuration_scalesWithDistanceAndStaysBounded() {
        assertEquals(260, librarySearchReturnDuration(0f))
        assertEquals(360, librarySearchReturnDuration(400f))
        assertEquals(460, librarySearchReturnDuration(2_000f))
    }

    @Test
    fun restoringUnfilteredPosition_findsTheSameEntryAfterOrderChanges() {
        val entries = listOf(folder("first", "albums"), folder("anchor", "albums"), folder("last", "albums"))
        val position = BrowseScrollPosition(
            anchorEntryKey = "media:albums:anchor",
            fallbackItemIndex = 12,
            scrollOffset = 31,
        )

        assertEquals(4, browseItemIndex(entries, position, headerCount = 3))
        assertEquals(5, browseItemIndex(entries.reversed(), position.copy(anchorEntryKey = "media:albums:first"), 3))
        assertEquals(5, browseItemIndex(entries, position.copy(anchorEntryKey = "missing"), 3))
        assertEquals(2, browseItemIndex(emptyList(), position, 3))
    }

    @Test
    fun librarySearch_matchesTitleArtistAndAlbumArtistIgnoringCase() {
        val album = folder("id", "albums").copy(title = "Kind of Blue", creator = "Miles Davis", albumArtist = "Sextet")
        assertTrue(album.matchesLibraryQuery("BLUE"))
        assertTrue(album.matchesLibraryQuery("miles"))
        assertTrue(album.matchesLibraryQuery("SEXTET"))
        assertFalse(album.matchesLibraryQuery("other"))
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
            unfilteredPosition = BrowseScrollPosition("media:albums:album-2", 22, 15),
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
