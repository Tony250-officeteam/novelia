package cc.novelia.app.ui.book

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.model.BookCard
import cc.novelia.app.data.model.BookRef
import cc.novelia.app.ui.theme.AppInteractionMode
import cc.novelia.app.ui.theme.AppMotion
import cc.novelia.app.ui.theme.NoveliaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class BookDetailTitlesTest {
    @get:Rule val compose = createComposeRule()
    private val book = BookCard(BookRef("syosetu", "long-title"),
        "为了找回失落的记忆而踏上旅途的我与伙伴们再次相遇，并一起寻找世界尽头那片遥远星空的故事",
        originalTitle = "失われた記憶を取り戻すために旅立った私たちが、世界の果てにある遥かな星空を探して再び出会うまでの長い物語")
    private val subtitle = "连载中 · Hameln"

    @Test fun titlesKeepTheirOriginalOrderAndExpandAndCollapseIndependently() {
        compose.setContent { NoveliaTheme("light") {
            Column(Modifier.requiredWidth(240.dp).height(480.dp).verticalScroll(rememberScrollState())) { BookDetailTitles(book, subtitle) }
        } }
        assertCollapsed("book-detail-title")
        assertCollapsed("book-detail-original-title")
        val titleBounds = compose.onNodeWithTag("book-detail-title-row").getUnclippedBoundsInRoot()
        val metadataBounds = compose.onNodeWithText(subtitle).getUnclippedBoundsInRoot()
        val originalBounds = compose.onNodeWithTag("book-detail-original-title-row").getUnclippedBoundsInRoot()
        assertTrue(titleBounds.bottom < metadataBounds.top)
        assertTrue(metadataBounds.bottom < originalBounds.top)
        // 箭头和各自标题位于同一行容器内，不再增加一个按钮行。
        for(original in listOf(false, true)) {
            val row = if(original) originalBounds else titleBounds
            val arrow = toggle(original).getUnclippedBoundsInRoot()
            assertTrue(arrow.top >= row.top && arrow.bottom <= row.bottom)
        }
        toggle().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "主标题已折叠")).performClick()
        assertComplete("book-detail-title")
        assertCollapsed("book-detail-original-title")
        toggle(original = true).performScrollTo().performClick()
        assertComplete("book-detail-title")
        assertComplete("book-detail-original-title")
        toggle().performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "主标题已展开")).performClick()
        assertCollapsed("book-detail-title")
        assertComplete("book-detail-original-title")
        toggle(original = true).performScrollTo().performClick()
        assertCollapsed("book-detail-title")
        assertCollapsed("book-detail-original-title")
    }

    @Test fun shortTitlesHaveNoArrowAndAnIdenticalOriginalTitleKeepsItsPlace() {
        var current by mutableStateOf(book.copy(title = "风与书页", originalTitle = "風と本"))
        compose.setContent { NoveliaTheme("light") {
            Box(Modifier.requiredWidth(240.dp)) { BookDetailTitles(current, subtitle) }
        } }
        assertComplete("book-detail-title")
        assertComplete("book-detail-original-title")
        toggle().assertDoesNotExist()
        toggle(original = true).assertDoesNotExist()
        compose.runOnIdle { current = current.copy(originalTitle = "  风与书页  ") }
        compose.onNodeWithTag("book-detail-original-title").assertTextEquals("风与书页")
        toggle(original = true).assertDoesNotExist()
        compose.runOnIdle { current = current.copy(originalTitle = " \n ") }
        compose.onNodeWithTag("book-detail-original-title").assertDoesNotExist()
        compose.onNodeWithTag("book-detail-title").assertTextEquals("风与书页")
    }

    @Test fun aLongOriginalTitleCanExpandEvenWhenTheMainTitleFits() {
        val shortTitle = book.copy(ref = BookRef("wenku", "original-title"), title = "星之旅")
        compose.setContent { NoveliaTheme("light") {
            Column(Modifier.requiredWidth(200.dp).height(480.dp).verticalScroll(rememberScrollState())) { BookDetailTitles(shortTitle, subtitle) }
        } }
        assertComplete("book-detail-title")
        assertCollapsed("book-detail-original-title")
        toggle().assertDoesNotExist()
        toggle(original = true).assertContentDescriptionEquals("展开原文标题").performClick()
        assertComplete("book-detail-title")
        assertComplete("book-detail-original-title")
        toggle(original = true).performScrollTo().assertContentDescriptionEquals("收起原文标题").performClick()
        assertCollapsed("book-detail-original-title")
    }

    @Test fun overflowIsRecomputedWhenTheAvailableWidthOrSystemFontScaleChanges() {
        var width by mutableStateOf(360.dp)
        var scale by mutableFloatStateOf(1f)
        val medium = book.copy(title = "和伙伴们踏上旅途寻找那片遥远星空的故事", originalTitle = "")
        compose.setContent { NoveliaTheme("light") {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                Column(Modifier.requiredWidth(width).height(480.dp).verticalScroll(rememberScrollState())) { BookDetailTitles(medium, subtitle) }
            }
        } }
        toggle().assertDoesNotExist()
        assertComplete("book-detail-title")
        compose.runOnIdle { width = 180.dp }
        assertCollapsed("book-detail-title")
        toggle().assertIsDisplayed()
        compose.runOnIdle { width = 360.dp }
        toggle().assertDoesNotExist()
        compose.runOnIdle { scale = 2f }
        assertCollapsed("book-detail-title")
        toggle().performClick()
        assertComplete("book-detail-title")
        compose.runOnIdle { scale = 1f }
        // 放宽布局后仍能主动结束展开状态，再缩窄时恢复两行限制。
        toggle().performScrollTo().performClick()
        toggle().assertDoesNotExist()
        compose.runOnIdle { width = 180.dp }
        assertCollapsed("book-detail-title")
    }

    @Test fun aDifferentBookOrChangedTitleStartsCollapsed() {
        var current by mutableStateOf(book)
        compose.setContent { NoveliaTheme("light") {
            Column(Modifier.requiredWidth(240.dp).height(480.dp).verticalScroll(rememberScrollState())) { BookDetailTitles(current, subtitle) }
        } }
        toggle().performClick()
        toggle(original = true).performScrollTo().performClick()
        compose.runOnIdle { current = current.copy(originalTitle = current.originalTitle + "、第二部") }
        assertComplete("book-detail-title")
        assertCollapsed("book-detail-original-title")
        toggle(original = true).performScrollTo().performClick()
        compose.runOnIdle { current = current.copy(title = current.title + "，第二部") }
        assertCollapsed("book-detail-title")
        assertComplete("book-detail-original-title")
        toggle().performScrollTo().performClick()
        assertComplete("book-detail-title")
        compose.runOnIdle { current = current.copy(ref = BookRef("wenku", "another-book")) }
        assertCollapsed("book-detail-title")
        assertCollapsed("book-detail-original-title")
    }

    @Test fun expansionSurvivesPanelSwitchesAndRecreationInStaticMode() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AppInteractionMode(eInk = true, reducedMotion = true) {
                NoveliaTheme("light") {
                    var selected by rememberSaveable { mutableIntStateOf(0) }
                    AdaptiveBookDetail(selected, { selected = it }, listOf("简介", "目录", "讨论"), rememberSaveableStateHolder(),
                        Modifier.requiredWidth(320.dp).height(480.dp)) { panel ->
                        if(panel == 0) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 30.dp)) {
                            BookDetailTitles(book, subtitle)
                        } else Text("面板 $panel")
                    }
                }
            }
        }
        toggle().performClick()
        assertComplete("book-detail-title")
        compose.onNodeWithText("讨论").performClick()
        compose.onNodeWithText("简介").performClick()
        assertComplete("book-detail-title")
        assertCollapsed("book-detail-original-title")
        restoration.emulateSavedInstanceStateRestore()
        toggle().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "主标题已展开"))
        toggle(original = true).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "原文标题已折叠"))
        assertComplete("book-detail-title")
        toggle().performScrollTo().performClick()
        assertCollapsed("book-detail-title")
    }

    @Test fun titleHeightAnimatesInBothDirections() {
        assumeTrue(ValueAnimator.areAnimatorsEnabled())
        compose.setContent {
            AppInteractionMode(eInk = false, reducedMotion = false) {
                NoveliaTheme("light") {
                    Column(Modifier.requiredWidth(240.dp).height(480.dp).verticalScroll(rememberScrollState())) {
                        BookDetailTitles(book, subtitle)
                    }
                }
            }
        }
        val collapsed = titleHeight()
        compose.mainClock.autoAdvance = false
        try {
            toggle().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)
            val expanding = titleHeight()
            compose.mainClock.advanceTimeBy(AppMotion.Standard.toLong())
            val expanded = titleHeight()
            assertTrue(expanding > collapsed && expanding < expanded)
            assertCollapsed("book-detail-original-title")
            toggle().performScrollTo().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(80)
            val shrinking = titleHeight()
            assertTrue(shrinking > collapsed && shrinking < expanded)
            compose.mainClock.advanceTimeBy(AppMotion.Standard.toLong())
            assertEquals(collapsed, titleHeight())
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun localTitleExpandsWithoutAnimationInEInkAndReducedMotionModes() {
        var eInk by mutableStateOf(true)
        compose.setContent {
            AppInteractionMode(eInk = eInk, reducedMotion = !eInk) {
                NoveliaTheme("light") {
                    Column(Modifier.requiredWidth(240.dp).height(480.dp).verticalScroll(rememberScrollState())) {
                        BookDetailTitle("local:book", book.title)
                    }
                }
            }
        }
        val collapsed = titleHeight()
        compose.mainClock.autoAdvance = false
        try {
            for(inkMode in listOf(true, false)) {
                compose.runOnIdle { eInk = inkMode }
                compose.mainClock.advanceTimeByFrame()
                toggle().performClick()
                compose.mainClock.advanceTimeByFrame()
                assertComplete("book-detail-title")
                val expanded = titleHeight()
                assertTrue(expanded > collapsed)
                compose.mainClock.advanceTimeBy(AppMotion.Standard.toLong())
                assertEquals(expanded, titleHeight())
                toggle().performScrollTo().performClick()
                compose.mainClock.advanceTimeByFrame()
                assertEquals(collapsed, titleHeight())
            }
        } finally { compose.mainClock.autoAdvance = true }
    }

    private fun toggle(original: Boolean = false) =
        compose.onNodeWithTag(if(original) "book-detail-original-title-toggle" else "book-detail-title-toggle")

    private fun titleHeight() = compose.onNodeWithTag("book-detail-title-row").getUnclippedBoundsInRoot().let { it.bottom - it.top }

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    private fun assertCollapsed(tag: String) {
        val result = layout(tag)
        assertEquals(2, result.lineCount)
        assertTrue(result.hasVisualOverflow)
        assertTrue(result.isLineEllipsized(1))
    }

    private fun assertComplete(tag: String) {
        val result = layout(tag)
        assertFalse(result.hasVisualOverflow)
        assertEquals(result.layoutInput.text.length, result.getLineEnd(result.lineCount - 1))
    }
}
