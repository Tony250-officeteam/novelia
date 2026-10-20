package cc.novelia.app.ui.components.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.model.BookCard

/** 放在书名下方，不覆盖封面；本地与云端关系独立展示，窄屏可换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun BookFavoriteIndicator(book: BookCard) {
    val favorite = LocalBookListPresentation.current.favoriteState(book)
    if(!favorite.isSaved) return
    FlowRow(Modifier.testTag("book-favorite-${book.ref.key}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if(favorite.local) FavoriteLabel("本地收藏", Icons.Outlined.BookmarkAdded, "book-favorite-local-${book.ref.key}")
        if(favorite.cloudFolder != null) {
            val pending = favorite.pendingMethod == "PUT"
            FavoriteLabel(if(pending) "云端收藏待同步" else "云端收藏",
                if(pending) Icons.Outlined.CloudUpload else Icons.Outlined.CloudDone, "book-favorite-cloud-${book.ref.key}")
        }
    }
}

@Composable private fun FavoriteLabel(label: String, icon: ImageVector, tag: String) {
    Row(Modifier.testTag(tag).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
