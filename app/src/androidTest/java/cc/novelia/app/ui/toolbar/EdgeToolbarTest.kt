package cc.novelia.app.ui.toolbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
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
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("阅读历史").assertIsDisplayed()
        compose.onNodeWithText("下载管理").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(9_000)
        compose.onNodeWithTag("edge-toolbar-panel").assertIsDisplayed()
        compose.onNodeWithContentDescription("收起快捷入口").performClick()
        compose.mainClock.advanceTimeBy(400)
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
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("edge-toolbar-dot").assertDoesNotExist()
        compose.runOnIdle { visible = true; page = "reader" }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("edge-toolbar-dot").assert(halfHidden).performClick()
        compose.mainClock.advanceTimeBy(400)
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

    @Test fun dotAndPanelAnimateBothWaysAndClosingActionsAreDisabled() {
        var visible by mutableStateOf(true)
        var navigations = 0
        compose.setContent {
            MaterialTheme(lightColorScheme(primary = Color.Red, surfaceContainerHigh = Color.Blue, scrim = Color.White)) {
                Box(Modifier.size(360.dp, 640.dp).background(Color.White)) {
                    EdgeToolbar(EdgeToolbarSettings(idleSeconds = 0), visible,
                        onPositionChange = {}, onNavigate = { navigations++ })
                }
            }
        }
        val dot = compose.onNodeWithTag("edge-toolbar-dot")
        val original = dot.fetchSemanticsNode().boundsInRoot
        val sample = Offset(original.center.x, original.top + original.height * .75f)
        assertEquals(0f, sampleLayer(sample).green, .01f)
        dot.performClick()
        val panel = compose.onNodeWithTag("edge-toolbar-panel")
        val fullPanel = panel.fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeBy(80)
        dot.assertExists().assertHasNoClickAction()
        panel.assertExists()
        compose.onNodeWithText("阅读历史").assertDoesNotExist()
        assertTrue("退场圆点应处于部分透明状态", sampleLayer(sample).green in .01f.. .99f)
        assertTrue("退场面板应轻微缩小", panel.fetchSemanticsNode().boundsInRoot.width < fullPanel.width)
        panel.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, navigations) }
        compose.mainClock.advanceTimeBy(400)
        dot.assertDoesNotExist()
        panel.assertDoesNotExist()
        assertEquals(Color.White, sampleLayer(sample))

        compose.runOnIdle { visible = true }
        compose.mainClock.advanceTimeBy(80)
        assertEquals("显现动画不能移动圆点", original, dot.fetchSemanticsNode().boundsInRoot)
        assertTrue("入场圆点应处于部分透明状态", sampleLayer(sample).green in .01f.. .99f)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(0f, sampleLayer(sample).green, .01f)
        dot.performClick()
        compose.mainClock.advanceTimeBy(80)
        assertTrue(panel.fetchSemanticsNode().boundsInRoot.width < fullPanel.width)
        // 入场、退场尚未结束时连续切换，不留残影或重新创建圆点位置。
        dot.performClick()
        compose.mainClock.advanceTimeBy(48)
        dot.performClick()
        compose.mainClock.advanceTimeBy(400)
        assertEquals(fullPanel, panel.fetchSemanticsNode().boundsInRoot)
        assertEquals(original, dot.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun staticModesImmediatelyShowAndRemoveDotAndPanel() {
        var eInk by mutableStateOf(true)
        var reduced by mutableStateOf(false)
        var visible by mutableStateOf(true)
        compose.setContent { NoveliaTheme("light") { AppInteractionMode(eInk, reduced) {
            Box(Modifier.size(360.dp, 640.dp)) {
                EdgeToolbar(EdgeToolbarSettings(idleSeconds = 0), visible, onPositionChange = {}, onNavigate = {})
            }
        } } }
        compose.mainClock.autoAdvance = false
        for(ink in listOf(true, false)) {
            compose.runOnIdle { eInk = ink; reduced = !ink; visible = true }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("edge-toolbar-dot").assertIsDisplayed().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("edge-toolbar-panel").assertIsDisplayed()
            compose.runOnIdle { visible = false }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("edge-toolbar-dot").assertDoesNotExist()
            compose.onNodeWithTag("edge-toolbar-panel").assertDoesNotExist()
        }
    }

    @Test fun enablingStaticModeDuringExitImmediatelyRemovesRetainedContent() {
        var eInk by mutableStateOf(false)
        var reduced by mutableStateOf(false)
        var visible by mutableStateOf(true)
        compose.setContent { NoveliaTheme("light") { AppInteractionMode(eInk, reduced) {
            Box(Modifier.size(360.dp, 640.dp)) {
                EdgeToolbar(EdgeToolbarSettings(idleSeconds = 0), visible, onPositionChange = {}, onNavigate = {})
            }
        } } }
        for(ink in listOf(false, true)) {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { eInk = false; reduced = false; visible = true }
            compose.onNodeWithTag("edge-toolbar-dot").performClick()
            compose.onNodeWithTag("edge-toolbar-panel").assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { visible = false }
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("edge-toolbar-dot").assertExists()
            compose.onNodeWithTag("edge-toolbar-panel").assertExists()
            compose.runOnIdle { eInk = ink; reduced = !ink }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("edge-toolbar-dot").assertDoesNotExist()
            compose.onNodeWithTag("edge-toolbar-panel").assertDoesNotExist()
        }
    }

    @Test fun eInkDragMovesOnceOnReleaseAndCancelDoesNotSave() {
        var saves = 0
        var saved = .55f
        compose.setContent { NoveliaTheme("light") { AppInteractionMode(true, false) {
            Box(Modifier.size(360.dp, 640.dp)) {
                EdgeToolbar(EdgeToolbarSettings(idleSeconds = 0), true,
                    onPositionChange = { saves++; saved = it }, onNavigate = {})
            }
        } } }
        val dot = compose.onNodeWithTag("edge-toolbar-dot")
        val original = dot.fetchSemanticsNode().boundsInRoot
        dot.performTouchInput { down(center); moveBy(Offset(0f, -80f)) }
        assertEquals(original, dot.fetchSemanticsNode().boundsInRoot)
        dot.performTouchInput { moveBy(Offset(0f, -80f)) }
        assertEquals(original, dot.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(0, saves) }
        dot.performTouchInput { up() }
        val moved = dot.fetchSemanticsNode().boundsInRoot
        assertTrue(moved.top < original.top)
        compose.runOnIdle { assertEquals(1, saves); assertTrue(saved < .55f) }
        dot.performTouchInput { down(center); moveBy(Offset(0f, 100f)) }
        assertEquals(moved, dot.fetchSemanticsNode().boundsInRoot)
        dot.performTouchInput { cancel() }
        assertEquals(moved, dot.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(1, saves) }
        compose.onNodeWithTag("edge-toolbar-panel").assertDoesNotExist()
    }

    @Test fun switchingToEInkCancelsAnActiveContinuousDrag() {
        var eInk by mutableStateOf(false)
        var saves = 0
        compose.setContent { NoveliaTheme("light") { AppInteractionMode(eInk, true) {
            Box(Modifier.size(360.dp, 640.dp)) {
                EdgeToolbar(EdgeToolbarSettings(idleSeconds = 0), true,
                    onPositionChange = { saves++ }, onNavigate = {})
            }
        } } }
        val dot = compose.onNodeWithTag("edge-toolbar-dot")
        val original = dot.fetchSemanticsNode().boundsInRoot
        dot.performTouchInput { down(center); moveBy(Offset(0f, -120f)) }
        assertTrue("普通/减少动效模式仍支持手动跟手定位", dot.fetchSemanticsNode().boundsInRoot.top < original.top)
        compose.runOnIdle { eInk = true }
        assertEquals(original, dot.fetchSemanticsNode().boundsInRoot)
        dot.performTouchInput { cancel() }
        compose.runOnIdle { assertEquals(0, saves) }
    }

    private fun sampleLayer(point: Offset): Color {
        val layer = compose.onNodeWithTag("edge-toolbar-layer")
        val bounds = layer.fetchSemanticsNode().boundsInRoot
        val pixels = layer.captureToImage().toPixelMap()
        return pixels[(point.x - bounds.left).toInt(), (point.y - bounds.top).toInt()]
    }
}
