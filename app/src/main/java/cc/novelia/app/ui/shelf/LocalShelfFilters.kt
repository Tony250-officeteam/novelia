@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cc.novelia.app.ui.shelf

import cc.novelia.app.ui.components.base.AppTextButton
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.library.ShelfBookType
import cc.novelia.app.data.library.readingStatuses
import cc.novelia.app.data.catalog.CharacterCountFilter
import cc.novelia.app.ui.components.base.CharacterCountFilterFields
import cc.novelia.app.data.model.Folder
import cc.novelia.app.ui.components.base.AppSheet
import cc.novelia.app.ui.components.base.AppScrollColumn

@Composable internal fun LocalShelfFilters(
    localFiles: Boolean, type: ShelfBookType, onType: (ShelfBookType) -> Unit,
    folders: List<String>, folder: String, onFolder: (String) -> Unit,
    sort: Int, onSort: (Int) -> Unit, query: String, onQuery: (String) -> Unit,
    status: String, onStatus: (String) -> Unit, expanded: Boolean, onExpanded: (Boolean) -> Unit,
    maxHeight: Dp, onCreateFolder: () -> Unit, onRenameFolder: () -> Unit, onDeleteFolder: () -> Unit,
    characters: CharacterCountFilter = CharacterCountFilter(), onCharacters: (CharacterCountFilter) -> Unit = {},
    controlsExpanded: Boolean = true, onToggleControls: () -> Unit = {},
    trailingContent: @Composable () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var characterFilterOpen by remember { mutableStateOf(false) }
    val statuses = listOf("全部") + readingStatuses
    val types = if(localFiles) listOf(ShelfBookType.All, ShelfBookType.Wenku, ShelfBookType.Local)
        else listOf(ShelfBookType.All, ShelfBookType.Web, ShelfBookType.Wenku, ShelfBookType.Local)
    val labels = if(localFiles) listOf("全部文件", "文库分卷", "本地小说") else listOf("全部", "网络小说", "文库小说", "本地小说")
    val choices = (listOf("全部") + folders).map { Folder(it, if(it == "全部") "全部收藏夹" else it) }
    val active = buildList {
        if(status != "全部") add(status)
        if(!localFiles && type == ShelfBookType.Web && characters.active) add(characters.summary())
    }
    val summary = active.joinToString(" · ")
    val context = listOf(labels[types.indexOf(type).coerceAtLeast(0)],
        choices.firstOrNull { it.id == folder }?.title ?: folder)
    val searchSummary = if(query.isBlank()) emptyList() else listOf("搜索：${query.trim()}")
    ShelfControlsPanel((context + searchSummary + active + listOf("最近阅读", "添加时间", "书名排序")[sort.coerceIn(0, 2)])
        .joinToString(" · "), controlsExpanded, onToggleControls, "local", headerActions = {
            ShelfSearchToggle(searchExpanded, query.isNotBlank(), { searchExpanded = !searchExpanded; focus.clearFocus() }, "local")
        }) {
        ShelfKindSwitch(labels, types.indexOf(type).coerceAtLeast(0), "local-novel-kind") { onType(types[it]) }
        ShelfToolbar(choices, choices.firstOrNull { it.id == folder }, onFolder, sort.toString(), { onSort(it.toInt()) },
            listOf("0" to "最近阅读", "1" to "添加时间", "2" to "书名排序"), active.size, expanded,
            { onExpanded(!expanded); focus.clearFocus() }, "local", filterMenuContent = { close ->
                Text("阅读状态", Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                statuses.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onStatus(option); close() },
                        modifier = Modifier.testTag("local-reading-status-$option").semantics { selected = status == option },
                        trailingIcon = if(status == option) ({ Icon(Icons.Outlined.Check, null) }) else null)
                }
                if(!localFiles && type == ShelfBookType.Web) {
                    HorizontalDivider()
                    DropdownMenuItem(text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("字数范围")
                            Text(characters.summary(), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, onClick = { close(); characterFilterOpen = true },
                        trailingIcon = { Icon(Icons.Outlined.ChevronRight, null) })
                }
                if(active.isNotEmpty()) {
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("重置筛选") }, onClick = {
                        onStatus("全部"); onCharacters(CharacterCountFilter()); close()
                    }, leadingIcon = { Icon(Icons.Outlined.FilterAltOff, null) })
                }
            }) { close ->
            DropdownMenuItem({ Text("新建收藏夹") }, { close(); onCreateFolder() }, leadingIcon = { Icon(Icons.Outlined.Add, null) })
            if(folder != "全部" && folder != "默认收藏") {
                DropdownMenuItem({ Text("重命名收藏夹") }, { close(); onRenameFolder() })
                DropdownMenuItem({ Text("删除收藏夹") }, { close(); onDeleteFolder() })
            }
        }
        if(active.isNotEmpty()) Text(summary, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
            .testTag("local-filter-summary"), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        trailingContent()
    }
    ShelfSearchField(searchExpanded, query, onQuery, onSubmit = {}, onClear = { onQuery("") },
        label = "搜索书名或作者", tagPrefix = "local")
    if(characterFilterOpen && !localFiles && type == ShelfBookType.Web) AppSheet(onDismissRequest = { characterFilterOpen = false }) {
        AppScrollColumn(Modifier.heightIn(max = maxHeight), contentModifier = Modifier.padding(bottom = 16.dp)) {
            CharacterCountFilterFields(characters, onCharacters)
            AppTextButton(onClick = { focus.clearFocus(); characterFilterOpen = false }, Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Text("完成")
            }
        }
    }
}
