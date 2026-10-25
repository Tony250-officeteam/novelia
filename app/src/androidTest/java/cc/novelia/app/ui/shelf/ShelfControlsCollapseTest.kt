package cc.novelia.app.ui.shelf

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.platform.app.InstrumentationRegistry
import cc.novelia.app.NoveliaApplication
import cc.novelia.app.data.model.BookCard
import cc.novelia.app.data.model.BookRef
import cc.novelia.app.data.model.SavedBook
import cc.novelia.app.ui.navigation.AppController
import cc.novelia.app.ui.theme.AppInteractionMode
import cc.novelia.app.ui.theme.NoveliaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShelfControlsCollapseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactLocalControlsKeepCommittedFiltersAndScrollPositionAcrossSheetsAndRestoration() {
        withShelf { restoration ->
            compose.onNodeWithText("批量整理").assertDoesNotExist()
            compose.onNodeWithTag("local-filter-toggle").performClick()
            compose.onNodeWithText("本地小说").performClick()
            compose.onNodeWithTag("local-filter-apply").performClick()
            compose.onNodeWithTag("local-sort-picker").performClick()
            compose.onNodeWithText("书名排序").performClick()
            compose.onNodeWithTag("shelf-books").performScrollToIndex(20)
            fun top() = compose.onNodeWithTag("shelf-book-local/collapse-20").getUnclippedBoundsInRoot().top
            val before = top()
            compose.onNodeWithTag("local-filter-toggle").performClick()
            compose.onNodeWithTag("local-reading-status-想读").performClick()
            compose.onNodeWithText("关闭面板").performClick()
            assertEquals(before, top())
            compose.onNodeWithTag("local-clear-type").assertTextContains("本地小说")
            compose.onNodeWithTag("local-clear-status").assertDoesNotExist()
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("local-sort-picker").assertTextContains("书名排序", substring = true)
            compose.onNodeWithTag("local-clear-type").assertIsDisplayed()
            compose.onNodeWithTag("shelf-tab-1").performClick()
            compose.onNodeWithTag("local-filter-toggle").performClick()
            compose.onNodeWithText("文库分卷").assertIsDisplayed()
            compose.onNodeWithText("关闭面板").performClick()
            compose.onNodeWithTag("shelf-tab-0").performClick()
            compose.onNodeWithTag("local-clear-type").assertIsDisplayed()
        }
    }

    @Test fun longPressSelectionKeepsAllChoicesDuringEInkPageTurnsAndFinishesCleanly() {
        withShelf(eInk = true) {
            compose.onNodeWithTag("shelf-book-local/collapse-0").performTouchInput { longClick() }
            compose.onNodeWithText("已选 1 本").assertIsDisplayed()
            compose.onNodeWithText("全选").performClick()
            compose.onNodeWithText("下一屏").performClick()
            compose.onNodeWithText("已选 60 本").assertIsDisplayed()
            compose.onNodeWithTag("local-selection-move").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithTag("local-selection-done").performClick()
            compose.onNodeWithTag("local-selection-actions").assertDoesNotExist()
            compose.onNodeWithTag("local-filter-toggle").assertIsDisplayed()
            compose.onNodeWithText("批量整理").assertDoesNotExist()
        }
    }

    private fun withShelf(eInk: Boolean = false, block: (StateRestorationTester) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NoveliaApplication
        runBlocking { app.initialization.await() }
        val before = app.store.state.value
        try {
            app.store.update { it.copy(books = List(60) { index -> SavedBook(
                BookCard(BookRef("local", "collapse-$index"), "森林来信 ${index.toString().padStart(2, '0')}", authors = listOf("山川遥")), addedAt = 1L)
            }, positions = emptyMap()) }
            val restoration = StateRestorationTester(compose)
            restoration.setContent {
                AppInteractionMode(eInk, true) {
                    NoveliaTheme("light") {
                        val nav = rememberNavController()
                        val scope = rememberCoroutineScope()
                        val c = remember(app, nav, scope) { AppController(app, nav, scope, SnackbarHostState()) }
                        ShelfScreen(c, onOpenBook = {})
                    }
                }
            }
            block(restoration)
        } finally { app.store.update { before } }
    }

}
