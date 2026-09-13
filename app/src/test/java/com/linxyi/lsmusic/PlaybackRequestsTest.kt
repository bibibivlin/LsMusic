package com.linxyi.lsmusic

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import com.linxyi.lsmusic.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlaybackRequestsTest {
    private val track = MediaEntry("track", "album", "Song", isContainer = false, resourceUri = "https://example.test/song.flac")
    private val original = listOf(QueueItem("old-1", track), QueueItem("old-2", track.copy(id = "other")))

    @Test
    fun defaults_replaceQueueAndShowMiniPlayerWithoutEnqueueOverride() {
        val preferences = AppPreferences()
        assertFalse(preferences.enqueueWhilePlaying)
        assertTrue(preferences.miniPlayerEnabled)
        assertTrue(preferences.clearQueueOnPlay)
        assertEquals(30, preferences.sleepTimerMinutes)
        assertFalse(preferences.sleepTimerFinishTrack)
    }

    @Test
    fun libraryRequests_obeyPrecedenceForEveryPreferenceAndPlaybackState() {
        for (enqueue in listOf(false, true)) for (clear in listOf(false, true)) {
            for (state in RemotePlaybackState.entries) for (hasTrack in listOf(false, true)) {
                val preferences = AppPreferences(enqueueWhilePlaying = enqueue, clearQueueOnPlay = clear)
                val expected = when {
                    enqueue && hasTrack && state == RemotePlaybackState.PLAYING -> PlaybackRequestMode.APPEND_ONLY
                    clear -> PlaybackRequestMode.REPLACE_AND_PLAY
                    else -> PlaybackRequestMode.APPEND_AND_PLAY
                }
                assertEquals("enqueue=$enqueue clear=$clear state=$state hasTrack=$hasTrack", expected,
                    playbackRequestMode(preferences, hasTrack, state))
            }
        }
    }

    @Test
    fun appendOnly_preservesExistingOccurrencesAndDoesNotRequestPlayback() {
        val result = requireNotNull(applyPlaybackRequest(original, listOf(track, track), PlaybackRequestMode.APPEND_ONLY))
        assertEquals(original, result.queue.take(2))
        assertNull(result.startIndex)
        assertEquals(4, result.queue.map { it.queueId }.distinct().size)
        assertEquals(listOf(track, track), result.addedItems.map { it.track })
    }

    @Test
    fun appendAndPlay_startsNewOccurrenceEvenWhenSameSongAlreadyExists() {
        val result = requireNotNull(applyPlaybackRequest(original, listOf(track), PlaybackRequestMode.APPEND_AND_PLAY))
        assertEquals(2, result.startIndex)
        assertEquals(original, result.queue.take(2))
        assertNotEquals(original.first().queueId, result.queue[2].queueId)
        assertEquals(track, result.queue[2].track)
    }

    @Test
    fun replaceQueue_startsFirstPlayableAndKeepsOriginalMetadata() {
        val tagged = track.copy(didlMetadata = "<DIDL-Lite>original</DIDL-Lite>")
        val result = requireNotNull(applyPlaybackRequest(
            original, listOf(track.copy(isContainer = true), track.copy(resourceUri = null), tagged, track),
            PlaybackRequestMode.REPLACE_AND_PLAY,
        ))
        assertEquals(0, result.startIndex)
        assertEquals(listOf(tagged, track), result.queue.map { it.track })
        assertSame(tagged, result.queue.first().track)
    }

    @Test
    fun emptyOrUnplayableRequests_leaveQueueUntouchedInEveryMode() {
        for (mode in PlaybackRequestMode.entries) {
            assertNull(applyPlaybackRequest(original, emptyList(), mode))
            assertNull(applyPlaybackRequest(original, listOf(track.copy(resourceUri = ""), track.copy(isContainer = true)), mode))
        }
    }

    @Test
    fun duplicateOccurrences_eachParticipateInShuffleAndNewDuplicatesRemainEligible() {
        val queue = List(3) { QueueItem("occurrence-$it", track) }
        var index = 0
        var order = PlaybackOrder().toggleShuffle(queue[index].queueId)
        val visited = mutableSetOf(queue[index].queueId)
        repeat(2) {
            val selection = requireNotNull(selectNextTrack(queue, index, order, true, Random(3)))
            index = selection.index
            order = selection.order
            assertTrue(visited.add(queue[index].queueId))
        }
        assertNull(selectNextTrack(queue, index, order, true))
        val appended = queue + QueueItem("new-duplicate", track)
        assertEquals(3, selectNextTrack(appended, index, order, true)?.index)
    }

    @Test
    fun reorder_tracksCurrentOccurrenceInsteadOfAnotherCopyOfSameSong() {
        val queue = List(3) { QueueItem("occurrence-$it", track) }
        val moved = moveListItem(queue, 2, 0)
        val currentIndex = indexAfterListItemMove(2, 2, 0)
        assertEquals("occurrence-2", moved[currentIndex].queueId)
        assertEquals("occurrence-0", moved[1].queueId)
    }

    @Test
    fun removingDuplicate_preservesCurrentOccurrenceAndSelectsSuccessorOnlyWhenCurrentIsRemoved() {
        val queue = List(3) { QueueItem("occurrence-$it", track) }
        val removedOther = requireNotNull(removeQueueOccurrence(queue, 2, 0))
        assertFalse(removedOther.removedCurrent)
        assertEquals("occurrence-2", removedOther.queue[removedOther.currentIndex].queueId)
        val removedCurrent = requireNotNull(removeQueueOccurrence(queue, 1, 1))
        assertTrue(removedCurrent.removedCurrent)
        assertEquals("occurrence-2", removedCurrent.queue[removedCurrent.currentIndex].queueId)
        assertEquals(-1, removeQueueOccurrence(queue.take(1), 0, 0)?.currentIndex)
        assertEquals(-1, removeQueueOccurrence(queue, -1, 0)?.currentIndex)
        assertNull(removeQueueOccurrence(queue, 0, -1))
    }
}
