package cc.novelia.app.ui.account

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cc.novelia.app.ui.navigation.AppController

/** 线路变化重建表单、取消旧请求，并清除只保存在内存中的密码和验证码。 */
@Composable fun LoginScreen(c: AppController, forum: Boolean = false) {
    val selected by c.app.bookSources.state.collectAsStateWithLifecycle()
    DisposableEffect(c) { onDispose { c.afterLogin = null } }
    key(selected.revision, forum) { LoginForm(c, selected, forum) }
}
