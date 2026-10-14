@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cc.novelia.app.ui.toolbar

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.novelia.app.data.model.EdgeToolbarSettings
import cc.novelia.app.ui.account.*
import cc.novelia.app.ui.components.base.*
import cc.novelia.app.ui.navigation.AppController
import kotlin.math.roundToInt

internal data class EdgeShortcut(val id: String, val title: String, val icon: ImageVector)
internal val edgeShortcuts = listOf(
    EdgeShortcut("history", "阅读历史", Icons.Outlined.History),
    EdgeShortcut("downloads", "下载管理", Icons.Outlined.Download),
    EdgeShortcut("updates", "书架更新", Icons.Outlined.Notifications),
    EdgeShortcut("notes", "书签与笔记", Icons.Outlined.Bookmarks),
    EdgeShortcut("tools", "文件工具", Icons.Outlined.Build),
    EdgeShortcut("settings", "设置", Icons.Outlined.Settings),
)

@Composable internal fun EdgeToolbarSettingsScreen(c: AppController) {
    val library by c.store.state.collectAsStateWithLifecycle()
    val settings = library.edgeToolbar.normalized()
    fun update(change: (EdgeToolbarSettings) -> EdgeToolbarSettings) {
        c.store.update { it.copy(edgeToolbar = change(it.edgeToolbar.normalized())) }
    }
    var reset by remember { mutableStateOf(false) }
    ProfileDetailScreen("边缘工具栏", c::back) { padding ->
        ProfileDetailList(Modifier.padding(padding)) {
            item { ProfileToggle("启用边缘工具栏", "带快捷入口图标的浮动圆点，可上下拖动", settings.enabled) { value -> update { it.copy(enabled = value) } } }
            item { ProfileMenuRow("编辑快捷入口", settings.entries.mapNotNull { id -> edgeShortcuts.find { it.id == id }?.title }.joinToString("、"),
                Icons.Outlined.Widgets, { c.go("edge-toolbar-edit", replaceTop = true) }) }
            item { ProfileSectionTitle("位置与外观") }
            item { ProfileChoiceRow("屏幕侧边", listOf("左侧", "右侧"), EdgeToolbarSettings.SIDES.indexOf(settings.side)) { index -> update { it.copy(side = EdgeToolbarSettings.SIDES[index]) } } }
            item { ToolbarSlider("垂直位置", settings.position, 0f..1f) { value -> update { it.copy(position = value) } } }
            item { ProfileMenuRow("恢复默认高度", "回到窗口安全区域 55% 的位置", Icons.Outlined.RestartAlt, { update { it.copy(position = .55f) } }) }
            item { ProfileChoiceRow("闲置后缩回边缘", listOf("3 秒", "5 秒", "8 秒", "关闭"), EdgeToolbarSettings.IDLE_SECONDS.indexOf(settings.idleSeconds)) { index -> update { it.copy(idleSeconds = EdgeToolbarSettings.IDLE_SECONDS[index]) } } }
            item { ToolbarSlider("半隐藏时不透明度", settings.idleOpacity, 0.3f..0.8f) { value -> update { it.copy(idleOpacity = value) } } }
            item { ProfileSectionTitle("显示方式") }
            item { ProfileChoiceRow("阅读时显示", listOf("跟随阅读菜单", "始终隐藏", "始终显示"), EdgeToolbarSettings.READER_VISIBILITY.indexOf(settings.readerVisibility)) { index -> update { it.copy(readerVisibility = EdgeToolbarSettings.READER_VISIBILITY[index]) } } }
            item { ProfileToggle("显示下载任务数量", "展开后显示等待和下载中的任务数量", settings.downloadBadge) { value -> update { it.copy(downloadBadge = value) } } }
            item { ProfileMenuRow("恢复工具栏默认配置", "重置入口、位置和显示方式", Icons.Outlined.Restore, { reset = true }) }
        }
    }
    if(reset) ConfirmDialog("恢复工具栏默认配置？", "仅重置边缘工具栏，其他应用设置不受影响。", { reset = false }, confirmLabel = "恢复默认") {
        update { EdgeToolbarSettings() }; reset = false
    }
}

@Composable private fun ToolbarSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    var pending by remember(value) { mutableFloatStateOf(value) }
    ProfileDetailCard {
        Column(Modifier.padding(18.dp)) {
            Text("$title · ${(pending * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            AppSlider(pending, { pending = it }, Modifier.fillMaxWidth().semantics { contentDescription = title },
                valueRange = range, onValueChangeFinished = { onChange(pending) })
        }
    }
}

@Composable internal fun EdgeToolbarEditorScreen(c: AppController) {
    var selected by rememberSaveable { mutableStateOf<List<String>>(c.store.state.value.edgeToolbar.normalized().entries.toList()) }
    fun add(id: String) {
        if(selected.size < 5 && id !in selected) selected = selected + id
    }
    fun move(id: String, delta: Int) {
        val index = selected.indexOf(id)
        val other = index + delta
        if(index >= 0 && other in selected.indices) selected = ArrayList(selected).apply {
            val item = removeAt(index); add(other, item)
        }
    }
    ProfileDetailScreen("编辑快捷入口", c::back, actions = {
        AppTextButton(onClick = {
            c.store.update { it.copy(edgeToolbar = it.edgeToolbar.normalized().copy(entries = selected.toList())) }
            c.back()
        }) { Text("完成") }
    }) { padding ->
        ProfileDetailList(Modifier.padding(padding)) {
            item { ProfileSectionTitle("已选入口 · ${selected.size} / 5") }
            selected.forEachIndexed { index, id ->
                val entry = edgeShortcuts.first { it.id == id }
                item(key = "selected-$id") { ProfileDetailCard {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(entry.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Text(entry.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            AppIconButton(onClick = { move(id, -1) }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, "上移${entry.title}") }
                            AppIconButton(onClick = { move(id, 1) }, enabled = index < selected.lastIndex) { Icon(Icons.Outlined.ArrowDownward, "下移${entry.title}") }
                            AppIconButton(onClick = { selected = ArrayList(selected.filterNot { it == id }) }, enabled = selected.size > 1) { Icon(Icons.Outlined.Remove, "移除${entry.title}") }
                        }
                    }
                } }
            }
            item { ProfileSectionTitle("可添加") }
            edgeShortcuts.filter { it.id !in selected }.forEach { entry ->
                item(key = "available-${entry.id}") { ProfileMenuRow(entry.title, if(selected.size == 5) "最多添加 5 个入口" else "添加到末尾", entry.icon,
                    { add(entry.id) }, trailing = {
                        AppIconButton(onClick = { add(entry.id) }, enabled = selected.size < 5) { Icon(Icons.Outlined.Add, "添加${entry.title}") }
                    }) }
            }
            item { ProfileSummary("保存与取消", "至少保留一个入口。点击完成保存顺序，返回取消本次编辑；可在工具栏设置中关闭整个工具栏。") }
        }
    }
}
