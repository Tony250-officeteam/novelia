package cc.novelia.app.ui.discover

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cc.novelia.app.ui.components.base.appHorizontalScroll
import cc.novelia.app.ui.theme.AppInteractionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DiscoverSwipeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun swipesAndTabClicksStayInSyncAndVerticalScrollingDoesNotSwitchSections() {
        compose.setContent {
            MaterialTheme {
                val pager = rememberPagerState { 3 }
                DiscoverPager(pager, rememberDiscoverNavigation(pager) {}, Modifier.size(360.dp, 480.dp)) { section ->
                    TestResults(section)
                }
            }
        }
        val pager = compose.onNodeWithTag("discover-pager")
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("discover-tab-0").assertIsSelected()
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("discover-tab-1").assertIsSelected()
        compose.onNodeWithTag("results-1").assertIsDisplayed()
        pager.performTouchInput { swipeUp() }
        compose.onNodeWithTag("discover-tab-1").assertIsSelected()
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("discover-tab-2").assertIsSelected()
        compose.onNodeWithTag("results-2").assertIsDisplayed()
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("discover-tab-2").assertIsSelected()
        compose.onNodeWithTag("discover-tab-0").performClick().assertIsSelected()
        compose.onNodeWithTag("results-0").assertIsDisplayed()
        compose.onNodeWithTag("discover-tab-2").performClick()
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("discover-tab-1").assertIsSelected()
    }

    @Test fun eachSectionRestoresItsScrollAndControls() = restoreSections(static = false)

    @Test fun staticModeSupportsSwipesAndRestoration() = restoreSections(static = true)

    private fun restoreSections(static: Boolean) {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AppInteractionMode(eInk = static, reducedMotion = static) {
                MaterialTheme {
                    val pager = rememberPagerState { 3 }
                    DiscoverPager(pager, rememberDiscoverNavigation(pager) {}, Modifier.size(360.dp, 480.dp)) { section ->
                        var filter by rememberSaveable { mutableStateOf("") }
                        Column(Modifier.fillMaxSize()) {
                            OutlinedTextField(filter, { filter = it }, Modifier.testTag("filter-$section"))
                            TestResults(section, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("results-0").performScrollToIndex(10)
        compose.onNodeWithTag("discover-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("filter-1").performTextReplacement("已完结")
        compose.onNodeWithTag("results-1").performScrollToIndex(15)
        compose.onNodeWithTag("discover-tab-2").performClick()
        compose.onNodeWithTag("filter-2").performTextReplacement("轻文学")
        compose.onNodeWithTag("results-2").performScrollToIndex(20)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("discover-tab-2").assertIsSelected()
        compose.onNodeWithTag("filter-2").assertTextContains("轻文学")
        compose.onNodeWithText("分区 2 作品 20").assertIsDisplayed()
        compose.onNodeWithTag("discover-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("filter-1").assertTextContains("已完结")
        compose.onNodeWithText("分区 1 作品 15").assertIsDisplayed()
        compose.onNodeWithTag("discover-tab-0").performClick()
        compose.onNodeWithTag("filter-0").assertTextEquals("")
        compose.onNodeWithText("分区 0 作品 10").assertIsDisplayed()
    }

    @Test fun externalNavigationAndTabClicksUseOnePositionEvenWhenInterrupted() {
        compose.setContent {
            MaterialTheme {
                // 带查询词进入发现页时，从网络小说开始。
                val pager = rememberPagerState(initialPage = 1) { 3 }
                val select = rememberDiscoverNavigation(pager) {}
                Column(Modifier.size(360.dp, 480.dp)) {
                    Row {
                        TextButton(onClick = { select(0) }) { Text("返回推荐") }
                        TextButton(onClick = { select(2) }) { Text("恢复文库搜索") }
                    }
                    DiscoverPager(pager, select, Modifier.weight(1f)) { section -> TestResults(section) }
                }
            }
        }
        compose.onNodeWithTag("discover-tab-1").assertIsSelected()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("返回推荐").performClick()
            compose.mainClock.advanceTimeBy(48)
            compose.onNodeWithText("恢复文库搜索").performClick()
        } finally { compose.mainClock.autoAdvance = true }
        compose.onNodeWithTag("discover-tab-2").assertIsSelected()
        compose.onNodeWithTag("results-2").assertIsDisplayed()
        compose.onNodeWithTag("discover-tab-1").performClick().assertIsSelected()
        compose.onNodeWithTag("discover-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("discover-tab-0").assertIsSelected()
    }

    @Test fun completingASwipeEndsSearchEditingAndKeepsTheUnsubmittedDraft() {
        var draft by mutableStateOf("魔法")
        var editing by mutableStateOf(false)
        var searches = 0
        compose.setContent {
            MaterialTheme {
                val pager = rememberPagerState { 3 }
                val select = rememberDiscoverNavigation(pager) { editing = false }
                DiscoverSearchLayout(draft, "魔法", editing, { draft = it }, { editing = it }, { searches++ }) {
                    DiscoverPager(pager, select) { section -> TestResults(section) }
                }
            }
        }
        compose.onNodeWithTag("discover-search-bar").performClick()
        compose.onNodeWithTag("discover-search-input").performTextReplacement("旅行")
        val pager = compose.onNodeWithTag("discover-pager")
        pager.performTouchInput {
            down(center)
            moveTo(center.copy(x = center.x - width * .3f), 300)
            moveTo(center, 300)
            up()
        }
        compose.onNodeWithTag("discover-tab-0").assertIsSelected()
        compose.onNodeWithTag("discover-search-input").assertTextContains("旅行")
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("discover-tab-1").assertIsSelected()
        compose.onNodeWithTag("discover-search-input").assertDoesNotExist()
        compose.onNodeWithTag("discover-search-bar").assertTextContains("魔法")
        compose.onNodeWithTag("discover-search-bar").performClick()
        compose.onNodeWithTag("discover-search-input").assertTextContains("旅行")
        compose.runOnIdle { assertEquals(0, searches) }
    }

    @Test fun horizontalSearchHistoryScrollsBeforeTheOuterPager() {
        lateinit var historyScroll: ScrollState
        compose.setContent {
            MaterialTheme {
                val pager = rememberPagerState { 3 }
                DiscoverPager(pager, rememberDiscoverNavigation(pager) {}, Modifier.size(360.dp, 480.dp)) { section ->
                    Column(Modifier.fillMaxSize()) {
                        if(section == 0) {
                            val scroll = rememberScrollState()
                            SideEffect { historyScroll = scroll }
                            Row(Modifier.fillMaxWidth().appHorizontalScroll(scroll).testTag("recent-searches")) {
                                repeat(20) { Text("搜索 $it", Modifier.width(120.dp).height(48.dp)) }
                            }
                        }
                        TestResults(section, Modifier.weight(1f))
                    }
                }
            }
        }
        compose.onNodeWithTag("recent-searches").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("discover-tab-0").assertIsSelected()
        compose.runOnIdle { assertTrue(historyScroll.value > 0) }
        compose.onNodeWithTag("results-0").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("discover-tab-1").assertIsSelected()
    }

    @Composable private fun TestResults(section: Int, modifier: Modifier = Modifier) {
        LazyColumn(modifier.fillMaxSize().testTag("results-$section")) {
            items(40) { Text("分区 $section 作品 $it", Modifier.fillMaxWidth().height(60.dp)) }
        }
    }
}
