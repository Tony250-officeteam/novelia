@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package cc.novelia.app.ui.shelf

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.novelia.app.ui.components.base.AppScrollColumn
import cc.novelia.app.ui.components.base.AppSheet
import cc.novelia.app.ui.components.base.MenuRow
import cc.novelia.app.ui.components.base.TextPrompt

@Composable internal fun CloudFavoriteLocalSheet(folders: List<String>, count: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var create by remember { mutableStateOf(false) }
    AppSheet(onDismissRequest = onDismiss) {
        AppScrollColumn(contentModifier = Modifier.padding(bottom = 28.dp)) {
            Text("加入本地收藏 · $count 本", Modifier.padding(20.dp), style = MaterialTheme.typography.titleLarge)
            Text("选择本地收藏夹。已在本地的作品保留原收藏夹，云端收藏不受影响。", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
            folders.forEach { folder -> MenuRow(folder, "保存到此设备", Icons.Outlined.Folder, { onSave(folder) }) }
            MenuRow("新建本地收藏夹", "创建并加入所选作品", Icons.Outlined.CreateNewFolder, { create = true })
        }
    }
    if(create) TextPrompt("新建本地收藏夹", "名称", onDismiss = { create = false }, onSave = onSave)
}
