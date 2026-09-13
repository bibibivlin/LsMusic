package com.linxyi.lsmusic.ui

import kotlin.random.Random

enum class RepeatMode {
    NONE,
    ONE,
    ALL,
}

data class PlaybackOrder(
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val shuffleEnabled: Boolean = false,
    val shuffledQueueIds: Set<String> = emptySet(),
)

internal data class NextTrackSelection(
    val index: Int,
    val order: PlaybackOrder,
)

internal fun PlaybackOrder.toggleShuffle(currentTrackId: String?): PlaybackOrder {
    val enabled = !shuffleEnabled
    return copy(
        shuffleEnabled = enabled,
        shuffledQueueIds = if (enabled && currentTrackId != null) setOf(currentTrackId) else emptySet(),
    )
}

internal fun PlaybackOrder.markPlayed(trackId: String?): PlaybackOrder = when {
    !shuffleEnabled || trackId == null -> this
    else -> copy(shuffledQueueIds = shuffledQueueIds + trackId)
}

internal fun PlaybackOrder.resetForQueue(currentTrackId: String?): PlaybackOrder = copy(
    shuffledQueueIds = if (shuffleEnabled && currentTrackId != null) setOf(currentTrackId) else emptySet(),
)

internal fun selectNextTrack(
    queue: List<QueueItem>,
    currentIndex: Int,
    order: PlaybackOrder,
    automatic: Boolean,
    random: Random = Random.Default,
): NextTrackSelection? {
    if (queue.isEmpty() || currentIndex !in queue.indices) return null
    if (automatic && order.repeatMode == RepeatMode.ONE) {
        return NextTrackSelection(currentIndex, order.markPlayed(queue[currentIndex].queueId))
    }

    if (!order.shuffleEnabled) {
        val nextIndex = when {
            currentIndex < queue.lastIndex -> currentIndex + 1
            order.repeatMode == RepeatMode.ALL -> 0
            else -> return null
        }
        return NextTrackSelection(nextIndex, order)
    }

    var updatedOrder = order.markPlayed(queue[currentIndex].queueId)
    var candidates = queue.indices.filter { queue[it].queueId !in updatedOrder.shuffledQueueIds }
    if (candidates.isEmpty() && order.repeatMode == RepeatMode.ALL) {
        updatedOrder = updatedOrder.copy(shuffledQueueIds = setOf(queue[currentIndex].queueId))
        candidates = queue.indices.filter { queue[it].queueId !in updatedOrder.shuffledQueueIds }
        if (candidates.isEmpty()) {
            return NextTrackSelection(currentIndex, updatedOrder)
        }
    }
    if (candidates.isEmpty()) return null

    val nextIndex = candidates[random.nextInt(candidates.size)]
    return NextTrackSelection(
        index = nextIndex,
        order = updatedOrder.markPlayed(queue[nextIndex].queueId),
    )
}

internal fun canSelectNextTrack(
    queue: List<QueueItem>,
    currentIndex: Int,
    order: PlaybackOrder,
): Boolean {
    if (queue.isEmpty() || currentIndex !in queue.indices) return false
    if (order.repeatMode == RepeatMode.ALL) return true
    if (!order.shuffleEnabled) return currentIndex < queue.lastIndex
    val played = order.markPlayed(queue[currentIndex].queueId).shuffledQueueIds
    return queue.any { it.queueId !in played }
}

internal fun isConfirmedLocalRepeatTransition(
    currentTrackId: String?,
    playbackReadyTrackId: String?,
    transitionedTrackId: String?,
): Boolean = currentTrackId != null &&
    playbackReadyTrackId == currentTrackId &&
    transitionedTrackId == currentTrackId
