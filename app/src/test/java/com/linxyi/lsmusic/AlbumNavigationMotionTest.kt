package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.AlbumNavigationMotion
import com.linxyi.lsmusic.ui.BrowseLocation
import com.linxyi.lsmusic.ui.BrowsePageKey
import com.linxyi.lsmusic.ui.LibraryPageKind
import com.linxyi.lsmusic.ui.albumNavigationMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumNavigationMotionTest {
    private val library = BrowsePageKey("server", "albums")
    private val album = BrowsePageKey("server", "album-12")
    private val parentPath = listOf(BrowseLocation("root"), BrowseLocation("albums"))
    private val albumPath = parentPath + BrowseLocation("album-12", pageKind = LibraryPageKind.ALBUM)

    @Test
    fun openingAndReturningShareTheSameAlbumIdentity() {
        assertEquals(
            AlbumNavigationMotion(album, opening = true),
            albumNavigationMotion(library, parentPath, album, albumPath),
        )
        assertEquals(
            AlbumNavigationMotion(album, opening = false),
            albumNavigationMotion(album, albumPath, library, parentPath),
        )
    }

    @Test
    fun loadingOrArtworkUpdatesOnTheSamePageDoNotStartNavigation() {
        assertNull(albumNavigationMotion(album, albumPath, album, albumPath))
        assertNull(albumNavigationMotion(
            album,
            parentPath + BrowseLocation("album-12", pageKind = LibraryPageKind.RESOLVING),
            album,
            albumPath,
        ))
    }

    @Test
    fun identicalObjectIdsOnDifferentServersCannotShareArtwork() {
        assertNull(albumNavigationMotion(library, parentPath, album.copy(serverId = "other"), albumPath))
        assertNull(albumNavigationMotion(
            library.copy(serverId = ""), parentPath, album.copy(serverId = ""), albumPath,
        ))
    }

    @Test
    fun siblingFoldersAndSkippedAncestorsDoNotCreateACoverFlight() {
        assertNull(albumNavigationMotion(
            library, listOf(BrowseLocation("root"), BrowseLocation("other")), album, albumPath,
        ))
        assertNull(albumNavigationMotion(BrowsePageKey("server", "root"), parentPath.take(1), album, albumPath))
        assertNull(albumNavigationMotion(
            album, albumPath, BrowsePageKey("server", "album-13"),
            parentPath + BrowseLocation("album-13", pageKind = LibraryPageKind.ALBUM),
        ))
    }

    @Test
    fun ordinaryFoldersUseThePageFadeWhileAlbumMetadataChangesKeepTheMatch() {
        assertNull(albumNavigationMotion(
            library, parentPath, album, parentPath + BrowseLocation("album-12"),
        ))
        assertEquals(
            AlbumNavigationMotion(album, opening = true),
            albumNavigationMotion(
                library, parentPath.map { it.copy(title = "Old name") },
                album, albumPath.map { it.copy(title = "Updated name") },
            ),
        )
    }
}
