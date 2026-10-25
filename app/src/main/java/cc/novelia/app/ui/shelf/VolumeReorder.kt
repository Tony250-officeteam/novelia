package cc.novelia.app.ui.shelf

import cc.novelia.app.ui.components.base.AppIconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cc.novelia.app.ui.components.base.AppDropdownMenu
import cc.novelia.app.ui.theme.LocalEInkMode
import cc.novelia.app.ui.theme.AppMotion
import kotlin.math.abs

/** 排序草稿仅在手势期间存在，完成一次拖放只持久化一次。 */
internal class VolumeReorderState(
    private val list: LazyListState,
    private val sourceRows: List<ShelfRowItem>,
    private val onCommit: (String, List<String>) -> Unit
) {
    var draggedKey by mutableStateOf<String?>(null)
        private set
    private var parentKey by mutableStateOf<String?>(null)
    private var order by mutableStateOf(emptyList<String>())
    private var top by mutableFloatStateOf(0f)
    private var height = 0
    private var lastDirection = 0f
    private var pendingIndex: Int? = null

    val rows: List<ShelfRowItem>
        get() {
            if(parentKey == null) return sourceRows
            val volumes = sourceRows.filter { it.parent?.book?.ref?.key == parentKey }.associateBy { it.saved.book.ref.key }
            var index = 0
            return sourceRows.map { if(it.parent?.book?.ref?.key == parentKey) volumes.getValue(order[index++]) else it }
        }

    val offset: Float
        get() = draggedKey?.let { key -> list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { top - it.offset } } ?: 0f

    val draggedTop: Float get() = top
    fun itemTop(key: String): Float? = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset?.toFloat()

    fun siblings(key: String): List<String> {
        val parent = sourceRows.firstOrNull { it.saved.book.ref.key == key }?.parent ?: return emptyList()
        return sourceRows.filter { it.parent?.book?.ref == parent.book.ref }.map { it.saved.book.ref.key }
    }

    fun start(key: String) {
        val row = sourceRows.firstOrNull { it.saved.book.ref.key == key } ?: return
        val parent = row.parent ?: return
        val info = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        order = siblings(key)
        if(order.size < 2) return
        parentKey = parent.book.ref.key
        top = info.offset.toFloat()
        height = info.size
        lastDirection = 0f
        pendingIndex = null
        draggedKey = key
    }

    fun drag(delta: Float) {
        if(draggedKey == null) return
        if(delta != 0f) lastDirection = delta
        val layout = list.layoutInfo
        top = (top + delta).coerceIn(layout.viewportStartOffset.toFloat(), maxOf(layout.viewportStartOffset.toFloat(), (layout.viewportEndOffset - height).toFloat()))
        moveAcrossSiblings()
    }

    private fun moveAcrossSiblings() {
        val key = draggedKey ?: return
        val visible = list.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == key } ?: return
        // 等待列表更新条目位置后，再判断下一次跨越。
        if(pendingIndex != null && current.index != pendingIndex) return
        pendingIndex = null
        val center = top + height / 2f
        val from = order.indexOf(key)
        val target = visible.filter { it.key in order && it.key != key }.filter {
            val to = order.indexOf(it.key)
            // 按重叠量判断跨越，避免较高的进度行在首尾永远无法越过较矮分卷的中心。
            val overlap = minOf(height, it.size) / 2f
            if(lastDirection > 0) to > from && top + height > it.offset + overlap
            else lastDirection < 0 && to < from && top < it.offset + it.size - overlap
        }.minByOrNull { abs(center - (it.offset + it.size / 2f)) } ?: return
        val to = order.indexOf(target.key)
        // 首个可见项被重排时，保持视口位置稳定。
        list.requestScrollToItem(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
        pendingIndex = target.index
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    suspend fun scrollAtEdge(edge: Float, maxStep: Float) {
        if(draggedKey == null) return
        val layout = list.layoutInfo
        val above = top - layout.viewportStartOffset
        val below = layout.viewportEndOffset - (top + height)
        val index = order.indexOf(draggedKey)
        var step = when {
            lastDirection < 0 && index > 0 && above < edge -> -maxStep * (1f - above / edge).coerceIn(0f, 1f)
            lastDirection > 0 && index < order.lastIndex && below < edge -> maxStep * (1f - below / edge).coerceIn(0f, 1f)
            else -> 0f
        }
        // 刚成为首/末项时仍可能被视口裁切；继续滚到该分卷完整可见，但不越过所属作品。
        if(index == 0 && lastDirection < 0 && above < edge) {
            val first = layout.visibleItemsInfo.firstOrNull { it.key == order.first() }
            if(first != null) step = (first.offset - layout.viewportStartOffset).toFloat().coerceIn(-maxStep, 0f)
        } else if(index == order.lastIndex && lastDirection > 0 && below < edge) {
            val last = layout.visibleItemsInfo.firstOrNull { it.key == order.last() }
            if(last != null) step = (last.offset + last.size - layout.viewportEndOffset).toFloat().coerceIn(0f, maxStep)
        }
        if(step != 0f) list.scrollBy(step)
        moveAcrossSiblings()
    }

    fun finish() {
        val key = draggedKey ?: return
        val parent = parentKey ?: return
        val result = order
        val changed = result != siblings(key)
        if(changed) {
            // Store 的新列表要到下一次组合才接管；此时清空草稿会先恢复旧顺序，
            // LazyColumn 随即跟随旧 key 调整视口，首尾分卷可能跳走甚至被回收。
            draggedKey = null
            pendingIndex = null
            onCommit(parent, result)
        } else cancel()
    }

    fun cancel() {
        draggedKey = null
        parentKey = null
        pendingIndex = null
        order = emptyList()
    }

    fun moveOne(key: String, delta: Int) {
        val row = sourceRows.firstOrNull { it.saved.book.ref.key == key } ?: return
        val parent = row.parent ?: return
        val keys = siblings(key)
        val from = keys.indexOf(key)
        val to = from + delta
        if(from >= 0 && to in keys.indices) onCommit(parent.book.ref.key, keys.toMutableList().apply { add(to, removeAt(from)) })
    }
}

private class VolumeDragPlacement(var top: Float = 0f)

/** 拖拽行紧跟手指，其余行让位；松手后从屏幕中的最后位置归位，不因草稿提交而跳回。 */
@Composable internal fun LazyItemScope.volumeReorderMotion(
    state: VolumeReorderState, key: String, reducedMotion: Boolean, selectionDragging: Boolean = false
): Modifier {
    val moving = state.draggedKey == key
    val placement = remember(key) { VolumeDragPlacement() }
    val remaining by animateFloatAsState(if(moving) 1f else 0f,
        animationSpec = if(moving || reducedMotion) snap() else tween(AppMotion.Release), label = "volume-drop")
    val lifted = moving || remaining > 0f
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    return (if(reducedMotion) Modifier else Modifier.animateItem(
        fadeInSpec = null, placementSpec = if(lifted || selectionDragging) null else tween(AppMotion.Standard), fadeOutSpec = null))
        .zIndex(if(lifted) 1f else 0f)
        .graphicsLayer {
            if(moving) {
                placement.top = state.draggedTop
                translationY = state.offset
            } else translationY = if(remaining > 0f) (placement.top - (state.itemTop(key) ?: placement.top)) * remaining else 0f
            shadowElevation = if(lifted && !reducedMotion) 4.dp.toPx() else 0f
        }
        .drawBehind { if(lifted) drawRect(surface) }
}

@Composable internal fun rememberVolumeReorderState(
    list: LazyListState, rows: List<ShelfRowItem>, enabled: Boolean, onCommit: (String, List<String>) -> Unit
): VolumeReorderState {
    val commit by rememberUpdatedState(onCommit)
    val state = remember(list, rows, enabled) { VolumeReorderState(list, rows) { parent, keys -> commit(parent, keys) } }
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val speed = with(density) { 12.dp.toPx() }
    LaunchedEffect(state, state.draggedKey, edge, speed) {
        if(state.draggedKey != null) {
            var lastFrame = withFrameNanos { it }
            while(state.draggedKey != null) {
                val frame = withFrameNanos { it }
                val scale = ((frame - lastFrame) / 16_666_667f).coerceIn(0f, 3f)
                lastFrame = frame
                state.scrollAtEdge(edge, speed * scale)
            }
        }
    }
    DisposableEffect(state) { onDispose { state.cancel() } }
    return state
}

@Composable internal fun VolumeDragHandle(state: VolumeReorderState, key: String, title: String) {
    var menu by remember { mutableStateOf(false) }
    val siblings = state.siblings(key)
    val index = siblings.indexOf(key)
    if(LocalEInkMode.current) {
        Column {
            AppIconButton(onClick = { state.moveOne(key, -1) }, enabled = index > 0,
                modifier = Modifier.size(48.dp).testTag("volume-up-$key")) { Icon(Icons.Outlined.KeyboardArrowUp, "上移 $title") }
            AppIconButton(onClick = { state.moveOne(key, 1) }, enabled = index in 0 until siblings.lastIndex,
                modifier = Modifier.size(48.dp).testTag("volume-down-$key")) { Icon(Icons.Outlined.KeyboardArrowDown, "下移 $title") }
        }
        return
    }
    Box {
        AppIconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("volume-drag-$key")
            .pointerInput(state, key) {
                detectDragGestures(
                    onDragStart = { menu = false; state.start(key) },
                    onDragCancel = state::cancel,
                    onDragEnd = state::finish,
                    onDrag = { change, amount -> change.consume(); state.drag(amount.y) }
                )
            }) { Icon(Icons.Outlined.DragHandle, "拖动排序 $title") }
        AppDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("上移一位") }, enabled = index > 0, onClick = { menu = false; state.moveOne(key, -1) })
            DropdownMenuItem(text = { Text("下移一位") }, enabled = index in 0 until siblings.lastIndex, onClick = { menu = false; state.moveOne(key, 1) })
        }
    }
}
