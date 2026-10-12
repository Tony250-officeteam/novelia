package cc.novelia.app.ui.toolbar

import kotlin.math.roundToInt

/** 像素坐标，输入已经排除系统栏、应用导航和阅读工具栏。位置按可拖动区域的比例保存。 */
internal data class EdgeToolbarBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(0)
    val height get() = (bottom - top).coerceAtLeast(0)

    fun centerY(position: Float, touchSize: Int): Float {
        val fraction = position.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: .55f
        return top + touchSize / 2f + (height - touchSize).coerceAtLeast(0) * fraction
    }

    fun fractionAt(centerY: Float, touchSize: Int): Float =
        if(height <= touchSize) .55f else ((centerY - top - touchSize / 2f) / (height - touchSize)).coerceIn(0f, 1f)

    fun panelTop(centerY: Float, panelHeight: Int): Int =
        (centerY - panelHeight / 2f).roundToInt().coerceIn(top, (bottom - panelHeight).coerceAtLeast(top))
}

internal fun edgeToolbarRouteAllowed(route: String?): Boolean = route?.substringBefore('?')?.substringBefore('/') in
    setOf("shelf", "discover", "community", "profile", "book", "article", "rank", "keywords", "reader",
        "history", "downloads", "updates", "notes", "tools", "blocked", "about", "licenses", "backup", "sync", "webdav")
