package cc.novelia.app.ui.account

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cc.novelia.app.data.network.BookSource
import cc.novelia.app.data.network.SourceSelection
import cc.novelia.app.data.sync.CloudSyncWorker
import cc.novelia.app.ui.components.base.AppButton
import cc.novelia.app.ui.components.base.AppChipFlowRow
import cc.novelia.app.ui.components.base.AppTextButton
import cc.novelia.app.ui.components.base.Screen
import cc.novelia.app.ui.components.base.friendlyMessage
import cc.novelia.app.ui.navigation.AppController
import cc.novelia.app.ui.navigation.finishLoginNavigation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LoginMode { LOGIN, REGISTER, RESET_PASSWORD }

@Composable internal fun LoginForm(c: AppController, selected: SourceSelection, forum: Boolean) {
    val session = if(forum) c.forumSession else c.session
    // 密码和验证码不使用 rememberSaveable，也不写入磁盘或日志。
    var mode by remember { mutableStateOf(LoginMode.LOGIN) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(forum) }
    var cooldown by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val register = mode == LoginMode.REGISTER
    val resetting = mode == LoginMode.RESET_PASSWORD
    val enabled = !busy && !sending && !switching && !restoring

    LaunchedEffect(cooldown) { if(cooldown > 0) { delay(1000); cooldown-- } }
    LaunchedEffect(session, selected) {
        if(forum) {
            val signedIn = try { session.loginFromSharedAuth(c.session, explicit = true) }
                catch(cancelled: CancellationException) { throw cancelled }
                catch(_: Exception) { false }
            try {
                // 前一页的自动登录可能已先完成，继续复用其有效会话。
                if(signedIn || session.profile.value != null) finishLoginNavigation(c, forum = true)
            } finally { restoring = false }
        }
    }
    fun changeMode(next: LoginMode) {
        mode = next; password = ""; otp = ""; error = null; notice = null
    }
    fun complete() {
        password = ""; otp = ""
        if(!forum && c.store.state.value.autoSync) session.profile.value?.username?.let { CloudSyncWorker.enqueue(c.app, it) }
        finishLoginNavigation(c, forum)
        c.message("已登录")
    }

    Screen(if(register) "注册 Novelia 账号" else if(resetting) "找回密码" else if(forum) "登录 Novelia 论坛" else "登录 Novelia", c::back) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("书源线路", style = MaterialTheme.typography.titleMedium)
            AppChipFlowRow {
                BookSource.entries.forEach { source ->
                    FilterChip(selected = selected.source == source,
                        enabled = !busy && !sending && !switching && (source == BookSource.ORIGINAL || selected.hasAccessToken),
                        modifier = Modifier.testTag("login-source-${source.id}"),
                        label = { Text(source.title) }, onClick = {
                            if(source != selected.source) {
                                switching = true; error = null
                                scope.launch {
                                    try { withContext(Dispatchers.IO) { c.app.bookSources.select(source) } }
                                    catch(cancelled: CancellationException) { throw cancelled }
                                    catch(e: Exception) { error = e.friendlyMessage() }
                                    finally { switching = false }
                                }
                            }
                        })
                }
            }
            Text(selected.source.origin.substringAfter("://"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("使用 Novelia 账号。切换书源会同时切换小说和论坛线路。" + (if(forum) "论坛登录状态单独保存。" else ""),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(!selected.hasAccessToken) Text("当前安装包未包含镜像配置", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if(restoring) Text("正在恢复登录状态…", style = MaterialTheme.typography.bodyMedium)
            if(!resetting) OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth().testTag("login-username"), enabled = enabled,
                label = { Text(if(register) "用户名" else "用户名或邮箱") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().testTag("login-password"), enabled = enabled,
                label = { Text(if(resetting) "新密码" else "密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if(register || resetting) {
                OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), enabled = enabled,
                    label = { Text("邮箱") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(otp, { otp = it }, Modifier.fillMaxWidth().testTag("login-otp"), enabled = enabled,
                    label = { Text("邮箱验证码") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                AppTextButton(enabled = enabled && cooldown == 0 && email.isNotBlank(), onClick = {
                    if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { error = "请填写有效的邮箱"; return@AppTextButton }
                    sending = true; error = null; notice = null
                    scope.launch {
                        try { session.requestOtp(email, resetPassword = resetting); notice = "验证码已发送，请检查邮箱及垃圾箱"; cooldown = 60 }
                        catch(cancelled: CancellationException) { throw cancelled }
                        catch(e: Exception) { error = e.friendlyMessage(); cooldown = 60 }
                        finally { sending = false }
                    }
                }) { Text(if(sending) "正在发送…" else if(cooldown > 0) "${cooldown} 秒后重试" else "发送邮箱验证码") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            AppButton(enabled = enabled && (if(resetting) email.isNotBlank() else username.isNotBlank()) && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(), onClick = {
                    if((register || resetting) && (password.length !in 8..100 ||
                            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() || !otp.trim().matches(Regex("[0-9]{6}")))) {
                        error = "请检查密码（8–100 字符）、邮箱和 6 位验证码"; return@AppButton
                    }
                    if(register && username.trim().length !in 2..16) { error = "用户名应为 2–16 字符"; return@AppButton }
                    busy = true; error = null; notice = null
                    scope.launch {
                        try {
                            if(resetting) {
                                session.resetPassword(email, password, otp)
                                username = email.trim(); changeMode(LoginMode.LOGIN)
                                notice = "密码已重置，请使用新密码登录"
                            } else if(session.login(username, password, email.takeIf { register }, otp.takeIf { register })) complete()
                            else error = "认证成功，但未取得登录会话，请稍后重试"
                        } catch(cancelled: CancellationException) { throw cancelled }
                        catch(e: Exception) { error = e.friendlyMessage() }
                        finally { busy = false }
                    }
                }) { Text(if(busy) "正在提交…" else if(register) "注册并登录" else if(resetting) "重置密码" else "登录") }
            AppTextButton(enabled = enabled, onClick = { changeMode(if(mode == LoginMode.LOGIN) LoginMode.REGISTER else LoginMode.LOGIN) }) {
                Text(if(mode == LoginMode.LOGIN) "没有账号，注册" else "已有账号，去登录")
            }
            if(mode == LoginMode.LOGIN) AppTextButton(enabled = enabled, onClick = { changeMode(LoginMode.RESET_PASSWORD) }) { Text("忘记密码？") }
        }
    }
}
