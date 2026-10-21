package cc.novelia.app.ui.reader

import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cc.novelia.app.data.model.ReaderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReaderFontPanelLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun finishingFontLibraryLoadingDoesNotResizeTheCenteredPanel() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(app.cacheDir, "font-panel-${System.nanoTime()}").apply { mkdirs() }
        val loadingStarted = CountDownLatch(1)
        val finishLoading = CountDownLatch(1)
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext() = this
            override fun getFilesDir(): File {
                loadingStarted.countDown()
                check(finishLoading.await(15, TimeUnit.SECONDS)) { "字体库加载测试超时" }
                return directory
            }
        }
        try {
            compose.setContent {
                MaterialTheme {
                    ReaderPreferencesSheet({}) { _, _ ->
                        // 在父弹层的新窗口内提供上下文，并给内容足够高度以暴露 wrap_content 回跳。
                        val configuration = Configuration(LocalConfiguration.current).apply { screenHeightDp = 1000 }
                        CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides configuration) {
                            ReaderFontPreference(ReaderSettings(), null) {}
                        }
                    }
                }
            }
            compose.onNodeWithTag("reader-font-settings").performClick()
            compose.waitForIdle()
            assertTrue(loadingStarted.await(5, TimeUnit.SECONDS))
            compose.onNodeWithText("正在读取已导入字体…").assertIsDisplayed()
            val loadingBounds = compose.onNodeWithTag("reader-font-dialog").getUnclippedBoundsInRoot()
            finishLoading.countDown()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("正在读取已导入字体…").fetchSemanticsNodes().isEmpty()
            }
            val loadedBounds = compose.onNodeWithTag("reader-font-dialog").getUnclippedBoundsInRoot()
            assertEquals("字体加载完成不能改变窗口高度并触发重新居中",
                (loadingBounds.bottom - loadingBounds.top).value, (loadedBounds.bottom - loadedBounds.top).value, .5f)
            compose.onNodeWithTag("reader-font-import").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("完成").assertIsDisplayed().performClick()
            compose.onNodeWithTag("reader-font-dialog").assertDoesNotExist()
            compose.onNodeWithTag("reader-font-settings").assertIsDisplayed()
        } finally {
            finishLoading.countDown()
            compose.waitForIdle()
            directory.deleteRecursively()
        }
    }
}
