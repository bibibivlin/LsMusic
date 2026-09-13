package com.linxyi.lsmusic.ui

import com.linxyi.lsmusic.dlna.MediaEntry
import com.linxyi.lsmusic.dlna.RemotePlaybackState
import java.util.UUID

/** Identity belongs to an occurrence in the queue, not to the server's media object. */
data class QueueItem(val queueId: String, val track: MediaEntry) {
    companion object {
        fun create(track: MediaEntry): QueueItem = QueueItem(UUID.randomUUID().toString(), track)
    }
}

internal enum class PlaybackRequestMode { APPEND_ONLY, APPEND_AND_PLAY, REPLACE_AND_PLAY }

internal fun playbackRequestMode(
    preferences: AppPreferences,
    hasCurrentTrack: Boolean,
    playbackState: RemotePlaybackState,
): PlaybackRequestMode = when {
    preferences.enqueueWhilePlaying && hasCurrentTrack && playbackState == RemotePlaybackState.PLAYING ->
        PlaybackRequestMode.APPEND_ONLY
    preferences.clearQueueOnPlay -> PlaybackRequestMode.REPLACE_AND_PLAY
    else -> PlaybackRequestMode.APPEND_AND_PLAY
}

internal data class PlaybackRequestResult(
    val queue: List<QueueItem>,
    val startIndex: Int?,
    val addedItems: List<QueueItem>,
)

internal fun applyPlaybackRequest(
    queue: List<QueueItem>,
    tracks: List<MediaEntry>,
    mode: PlaybackRequestMode,
    createItem: (MediaEntry) -> QueueItem = QueueItem::create,
): PlaybackRequestResult? {
    val added = tracks.filter { !it.isContainer && !it.resourceUri.isNullOrBlank() }.map(createItem)
    if (added.isEmpty()) return null
    return when (mode) {
        PlaybackRequestMode.APPEND_ONLY -> PlaybackRequestResult(queue + added, null, added)
        PlaybackRequestMode.APPEND_AND_PLAY -> PlaybackRequestResult(queue + added, queue.size, added)
        PlaybackRequestMode.REPLACE_AND_PLAY -> PlaybackRequestResult(added, 0, added)
    }
}

internal data class QueueRemoval(val queue: List<QueueItem>, val currentIndex: Int, val removedCurrent: Boolean)

internal fun removeQueueOccurrence(queue: List<QueueItem>, currentIndex: Int, removedIndex: Int): QueueRemoval? {
    if (removedIndex !in queue.indices) return null
    val currentId = queue.getOrNull(currentIndex)?.queueId
    val removedCurrent = queue[removedIndex].queueId == currentId
    val remaining = queue.filterIndexed { index, _ -> index != removedIndex }
    val nextIndex = when {
        remaining.isEmpty() || currentId == null -> -1
        removedCurrent -> removedIndex.coerceAtMost(remaining.lastIndex)
        else -> remaining.indexOfFirst { it.queueId == currentId }
    }
    return QueueRemoval(remaining, nextIndex, removedCurrent)
}
