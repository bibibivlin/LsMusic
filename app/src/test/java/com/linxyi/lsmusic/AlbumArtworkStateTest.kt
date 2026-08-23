package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.BrowsePageKey
import com.linxyi.lsmusic.ui.shouldApplyAlbumArtworkResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumArtworkStateTest {
    @Test
    fun staleResult_isRejectedAfterPageOrGenerationChanges() {
        val album = BrowsePageKey("server", "album")

        assertTrue(shouldApplyAlbumArtworkResult(album, album, 4L, 4L))
        assertFalse(
            shouldApplyAlbumArtworkResult(
                BrowsePageKey("server", "other"),
                album,
                4L,
                4L,
            ),
        )
        assertFalse(shouldApplyAlbumArtworkResult(album, album, 5L, 4L))
    }
}
