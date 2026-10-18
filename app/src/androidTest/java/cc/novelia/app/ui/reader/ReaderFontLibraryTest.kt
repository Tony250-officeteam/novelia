package cc.novelia.app.ui.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import cc.novelia.app.data.model.ReaderCustomFont
import cc.novelia.app.data.model.ReaderSettings
import cc.novelia.app.reader.ReaderFontStore
import cc.novelia.app.ui.saveTestScreenshot
import cc.novelia.app.ui.theme.NoveliaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ReaderFontLibraryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun libraryRetainsNamesAndMultipleFontsAfterReloadAndInvalidatesDeletedFonts() = runBlocking {
        val fixture = FontFixture()
        try {
            val first = fixture.importFont(0, "第一款.ttf")
            val second = fixture.importFont(1, "第二款.ttf")
            assertEquals(setOf(first, second), ReaderFontStore.library(fixture.context).toSet())
            // 重新从磁盘读取，且重复导入只更新同一条记录。
            val renamed = fixture.importFont(0, "第一款的新名称.ttf")
            assertEquals(first.id, renamed.id)
            assertEquals(setOf(renamed, second), ReaderFontStore.library(fixture.newContext()).toSet())
            val selected = ReaderSettings(fontFamily = "custom", customFont = first)
            assertNotNull(ReaderFontStore.load(fixture.context, selected))
            ReaderFontStore.remove(fixture.context, first)
            assertNull(ReaderFontStore.load(fixture.context, selected))
            assertEquals(listOf(second), ReaderFontStore.library(fixture.context))
            assertNotNull(ReaderFontStore.load(fixture.context, selected.copy(customFont = second)))
        } finally { fixture.close() }
    }

    @Test fun libraryRecoversAllLegacyFilesAndKeepsKnownNames() = runBlocking {
        val fixture = FontFixture()
        try {
            val first = fixture.importFont(0, "旧版字体.ttf")
            val second = fixture.importFont(1, "另一个旧版字体.ttf")
            val fonts = File(fixture.context.filesDir, "reader-fonts")
            // 模拟旧版只有内容摘要命名的文件，没有字体库元数据。
            assertTrue(File(fonts, "${first.id}.json").delete())
            assertTrue(File(fonts, "${second.id}.json").delete())
            val recovered = ReaderFontStore.library(fixture.context, listOf(first))
            assertEquals(setOf(first.id, second.id), recovered.map { it.id }.toSet())
            assertEquals(first, recovered.single { it.id == first.id })
            assertTrue(recovered.single { it.id == second.id }.name.startsWith("历史字体"))
            // 另一书的旧设置后来提供名称时，仍能补全该记录。
            assertEquals(setOf(first, second), ReaderFontStore.library(fixture.context, listOf(second)).toSet())
            assertEquals(setOf(first, second), ReaderFontStore.library(fixture.newContext()).toSet())
        } finally { fixture.close() }
    }

    @Test fun batchImportKeepsSuccessfulFontsAndAllowsSwitchingReopeningAndDeleting() {
        val fixture = FontFixture()
        var open by mutableStateOf(true)
        var settings by mutableStateOf(ReaderSettings(fontSize = 23f))
        try {
            val firstFile = fixture.source(0, "清风宋体.ttf")
            val secondFile = fixture.source(1, "书页黑体.ttf")
            val badFile = File(firstFile.parentFile, "损坏的字体.ttf").apply { writeText("not a font", Charsets.UTF_8) }
            val uris = listOf(firstFile, badFile, secondFile).map { FileProvider.getUriForFile(fixture.app, "${fixture.app.packageName}.files", it) }
            val registry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    dispatchResult(requestCode, uris)
                }
            }
            val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
            compose.setContent {
                CompositionLocalProvider(LocalContext provides fixture.context, LocalActivityResultRegistryOwner provides owner) {
                    NoveliaTheme("dark") {
                        if(open) ReaderFontDialog(settings, null, { open = false }) { settings = it }
                    }
                }
            }
            compose.onNodeWithTag("reader-font-import").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("成功 2 款，失败 1 款。", substring = true).fetchSemanticsNodes().isNotEmpty() }
            val fonts = runBlocking { ReaderFontStore.library(fixture.context) }
            assertEquals(2, fonts.size)
            val first = fonts.single { it.name == firstFile.name }
            val second = fonts.single { it.name == secondFile.name }
            // 不切换当前字体，直接检查每一行实际渲染的字体，防止所有行误用当前字体。
            for(imported in fonts) {
                val tag = "reader-font-sample-latin-custom-${imported.id}"
                compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
                val sample = compose.onNodeWithTag(tag, useUnmergedTree = true)
                sample.performScrollTo().assertTextEquals(READER_FONT_SAMPLE_LATIN)
                val layouts = mutableListOf<TextLayoutResult>()
                sample.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val expected = ReaderFontStore.load(fixture.context, ReaderSettings(fontFamily = "custom", customFont = imported))!!
                val input = layouts.single().layoutInput
                val resolved = input.fontFamilyResolver.resolve(input.style.fontFamily, FontWeight.Normal, FontStyle.Normal, FontSynthesis.None).value
                assertEquals(expected, resolved)
                assertEquals(FontWeight.Normal, input.style.fontWeight)
            }
            compose.runOnIdle { assertEquals(second, settings.customFont) }
            compose.onNodeWithTag("reader-font-option-custom-${first.id}").performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(first, settings.customFont); assertEquals(23f, settings.fontSize) }
            compose.onNodeWithText("完成").performClick()
            compose.runOnIdle { open = true }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("reader-font-option-custom-${second.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("reader-font-option-custom-${first.id}").performScrollTo().assertIsSelected()
            compose.onNodeWithTag("reader-font-import").performScrollTo()
            saveTestScreenshot("reader-font-library.png")
            compose.onNodeWithTag("reader-font-delete-custom-${second.id}").performScrollTo().performClick()
            compose.onNodeWithText("取消").performClick()
            assertEquals(2, runBlocking { ReaderFontStore.library(fixture.context).size })
            compose.onNodeWithTag("reader-font-delete-custom-${second.id}").performClick()
            compose.onNodeWithTag("reader-font-confirm-delete").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("reader-font-option-custom-${second.id}").fetchSemanticsNodes().isEmpty() }
            compose.runOnIdle { assertEquals(first, settings.customFont) }
            compose.onNodeWithTag("reader-font-delete-custom-${first.id}").performScrollTo().performClick()
            compose.onNodeWithTag("reader-font-confirm-delete").performClick()
            compose.waitUntil(5_000) { settings.customFont == null }
            compose.runOnIdle { assertEquals("system", settings.fontFamily); assertEquals(23f, settings.fontSize) }
            assertTrue(runBlocking { ReaderFontStore.library(fixture.context).isEmpty() })
        } finally {
            compose.runOnIdle { open = false }
            compose.waitForIdle()
            fixture.close()
        }
    }

    private class FontFixture {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        private val root = File(app.filesDir, "exports/font-library-test-${System.nanoTime()}").apply { mkdirs() }
        private val originals = File("/system/fonts").listFiles()!!.filter { it.extension == "ttf" }.sortedBy { it.name }.take(2)
        val context = newContext()
        fun newContext(): Context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(root, "storage").apply { mkdirs() }
            override fun getApplicationContext(): Context = this
        }
        fun source(index: Int, name: String): File = File(root, name).also { originals[index].copyTo(it, overwrite = true) }
        suspend fun importFont(index: Int, name: String): ReaderCustomFont = originals[index].inputStream().use { ReaderFontStore.importStream(context, name, it) }
        fun close() { root.deleteRecursively() }
    }
}
