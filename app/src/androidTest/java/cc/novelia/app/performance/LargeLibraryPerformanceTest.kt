package cc.novelia.app.performance

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.novelia.app.data.library.shelfGroups
import cc.novelia.app.data.model.Position
import cc.novelia.app.data.model.ReadingHistoryEntry
import cc.novelia.app.data.storage.LocalStore
import cc.novelia.app.data.storage.appJson
import cc.novelia.app.files.importing.importLocalDocument
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 真实导入 2000 个小 TXT；所有书目、正文、进度均位于隔离上下文，结束后删除。 */
@RunWith(AndroidJUnit4::class)
class LargeLibraryPerformanceTest {
    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        override fun getApplicationContext(): Context = this
    }

    @Test fun cancelledBatchKeepsCompletedBooksAndProgress(): Unit = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "cancelled-library-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = IsolatedContext(base, root)
        val store = withContext(Dispatchers.IO) { LocalStore(isolated) }
        try {
            withContext(Dispatchers.IO) {
                val input = File(root, "已有书.txt").apply { writeText("第一章\n\n不能丢失的正文。", Charsets.UTF_8) }
                val first = importLocalDocument(store, input)
                val chapter = store.documentIndex(first.ref.id).chapters.first()
                val position = Position(chapter.id, index = 1, offset = 7, textOffset = 9)
                store.savePosition(first.ref, position)
                try {
                    store.documentImportBatch {
                        assertFalse(importLocalDocument(store, input).imported)
                        val added = File(root, "新增书.txt").apply { writeText("第一章\n\n已成功导入的新正文。", Charsets.UTF_8) }
                        assertTrue(importLocalDocument(store, added).imported)
                        throw CancellationException("test pause")
                    }
                } catch(_: CancellationException) { }
                val reopened = LocalStore(isolated)
                assertEquals(2, reopened.state.value.books.size)
                assertEquals(position, reopened.state.value.positions[first.ref.key])
                assertFalse(importLocalDocument(reopened, input).imported)
                assertEquals(position, reopened.state.value.positions[first.ref.key])
                reopened.flush()
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                store.flush()
                check(root.canonicalFile.parentFile == base.cacheDir.canonicalFile && root.name.startsWith("cancelled-library-"))
                root.deleteRecursively()
            }
        }
    }

    @Test fun importsDuplicatesAndProgressSurviveReload(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val count = arguments.getString("performanceBooks")?.toIntOrNull() ?: 2000
        require(count in 1..5000)
        val iterations = arguments.getString("performanceIterations")?.toIntOrNull()?.coerceIn(1, 10) ?: 3
        val base = instrumentation.targetContext
        val runId = UUID.randomUUID().toString()
        val root = File(base.cacheDir, "large-library-$runId").apply { mkdirs() }
        val isolated = IsolatedContext(base, root)
        val report = PerformanceScenarioTest.PerformanceReport(base, iterations)
        report.data.put("scenario", "large-local-library").put("books", count)
            .put("fixture", "Unique small UTF-8 TXT files, one chapter each; original files and parsed chapters are persisted. Not a large/illustrated EPUB workload.")
        var store: LocalStore? = null
        try {
            withContext(Dispatchers.IO) {
                val current = LocalStore(isolated).also { store = it }
                val inputs = File(root, "inputs").apply { mkdirs() }
                val files = report.stage("import.prepareFixtures", JSONObject().put("books", count)) {
                    List(count) { index -> File(inputs, "测试书籍-${index.toString().padStart(4, '0')}.txt").apply {
                        writeText("第1章 开始\n\n第 $index 本小说的独立正文。" + "旅人沿着小路走向远方。".repeat(30), Charsets.UTF_8)
                    } }
                }
                val refs = report.stage("import.newBatch", JSONObject().put("books", count)) {
                    current.documentImportBatch {
                        files.map { file ->
                            val result = importLocalDocument(current, file)
                            assertTrue(result.imported)
                            result.ref
                        }
                    }
                }
                assertEquals(count, current.state.value.books.size)
                val last = refs.last()
                val chapter = current.documentIndex(last.id).chapters.first()
                // 已阅读过全部书目，避免只测只有一条进度的理想情况。
                val positions = refs.associate { it.key to Position(chapter.id, index = 1, updatedAt = 1L, chapterIndex = 0, chapterCount = 1) }
                current.update { it.copy(positions = positions,
                    readingHistory = refs.associate { ref -> ref.key to ReadingHistoryEntry(ref.key, "小说", chapter.id, chapter.title, 1L) }, historyMigrated = true) }
                current.flush()
                for(iteration in 0 until iterations) {
                    val loaded = report.stage("library.reload", JSONObject().put("books", count).put("iteration", iteration)) { LocalStore(isolated) }
                    assertNull(loaded.recoveryIssue.value)
                    assertEquals(current.state.value, loaded.state.value)
                    for(sort in 0..2) report.stage("shelf.groupAndSort", JSONObject().put("books", count).put("sort", sort).put("iteration", iteration)) {
                        assertEquals(count, loaded.state.value.shelfGroups(true, "全部", "", sort).size)
                    }
                    report.stage("shelf.filter", JSONObject().put("books", count).put("iteration", iteration)) {
                        assertTrue(loaded.state.value.shelfGroups(true, "全部", "测试书籍", 0).isNotEmpty())
                    }
                    val beforeBooks = current.state.value.books
                    val before = partitionRefs(isolated)
                    val position = positions.getValue(last.key).copy(index = iteration + 2, offset = 17,
                        textOffset = 31 + iteration, sourceParagraph = 1, anchorSource = "jp", anchorTextHash = "fixture-anchor")
                    report.stage("progress.update", JSONObject().put("books", count).put("iteration", iteration)) {
                        current.savePosition(last, position, "最后一本")
                    }
                    assertSame(beforeBooks, current.state.value.books)
                    val dimensions = JSONObject().put("books", count).put("iteration", iteration)
                    report.stage("progress.flush", dimensions) { current.flush() }
                    val after = partitionRefs(isolated)
                    val changed = after.keys.filter { before[it] != after[it] }
                    assertEquals(listOf("reading"), changed)
                    val payloadBytes = changed.sumOf { File(isolated.filesDir, "library-text/${after.getValue(it)}.json").length() }
                    val manifestBytes = File(isolated.filesDir, "library.json").length()
                    dimensions.put("payloadBytesWritten", payloadBytes).put("manifestBytesPerCopy", manifestBytes)
                        .put("totalContentBytesWritten", payloadBytes + manifestBytes * 2)
                    report.stage("progress.legacyEncodeReference", JSONObject().put("books", count).put("iteration", iteration)) {
                        // 仅提供旧式整快照序列化/字节量参考，不冒充旧版 AtomicFile 实测。
                        val legacyBytes = appJson.encodeToString(current.state.value).toByteArray(Charsets.UTF_8).size
                        dimensions.put("legacySnapshotBytesPerCopy", legacyBytes)
                    }
                    val reopened = LocalStore(isolated)
                    assertEquals(position, reopened.state.value.positions[last.key])
                    assertEquals(current.state.value, reopened.state.value)
                    assertTrue(reopened.documentChapter(last.id, chapter.id).paragraphs.isNotEmpty())
                }
                val beforeDuplicates = current.state.value
                report.stage("import.duplicateBatch", JSONObject().put("books", count)) {
                    current.documentImportBatch {
                        files.forEachIndexed { index, file ->
                            val result = importLocalDocument(current, file)
                            assertFalse(result.imported)
                            assertEquals(refs[index], result.ref)
                        }
                    }
                }
                assertEquals(beforeDuplicates, current.state.value)
                assertEquals(beforeDuplicates, LocalStore(isolated).state.value)
                report.data.put("status", "completed")
            }
        } catch(error: Throwable) {
            report.data.put("status", "failed").put("failureType", error.javaClass.simpleName)
            throw error
        } finally {
            try {
                withContext(NonCancellable + Dispatchers.IO) {
                    try { store?.flush() }
                    finally {
                        check(root.canonicalFile.parentFile == base.cacheDir.canonicalFile && root.name == "large-library-$runId")
                        report.data.put("fixtureRemoved", root.deleteRecursively())
                    }
                }
            } finally {
                val output = File(base.getExternalFilesDir(null) ?: base.filesDir, "performance").apply { mkdirs() }
                val json = File(output, "large-library-$runId.json").apply { writeText(report.data.toString(2), Charsets.UTF_8) }
                val summary = File(output, "large-library-$runId.txt").apply { writeText(report.summary(), Charsets.UTF_8) }
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("performanceReport", json.absolutePath); putString("performanceSummary", summary.absolutePath)
                })
            }
        }
    }

    private fun partitionRefs(context: Context): Map<String, String> {
        val json = JSONObject(File(context.filesDir, "library.json").readText(Charsets.UTF_8)).getJSONObject("localLibraryParts")
        return json.keys().asSequence().associateWith(json::getString)
    }
}
