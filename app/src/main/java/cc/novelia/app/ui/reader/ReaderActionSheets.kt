@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cc.novelia.app.ui.reader

import cc.novelia.app.ui.components.base.AppButton
import cc.novelia.app.ui.components.base.AppTextButton
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.model.ReaderSettings
import cc.novelia.app.reader.*
import cc.novelia.app.ui.components.base.AppScrollColumn
import cc.novelia.app.ui.components.base.appVerticalScroll
import cc.novelia.app.ui.feedback.MidoriSticker
import cc.novelia.app.ui.feedback.StickerAccent


/** 弹层不持有阅读位置或异步任务；关闭、朗读及书签操作交回页面。 */
@Composable internal fun ReaderSpeechSheet(
    settings: ReaderSettings, speechStatus: String, onDismiss: () -> Unit,
    onStart: () -> Unit, onPause: () -> Unit, onResume: () -> Unit,
    onStop: () -> Unit, onSettings: () -> Unit,
) {
    ReaderSheet(onDismissRequest = onDismiss) {
        AppScrollColumn(contentModifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("系统朗读", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if(speechStatus == ReadAloudService.SLEEP_TIMER_FINISHED) StickerAccent(MidoriSticker.Sleep, speechStatus, Modifier.size(64.dp))
                Text(speechStatus.ifBlank { if(settings.speechContinueChapters) "从当前段落开始，自动连续朗读后续章节。语音由系统提供。" else "从当前段落朗读至本章结束。语音由系统提供。" }, style = MaterialTheme.typography.bodyMedium)
            }
            Text("${settings.speechRate}× · ${settings.speechMinutes} 分钟后停止", style = MaterialTheme.typography.labelLarge)
            if(settings.speechContinueChapters) Text(if(settings.speechNetworkContinuation) "连续听书 · 优先本地与缓存，未缓存章节自动联网加载" else "连续听书 · 仅本地与缓存章节", style = MaterialTheme.typography.bodySmall)
            AppButton(onClick = onStart, Modifier.fillMaxWidth(), enabled = speechStatus != "正在准备朗读…") { Text(if(speechStatus == "正在准备朗读…") "正在准备朗读…" else "从这里开始朗读") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTextButton(onClick = onPause, enabled = speechStatus.startsWith("正在朗读") || speechStatus == "正在准备下一章…") { Text("暂停") }
                AppTextButton(onClick = onResume, enabled = speechStatus == "朗读已暂停" || speechStatus.startsWith("续章已暂停")) { Text("继续") }
                AppTextButton(onClick = onStop) { Text("停止") }
                AppTextButton(onClick = onSettings) { Text("设置") }
            }
        }
    }
}

@Composable internal fun ReaderParagraphSheet(
    paragraph: ReadingParagraph, onDismiss: () -> Unit,
    onShare: () -> Unit, onBookmark: () -> Unit, onEditNote: () -> Unit,
) {
    ReaderSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            SelectionContainer { Text(paragraph.parts.joinToString("\n\n") { it.text }, Modifier.heightIn(max = 240.dp).appVerticalScroll(rememberScrollState())) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextButton(onClick = onShare, modifier = Modifier.heightIn(min = 48.dp)) { Text("分享段落") }
                AppTextButton(onClick = onBookmark, modifier = Modifier.heightIn(min = 48.dp)) { Text("保存书签") }
                AppTextButton(onClick = onEditNote, modifier = Modifier.heightIn(min = 48.dp)) { Text("编辑笔记") }
            }
        }
    }
}
