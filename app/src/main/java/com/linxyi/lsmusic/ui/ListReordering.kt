package com.linxyi.lsmusic.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.PinnableContainer
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun <T> moveListItem(
    items: List<T>,
    fromIndex: Int,
    toIndex: Int,
): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

internal fun indexAfterListItemMove(
    trackedIndex: Int,
    fromIndex: Int,
    toIndex: Int,
): Int = when {
    trackedIndex < 0 || fromIndex == toIndex -> trackedIndex
    trackedIndex == fromIndex -> toIndex
    fromIndex < toIndex && trackedIndex in (fromIndex + 1)..toIndex -> trackedIndex - 1
    fromIndex > toIndex && trackedIndex in toIndex..<fromIndex -> trackedIndex + 1
    else -> trackedIndex
}

internal fun boundedListDragTop(
    desiredTop: Float,
    itemSize: Int,
    listStart: Int,
    listEnd: Int,
): Float {
    val minimumTop = listStart.toFloat()
    val maximumTop = (listEnd - itemSize).toFloat()
    return if (maximumTop <= minimumTop) minimumTop else desiredTop.coerceIn(minimumTop, maximumTop)
}

internal class LazyListReorderState(
    val listState: LazyListState,
    private val itemIndexOffset: Int,
) {
    var onMove: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> }

    var draggedItemKey by mutableStateOf<Any?>(null)
        private set

    private var draggedItemIndex = -1
    private var initialItemOffset = 0
    private var dragDistance by mutableFloatStateOf(0f)
    private var draggedItemLayoutOffset by mutableIntStateOf(0)
    private var draggedItemSlotOffset = 0
    private var draggedItemSize = 0
    private var pinnedHandle: PinnableContainer.PinnedHandle? = null

    val draggedItemOffset: Float
        get() {
            val key = draggedItemKey ?: return 0f
            val actualLayoutOffset = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == key }
                ?.offset
                ?: draggedItemLayoutOffset
            return boundedDraggedItemTop() - actualLayoutOffset
        }

    fun isDragging(key: Any): Boolean = draggedItemKey == key

    fun startDragging(key: Any, pinnableContainer: PinnableContainer?) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val itemIndex = item.index - itemIndexOffset
        if (itemIndex < 0) return
        pinnedHandle?.release()
        pinnedHandle = pinnableContainer?.pin()
        draggedItemKey = key
        draggedItemIndex = itemIndex
        initialItemOffset = item.offset
        draggedItemLayoutOffset = item.offset
        draggedItemSlotOffset = item.offset
        draggedItemSize = item.size
        dragDistance = 0f
    }

    fun dragBy(distance: Float) {
        if (draggedItemKey == null) return
        dragDistance += distance
        moveToClosestItem()
    }

    fun stopDragging() {
        pinnedHandle?.release()
        pinnedHandle = null
        draggedItemKey = null
        draggedItemIndex = -1
        initialItemOffset = 0
        draggedItemLayoutOffset = 0
        draggedItemSlotOffset = 0
        draggedItemSize = 0
        dragDistance = 0f
    }

    suspend fun scrollAtEdge(edgeSizePx: Float, maximumScrollPx: Float) {
        if (draggedItemKey == null) return
        syncDraggedItemLayout()
        val layoutInfo = listState.layoutInfo
        val draggedCenter = initialItemOffset + dragDistance + draggedItemSize / 2f
        val listStart = layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding
        val listEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
        val startEdge = listStart + edgeSizePx
        val endEdge = listEnd - edgeSizePx
        val scrollDistance = when {
            draggedCenter < startEdge -> {
                -maximumScrollPx * ((startEdge - draggedCenter) / edgeSizePx).coerceIn(0.15f, 1f)
            }
            draggedCenter > endEdge -> {
                maximumScrollPx * ((draggedCenter - endEdge) / edgeSizePx).coerceIn(0.15f, 1f)
            }
            else -> 0f
        }
        val previousLayoutOffset = draggedItemLayoutOffset
        val previousSlotOffset = draggedItemSlotOffset
        val consumedScroll = if (scrollDistance == 0f) 0f else listState.scrollBy(scrollDistance)
        if (consumedScroll != 0f) {
            if (!syncDraggedItemLayout()) {
                draggedItemLayoutOffset = (previousLayoutOffset - consumedScroll).roundToInt()
                draggedItemSlotOffset = (previousSlotOffset - consumedScroll).roundToInt()
            }
            moveToClosestItem()
        }
    }

    private fun moveToClosestItem() {
        val key = draggedItemKey ?: return
        val visibleItems = listState.layoutInfo.visibleItemsInfo.filter { it.index >= itemIndexOffset }
        val draggedItem = visibleItems.firstOrNull { it.key == key }
        if (draggedItem != null) {
            val layoutIndex = draggedItem.index - itemIndexOffset
            if (layoutIndex != draggedItemIndex) return
            draggedItemLayoutOffset = draggedItem.offset
            draggedItemSlotOffset = draggedItem.offset
            draggedItemSize = draggedItem.size
        }

        val draggedCenter = boundedDraggedItemTop() + draggedItemSize / 2f
        val currentSlotCenter = draggedItemSlotOffset + draggedItemSize / 2f
        val targetItem = visibleItems
            .asSequence()
            .filterNot { it.key == key }
            .minByOrNull { item ->
                abs(item.offset + item.size / 2f - draggedCenter)
            } ?: return
        val targetDistance = abs(targetItem.offset + targetItem.size / 2f - draggedCenter)
        if (targetDistance >= abs(currentSlotCenter - draggedCenter)) return

        val targetIndex = targetItem.index - itemIndexOffset
        if (targetIndex == draggedItemIndex) return

        val fromIndex = draggedItemIndex
        val firstVisibleItemIndex = listState.firstVisibleItemIndex
        val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
        draggedItemIndex = targetIndex
        draggedItemSlotOffset = targetItem.offset
        // Prevent key-based anchoring from fighting edge scrolling after an upward data move.
        listState.requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        onMove(fromIndex, targetIndex)
    }

    private fun syncDraggedItemLayout(): Boolean {
        val key = draggedItemKey ?: return false
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        draggedItemLayoutOffset = item.offset
        draggedItemSize = item.size
        if (item.index - itemIndexOffset != draggedItemIndex) return false
        draggedItemSlotOffset = item.offset
        return true
    }

    private fun boundedDraggedItemTop(): Float {
        val layoutInfo = listState.layoutInfo
        return boundedListDragTop(
            desiredTop = initialItemOffset + dragDistance,
            itemSize = draggedItemSize,
            listStart = layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding,
            listEnd = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding,
        )
    }
}

@Composable
internal fun Modifier.lazyListReorderHandle(
    enabled: Boolean,
    itemKey: Any,
    reorderState: LazyListReorderState,
    pinnableContainer: PinnableContainer?,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier {
    val currentPinnableContainer by rememberUpdatedState(pinnableContainer)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    return pointerInput(enabled, itemKey, reorderState) {
        if (!enabled) return@pointerInput
        try {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    reorderState.startDragging(itemKey, currentPinnableContainer)
                },
                onDrag = { change, dragAmount ->
                    if (reorderState.isDragging(itemKey)) {
                        change.consume()
                        reorderState.dragBy(dragAmount.y)
                    }
                },
                onDragEnd = {
                    if (reorderState.isDragging(itemKey)) currentOnDragEnd()
                    reorderState.stopDragging()
                },
                onDragCancel = {
                    if (reorderState.isDragging(itemKey)) currentOnDragCancel()
                    reorderState.stopDragging()
                },
            )
        } finally {
            if (reorderState.isDragging(itemKey)) reorderState.stopDragging()
        }
    }
}
