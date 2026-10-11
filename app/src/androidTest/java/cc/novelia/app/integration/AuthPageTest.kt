package cc.novelia.app.integration

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cc.novelia.app.MainActivity
import cc.novelia.app.NoveliaApplication
import cc.novelia.app.data.network.BookSource
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** 原生表单离线可见；只填虚构数据，不发送认证或邮件请求。 */
class AuthPageTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun containsWebView(view: View): Boolean = view is WebView ||
        (view is ViewGroup && (0 until view.childCount).any { containsWebView(view.getChildAt(it)) })

    @Test fun originalUsesNativeFormAndDoesNotRestorePassword() {
        val app = compose.activity.application as NoveliaApplication
        compose.waitUntil(15_000) { compose.onAllNodesWithText("我的").fetchSemanticsNodes().isNotEmpty() }
        val previous = app.bookSources.capture().source
        try {
            compose.runOnIdle { app.bookSources.select(BookSource.ORIGINAL) }
            compose.onNodeWithText("我的").performClick()
            compose.onNodeWithText("登录 / 注册").performClick()
            compose.onNodeWithTag("login-source-original").assertIsSelected()
            compose.onNodeWithTag("login-username").assertIsDisplayed()
            compose.onNodeWithTag("login-password").performTextInput("synthetic-password")
            compose.runOnIdle { assertFalse(containsWebView(compose.activity.window.decorView)) }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("login-password").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("login-password").assertTextEquals("密码", "")
            compose.onNodeWithText("没有账号，注册").performScrollTo().performClick()
            compose.onNodeWithTag("login-otp").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("已有账号，去登录").performScrollTo().performClick()
            compose.onNodeWithText("忘记密码？").performScrollTo().performClick()
            compose.onNodeWithText("找回密码").assertIsDisplayed()
            compose.onNodeWithTag("login-username").assertDoesNotExist()
            compose.onNodeWithTag("login-otp").performScrollTo().assertIsDisplayed()
        } finally { compose.runOnIdle { app.bookSources.select(previous) } }
    }
}
