package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.shouldShowLyricsBesideArtwork
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLayoutTest {
    @Test
    fun sideBySideRequiresWidthHeightAndAWideEnoughAspectRatio() {
        assertFalse(shouldShowLyricsBesideArtwork(widthDp = 800f, heightDp = 800f))
        assertFalse(shouldShowLyricsBesideArtwork(widthDp = 1000f, heightDp = 500f))
        assertFalse(shouldShowLyricsBesideArtwork(widthDp = 700f, heightDp = 560f))
        assertTrue(shouldShowLyricsBesideArtwork(widthDp = 1000f, heightDp = 700f))
    }
}
