package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.ui.PlaybackOrder
import com.linxyi.lsmusic.ui.RepeatMode
import com.linxyi.lsmusic.ui.isConfirmedLocalRepeatTransition
import com.linxyi.lsmusic.ui.selectNextTrack
import com.linxyi.lsmusic.ui.toggleShuffle
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOrderTest {
    private val queue = (1..4).map { index ->
        MediaEntry(
            id = "track-$index",
            parentId = "album",
            title = "Track $index",
            resourceUri = "https://media.example/$index.flac",
            isContainer = false,
        )
    }

    @Test
    fun shuffle_doesNotRepeatUntilEveryTrackHasPlayed() {
        var index = 0
        var order = PlaybackOrder().toggleShuffle(queue[index].id)
        val played = mutableListOf(queue[index].id)

        repeat(queue.size - 1) {
            val selection = requireNotNull(selectNextTrack(queue, index, order, automatic = true, Random(7)))
            index = selection.index
            order = selection.order
            played += queue[index].id
        }

        assertEquals(queue.size, played.distinct().size)
        assertNull(selectNextTrack(queue, index, order, automatic = true, Random(7)))
    }

    @Test
    fun shuffle_newQueueTrackRemainsEligible() {
        val playedOrder = PlaybackOrder(
            shuffleEnabled = true,
            shuffledTrackIds = queue.take(3).mapTo(mutableSetOf()) { it.id },
        )

        val selection = requireNotNull(
            selectNextTrack(queue, currentIndex = 2, playedOrder, automatic = true, Random(1)),
        )

        assertEquals(3, selection.index)
    }

    @Test
    fun togglingShuffle_resetsPlayedTracks() {
        val enabled = PlaybackOrder().toggleShuffle(queue[0].id).copy(
            shuffledTrackIds = queue.mapTo(mutableSetOf()) { it.id },
        )

        val enabledAgain = enabled.toggleShuffle(queue[0].id).toggleShuffle(queue[1].id)

        assertTrue(enabledAgain.shuffleEnabled)
        assertEquals(setOf(queue[1].id), enabledAgain.shuffledTrackIds)
    }

    @Test
    fun repeatOne_replaysCurrentTrackOnlyForAutomaticAdvance() {
        val order = PlaybackOrder(repeatMode = RepeatMode.ONE)

        assertEquals(1, selectNextTrack(queue, 1, order, automatic = true)?.index)
        assertEquals(2, selectNextTrack(queue, 1, order, automatic = false)?.index)
    }

    @Test
    fun repeatAll_wrapsSequentialQueueAndStartsNewShuffleCycle() {
        val sequential = PlaybackOrder(repeatMode = RepeatMode.ALL)
        assertEquals(0, selectNextTrack(queue, queue.lastIndex, sequential, automatic = true)?.index)

        val shuffled = PlaybackOrder(
            repeatMode = RepeatMode.ALL,
            shuffleEnabled = true,
            shuffledTrackIds = queue.mapTo(mutableSetOf()) { it.id },
        )
        val selection = requireNotNull(
            selectNextTrack(queue, queue.lastIndex, shuffled, automatic = true, Random(3)),
        )
        assertTrue(selection.index != queue.lastIndex)
    }

    @Test
    fun initialPlayerRepeatCallback_doesNotSkipFirstTrackBeforePlaybackIsReady() {
        assertFalse(
            isConfirmedLocalRepeatTransition(
                currentTrackId = queue.first().id,
                playbackReadyTrackId = null,
                transitionedTrackId = queue.first().id,
            ),
        )
        assertTrue(
            isConfirmedLocalRepeatTransition(
                currentTrackId = queue.first().id,
                playbackReadyTrackId = queue.first().id,
                transitionedTrackId = queue.first().id,
            ),
        )
    }
}
