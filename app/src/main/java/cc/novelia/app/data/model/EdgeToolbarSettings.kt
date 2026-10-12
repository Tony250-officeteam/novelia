package cc.novelia.app.data.model

import kotlinx.serialization.Serializable

/** 本机快捷入口偏好；不属于 WebDAV 阅读资料。入口只允许应用内的固定目的地。 */
@Serializable
data class EdgeToolbarSettings(
    val enabled: Boolean = true,
    val side: String = "right",
    val position: Float = .55f,
    val idleSeconds: Int = 3,
    val idleOpacity: Float = .45f,
    val readerVisibility: String = "menu",
    val downloadBadge: Boolean = true,
    val entries: List<String> = listOf("history", "downloads"),
) {
    val valid: Boolean get() = side in SIDES && position.isFinite() && position in 0f..1f &&
        idleSeconds in IDLE_SECONDS && idleOpacity.isFinite() && idleOpacity in 0.3f..0.8f &&
        readerVisibility in READER_VISIBILITY && entries.size in 1..5 &&
        entries.distinct().size == entries.size && entries.all { it in ENTRIES }

    /** 旧版本或本机异常偏好不能使浮层越界，也不能注入任意导航路由。 */
    fun normalized(): EdgeToolbarSettings = copy(
        side = side.takeIf { it in SIDES } ?: "right",
        position = position.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .55f,
        idleSeconds = idleSeconds.takeIf { it in IDLE_SECONDS } ?: 3,
        idleOpacity = idleOpacity.takeIf(Float::isFinite)?.coerceIn(.3f, .8f) ?: .45f,
        readerVisibility = readerVisibility.takeIf { it in READER_VISIBILITY } ?: "menu",
        entries = entries.filter { it in ENTRIES }.distinct().take(5).ifEmpty { listOf("history", "downloads") },
    )

    companion object {
        val SIDES = listOf("left", "right")
        val IDLE_SECONDS = listOf(3, 5, 8, 0)
        val READER_VISIBILITY = listOf("menu", "hidden", "always")
        val ENTRIES = listOf("history", "downloads", "updates", "notes", "tools", "settings")
    }
}
