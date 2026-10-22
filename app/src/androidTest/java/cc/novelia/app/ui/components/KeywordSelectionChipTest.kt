package cc.novelia.app.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.catalog.KeywordSelectionState
import cc.novelia.app.ui.components.keywords.KeywordSelectionChip
import cc.novelia.app.ui.theme.AppInteractionMode
import cc.novelia.app.ui.theme.NoveliaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeywordSelectionChipTest {
    @get:Rule val compose = createComposeRule()

    private fun background(): Int {
        val pixels = compose.onNodeWithTag("tag").captureToImage().toPixelMap()
        // 标签左侧内边距，不采样边框或文字。
        return pixels[pixels.width / 20, pixels.height / 2].toArgb()
    }

    @Test fun allThreeColorsAnimateWithoutResizingInLightAndDarkThemes() {
        var dark by mutableStateOf(false)
        var state by mutableStateOf(KeywordSelectionState.Unselected)
        lateinit var scheme: ColorScheme
        compose.setContent { NoveliaTheme(if(dark) "dark" else "light") { AppInteractionMode(false, false) {
            scheme = MaterialTheme.colorScheme
            KeywordSelectionChip("标签", state, {}, Modifier.width(160.dp).testTag("tag"))
        } } }
        for(darkMode in listOf(false, true)) {
            compose.runOnIdle { dark = darkMode }
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("tag").getUnclippedBoundsInRoot()
            assertEquals(scheme.surfaceContainerLow.toArgb(), background())
            compose.mainClock.autoAdvance = false
            try {
                for((next, target) in listOf(KeywordSelectionState.Included to scheme.primaryContainer,
                    KeywordSelectionState.Excluded to scheme.errorContainer, KeywordSelectionState.Unselected to scheme.surfaceContainerLow)) {
                    val before = background()
                    compose.runOnIdle { state = next }
                    compose.mainClock.advanceTimeBy(64)
                    val between = background()
                    assertNotEquals("颜色需要出现过渡帧", before, between)
                    assertNotEquals("颜色不应直接跳到目标值", target.toArgb(), between)
                    compose.mainClock.advanceTimeBy(240)
                    assertEquals(target.toArgb(), background())
                    assertEquals(bounds, compose.onNodeWithTag("tag").getUnclippedBoundsInRoot())
                }
            } finally { compose.mainClock.autoAdvance = true }
        }
    }

    @Test fun reducedMotionAndEInkApplyColorsImmediatelyEvenDuringATransition() {
        var state by mutableStateOf(KeywordSelectionState.Unselected)
        var reduced by mutableStateOf(false)
        var eInk by mutableStateOf(false)
        lateinit var scheme: ColorScheme
        compose.setContent { NoveliaTheme("light") { AppInteractionMode(eInk, reduced) {
            scheme = MaterialTheme.colorScheme
            KeywordSelectionChip("标签", state, {}, Modifier.width(160.dp).testTag("tag"))
        } } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { state = KeywordSelectionState.Included }
            compose.mainClock.advanceTimeBy(64)
            compose.runOnIdle { reduced = true }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(scheme.primaryContainer.toArgb(), background())
            compose.runOnIdle { state = KeywordSelectionState.Excluded }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(scheme.errorContainer.toArgb(), background())
            compose.runOnIdle { reduced = false; eInk = true; state = KeywordSelectionState.Unselected }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(scheme.surfaceContainerLow.toArgb(), background())
            compose.runOnIdle { state = KeywordSelectionState.Included }
            compose.mainClock.advanceTimeByFrame()
            assertEquals(scheme.primaryContainer.toArgb(), background())
        } finally { compose.mainClock.autoAdvance = true }
    }
}
