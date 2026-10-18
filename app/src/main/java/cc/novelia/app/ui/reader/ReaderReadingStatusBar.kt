package cc.novelia.app.ui.reader

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cc.novelia.app.reader.safeFraction
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 常驻信息不依赖菜单显隐；时钟只在前台按分钟更新，电子纸也无需逐秒刷新。 */
@Composable internal fun ReaderReadingStatusBar(
    state: ReaderBottomToolbarState, staticPagination: Boolean, foreground: Color, modifier: Modifier = Modifier,
) {
    Row(modifier.testTag("reader-reading-status-bar").padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ReaderClock(foreground)
        Text(state.chapterTitle, Modifier.weight(1f).testTag("reader-status-chapter-title"),
            color = foreground, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if(staticPagination && !state.pageReady) {
            Text("正在分页…", color = foreground, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
        } else {
            val percent = if(staticPagination) {
                val total = state.pageCount.coerceAtLeast(1)
                val current = (state.pageIndex + 1).coerceIn(1, total)
                Text("$current / $total", Modifier.testTag("reader-page-counter").semantics { contentDescription = "本章第${current}页，共${total}页" },
                    color = foreground, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
                (current.toLong() * 100 / total).toInt()
            } else (state.progress.safeFraction() * 100).toInt()
            Text("本章 $percent%", Modifier.testTag("reader-chapter-progress"),
                color = foreground, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
        }
    }
}

@Composable private fun ReaderClock(foreground: Color) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val time by produceState(DateFormat.getTimeFormat(context).format(Date()), context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(isActive) {
                value = DateFormat.getTimeFormat(context).format(Date())
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
    Text(time, Modifier.testTag("reader-status-clock"), color = foreground, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1)
}
