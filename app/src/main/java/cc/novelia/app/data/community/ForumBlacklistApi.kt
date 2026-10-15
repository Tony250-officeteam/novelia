package cc.novelia.app.data.community

import cc.novelia.app.data.auth.SessionBinding
import cc.novelia.app.data.network.NoveliaApi
import cc.novelia.app.data.storage.appJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 论坛和小说评论共用账号黑名单，但始终沿用调用方的会话与线路。 */
class ForumBlacklistApi(private val http: NoveliaApi, private val onChanged: () -> Unit = {}) {
    suspend fun list(binding: SessionBinding? = http.session?.capture()): ForumPage<ForumBlacklistEntry> {
        val raw = http.request("GET", "me/blacklist", binding = binding, errorMessage = ::forumErrorMessage)
        val page = withContext(Dispatchers.Default) { appJson.decodeFromString<ForumPage<ForumBlacklistEntry>>(raw) }
        binding?.let { http.session?.ensureCurrent(it) }
        return page
    }

    suspend fun setBlocked(userId: Long, blocked: Boolean, binding: SessionBinding? = http.session?.capture()) {
        require(userId > 0) { "用户 ID 必须为正整数" }
        http.request(if(blocked) "PUT" else "DELETE", "me/blacklist/$userId", binding = binding,
            errorMessage = ::forumErrorMessage)
        binding?.let { http.session?.ensureCurrent(it) }
        onChanged()
    }
}
