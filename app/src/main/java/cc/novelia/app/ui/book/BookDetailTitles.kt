package cc.novelia.app.ui.book

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.model.BookCard
import cc.novelia.app.ui.components.base.AppIconButton
import cc.novelia.app.ui.theme.AppMotion
import cc.novelia.app.ui.theme.appReducedMotion

/** 保留详情原有的主标题、状态/来源、原文标题顺序和间距。 */
@Composable internal fun BookDetailTitles(book: BookCard, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BookDetailTitle(book.ref.key, book.title)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if(book.originalTitle.isNotBlank()) BookDetailTitle(book.ref.key, book.originalTitle, original = true)
    }
}

/** 每个标题独立折叠；箭头留在标题末行右侧，不额外占用一行。 */
@Composable internal fun BookDetailTitle(bookKey: String, title: String, modifier: Modifier = Modifier, original: Boolean = false) {
    val text = title.trim()
    val tag = if(original) "book-detail-original-title" else "book-detail-title"
    val label = if(original) "原文标题" else "主标题"
    var expanded by rememberSaveable(bookKey, text) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val style = if(original) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleLarge
    val reducedMotion = appReducedMotion()
    val targetRotation = if(expanded) 180f else 0f
    val rotation by animateFloatAsState(targetRotation,
        animationSpec = tween(if(reducedMotion) 0 else AppMotion.Standard, easing = FastOutSlowInEasing),
        label = "$tag arrow")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 先按完整宽度测量，避免按钮自身挤出溢出；展开后仍保留收起入口。
        val overflows = measurer.measure(text, style, maxLines = 2, overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = constraints.maxWidth)).hasVisualOverflow
        Row(Modifier.fillMaxWidth().testTag("$tag-row"), verticalAlignment = Alignment.Bottom) {
            Text(text,
                Modifier.weight(1f).testTag(tag).semantics { if(!original) heading() }
                    .then(if(reducedMotion) Modifier else Modifier.animateContentSize(
                        tween(AppMotion.Standard, easing = FastOutSlowInEasing), alignment = Alignment.TopStart)),
                style = style,
                color = if(original) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = if(expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            if(expanded || overflows) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    AppIconButton(onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp).testTag("$tag-toggle")
                            .semantics { stateDescription = if(expanded) "${label}已展开" else "${label}已折叠" }) {
                        Icon(Icons.Outlined.ExpandMore, if(expanded) "收起$label" else "展开$label",
                            Modifier.size(18.dp).rotate(if(reducedMotion) targetRotation else rotation))
                    }
                }
            }
        }
    }
}
