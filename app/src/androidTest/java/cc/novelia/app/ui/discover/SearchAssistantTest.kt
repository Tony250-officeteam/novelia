package cc.novelia.app.ui.discover

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.novelia.app.data.catalog.KeywordCatalog
import cc.novelia.app.data.catalog.KeywordEntry
import cc.novelia.app.ui.components.base.AppLazyColumn
import cc.novelia.app.ui.components.base.AppSheet
import cc.novelia.app.ui.components.keywords.KeywordEditorDialog
import cc.novelia.app.ui.components.base.rememberCloudFilterCollapse
import cc.novelia.app.ui.theme.AppInteractionMode
import cc.novelia.app.ui.theme.NoveliaTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchAssistantTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Test fun sheetPreviewStaysVisibleWithScrollingKeyboardAndLargeText() {
        var expanded by mutableStateOf(true)
        var scale by mutableFloatStateOf(1f)
        var imeVisible by mutableStateOf(false)
        var applied = ""
        val generated = (1..18).joinToString(" ") { "关键词$it" }
        compose.setContent {
            val assistantState = rememberSaveableStateHolder()
            NoveliaTheme("dark") {
                if(expanded) AppSheet(onDismissRequest = { expanded = false }) {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                        val ime = WindowInsets.isImeVisible
                        SideEffect { imeVisible = ime }
                        assistantState.SaveableStateProvider("assistant") {
                            SearchAssistantPanel("手工条件", KeywordCatalog.common, true, { expanded = it }, { applied = it },
                                { _, _ -> }, {}, Modifier.width(320.dp), sheetMode = true)
                        }
                    }
                }
            }
        }
        val preview = compose.onNodeWithTag("assistant-preview-bar")
        preview.assertIsDisplayed()
        compose.onNodeWithTag("assistant-preview-details").assertIsNotEnabled()
        val beforeScroll = preview.getUnclippedBoundsInRoot()
        compose.onNodeWithTag("assistant-all").performScrollTo()
        assertEquals("滚动表单不应移动预览条", beforeScroll, preview.getUnclippedBoundsInRoot())
        compose.onNodeWithTag("assistant-all").performClick().performTextInput(generated)
        compose.waitUntil(5_000) { imeVisible }
        compose.onNodeWithTag("assistant-preview").assertIsDisplayed().assertTextEquals(generated)
        val withKeyboard = preview.getUnclippedBoundsInRoot()
        assertTrue("键盘弹起时预览应缩为一行", withKeyboard.bottom - withKeyboard.top < beforeScroll.bottom - beforeScroll.top)
        screenshot("assistant-preview-keyboard")
        compose.onNodeWithText("查看原站搜索语法").performScrollTo()
        assertEquals(withKeyboard, preview.getUnclippedBoundsInRoot())
        preview.assertIsDisplayed()
        compose.onNodeWithTag("assistant-preview-details").performClick()
        compose.onNodeWithTag("assistant-preview-full").assertTextEquals(generated)
        compose.onNodeWithTag("assistant-preview-appended").performScrollTo().assertTextEquals("手工条件 $generated")
        screenshot("assistant-preview-details")
        compose.onNodeWithText("返回编辑").performClick()
        compose.waitUntil(5_000) { !imeVisible }
        compose.onNodeWithTag("assistant-preview").assertTextEquals(generated)
        compose.runOnIdle { scale = 2f }
        compose.onNodeWithTag("assistant-preview").assertIsDisplayed().assertTextEquals(generated)
        val largePreview = preview.getUnclippedBoundsInRoot()
        assertTrue("字体缩放应作用到弹窗内的预览条", largePreview.bottom - largePreview.top > beforeScroll.bottom - beforeScroll.top)
        compose.onNodeWithText("查看原站搜索语法").performScrollTo()
        val content = compose.onNodeWithTag("search-assistant-content").getUnclippedBoundsInRoot()
        assertTrue("预览和滚动表单不能重叠", content.top >= preview.getUnclippedBoundsInRoot().bottom)
        assertTrue("大字号下表单应仍有可用空间", content.bottom - content.top > 80.dp)
        screenshot("assistant-preview-large-text")
        compose.onNodeWithTag("assistant-append").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("手工条件 $generated", applied); assertFalse(expanded) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir("screenshots"), "$name.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun assistantCollapsesAndOnlyChangesManualQueryWhenApplied() {
        var expanded by mutableStateOf(false)
        var query = "(旅人 | 少女)"
        compose.setContent {
            MaterialTheme { SearchAssistantPanel(query, KeywordCatalog.common, expanded, { expanded = it }, { query = it }, { _, _ -> }, {}, Modifier.fillMaxWidth()) }
        }
        compose.onNodeWithTag("assistant-tag-input").assertDoesNotExist()
        compose.onNodeWithTag("search-assistant-toggle").performClick()
        screenshot("search-assistant-expanded")
        compose.onNodeWithTag("assistant-tag-input").performTextInput("病娇")
        compose.onNodeWithTag("assistant-candidate-ヤンデレ").performScrollTo().performClick()
        screenshot("search-assistant-tag-alias")
        compose.onNodeWithTag("keyword-include").performClick()
        compose.runOnIdle { assertEquals("(旅人 | 少女)", query) }
        compose.onNodeWithTag("assistant-append").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("(旅人 | 少女) ヤンデレ$", query); assertFalse(expanded) }
    }

    @Test fun unknownTagCanBeManuallyExcluded() {
        var expanded by mutableStateOf(true)
        var expression = ""
        compose.setContent {
            MaterialTheme { SearchAssistantPanel("", KeywordCatalog.common, expanded, { expanded = it }, { expression = it }, { _, _ -> }, {}) }
        }
        compose.onNodeWithTag("assistant-tag-input").performTextInput("未知の世界")
        compose.onNodeWithTag("assistant-manual-tag").performScrollTo().performClick()
        compose.onNodeWithTag("keyword-exclude").performClick()
        compose.onNodeWithTag("assistant-append").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("-未知の世界$", expression) }
    }

    @Test fun tagTranslationCanBeCleared() {
        var saved: Pair<String, String>? = null
        compose.setContent {
            MaterialTheme { KeywordEditorDialog(KeywordEntry("ヤンデレ", "病娇"), {}, { original, translation -> saved = original to translation }) }
        }
        screenshot("search-assistant-translation-editor")
        compose.onNodeWithTag("keyword-translation").performTextClearance()
        compose.onNodeWithText("保存翻译").performClick()
        compose.runOnIdle { assertEquals("ヤンデレ" to "", saved) }
    }

    @Test fun overlongTranslationShowsAnErrorAndCannotBeSaved() {
        var saved = false
        compose.setContent {
            MaterialTheme { KeywordEditorDialog(KeywordEntry("ヤンデレ"), {}, { _, _ -> saved = true }) }
        }
        compose.onNodeWithTag("keyword-translation").performTextInput("字".repeat(KeywordCatalog.MAX_TEXT_LENGTH + 1))
        compose.onNodeWithText("原文和翻译最多 256 字符，请缩短后保存。").assertIsDisplayed()
        compose.onNodeWithText("保存翻译").assertIsNotEnabled()
        compose.runOnIdle { assertFalse(saved) }
    }

    @Test fun persistenceFailureRemainsVisibleWithTheAssistantCollapsed() {
        compose.setContent {
            MaterialTheme { SearchAssistantPanel("", KeywordCatalog.common, false, {}, {}, { _, _ -> }, {}, persistenceError = "标签词库和翻译暂未保存，正在重试") }
        }
        compose.onNodeWithText("标签词库和翻译暂未保存，正在重试").assertIsDisplayed()
    }

    @Test fun onlyBrowsingResultsCollapsesAssistantAndTheSharedSwitchDisablesIt() {
        var expanded by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                AppInteractionMode(false, true) {
                    Column(Modifier.width(375.dp).fillMaxHeight()) {
                        SearchAssistantPanel("", KeywordCatalog.common, expanded, { expanded = it }, {}, { _, _ -> }, {})
                        val collapse = rememberCloudFilterCollapse(enabled, expanded) { expanded = false }
                        AppLazyColumn(Modifier.weight(1f).nestedScroll(collapse), listModifier = Modifier.testTag("results")) {
                            items(40) { Text("小说 $it", Modifier.fillMaxWidth().height(72.dp)) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("search-assistant-content").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue("编辑条件时滚动面板不应收起", expanded) }
        compose.onNodeWithTag("results").performTouchInput { swipeDown() }
        compose.runOnIdle { assertTrue("向列表顶部滚动不应收起", expanded) }
        compose.onNodeWithTag("results").performTouchInput { swipeUp() }
        compose.onNodeWithTag("search-assistant-content").assertDoesNotExist()
        compose.onNodeWithTag("search-assistant-toggle").performClick()
        compose.onNodeWithTag("search-assistant-content").assertIsDisplayed()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("results").performTouchInput { swipeUp() }
        compose.onNodeWithTag("search-assistant-content").assertIsDisplayed()
    }

    @Test fun assistantUsesTheSameExpandableMotionAndStopsImmediatelyWhenDisabled() {
        var expanded by mutableStateOf(false)
        var reduced by mutableStateOf(false)
        var eInk by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                AppInteractionMode(eInk, reduced) {
                    SearchAssistantPanel("", KeywordCatalog.common, expanded, { expanded = it }, {}, { _, _ -> }, {},
                        Modifier.width(375.dp).testTag("assistant"))
                }
            }
        }
        fun height() = compose.onNodeWithTag("assistant").getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val collapsedHeight = height()
        compose.runOnIdle { expanded = true }
        compose.waitForIdle()
        val fullHeight = height()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { expanded = false }
            compose.mainClock.advanceTimeBy(80)
            assertTrue(height() < fullHeight && height() > collapsedHeight)
            compose.runOnIdle { reduced = true }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(collapsedHeight, height())
            compose.runOnIdle { expanded = true }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(fullHeight, height())
            compose.runOnIdle { expanded = false; reduced = false; eInk = true }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(collapsedHeight, height())
            compose.runOnIdle { expanded = true }
            compose.mainClock.advanceTimeByFrame()
            val eInkHeight = height()
            assertTrue(eInkHeight > collapsedHeight)
            compose.mainClock.advanceTimeBy(100)
            assertEquals("电子纸模式展开后无需等待动效", eInkHeight, height())
            compose.runOnIdle { expanded = false }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(collapsedHeight, height())
            compose.runOnIdle { eInk = false }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { expanded = true }
            compose.mainClock.advanceTimeBy(80)
            assertTrue(height() > collapsedHeight && height() < fullHeight)
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun eInkResultPagingAlsoRespectsTheSharedAutoCollapseSwitch() {
        var expanded by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                AppInteractionMode(true, false) {
                    Column(Modifier.width(375.dp).fillMaxHeight()) {
                        SearchAssistantPanel("", KeywordCatalog.common, expanded, { expanded = it }, {}, { _, _ -> }, {})
                        val collapse = rememberCloudFilterCollapse(enabled, expanded) { expanded = false }
                        AppLazyColumn(Modifier.weight(1f).nestedScroll(collapse), listModifier = Modifier.testTag("results"),
                            onPageTurn = { direction -> if(direction > 0 && enabled) expanded = false }) {
                            items(40) { Text("小说 $it", Modifier.fillMaxWidth().height(72.dp)) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("results").performTouchInput { swipeUp() }
        compose.onNodeWithTag("search-assistant-content").assertDoesNotExist()
        compose.onNodeWithTag("search-assistant-toggle").performClick()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("results").performTouchInput { swipeUp() }
        compose.onNodeWithTag("search-assistant-content").assertIsDisplayed()
    }
}
