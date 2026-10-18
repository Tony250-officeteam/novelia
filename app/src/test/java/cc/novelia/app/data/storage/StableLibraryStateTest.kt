package cc.novelia.app.data.storage

import cc.novelia.app.data.library.withReadingPosition
import cc.novelia.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class StableLibraryStateTest {
    @Test fun twoThousandBooksKeepTheirIdentityAcrossProgressUpdates() {
        val original = LibraryState(books = List(2000) {
            SavedBook(BookCard(BookRef("local", "book-$it"), "小说 $it"))
        }).withStableFolderIds()
        assertSame(original, original.withStableFolderIds())
        var state = original
        repeat(100) { index ->
            state = state.withReadingPosition(BookRef("local", "book-1999"), Position("c", index, textOffset = index * 3))
                .withStableFolderIds(state)
            assertSame(original.books, state.books)
            assertSame(original.folderIds, state.folderIds)
            assertSame(original.books[1999], state.books[1999])
        }
        assertEquals(99, state.positions.getValue("local/book-1999").index)
        assertEquals(297, state.positions.getValue("local/book-1999").textOffset)
        assertTrue(state.readingHistory.containsKey("local/book-1999"))
    }

    @Test fun onlyBooksWithChangedFolderIdsAreCopied() {
        val original = LibraryState(books = List(3) {
            SavedBook(BookCard(BookRef("local", "$it"), "卷 $it"))
        }).withStableFolderIds()
        val moved = original.copy(books = original.books.mapIndexed { i, book -> if(i == 1) book.copy(folder = "新收藏夹") else book })
            .withStableFolderIds(original)
        assertSame(original.books[0], moved.books[0])
        assertSame(original.books[2], moved.books[2])
        assertEquals(legacyFolderId("新收藏夹"), moved.books[1].folderId)
        assertTrue("新收藏夹" in moved.folders)
        assertSame(moved, moved.withStableFolderIds())
        val renamed = moved.renameShelfFolder("新收藏夹", "改名").withStableFolderIds(moved)
        assertEquals(moved.books[1].folderId, renamed.books[1].folderId)
    }
}
