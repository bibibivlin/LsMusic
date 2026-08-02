package com.linxyi.lsmusic

import com.linxyi.lsmusic.ui.boundedListDragTop
import com.linxyi.lsmusic.ui.indexAfterListItemMove
import com.linxyi.lsmusic.ui.moveListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ListReorderingTest {
    private val items = listOf("a", "b", "c", "d", "e")

    @Test
    fun moveListItem_movesDirectlyToAnyLaterPosition() {
        assertEquals(listOf("b", "c", "d", "a", "e"), moveListItem(items, 0, 3))
    }

    @Test
    fun moveListItem_movesDirectlyToAnyEarlierPosition() {
        assertEquals(listOf("a", "d", "b", "c", "e"), moveListItem(items, 3, 1))
    }

    @Test
    fun moveListItem_invalidOrUnchangedMoveKeepsOriginalList() {
        assertSame(items, moveListItem(items, -1, 2))
        assertSame(items, moveListItem(items, 2, 5))
        assertSame(items, moveListItem(items, 2, 2))
    }

    @Test
    fun indexAfterListItemMove_tracksMovedItemAndShiftedRange() {
        assertEquals(4, indexAfterListItemMove(trackedIndex = 1, fromIndex = 1, toIndex = 4))
        assertEquals(1, indexAfterListItemMove(trackedIndex = 2, fromIndex = 1, toIndex = 4))
        assertEquals(3, indexAfterListItemMove(trackedIndex = 2, fromIndex = 4, toIndex = 1))
        assertEquals(0, indexAfterListItemMove(trackedIndex = 0, fromIndex = 4, toIndex = 1))
    }

    @Test
    fun boundedListDragTop_stopsAtStableListBounds() {
        assertEquals(
            30f,
            boundedListDragTop(
                desiredTop = -100f,
                itemSize = 60,
                listStart = 30,
                listEnd = 430,
            ),
        )
        assertEquals(
            370f,
            boundedListDragTop(
                desiredTop = 800f,
                itemSize = 60,
                listStart = 30,
                listEnd = 430,
            ),
        )
    }

    @Test
    fun boundedListDragTop_usesListStartWhenItemDoesNotFit() {
        assertEquals(
            10f,
            boundedListDragTop(
                desiredTop = 250f,
                itemSize = 600,
                listStart = 10,
                listEnd = 500,
            ),
        )
    }
}
