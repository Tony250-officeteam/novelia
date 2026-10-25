package cc.novelia.app.ui.shelf

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfRangeSelectionTest {
    private val keys = listOf("a", "b", "c", "d", "e")
    @Test fun backtrackingRestoresTheOriginalChoicesOutsideTheNewRange() {
        val before = setOf("a", "e")
        assertEquals(setOf("a", "b", "c", "d", "e"), shelfRangeSelection(keys, before, "b", "d", true))
        assertEquals(setOf("a", "b", "e"), shelfRangeSelection(keys, before, "b", "b", true))
    }
    @Test fun draggingFromASelectedBookDeselectsAndSupportsReversingDirection() {
        val before = keys.toSet()
        assertEquals(setOf("a", "e"), shelfRangeSelection(keys, before, "b", "d", false))
        assertEquals(setOf("c", "d", "e"), shelfRangeSelection(keys, before, "b", "a", false))
    }
    @Test fun disappearingAnchorDoesNotSelectUnrelatedBooks() {
        assertEquals(setOf("a"), shelfRangeSelection(keys, setOf("a"), "removed", "e", true))
    }
}
