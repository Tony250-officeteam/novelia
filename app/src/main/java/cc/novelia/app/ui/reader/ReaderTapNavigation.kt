@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package cc.novelia.app.ui.reader

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import cc.novelia.app.reader.ReaderTapAction
import cc.novelia.app.reader.ReaderTapKind
import cc.novelia.app.reader.ReaderTapZoneMap

/**
 * 只观察正文视口中的指针，不抢占列表、长按或按钮的事件。
 * 真正的点击仍由现有 clickable / combinedClickable 确认，再按视口坐标分区。
 * 键盘与无障碍点击没有指针坐标，始终保留工具栏入口。
 *
 * 点击分区方案来自 [ReaderTapZoneMap]（移植自 FBReaderJ）；
 * 默认右开本：左列上一页、右列下一页、中列与未配置区域收起或展开工具栏，
 * 与旧版写死三等分的行为保持兼容。
 */
internal class ReaderTapNavigation {
    private var tapPageTurn = false
    private var pageTurningEnabled = true
    private var onToggleMenu: () -> Unit = {}
    private var onPage: (Int) -> Unit = {}
    private var zoneMap: ReaderTapZoneMap = ReaderTapZoneMap.Default
    private var tracking = false
    private var valid = false
    private var tap = false
    private var released = false
    private var start = Offset.Zero
    private var width = 0
    private var height = 0
    private var feedbackId = 0L
    var feedback by mutableStateOf<ReaderTapFeedback?>(null)
        private set

    val canSwipe: Boolean get() = pageTurningEnabled && tracking && valid

    fun configure(tapPageTurn: Boolean, pageTurningEnabled: Boolean, onToggleMenu: () -> Unit, onPage: (Int) -> Unit,
        zoneMap: ReaderTapZoneMap = ReaderTapZoneMap.Default) {
        if(this.tapPageTurn != tapPageTurn || this.pageTurningEnabled != pageTurningEnabled || this.zoneMap !== zoneMap) {
            cancel()
            feedback = null
        }
        this.tapPageTurn = tapPageTurn
        this.pageTurningEnabled = pageTurningEnabled
        this.onToggleMenu = onToggleMenu
        this.onPage = onPage
        this.zoneMap = zoneMap
    }

    fun begin(position: Offset, width: Int, height: Int = 0) {
        feedback = null
        tracking = true
        valid = true
        tap = true
        released = false
        start = position
        this.width = width
        this.height = height
    }

    fun cancel() { valid = false }
    fun moved(position: Offset, slop: Float, insideViewport: Boolean) {
        // 越过阈值后即使回到起点也不能重新成为点击。
        // 越出正文只取消点击；已开始的滑动仍可在视口外松手提交。
        if(!insideViewport || (position - start).getDistance() > slop) tap = false
    }
    fun release(shortPress: Boolean) { released = true; tap = tap && shortPress }
    fun finish() { tracking = false; valid = false }
    fun clearFeedback(value: ReaderTapFeedback) { if(feedback == value) feedback = null }

    fun click() {
        if(!tracking) { onToggleMenu(); return }
        if(!valid || !tap || !released) return
        valid = false
        // 搜索等状态暂停翻页时，仍能通过正文收起或展开工具栏。
        if(!pageTurningEnabled || !tapPageTurn || width <= 0) {
            feedback = null
            onToggleMenu()
            return
        }
        val action = zoneMap.getActionByCoordinates(start.x.toInt(), start.y.toInt(), width, height, ReaderTapKind.SingleTap)
        // 只反馈已确认的区域单击；滑动、长按、控件及无障碍点击不会产生闪烁。
        feedback = when(action) {
            ReaderTapAction.PreviousPage -> ReaderTapFeedback(++feedbackId, -1)
            ReaderTapAction.NextPage -> ReaderTapFeedback(++feedbackId, 1)
            else -> null
        }
        when(action) {
            ReaderTapAction.PreviousPage -> onPage(-1)
            ReaderTapAction.NextPage -> onPage(1)
            // 中列与未配置区域保持旧行为：收起或展开工具栏。
            else -> onToggleMenu()
        }
    }
}

internal data class ReaderTapFeedback(val id: Long, val direction: Int)

@Composable internal fun rememberReaderTapNavigation(
    tapPageTurn: Boolean, pageTurningEnabled: Boolean = true, onToggleMenu: () -> Unit, onPage: (Int) -> Unit,
    zoneMap: ReaderTapZoneMap = ReaderTapZoneMap.Default
): ReaderTapNavigation {
    val navigation = remember { ReaderTapNavigation() }
    SideEffect { navigation.configure(tapPageTurn, pageTurningEnabled, onToggleMenu, onPage, zoneMap) }
    return navigation
}

@Composable internal fun Modifier.readerTapNavigation(navigation: ReaderTapNavigation): Modifier =
    pointerInput(navigation) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val viewport = size
            navigation.begin(down.position, viewport.width, viewport.height)
            if(down.isConsumed || currentEvent.changes.size != 1) navigation.cancel()
            try {
                while(true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull { it.id == down.id }
                    if(pointer == null || pointer.isConsumed || size != viewport ||
                        event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) }) navigation.cancel()
                    if(pointer != null) {
                        navigation.moved(pointer.position, viewConfiguration.touchSlop,
                            pointer.position.x in 0f..viewport.width.toFloat() && pointer.position.y in 0f..viewport.height.toFloat())
                    }
                    if(event.changes.none { it.pressed }) {
                        navigation.release(pointer != null && pointer.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis)
                        // 子项在 Main 阶段确认点击；Final 后清理，避免语义点击复用旧触点。
                        awaitPointerEvent(PointerEventPass.Final)
                        break
                    }
                }
            } finally { navigation.finish() }
        }
    }.combinedClickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
        onClickLabel = "显示或收起阅读工具栏", onClick = navigation::click)
