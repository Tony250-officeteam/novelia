package cc.novelia.app.ui.community

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.novelia.app.ui.components.base.AppIconButton
import cc.novelia.app.ui.components.base.AppLazyColumn
import cc.novelia.app.ui.components.base.AppTextButton
import cc.novelia.app.ui.components.base.AsyncContent
import cc.novelia.app.ui.components.base.EmptyState
import cc.novelia.app.ui.components.base.Screen
import cc.novelia.app.ui.components.base.TextPrompt
import cc.novelia.app.ui.components.base.displayDate
import cc.novelia.app.ui.navigation.AppController
import cc.novelia.app.ui.navigation.ObserveForumLogin
import java.time.Instant

@Composable internal fun ForumBlacklistScreen(c: AppController) {
    ObserveForumLogin(c.session, c.forumSession)
    val session = c.forumSession
    val api = c.forumApi.blacklist
    val profile by session.profile.collectAsStateWithLifecycle()
    val binding = session.capture()
    val revision by c.app.forumBlacklistRevision.collectAsStateWithLifecycle()
    var adding by remember(binding) { mutableStateOf(false) }
    var saving by remember(binding) { mutableStateOf(false) }
    fun setBlocked(userId: Long, blocked: Boolean) {
        if(saving) return
        saving = true
        c.action(if(blocked) "已加入账号黑名单" else "已取消拉黑") {
            try {
                require(!blocked || userId != profile?.userId) { "不能拉黑自己" }
                api.setBlocked(userId, blocked, binding)
            } finally { saving = false }
        }
    }
    Screen("账号黑名单", c::back, actions = {
        if(profile != null) AppIconButton(enabled = !saving, onClick = { adding = true }) {
            Icon(Icons.Outlined.Add, "按用户 ID 拉黑")
        }
    }) { padding ->
        if(profile == null) Box(Modifier.padding(padding)) {
            EmptyState("登录后管理账号黑名单", "拉黑设置保存在账号中，可在不同设备使用。", Icons.Outlined.PersonOff,
                "登录论坛", { c.go("forum-login") })
        } else AsyncContent(listOf(binding, revision), load = { api.list(binding) }, modifier = Modifier.padding(padding)) { result, _ ->
            AppLazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("${profile?.username} · ${result.total} / 1000 人", style = MaterialTheme.typography.titleMedium)
                    Text("拉黑后，该用户的帖子和评论将从当前账号的列表中隐藏。本地屏蔽可在设置中单独管理。",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
                if(result.items.isEmpty()) item { EmptyState("账号黑名单为空", "可以从帖子或评论中拉黑用户。", Icons.Outlined.PersonOff) }
                items(result.items, key = { it.userId }) { entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("用户 #${entry.userId}", style = MaterialTheme.typography.titleSmall)
                            Text("加入于 ${displayDate(Instant.parse(entry.createdAt).epochSecond)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AppTextButton(enabled = !saving, onClick = { setBlocked(entry.userId, false) }) { Text("取消拉黑") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if(adding) TextPrompt("添加到账号黑名单", "输入数字用户 ID", onDismiss = { adding = false }) { input ->
        val userId = input.trim().toLongOrNull()
        if(userId == null || userId <= 0) c.message("请输入有效的正整数用户 ID")
        else setBlocked(userId, true)
    }
}
