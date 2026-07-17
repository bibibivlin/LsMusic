package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.ui.LsMusicUiState
import com.linxyi.lsmusic.ui.LsMusicViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LsMusicUiStateTest {
    private val track = MediaEntry(
        id = "track-1",
        parentId = "0",
        title = "Soft Focus",
        resourceUri = "http://media.example/soft-focus.flac",
        isContainer = false,
    )

    @Test
    fun currentTrack_readsSelectedQueueItem() {
        assertEquals(track, LsMusicUiState(queue = listOf(track), currentQueueIndex = 0).currentTrack)
    }

    @Test
    fun currentTrack_isNullForEmptyQueue() {
        assertNull(LsMusicUiState().currentTrack)
    }

    @Test
    fun parseTimeMs_supportsDlnaFractionalDuration() {
        assertEquals(229_009L, LsMusicViewModel.parseTimeMs("0:03:49.009"))
        assertEquals(62_000L, LsMusicViewModel.parseTimeMs("01:02"))
        assertEquals(0L, LsMusicViewModel.parseTimeMs("NOT_IMPLEMENTED"))
    }
}
