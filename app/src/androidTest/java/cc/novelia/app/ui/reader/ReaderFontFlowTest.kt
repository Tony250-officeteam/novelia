package cc.novelia.app.ui.reader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cc.novelia.app.MainActivity
import cc.novelia.app.NoveliaApplication
import cc.novelia.app.data.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderFontFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun changingFontsInALongScrollParagraphKeepsTheCharacterAnchorAndPersistsTheChoice() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("本地文件").fetchSemanticsNodes().isNotEmpty() }
        val app = compose.activity.application as NoveliaApplication
        val previous = app.store.state.value
        val ref = BookRef("local", "reader-font-flow")
        try {
            compose.runOnIdle {
                app.store.update { it.copy(reader = ReaderSettings(showPageButtons = true, indent = false),
                    bookSettings = it.bookSettings - ref.key, positions = it.positions - ref.key) }
                app.store.saveDocument(LocalDocument(ref.id, "字体定位测试", "txt", listOf(
                    LocalChapter("first", "字体测试章节", listOf("A traveller follows the winding road. iii WWW mmm. ".repeat(400))))))
                app.store.saveBook(BookCard(ref, "字体定位测试"))
            }
            compose.onNodeWithText("本地文件").performClick()
            compose.onNodeWithText("字体定位测试").performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText("下一屏").fetchSemanticsNodes().isNotEmpty() }
            repeat(2) {
                val before = app.store.state.value.positions[ref.key]?.textOffset ?: 0
                compose.onNodeWithText("下一屏").performClick()
                compose.waitUntil(10_000) { (app.store.state.value.positions[ref.key]?.textOffset ?: 0) > before }
            }
            val anchor = app.store.state.value.positions.getValue(ref.key).let { it.index to it.textOffset }
            compose.onNodeWithContentDescription("阅读设置").performClick()
            compose.onNodeWithTag("reader-font-settings").performScrollTo().performClick()
            listOf("等宽" to "monospace", "衬线" to "serif").forEach { (label, family) ->
                compose.onNodeWithTag("reader-font-option-$family").performScrollTo().performClick()
                compose.waitForIdle()
                compose.waitUntil(10_000) { app.store.state.value.reader.fontFamily == family }
                assertEquals(anchor, app.store.state.value.positions.getValue(ref.key).let { it.index to it.textOffset })
            }
            compose.onNodeWithText("完成").performClick()
            compose.onNodeWithText("关闭面板").performClick()
            compose.onNodeWithContentDescription("阅读设置").performClick()
            compose.onNodeWithTag("reader-font-settings").assertTextContains("衬线")
        } finally { compose.runOnIdle { app.store.update { previous } } }
    }
}
