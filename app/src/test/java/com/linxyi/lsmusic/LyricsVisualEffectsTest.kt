package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.lyricsStaggerOffsetDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsVisualEffectsTest {
    @Test
    fun staggerAlwaysMovesInOneDirectionAndStopsGrowingAtTheLimit() {
        val offsets = (0..6).map(::lyricsStaggerOffsetDp)

        assertEquals(listOf(0f, 4f, 8f, 12f, 12f, 12f, 12f), offsets)
        assertTrue(offsets.zipWithNext().all { (first, second) -> second >= first })
    }
}
