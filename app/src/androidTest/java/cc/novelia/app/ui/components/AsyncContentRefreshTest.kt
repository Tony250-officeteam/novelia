package cc.novelia.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cc.novelia.app.ui.components.base.AppLazyColumn
import cc.novelia.app.ui.components.base.AsyncContent
import cc.novelia.app.ui.theme.AppInteractionMode
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AsyncContentRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test fun onlyPullingPastTheTopRefreshesAndPendingRefreshCannotBeDuplicated() {
        var requests = 0
        val pending = CompletableDeferred<String>()
        compose.setContent {
            MaterialTheme {
                AsyncContent("forum", load = {
                    if(++requests == 1) "原列表" else pending.await()
                }, modifier = Modifier.size(360.dp, 480.dp), pullToRefreshEnabled = true) { value, _ ->
                    AppLazyColumn(Modifier.fillMaxSize(), listModifier = Modifier.testTag("feed")) {
                        items(50) { Text("$value $it", Modifier.fillMaxWidth().height(64.dp)) }
                    }
                }
            }
        }
        val feed = compose.onNodeWithTag("feed")
        feed.performScrollToIndex(25).performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, requests) }
        feed.performScrollToIndex(0).performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(1, requests) }
        feed.performScrollToIndex(0).performTouchInput {
            swipe(center, center + Offset(0f, 12f), durationMillis = 300)
        }
        compose.runOnIdle { assertEquals(1, requests) }
        feed.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(1, requests) }
        feed.performTouchInput { swipeDown() }
        compose.waitUntil { requests == 2 }
        compose.onNodeWithText("原列表 0").assertIsDisplayed()
        feed.performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(2, requests) }
        compose.runOnIdle { pending.complete("新列表") }
        compose.onNodeWithText("新列表 0").assertIsDisplayed()
    }

    @Test fun emptyFeedCanRefreshAndFailureKeepsTheCachedContentForRetry() {
        var requests = 0
        var shown by mutableStateOf(true)
        var cached: String? = "空列表"
        val pending = CompletableDeferred<String>()
        compose.setContent {
            AppInteractionMode(eInk = false, reducedMotion = true) {
                MaterialTheme {
                    if(shown) AsyncContent("forum", load = {
                        if(++requests == 1) pending.await() else "已更新"
                    }, initialResult = cached, onLoaded = { cached = it },
                        modifier = Modifier.size(360.dp, 480.dp), pullToRefreshEnabled = true) { value, _ ->
                        AppLazyColumn(Modifier.fillMaxSize(), listModifier = Modifier.testTag("feed")) {
                            item { Text(value) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("空列表").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("feed").performTouchInput { swipeDown() }
        compose.waitUntil { requests == 1 }
        compose.runOnIdle { pending.completeExceptionally(IllegalArgumentException("测试刷新失败")) }
        compose.onNodeWithText("空列表").assertIsDisplayed()
        compose.onNodeWithText("刷新未完成：测试刷新失败").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("已更新").assertIsDisplayed()
        compose.runOnIdle { shown = false }
        compose.runOnIdle { shown = true }
        compose.onNodeWithText("已更新").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun returningToCachedContentRestoresScrollWithoutLoadingAndNewIdentityStillLoads() {
        var requests = 0
        var shown by mutableStateOf(true)
        var identity by mutableStateOf("account-a")
        val cache = mutableMapOf<String, String>()
        compose.setContent {
            AppInteractionMode(eInk = false, reducedMotion = true) {
                MaterialTheme {
                    val pages = rememberSaveableStateHolder()
                    if(shown) pages.SaveableStateProvider("feed") {
                        val scroll = key(identity) { rememberLazyListState() }
                        AsyncContent(identity, load = { requests++; identity }, initialResult = cache[identity],
                            onLoaded = { cache[identity] = it }, modifier = Modifier.size(360.dp, 480.dp),
                            revealContent = true, pullToRefreshEnabled = true) { value, _ ->
                            AppLazyColumn(Modifier.fillMaxSize(), state = scroll, listModifier = Modifier.testTag("feed")) {
                                items(50) { Text("$value $it", Modifier.fillMaxWidth().height(64.dp)) }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("feed").performScrollToIndex(20)
        repeat(2) {
            compose.runOnIdle { shown = false }
            compose.runOnIdle { shown = true }
            compose.onNodeWithText("account-a 20").assertIsDisplayed()
        }
        compose.runOnIdle { assertEquals(1, requests); identity = "account-b" }
        compose.onNodeWithText("account-b 0").assertIsDisplayed()
        compose.onNodeWithText("account-a 20").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, requests) }
    }
}
