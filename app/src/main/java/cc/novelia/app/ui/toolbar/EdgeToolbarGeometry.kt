package cc.novelia.app.ui.toolbar

import kotlin.math.roundToInt

/** 系统安全边距用于固定圆点；页面导航和阅读控件的边距仅用于面板避让。 */
internal data class EdgeToolbarInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/** 像素坐标。圆点使用稳定的窗口区域，面板使用当前页面的可用区域。 */
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
