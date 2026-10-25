package cc.novelia.app.ui.shelf

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** 一次拖动以起点之前的选择为基准；回划时恢复离开区间的条目，保留原有选择。 */
internal fun shelfRangeSelection(keys: List<String>, baseline: Set<String>, anchor: String, target: String, selecting: Boolean): Set<String> {
    val start = keys.indexOf(anchor)
    val end = keys.indexOf(target)
    if(start < 0 || end < 0) return baseline
    val range = keys.subList(minOf(start, end), maxOf(start, end) + 1).toSet()
    return if(selecting) baseline + range else baseline - range
}

@Stable internal class ShelfDragSelectionState(
    private val list: LazyListState, private val keys: () -> List<String>,
    private val selection: () -> Set<String>, private val managing: () -> Boolean,
    private val begin: () -> Unit, private val change: (Set<String>) -> Unit,
    private val customHit: () -> ((Offset, Boolean) -> String?)? = { null },
) {
    var dragging by mutableStateOf(false)
        private set
    private var anchor: String? = null
    private var baseline = emptySet<String>()
    private var selecting = true
    private var pointer = Offset.Zero

    fun selectOne(key: String) {
        if(key !in keys()) return
        val current = if(managing()) selection() else emptySet()
        begin()
        change(if(key in current) current - key else current + key)
    }

    fun start(position: Offset) {
        val key = hit(position, nearest = false) ?: return
        anchor = key
        baseline = if(managing()) selection() else emptySet()
        selecting = key !in baseline
        pointer = position
        dragging = true
        begin()
        updateRange()
    }

    private fun hit(position: Offset, nearest: Boolean): String? {
        customHit()?.let { return it(position, nearest)?.takeIf { key -> key in keys() } }
        val y = position.y
        val available = keys().toSet()
        val visible = list.layoutInfo.visibleItemsInfo.filter { it.key in available }
        val item = visible.firstOrNull { y >= it.offset && y < it.offset + it.size }
            ?: if(nearest) visible.minByOrNull { if(y < it.offset) it.offset - y else y - it.offset - it.size } else null
        return item?.key as? String
    }

    fun drag(position: Offset) { if(dragging) { pointer = position; updateRange() } }

    private fun updateRange() {
        val start = anchor ?: return
        val currentKeys = keys()
        if(start !in currentKeys) { finish(); return }
        val target = hit(pointer, nearest = true) ?: return
        change(shelfRangeSelection(currentKeys, baseline, start, target, selecting))
    }

    suspend fun scrollAtEdge(edge: Float, maxStep: Float) {
        if(!dragging) return
        val layout = list.layoutInfo
        val above = pointer.y - layout.viewportStartOffset
        val below = layout.viewportEndOffset - pointer.y
        val step = when {
            above < edge -> -maxStep * (1f - above / edge).coerceIn(0f, 1f)
            below < edge -> maxStep * (1f - below / edge).coerceIn(0f, 1f)
            else -> 0f
        }
        if(step != 0f) { list.scrollBy(step); updateRange() }
    }

    fun finish() { dragging = false; anchor = null; baseline = emptySet() }
}

@Composable internal fun rememberShelfDragSelection(
    list: LazyListState, keys: List<String>, selection: Set<String>, managing: Boolean, enabled: Boolean,
    onBegin: () -> Unit, onSelection: (Set<String>) -> Unit,
    hitTest: ((Offset, Boolean) -> String?)? = null,
): ShelfDragSelectionState {
    val currentKeys by rememberUpdatedState(keys)
    val currentSelection by rememberUpdatedState(selection)
    val currentManaging by rememberUpdatedState(managing)
    val begin by rememberUpdatedState(onBegin)
    val change by rememberUpdatedState(onSelection)
    val hit by rememberUpdatedState(hitTest)
    val haptics = LocalHapticFeedback.current
    val state = remember(list) { ShelfDragSelectionState(list, { currentKeys }, { currentSelection }, { currentManaging },
        { haptics.performHapticFeedback(HapticFeedbackType.LongPress); begin() }, { change(it) }, { hit }) }
    val density = LocalDensity.current
    val edge = with(density) { 56.dp.toPx() }
    val speed = with(density) { 10.dp.toPx() }
    LaunchedEffect(enabled, state.dragging, edge, speed) {
        if(!enabled) state.finish()
        else if(state.dragging) {
            var lastFrame = withFrameNanos { it }
            while(state.dragging) {
                val frame = withFrameNanos { it }
                state.scrollAtEdge(edge, speed * ((frame - lastFrame) / 16_666_667f).coerceIn(0f, 3f))
                lastFrame = frame
            }
        }
    }
    DisposableEffect(state) { onDispose { state.finish() } }
    return state
}

internal fun Modifier.shelfDragSelection(state: ShelfDragSelectionState, enabled: Boolean): Modifier =
    if(!enabled) this else pointerInput(state) {
        detectDragGesturesAfterLongPress(onDragStart = state::start, onDragEnd = state::finish,
            onDragCancel = state::finish, onDrag = { change, _ ->
                if(state.dragging) { change.consume(); state.drag(change.position) }
            })
    }
