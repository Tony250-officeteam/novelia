package cc.novelia.app.ui.community

import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.novelia.app.data.auth.Session
import cc.novelia.app.data.community.ForumBlacklistApi
import cc.novelia.app.ui.components.base.AppTextButton
import cc.novelia.app.ui.components.base.ConfirmDialog
import cc.novelia.app.ui.navigation.AppController

/** 写入成功才改变状态；确认框和请求均绑定打开时的账号、登录代次及线路。 */
@Composable internal fun ForumBlacklistAction(c: AppController, session: Session, api: ForumBlacklistApi,
    userId: Long, username: String, blocked: Boolean, onChanged: (Boolean) -> Unit = {}) {
    val profile by session.profile.collectAsStateWithLifecycle()
    val binding = session.capture()
    var currentBlocked by remember(userId, binding, blocked) { mutableStateOf(blocked) }
    var confirm by remember(userId, binding) { mutableStateOf(false) }
    var saving by remember(userId, binding) { mutableStateOf(false) }
    if(profile == null || profile?.userId == userId || userId <= 0) return
    val label = if(currentBlocked) "取消拉黑" else "拉黑"
    AppTextButton(enabled = !saving, onClick = { confirm = true }) { Text(if(saving) "保存中…" else label) }
    if(confirm) ConfirmDialog("$label $username？",
        if(currentBlocked) "恢复在帖子列表和评论中显示该用户的内容。" else "该账号的帖子和评论将从你的列表中隐藏，可在账号黑名单中恢复。",
        { confirm = false }, confirmLabel = label) {
        if(!saving) {
            val targetBlocked = !currentBlocked
            confirm = false
            saving = true
            c.action(if(targetBlocked) "已拉黑 $username" else "已取消拉黑 $username") {
                try {
                    api.setBlocked(userId, targetBlocked, binding)
                    session.ensureCurrent(binding)
                    currentBlocked = targetBlocked
                    onChanged(targetBlocked)
                } finally { saving = false }
            }
        }
    }
}
