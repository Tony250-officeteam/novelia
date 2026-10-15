package cc.novelia.app.data.community

/** 论坛帖子的本地 ID 与用户保存的历史草稿键保持隔离。 */
object ForumLinks {
    const val ORIGIN = "https://forum.novelia.cc"
    const val TUTORIAL_ID = "f-1"
    fun localId(id: Long): String { require(id > 0); return "f-$id" }
    fun postId(id: String): Long? = id.takeIf { it.startsWith("f-") }?.removePrefix("f-")?.toLongOrNull()?.takeIf { it > 0 }
    fun articleUrl(id: String) = "$ORIGIN/p/${requireNotNull(postId(id)) { "帖子 ID 无效" }}"
}
