package cc.novelia.app.ui.shelf

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cc.novelia.app.ui.theme.NoveliaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CloudFavoriteBatchUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun localFolderChoiceAndBusyBatchActionsStayReachableAtNarrowWidths() {
        var managing by mutableStateOf(true)
        var selected by mutableIntStateOf(0)
        var busy by mutableStateOf(false)
        var local by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var savedFolder: String? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                NoveliaTheme("dark") {
                    Surface(Modifier.requiredWidth(320.dp)) {
                        Column {
                            ShelfSelectionHeader(selected, !busy, 48.dp, { selected = if(selected == 2) 0 else 2 },
                                { managing = false }, "cloud", if(selected == 2) "取消本页全选" else "全选本页")
                            if(managing) CloudSelectionActions(selected, busy, 1, { local = true }, { busy = true }, { selected = 0 })
                        }
                    }
                    if(local) CloudFavoriteLocalSheet(listOf("默认收藏", "周末阅读"), selected, { local = false }) {
                        savedFolder = it; local = false
                    }
                }
            }
        }
        compose.onNodeWithText("批量整理").assertDoesNotExist()
        compose.onNodeWithContentDescription("加入本地收藏").assertIsNotEnabled()
        compose.onNodeWithContentDescription("取消云端收藏").assertIsNotEnabled()
        compose.onNodeWithText("全选本页").performClick()
        compose.onNodeWithText("已选 2 本").assertIsDisplayed()
        compose.onNodeWithContentDescription("加入本地收藏").performClick()
        compose.onNodeWithText("周末阅读").performClick()
        compose.runOnIdle { assertEquals("周末阅读", savedFolder); scale = 2f }
        compose.onNodeWithContentDescription("加入本地收藏").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("取消云端收藏").assertIsDisplayed().performClick()
        compose.onNodeWithText("正在处理 1 / 2 本…").assertIsDisplayed()
        listOf("cloud-selection-done", "cloud-select-all", "cloud-selection-clear", "cloud-selection-local", "cloud-selection-remove").forEach {
            compose.onNodeWithTag(it).assertIsNotEnabled()
        }
    }
}
