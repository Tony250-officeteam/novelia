package cc.novelia.app.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.community.ForumCategory
import cc.novelia.app.ui.theme.AppInteractionMode
import cc.novelia.app.ui.theme.NoveliaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ForumSwipeTest {
    @get:Rule val compose = createComposeRule()
    private val categories = listOf(ForumCategory(2, "announcements"), ForumCategory(1, "novel"), ForumCategory(3, "feedback"))

    @Test fun categorySwipesAndClicksStayInSyncAndRestoreSelection() {
        val restoration = StateRestorationTester(compose)
        var selected = "announcements"
        restoration.setContent {
            NoveliaTheme("light") {
                // 故意不保存外层选择，验证分页器恢复后会同步它，避免筛选与页面错位。
                var category by remember { mutableStateOf("announcements") }
                ForumCategoryPager(categories, category, { category = it; selected = it }, Modifier.size(360.dp, 480.dp),
                    controls = { Text(category, Modifier.testTag("forum-selected-category")) }) {
                    LazyColumn(Modifier.fillMaxSize().testTag("category-content-${it.slug}")) {
                        items(40) { index -> Text("帖子 $index", Modifier.fillMaxWidth().height(60.dp)) }
                    }
                }
            }
        }
        val pager = compose.onNodeWithTag("forum-category-pager")
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("forum-category-novel").assertIsSelected()
        compose.onNodeWithTag("category-content-novel").assertIsDisplayed()
        compose.onNodeWithTag("forum-selected-category").assertTextEquals("novel")
        compose.runOnIdle { assertEquals("novel", selected) }
        pager.performTouchInput { swipeUp() }
        compose.onNodeWithTag("forum-category-novel").assertIsSelected()
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithTag("forum-category-feedback").assertIsSelected()
        pager.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals("feedback", selected) }
        compose.onNodeWithTag("forum-category-announcements").performClick().assertIsSelected()
        pager.performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals("announcements", selected) }
        compose.onNodeWithTag("forum-category-novel").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("forum-category-novel").assertIsSelected()
        compose.onNodeWithTag("category-content-novel").assertIsDisplayed()
        compose.onNodeWithTag("forum-selected-category").assertTextEquals("novel")
        compose.runOnIdle { assertEquals("novel", selected) }
    }

    @Test fun articleSwipesPreserveScrollAndCommentDraftAcrossRecreation() = articleStateSurvives(false)

    @Test fun articleSwipesAndTabClicksAlsoWorkInStaticMode() = articleStateSurvives(true)

    private fun articleStateSurvives(static: Boolean) {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            AppInteractionMode(eInk = static, reducedMotion = static) {
                NoveliaTheme("light") {
                    var selected by rememberSaveable { mutableIntStateOf(0) }
                    ArticleContentPager(selected, { selected = it }, 3, rememberSaveableStateHolder(), Modifier.size(360.dp, 480.dp)) { page ->
                        if(page == 0) LazyColumn(Modifier.fillMaxSize().testTag("article-body")) {
                            items(40) { Text("正文段落 $it", Modifier.fillMaxWidth().height(60.dp)) }
                        } else {
                            var draft by rememberSaveable { mutableStateOf("") }
                            OutlinedTextField(draft, { draft = it }, Modifier.testTag("article-draft"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("article-body").performScrollToIndex(12)
        val pager = compose.onNodeWithTag("article-content-pager")
        pager.performTouchInput { swipeLeft() }
        compose.onNodeWithText("讨论 3").assertIsSelected()
        compose.onNodeWithTag("article-draft").performTextInput("尚未发送的评论")
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithText("文章内容").assertIsSelected()
        compose.onNodeWithText("正文段落 12").assertIsDisplayed()
        compose.onNodeWithText("讨论 3").performClick()
        compose.onNodeWithTag("article-draft").assertTextContains("尚未发送的评论")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("讨论 3").assertIsSelected()
        compose.onNodeWithTag("article-draft").assertTextContains("尚未发送的评论")
        compose.onNodeWithText("文章内容").performClick()
        compose.onNodeWithText("正文段落 12").assertIsDisplayed()
    }
}
