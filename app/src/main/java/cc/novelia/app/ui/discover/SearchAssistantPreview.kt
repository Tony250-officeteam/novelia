package cc.novelia.app.ui.discover

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.catalog.SearchExpression
import cc.novelia.app.ui.components.base.AppAlertDialog
import cc.novelia.app.ui.components.base.AppIconButton
import cc.novelia.app.ui.components.base.AppScrollColumn
import cc.novelia.app.ui.components.base.AppTextButton

/** 预览占据固定行数；完整表达式单独查看，避免条件增长挤动正在编辑的表单。 */
@Composable internal fun SearchAssistantPreview(query: String, generated: String, compact: Boolean, hasError: Boolean) {
    var detailsOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(Modifier.fillMaxWidth().testTag("assistant-preview-bar"), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if(hasError) "生成的条件 · 需调整" else "生成的条件", style = MaterialTheme.typography.labelSmall,
                    color = if(hasError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(generated.ifBlank { "添加条件后在此实时预览" }, Modifier.testTag("assistant-preview"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if(hasError) MaterialTheme.colorScheme.error else if(generated.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    minLines = if(compact) 1 else 2, maxLines = if(compact) 1 else 2, overflow = TextOverflow.Ellipsis)
            }
            AppIconButton(onClick = { focus.clearFocus(); keyboard?.hide(); detailsOpen = true }, enabled = generated.isNotBlank(),
                modifier = Modifier.testTag("assistant-preview-details")) {
                Icon(Icons.Outlined.UnfoldMore, "查看完整条件")
            }
        }
    }
    if(detailsOpen) AppAlertDialog(onDismissRequest = { detailsOpen = false }, title = { Text("条件预览") }, text = {
        AppScrollColumn(Modifier.heightIn(max = 360.dp)) {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("生成的条件", style = MaterialTheme.typography.labelLarge)
                    Text(generated, Modifier.testTag("assistant-preview-full"), style = MaterialTheme.typography.bodyMedium)
                    if(query.isNotBlank()) {
                        HorizontalDivider()
                        Text("追加后的搜索", style = MaterialTheme.typography.labelLarge)
                        Text(SearchExpression.append(query, generated), Modifier.testTag("assistant-preview-appended"),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }, confirmButton = { AppTextButton(onClick = { detailsOpen = false }) { Text("返回编辑") } })
}
