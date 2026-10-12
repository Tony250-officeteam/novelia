package cc.novelia.app.ui.toolbar

import androidx.compose.runtime.*

internal data class EdgeReaderSnapshot(val menu: Boolean, val blocked: Boolean, val top: Int, val bottom: Int)
internal class EdgeToolbarReaderState {
    val entries = mutableStateMapOf<String, EdgeReaderSnapshot>()
}
internal val LocalEdgeToolbarReaderState = staticCompositionLocalOf<EdgeToolbarReaderState?> { null }

/** 以导航项为身份，退出中的旧章节不能覆盖当前章节的显示状态。 */
@Composable internal fun ReportEdgeToolbarReaderState(entryId: String?, menu: Boolean, blocked: Boolean, top: Int, bottom: Int) {
    val state = LocalEdgeToolbarReaderState.current
    SideEffect { if(entryId != null) state?.entries?.set(entryId, EdgeReaderSnapshot(menu, blocked, top, bottom)) }
    DisposableEffect(state, entryId) { onDispose { if(entryId != null) state?.entries?.remove(entryId) } }
}
