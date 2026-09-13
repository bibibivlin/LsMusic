package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.ui.QueueItem
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
    }.map { QueueItem(it.id, it) }

    @Test
    fun shuffle_doesNotRepeatUntilEveryTrackHasPlayed() {
        var index = 0
        var order = PlaybackOrder().toggleShuffle(queue[index].queueId)
        val played = mutableListOf(queue[index].queueId)

        repeat(queue.size - 1) {
            val selection = requireNotNull(selectNextTrack(queue, index, order, automatic = true, Random(7)))
            index = selection.index
            order = selection.order
            played += queue[index].queueId
        }

        assertEquals(queue.size, played.distinct().size)
        assertNull(selectNextTrack(queue, index, order, automatic = true, Random(7)))
    }

    @Test
    fun shuffle_newQueueTrackRemainsEligible() {
        val playedOrder = PlaybackOrder(
            shuffleEnabled = true,
            shuffledQueueIds = queue.take(3).mapTo(mutableSetOf()) { it.queueId },
        )

        val selection = requireNotNull(
            selectNextTrack(queue, currentIndex = 2, playedOrder, automatic = true, Random(1)),
        )

        assertEquals(3, selection.index)
    }

    @Test
    fun togglingShuffle_resetsPlayedTracks() {
        val enabled = PlaybackOrder().toggleShuffle(queue[0].queueId).copy(
            shuffledQueueIds = queue.mapTo(mutableSetOf()) { it.queueId },
        )

        val enabledAgain = enabled.toggleShuffle(queue[0].queueId).toggleShuffle(queue[1].queueId)

        assertTrue(enabledAgain.shuffleEnabled)
        assertEquals(setOf(queue[1].queueId), enabledAgain.shuffledQueueIds)
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
            shuffledQueueIds = queue.mapTo(mutableSetOf()) { it.queueId },
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
                currentTrackId = queue.first().queueId,
                playbackReadyTrackId = null,
                transitionedTrackId = queue.first().queueId,
            ),
        )
        assertTrue(
            isConfirmedLocalRepeatTransition(
                currentTrackId = queue.first().queueId,
                playbackReadyTrackId = queue.first().queueId,
                transitionedTrackId = queue.first().queueId,
            ),
        )
    }
}
