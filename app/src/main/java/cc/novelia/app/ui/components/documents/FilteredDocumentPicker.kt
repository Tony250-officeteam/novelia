package cc.novelia.app.ui.components.documents

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.novelia.app.ui.components.base.AppIconButton
import cc.novelia.app.ui.components.base.AppTextButton

internal class FilteredDocumentPicker(
    val launch: () -> Unit,
    val showFallback: Boolean,
    val launchAllFiles: () -> Unit,
    val dismissFallback: () -> Unit,
)

@Composable internal fun rememberOpenDocumentPicker(mimeTypes: List<String>, onResult: (Uri?) -> Unit): FilteredDocumentPicker =
    rememberFilteredDocumentPicker(ActivityResultContracts.OpenDocument(), mimeTypes, { it == null }, onResult)

@Composable internal fun rememberOpenMultipleDocumentsPicker(mimeTypes: List<String>, onResult: (List<Uri>) -> Unit): FilteredDocumentPicker =
    rememberFilteredDocumentPicker(ActivityResultContracts.OpenMultipleDocuments(), mimeTypes, { it.isEmpty() }, onResult)

@Composable private fun <T> rememberFilteredDocumentPicker(
    contract: ActivityResultContract<Array<String>, T>, mimeTypes: List<String>, isEmpty: (T) -> Boolean, onResult: (T) -> Unit,
): FilteredDocumentPicker {
    // 工具切换格式时清除旧提示；旋转或从系统选择器返回时保留当前提示。
    var showFallback by rememberSaveable(mimeTypes) { mutableStateOf(false) }
    val filtered = rememberLauncherForActivityResult(contract) { result ->
        showFallback = isEmpty(result)
        onResult(result)
    }
    val allFiles = rememberLauncherForActivityResult(contract, onResult)
    return FilteredDocumentPicker(
        launch = { showFallback = false; filtered.launch(mimeTypes.toTypedArray()) },
        showFallback = showFallback,
        launchAllFiles = { showFallback = false; allFiles.launch(arrayOf("*/*")) },
        dismissFallback = { showFallback = false },
    )
}

@Composable internal fun DocumentPickerFallback(picker: FilteredDocumentPicker, enabled: Boolean = true, modifier: Modifier = Modifier) {
    if(!picker.showFallback) return
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("找不到文件？", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            AppIconButton(onClick = picker.dismissFallback) { Icon(Icons.Outlined.Close, "关闭导入提示") }
        }
        AppTextButton(onClick = picker.launchAllFiles, enabled = enabled) { Text("显示所有文件") }
    }
}
