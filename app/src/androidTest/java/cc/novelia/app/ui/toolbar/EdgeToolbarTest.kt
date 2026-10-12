package cc.novelia.app.ui.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.novelia.app.data.model.EdgeToolbarSettings
import cc.novelia.app.ui.theme.AppInteractionMode
import cc.novelia.app.ui.theme.NoveliaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun idleDotOpensInOneTapAndPanelDoesNotTimeOut() {
        compose.setContent { NoveliaTheme("light") { Box(Modifier.size(360.dp, 640.dp)) {
            EdgeToolbar(EdgeToolbarSettings(), true, onPositionChange = {}, onNavigate = {})
        } } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3_500)
        compose.onNodeWithTag("edge-toolbar-dot").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "半隐藏，可上下拖动"))
            .performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("阅读历史").assertIsDisplayed()
        compose.onNodeWithText("下载管理").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(9_000)
        compose.onNodeWithTag("edge-toolbar-panel").assertIsDisplayed()
        compose.onNodeWithContentDescription("收起快捷入口").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("edge-toolbar-panel").assertDoesNotExist()
    }

    @Test fun verticalDragSavesPositionWithoutOpeningPanel() {
        var saved: Float? = null
        var page by mutableStateOf("shelf")
        compose.setContent { NoveliaTheme("light") { AppInteractionMode(false, true) {
            Box(Modifier.size(360.dp, 640.dp)) {
                EdgeToolbar(EdgeToolbarSettings(idleSeconds = 0), true, pageKey = page,
                    onPositionChange = { saved = it }, onNavigate = {})
            }
        } } }
        compose.onNodeWithTag("edge-toolbar-dot").performTouchInput { swipe(center, center + Offset(0f, -160f)) }
        compose.onNodeWithTag("edge-toolbar-panel").assertDoesNotExist()
        compose.runOnIdle { assertNotNull(saved); assertTrue(saved!! < .55f); assertTrue(saved!! >= 0f) }
        val dragged = compose.onNodeWithTag("edge-toolbar-dot").fetchSemanticsNode().boundsInRoot
        // 切页可能早于持久化投影回传，不能用旧的 .55 设置重建拖动位置。
        compose.runOnIdle { page = "history" }
        assertEquals(dragged, compose.onNodeWithTag("edge-toolbar-dot").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("edge-toolbar-dot").performClick()
        compose.onNodeWithTag("edge-toolbar-panel").assertIsDisplayed()
    }

    @Test fun suppressionClosesPanelAndDoesNotReopenItOnReturn() {
        var visible by mutableStateOf(true)
        compose.setContent { NoveliaTheme("dark") { Box(Modifier.size(360.dp, 640.dp)) {
            EdgeToolbar(EdgeToolbarSettings(), visible, onPositionChange = {}, onNavigate = {})
        } } }
        compose.onNodeWithTag("edge-toolbar-dot").performClick()
        compose.onNodeWithTag("edge-toolbar-panel").assertIsDisplayed()
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("edge-toolbar-dot").assertDoesNotExist()
        compose.onNodeWithTag("edge-toolbar-panel").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("edge-toolbar-dot").assertIsDisplayed()
        compose.onNodeWithTag("edge-toolbar-panel").assertDoesNotExist()
    }

    @Test fun largeTextPanelStaysInsideShortWindowOnBothSidesAndAtBothEnds() {
        var settings by mutableStateOf(EdgeToolbarSettings(idleSeconds = 0))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 1.6f)) {
                NoveliaTheme("light") { AppInteractionMode(true, true) {
                    Box(Modifier.size(320.dp, 240.dp)) {
                        EdgeToolbar(settings, true, onPositionChange = {}, onNavigate = {})
                    }
                } }
            }
        }
        for(side in listOf("left", "right")) for(position in listOf(0f, 1f)) {
            compose.runOnIdle { settings = settings.copy(side = side, position = position) }
            compose.onNodeWithTag("edge-toolbar-dot").performClick()
            val layer = compose.onNodeWithTag("edge-toolbar-layer").fetchSemanticsNode().boundsInRoot
            val panel = compose.onNodeWithTag("edge-toolbar-panel").fetchSemanticsNode().boundsInRoot
            val dot = compose.onNodeWithTag("edge-toolbar-dot").fetchSemanticsNode().boundsInRoot
            assertTrue(panel.top >= layer.top && panel.bottom <= layer.bottom)
            assertTrue(panel.left >= layer.left && panel.right <= layer.right)
            if(side == "left") assertTrue(panel.left >= dot.right) else assertTrue(panel.right <= dot.left)
            compose.onNodeWithText("下载管理").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("edge-toolbar-dot").performClick()
        }
    }

    @Test fun changingPageChromeAndReaderVisibilityNeverMovesTheDot() {
        var page by mutableStateOf("shelf")
        var visible by mutableStateOf(true)
        var top by mutableIntStateOf(80)
        var bottom by mutableIntStateOf(120)
        var left by mutableIntStateOf(0)
        compose.setContent { NoveliaTheme("light") { Box(Modifier.size(360.dp, 640.dp)) {
            EdgeToolbar(EdgeToolbarSettings(side = "left", position = .85f, idleSeconds = 0), visible,
                pageKey = page, leftInset = left, topInset = top, bottomInset = bottom,
                onPositionChange = {}, onNavigate = {})
        } } }
        val dot = compose.onNodeWithTag("edge-toolbar-dot")
        val original = dot.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { page = "reader"; top = 0; bottom = 0; left = 80 }
        assertEquals(original, dot.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { top = 160; bottom = 260 }
        assertEquals(original, dot.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { visible = false; top = 0; bottom = 0 }
        dot.assertDoesNotExist()
        compose.runOnIdle { visible = true }
        assertEquals(original, dot.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { top = 160; bottom = 260 }
        assertEquals(original, dot.fetchSemanticsNode().boundsInRoot)
        dot.performClick()
        val panel = compose.onNodeWithTag("edge-toolbar-panel").fetchSemanticsNode().boundsInRoot
        val layer = compose.onNodeWithTag("edge-toolbar-layer").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.left >= layer.left + left)
        assertTrue(panel.top >= layer.top + top && panel.bottom <= layer.bottom - bottom)
    }

    @Test fun halfHiddenStateSurvivesNavigationAndTemporarySuppression() {
        var page by mutableStateOf("shelf")
        var visible by mutableStateOf(true)
        compose.setContent { NoveliaTheme("light") { Box(Modifier.size(360.dp, 640.dp)) {
            EdgeToolbar(EdgeToolbarSettings(), visible, pageKey = page, onPositionChange = {}, onNavigate = {})
        } } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(3_500)
        val halfHidden = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "半隐藏，可上下拖动")
        compose.onNodeWithTag("edge-toolbar-dot").assert(halfHidden)
        compose.runOnIdle { page = "history" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("edge-toolbar-dot").assert(halfHidden)
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("edge-toolbar-dot").assertDoesNotExist()
        compose.runOnIdle { visible = true; page = "reader" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("edge-toolbar-dot").assert(halfHidden).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("edge-toolbar-panel").assertIsDisplayed()
    }

    @Test fun navigationAndSuppressionDoNotRestartAnExistingIdleCountdown() {
        var page by mutableStateOf("shelf")
        var visible by mutableStateOf(true)
        compose.setContent { NoveliaTheme("light") { Box(Modifier.size(360.dp, 640.dp)) {
            EdgeToolbar(EdgeToolbarSettings(), visible, pageKey = page, onPositionChange = {}, onNavigate = {})
        } } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1_500)
        compose.runOnIdle { page = "reader" }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeBy(1_400)
        compose.runOnIdle { visible = true }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("edge-toolbar-dot").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "半隐藏，可上下拖动"))
    }
}
