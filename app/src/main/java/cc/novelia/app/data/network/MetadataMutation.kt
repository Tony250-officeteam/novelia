package cc.novelia.app.data.network

import cc.novelia.app.data.auth.SessionBinding

/** 写入影响的读取域。个人进度和收藏不会使其他作品的内容缓存过期。 */
data class MetadataMutation(val method: String, val path: String, val binding: SessionBinding?, val timestamp: Long) {
    private val parts = path.trim('/').split('/').map { java.net.URLDecoder.decode(it, "UTF-8") }
    val history = parts.take(2) == listOf("user", "read-history")
    val favorites = parts.getOrNull(0) == "user" && parts.getOrNull(1) in setOf("favored-web", "favored-wenku")
    val catalog = parts.firstOrNull() in setOf("novel", "wenku")
    // null 表示未知/跨多书的操作，需要保守失效；空集合表示不影响详情。
    val detailPaths: Set<String>? = when {
        history && parts.size == 4 -> setOf("novel/${parts[2]}/${parts[3]}")
        history && parts.lastOrNull() == "paused" -> emptySet()
        favorites && parts[1] == "favored-web" && parts.size == 5 -> setOf("novel/${parts[3]}/${parts[4]}")
        favorites && parts[1] == "favored-wenku" && parts.size == 4 -> setOf("wenku/${parts[3]}")
        favorites && method != "DELETE" -> emptySet()
        catalog && parts[0] == "novel" && parts.size >= 3 -> setOf(parts.take(3).joinToString("/"))
        catalog && parts[0] == "wenku" && parts.size >= 2 -> setOf(parts.take(2).joinToString("/"))
        else -> null
    }
}
