package cc.novelia.app.data.cache

import cc.novelia.app.data.auth.SessionBinding
import cc.novelia.app.data.catalog.FilteredNovelBatch
import cc.novelia.app.data.community.*
import cc.novelia.app.data.model.*
import cc.novelia.app.data.network.MetadataMutation

internal data class DetailUiKey(val path: String, val binding: SessionBinding, val generation: Long, val role: String?)
internal data class DiscussionUiKey(val discussion: String, val binding: SessionBinding, val role: String?, val blacklist: Long, val generation: Long)
internal data class ReplyUiKey(val discussion: Any?, val rootId: Long, val page: Int)

/** 应用级只保存数据；所有调用键仍须包含来源、会话和清缓存代次。 */
internal class UiResultStore {
    val discoverPages = UiResultCache<Page<BookCard>>()
    val recommendations = UiResultCache<Pair<List<BookCard>, List<BookCard>>>(4)
    val ranks = UiResultCache<Page<BookCard>>(12, 5 * 60_000L)
    val filtered = UiResultCache<FilteredNovelBatch>(4, maxWeight = 8L * 1024 * 1024,
        weigh = { it.books.size * 1024L })
    val folders = UiResultCache<CloudFolders>(4, 5 * 60_000L)
    val cloudPages = UiResultCache<Page<BookCard>>()
    val history = UiResultCache<Page<BookCard>>(12)
    val categories = UiResultCache<List<ForumCategory>>(4, 10 * 60_000L)
    val forumPages = UiResultCache<ForumPage<ForumPost>>(maxWeight = 4L * 1024 * 1024,
        weigh = { page -> page.items.sumOf { 512L + (it.title.length + it.content.length) * 2L } })
    val articles = UiResultCache<Article>(12, maxWeight = 4L * 1024 * 1024, weigh = { 1024L + it.content.length * 2L })
    val comments = UiResultCache<ForumPage<ForumComment>>(24, maxWeight = 4L * 1024 * 1024,
        weigh = { page -> page.items.sumOf { 512L + it.content.length * 2L + it.replies?.items.orEmpty().sumOf { reply -> 256L + reply.content.length * 2L } } })
    val replies = UiResultCache<ForumPage<ForumComment>>(48, maxWeight = 4L * 1024 * 1024,
        weigh = { page -> page.items.sumOf { 256L + it.content.length * 2L } })
    val webDetails = UiResultCache<WebDetail>(12, 5 * 60_000L, 8L * 1024 * 1024,
        weigh = { 2048L + it.toc.sumOf { chapter -> 128L + (chapter.titleJp.length + chapter.titleZh.orEmpty().length) * 2L } })
    val wenkuDetails = UiResultCache<WenkuDetail>(12, 5 * 60_000L)
    private val all get() = listOf(discoverPages, recommendations, ranks, filtered, folders, cloudPages, history,
        categories, forumPages, articles, comments, replies, webDetails, wenkuDetails)
    fun clear() { all.forEach { it.clear() } }

    fun novelChanged(mutation: MetadataMutation) {
        val paths = mutation.detailPaths
        val matches: (Any) -> Boolean = { key -> key is DetailUiKey && (paths == null || key.path in paths) }
        if(paths == null || paths.isNotEmpty()) { webDetails.invalidate(matches); wenkuDetails.invalidate(matches) }
        if(mutation.history) history.invalidate()
        if(mutation.favorites) { folders.invalidate(); cloudPages.invalidate() }
        if(mutation.catalog || paths == null) {
            discoverPages.invalidate(); recommendations.invalidate(); ranks.invalidate(); filtered.invalidate()
        }
    }

    fun forumChanged() {
        forumPages.invalidate(); articles.invalidate(); comments.invalidate(); replies.invalidate()
    }
}
