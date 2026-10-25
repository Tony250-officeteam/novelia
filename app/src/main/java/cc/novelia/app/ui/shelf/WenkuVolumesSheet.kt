@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cc.novelia.app.ui.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.model.Position
import cc.novelia.app.data.model.SavedBook
import cc.novelia.app.ui.components.base.*
import cc.novelia.app.ui.components.book.bookRowStatus
import cc.novelia.app.ui.theme.appReducedMotion
import kotlinx.coroutines.launch

/** 分卷浏览使用完整挂载集合；来自搜索的匹配集合可一键展开为全部，不改写书架筛选。 */
@Composable internal fun WenkuVolumesSheet(
    parent: SavedBook, volumes: List<SavedBook>, positions: Map<String, Position>, matchingKeys: Set<String>?,
    managing: Boolean, selection: Set<String>, busy: Boolean,
    onManaging: (Boolean) -> Unit, onSelection: (Set<String>) -> Unit, onDismiss: () -> Unit,
    onOpen: (SavedBook) -> Unit, onContinue: (SavedBook, Position) -> Unit,
    onDetails: () -> Unit, onMount: () -> Unit, onManageBook: (SavedBook) -> Unit,
    onReorder: (String, List<String>) -> Unit, actions: @Composable () -> Unit,
) {
    var showAll by rememberSaveable(parent.book.ref.key) { mutableStateOf(false) }
    val filtered = !showAll && matchingKeys != null && volumes.any { it.book.ref.key !in matchingKeys }
    val shown = if(filtered) volumes.filter { it.book.ref.key in matchingKeys.orEmpty() } else volumes
    val keys = shown.map { it.book.ref.key }
    val currentBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !currentBusy || it != SheetValue.Hidden })
    val scope = rememberCoroutineScope()
    val reducedMotion = appReducedMotion()
    fun close(then: () -> Unit = onDismiss) {
        if(busy) return
        if(reducedMotion) then() else scope.launch { sheetState.hide(); if(!sheetState.isVisible) then() }
    }
    val list = rememberLazyListState()
    LaunchedEffect(showAll) { if(showAll) list.scrollToItem(0) }
    val density = LocalDensity.current
    var headingHeight by remember { mutableIntStateOf(0) }
    val drag = rememberShelfDragSelection(list, keys, selection, managing, !busy,
        onBegin = { onManaging(true) }, onSelection = onSelection)
    val rows = shown.map { ShelfRowItem(it, parent) }
    val reorder = rememberVolumeReorderState(list, rows, !managing && !filtered && !busy, onReorder)
    LaunchedEffect(keys) { onSelection(selection.intersect(keys.toSet())) }
    val recent = volumes.filter { it.book.ref.key in positions }.maxByOrNull { positions.getValue(it.book.ref.key).updatedAt }
    fun toggle(saved: SavedBook) {
        if(!drag.dragging && !busy) onSelection(if(saved.book.ref.key in selection) selection - saved.book.ref.key else selection + saved.book.ref.key)
    }
    AppSheet(onDismissRequest = { if(!busy) onDismiss() }, sheetState = sheetState) {
        BackHandler(managing && !busy) { onManaging(false); onSelection(emptySet()) }
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).testTag("wenku-volumes-sheet")) {
            if(managing) ShelfSelectionHeader(selection.size, !busy,
                if(drag.dragging) with(density) { headingHeight.toDp() } else 48.dp,
                onSelectAll = { onSelection(keys.toSet()) }, onFinish = { onManaging(false); onSelection(emptySet()) }, tagPrefix = "volume")
            else Column(Modifier.onSizeChanged { headingHeight = it.height }) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(parent.book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("本地 ${volumes.size} 卷" + parent.book.publishedVolumeCount?.let { " · 出版 $it 卷" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AppIconButton({ close() }, enabled = !busy, modifier = Modifier.testTag("volume-sheet-close")) { Icon(Icons.Outlined.Close, "关闭分卷") }
                }
                if(recent != null) {
                    val position = positions.getValue(recent.book.ref.key)
                    AppFilledTonalButton({ close { onContinue(recent, position) } }, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("volume-resume")) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("继续 · ${recent.book.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(position.title.ifBlank { bookRowStatus(recent.book, recent, position, null).progressLabel },
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if(filtered) Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("匹配 ${shown.size} 卷", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    AppTextButton({ showAll = true }, modifier = Modifier.testTag("volume-show-all")) { Text("显示全部") }
                }
            }
            AppLazyColumn(Modifier.weight(1f), state = list, listModifier = Modifier.testTag("volume-books").shelfDragSelection(drag, !busy)) {
                if(shown.isEmpty()) item {
                    if(filtered) EmptyState("没有符合条件的分卷", "这部作品的本地分卷未匹配当前筛选，可显示全部后继续阅读。", action = "显示全部", onAction = { showAll = true })
                    else EmptyState("尚未挂载本地分卷", "下载并导入分卷后，会显示在这里。也可以挂载已有的本地文件。", action = "管理挂载", onAction = onMount)
                }
                items(reorder.rows, key = { it.saved.book.ref.key }) { row ->
                    val saved = row.saved
                    val key = saved.book.ref.key
                    val open = { if(!drag.dragging && !busy) { if(managing) toggle(saved) else close { onOpen(saved) } } }
                    MountedVolumeRow(saved, positions[key], volumeReorderMotion(reorder, key, reducedMotion, drag.dragging)
                        .then(if(managing && key in selection) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                        .semantics {
                            selected = managing && key in selection
                            onClick(if(managing) "切换选择" else "阅读分卷") { open(); true }
                            if(!busy) onLongClick("选择${saved.book.title}") { drag.selectOne(key); true }
                        }, open, trailing = {
                            if(managing) AppCheckbox(key in selection, { toggle(saved) }, enabled = !busy)
                            else AppIconButton({ onManageBook(saved) }) { Icon(Icons.Outlined.MoreVert, "管理 ${saved.book.title}") }
                        }, dragHandle = if(!managing && !filtered && !busy && shown.size > 1) ({ VolumeDragHandle(reorder, key, saved.book.title) }) else null,
                        indented = false)
                }
            }
            if(managing) actions() else Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AppTextButton({ close(onDetails) }) { Icon(Icons.Outlined.Info, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("作品详情") }
                AppTextButton(onMount, modifier = Modifier.testTag("volume-mount")) { Icon(Icons.Outlined.LibraryAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("管理挂载") }
            }
        }
    }
}
