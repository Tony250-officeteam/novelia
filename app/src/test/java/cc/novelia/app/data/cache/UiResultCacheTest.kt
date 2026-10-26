package cc.novelia.app.data.cache

import java.io.IOException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class UiResultCacheTest {
    @Test fun pageRoundTripsKeepResultsAndPositionsWithoutExtendingFreshness() {
        var now = 1_000L
        val cache = UiResultCache<String>(capacity = 2, lifetimeMillis = 100, clock = { now })
        cache.put("page-1", "第一页")
        cache.savePosition("page-1", "list", UiResultCache.Position(8, 12))
        cache.put("page-2", "第二页")
        now = 1_099
        assertTrue(cache.snapshot("page-1")!!.fresh)
        assertEquals(UiResultCache.Position(8, 12), cache.position("page-1", "list"))
        now++
        assertFalse(cache.snapshot("page-1")!!.fresh)
        assertEquals("第一页", cache.snapshot("page-1")!!.value)
        now = 900
        assertFalse(cache.snapshot("page-1")!!.fresh)
    }

    @Test fun weightedLruEvictsOldPagesAndRejectsOversizedDocuments() {
        val cache = UiResultCache<String>(capacity = 3, maxWeight = 6, weigh = { it.length.toLong() })
        cache.put("a", "aaa"); cache.put("b", "bbb")
        cache.snapshot("a")
        cache.put("c", "ccc")
        assertNull(cache.snapshot("b"))
        assertNotNull(cache.snapshot("a"))
        cache.put("a", "oversized")
        assertNull(cache.snapshot("a"))
        assertNotNull(cache.snapshot("c"))
    }

    @Test fun failedRefreshKeepsThePreviousSnapshotAndCanRetry() = runBlocking {
        val cache = UiResultCache<String>()
        cache.put("page", "原结果")
        assertEquals("原结果", cache.load("page") { error("新鲜缓存不请求") })
        assertTrue(runCatching { cache.load("page", true) { throw IOException("离线") } }.exceptionOrNull() is IOException)
        assertEquals("原结果", cache.snapshot("page")!!.value)
        assertEquals("新结果", cache.load("page", true) { "新结果" })
    }

    @Test fun invalidationAndClearingRejectOldInFlightResponses() = runBlocking {
        val cache = UiResultCache<String>()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        val old = async { cache.load("page") { started.complete(Unit); response.await() } }
        withTimeout(5_000) { started.await() }
        cache.clear()
        cache.put("page", "清理后的新响应")
        response.complete("清理前的旧响应")
        old.await()
        assertEquals("清理后的新响应", cache.snapshot("page")!!.value)
        val revision = cache.revision.value
        cache.invalidate { it == "page" }
        cache.put("page", "写入前的旧响应", revision)
        assertEquals("清理后的新响应", cache.snapshot("page")!!.value)
        assertFalse(cache.snapshot("page")!!.fresh)
    }

    @Test fun sharedMetadataReadersJoinTheSameNetworkRequest() = runBlocking {
        val cache = UiResultCache<String>()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        val first = async { cache.load("categories") { started.complete(Unit); response.await() } }
        withTimeout(5_000) { started.await() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { cache.load("categories") { error("同键不重复请求") } }
        response.complete("分类")
        assertEquals("分类", first.await())
        assertEquals("分类", second.await())
    }

    @Test fun targetedInvalidationKeepsOtherBooksAndPositions() {
        val cache = UiResultCache<String>()
        cache.put("book-a", "A"); cache.put("book-b", "B")
        cache.savePosition("book-a", "list", UiResultCache.Position(4, 20))
        cache.invalidate { it == "book-a" }
        assertFalse(cache.snapshot("book-a")!!.fresh)
        assertEquals("B", cache.snapshot("book-b")!!.value)
        assertEquals(4, cache.position("book-a", "list").index)
        cache.clear()
        assertEquals(0, cache.position("book-a", "list").index)
    }
}
