package cc.novelia.app.data.community

import cc.novelia.app.data.auth.*
import cc.novelia.app.data.network.ApiException
import cc.novelia.app.data.network.NoveliaApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test

class ForumBlacklistApiTest {
    private class TestSession(override val target: AuthTarget) : ApiSession {
        var binding = SessionBinding("test-account", 1)
        override val token get() = "synthetic-${target.name}"
        override fun capture() = binding
        override fun tokenFor(binding: SessionBinding): String {
            if(binding != this.binding) throw SessionChangedException()
            return token
        }
        override suspend fun refreshIfCurrent(binding: SessionBinding, previousToken: String?) = false
    }

    @Test fun listAndIdempotentWritesUseTheCallingAccountAndExactLongIds() = runBlocking {
        for(target in listOf(AuthTarget.FORUM, AuthTarget.NOVEL)) MockWebServer().use { server ->
            val session = TestSession(target)
            val http = NoveliaApi(session, server.url("/api/v1/").toString())
            var changed = 0
            val api = if(target == AuthTarget.FORUM) ForumApi(http) { changed++ }.blacklist
                else NovelCommentApi(http) { changed++ }.blacklist
            server.enqueue(MockResponse().setBody("""{"total":1,"items":[{"userId":9007199254740993,"createdAt":"2026-10-15T00:00:00Z"}]}"""))
            repeat(2) { server.enqueue(MockResponse().setResponseCode(204)) }
            val snapshot = session.capture()
            val entries = api.list(snapshot)
            assertEquals(9007199254740993L, entries.items.single().userId)
            assertEquals(0, changed)
            api.setBlocked(entries.items.single().userId, true, snapshot)
            api.setBlocked(entries.items.single().userId, false, snapshot)
            val requests = List(3) { server.takeRequest() }
            assertEquals(listOf("GET", "PUT", "DELETE"), requests.map { it.method })
            assertEquals(listOf("/api/v1/me/blacklist", "/api/v1/me/blacklist/9007199254740993", "/api/v1/me/blacklist/9007199254740993"), requests.map { it.path })
            requests.forEach { assertEquals("Bearer ${session.token}", it.getHeader("Authorization")); assertEquals(0L, it.bodySize) }
            assertEquals(2, changed)
            for(id in listOf(0L, -1L)) assertTrue(runCatching { api.setBlocked(id, true) }.isFailure)
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun failedWritesKeepTheServerReasonAndDoNotInvalidateVisibleState() = runBlocking {
        MockWebServer().use { server ->
            var changed = 0
            val api = ForumBlacklistApi(NoveliaApi(TestSession(AuthTarget.FORUM), server.url("/").toString())) { changed++ }
            for(status in listOf(400, 403, 409)) {
                server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "text/plain; charset=utf-8").setBody("黑名单最多允许 1000 人"))
                val error = runCatching { api.setBlocked(42, true) }.exceptionOrNull() as ApiException
                assertEquals(status, error.status)
                assertEquals("黑名单最多允许 1000 人", error.message)
            }
            assertEquals(0, changed)
            assertEquals(3, server.requestCount)
        }
    }

    @Test fun staleAccountOrSourceBindingsNeverSendARequest() = runBlocking {
        MockWebServer().use { server ->
            val session = TestSession(AuthTarget.FORUM)
            val api = ForumBlacklistApi(NoveliaApi(session, server.url("/").toString()))
            val old = session.capture()
            session.binding = old.copy(source = "xkvi", sourceRevision = 2)
            assertTrue(runCatching { api.setBlocked(42, true, old) }.exceptionOrNull() is SessionChangedException)
            assertTrue(runCatching { api.list(old) }.exceptionOrNull() is SessionChangedException)
            session.binding = old.copy(account = "another", generation = 2)
            assertTrue(runCatching { api.setBlocked(42, false, old) }.exceptionOrNull() is SessionChangedException)
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun switchingIdentityDuringAReadOrWriteDiscardsTheResponseAndSuccessCallback() = runBlocking {
        MockWebServer().use { server ->
            val session = TestSession(AuthTarget.NOVEL)
            var changed = 0
            val api = ForumBlacklistApi(NoveliaApi(session, server.url("/").toString())) { changed++ }
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    session.binding = session.binding.copy(generation = session.binding.generation + 1)
                    return if(request.method == "GET") MockResponse().setBody("""{"total":0,"items":[]}""")
                        else MockResponse().setResponseCode(204)
                }
            }
            assertTrue(runCatching { api.list() }.exceptionOrNull() is SessionChangedException)
            assertTrue(runCatching { api.setBlocked(42, true) }.exceptionOrNull() is SessionChangedException)
            assertEquals(0, changed)
        }
    }
}
