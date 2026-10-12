package cc.novelia.app.ui.components.base

import androidx.compose.runtime.*

/** 共用弹窗/面板挂载期间，让应用内浮动入口让出屏幕；允许嵌套弹窗。 */
internal class AppOverlayRegistry {
    var count by mutableIntStateOf(0)
        private set
    fun enter() { count++ }
    fun leave() { count = (count - 1).coerceAtLeast(0) }
}

internal val LocalAppOverlayRegistry = staticCompositionLocalOf<AppOverlayRegistry?> { null }

@Composable internal fun AppOverlayPresence(visible: Boolean = true) {
    val registry = LocalAppOverlayRegistry.current
    DisposableEffect(registry, visible) {
        if(visible) registry?.enter()
        onDispose { if(visible) registry?.leave() }
    }
}
