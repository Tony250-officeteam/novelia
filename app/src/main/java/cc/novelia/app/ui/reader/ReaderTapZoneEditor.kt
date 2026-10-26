/*
 * 改编自 FBReaderJ (https://github.com/geometer/FBReaderJ) v2.5.10
 * 原始文件：org/geometerplus/fbreader/fbreader/TapZoneMap.java
 * 原作者：FBReader.ORG Limited <contact@fbreader.org>
 * 原始许可：GPL-2.0-or-later（本项目按 GPL-3.0-only 使用）
 *
 * 修改内容：
 *  - 网格编辑交互改为 Compose 实现，套用 Novelia 的 Material 3 主题与共享组件
 *  - 每格点击后从底部弹层选择动作，而不是 FBReader 的逐项偏好列表
 *  - 显示当前格子的动作图标与文字，便于一眼看清整张分区表
 */
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cc.novelia.app.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.novelia.app.reader.ReaderTapAction
import cc.novelia.app.reader.ReaderTapZoneMap
import cc.novelia.app.ui.components.base.AppSheet

/** 动作对应的图标，网格与选择列表共用。 */
private fun ReaderTapAction.icon(): ImageVector = when (this) {
    ReaderTapAction.PreviousPage -> Icons.AutoMirrored.Outlined.ArrowBack
    ReaderTapAction.NextPage -> Icons.AutoMirrored.Outlined.ArrowForward
    ReaderTapAction.Navigate -> Icons.Outlined.List
    ReaderTapAction.Menu -> Icons.Outlined.MoreVert
    ReaderTapAction.None -> Icons.Outlined.Remove
}

/** 动作在网格里显示的文字，尽量短以适配窄屏。 */
private fun ReaderTapAction.label(): String = when (this) {
    ReaderTapAction.PreviousPage -> "上一页"
    ReaderTapAction.NextPage -> "下一页"
    ReaderTapAction.Navigate -> "目录"
    ReaderTapAction.Menu -> "工具栏"
    ReaderTapAction.None -> "无"
}

/**
 * 3 × 3 点击分区编辑器。
 *
 * 每格显示当前的单击动作，点按后从底部弹层选择新动作。
 * 右上角开关切换「单击 / 双击」两套映射，与 FBReader 的双击动作语义一致。
 *
 * @param map 当前方案，修改通过 [onChange] 回传新快照
 */
@Composable
internal fun ReaderTapZoneEditor(
    map: ReaderTapZoneMap,
    onChange: (ReaderTapZoneMap) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var editingDouble by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().testTag("tap-zone-editor"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("点击分区", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChip(selected = !editingDouble, onClick = { editingDouble = false },
                label = { Text("单击") }, modifier = Modifier.testTag("tap-zone-single"))
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = editingDouble, onClick = { editingDouble = true },
                label = { Text("双击") }, modifier = Modifier.testTag("tap-zone-double"))
        }
        Text("点按任意格子设置该区域的动作；「四向翻页」等预设可直接使用，也可逐格调整后保存为自定义。",
            Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 网格按行列排布，保持 3 × 3；窄屏时格子自动缩小。
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("tap-zone-grid"),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(map.rows) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(map.columns) { column ->
                        val action = if (editingDouble) map.doubleAt(column, row) else map.singleAt(column, row)
                        TapZoneCell(action, Modifier.weight(1f)) { editing = column to row }
                    }
                }
            }
        }
    }

    editing?.let { (column, row) ->
        val current = if (editingDouble) map.doubleAt(column, row) else map.singleAt(column, row)
        AppSheet(onDismissRequest = { editing = null }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                Text("第 ${row + 1} 行第 ${column + 1} 列 · ${if (editingDouble) "双击" else "单击"}",
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
                ReaderTapAction.editable.forEach { action ->
                    ListItem(
                        headlineContent = { Text(action.label()) },
                        leadingContent = { Icon(action.icon(), null) },
                        supportingContent = { Text(action.actionHint()) },
                        trailingContent = if (action == current) ({ Icon(Icons.Outlined.Check, "当前") }) else null,
                        modifier = Modifier.clickable {
                            onChange(map.withAction(column, row, editingDouble, action))
                            editing = null
                        }.testTag("tap-zone-option-${action.name}"),
                    )
                }
            }
        }
    }
}

@Composable
private fun TapZoneCell(action: ReaderTapAction, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val active = action != ReaderTapAction.None
    Surface(
        modifier = modifier.aspectRatio(1.35f).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (active) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(action.icon(), null, tint = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(action.label(), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (active) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
        }
    }
}

/** 动作说明，供选择列表显示。 */
internal fun ReaderTapAction.actionHint(): String = when (this) {
    ReaderTapAction.PreviousPage -> "翻到上一页或上一屏"
    ReaderTapAction.NextPage -> "翻到下一页或下一屏"
    ReaderTapAction.Navigate -> "打开章节目录"
    ReaderTapAction.Menu -> "显示或收起阅读工具栏"
    ReaderTapAction.None -> "此区域不响应，事件继续传递"
}
