package cc.novelia.app.ui.discover

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cc.novelia.app.ui.components.base.AppFilledTonalButton
import cc.novelia.app.ui.components.base.AppTextButton

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun DiscoverFilterHelp(onAssistant: (() -> Unit)?, onSyntax: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("discover-filter-help"),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("搜索帮助", style = MaterialTheme.typography.titleSmall)
            Text("搜索框支持原站查询表达式，规则与示例可在使用教程中查看。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if(onAssistant != null) AppFilledTonalButton(onClick = onAssistant) { Text("打开辅助搜索") }
                AppTextButton(onClick = onSyntax) { Text("查看搜索语法") }
            }
        }
    }
}
