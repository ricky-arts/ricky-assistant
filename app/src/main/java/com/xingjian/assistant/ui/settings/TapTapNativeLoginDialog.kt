package com.xingjian.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xingjian.assistant.data.remote.SendCodeResult
import com.xingjian.assistant.data.remote.TapTapAuthApi
import com.xingjian.assistant.data.remote.TapTapLoginResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 应用自绘的 TapTap 登录页：直接调用官方账号接口完成手机号验证码登录，
 * 登录成功后自动保存登录态（Cookie），不需要再手动点“保存登录态”。
 *
 * 发送短信前若触发腾讯滑块（captcha.needs），会在弹窗内加载验证码组件，
 * 通过后用 ticket/randstr 重试发送；仍未注册的手机号可切换到网页登录兜底。
 */
@Composable
fun TapTapNativeLoginDialog(
    onDismiss: () -> Unit,
    onLoggedIn: (cookie: String) -> Unit,
    onUseWebLogin: () -> Unit
) {
    val api = remember { TapTapAuthApi() }
    val scope = rememberCoroutineScope()

    var countryCode by remember { mutableStateOf("86") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loggingIn by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var captchaAppId by remember { mutableStateOf<String?>(null) }
    var captchaMeta by remember { mutableStateOf("") }

    val fullPhone = "+" + countryCode.trim().removePrefix("+") + phone
    val canSend = !sending && countdown == 0 && phone.length >= 5 && countryCode.isNotBlank()
    val canLogin = !loggingIn && phone.length >= 5 && code.length >= 4

    fun requestCode(captchaData: String?) {
        sending = true
        error = null
        notice = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { api.sendCode(fullPhone, captchaData) }
            sending = false
            when (result) {
                is SendCodeResult.Sent -> {
                    countdown = result.retryAfterSeconds.coerceIn(1, 120)
                    notice = "验证码已发送，请注意查收短信"
                }
                is SendCodeResult.CaptchaRequired -> {
                    if (result.appId.isBlank()) {
                        error = "需要人机验证，请改用网页登录"
                    } else {
                        captchaMeta = result.metadataJson
                        captchaAppId = result.appId
                    }
                }
                is SendCodeResult.Failed -> error = result.message
            }
        }
    }

    fun submitLogin() {
        loggingIn = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { api.login(fullPhone, code) }
            loggingIn = false
            when (result) {
                is TapTapLoginResult.Success -> onLoggedIn(result.cookie)
                is TapTapLoginResult.NeedProfile -> error = result.message
                is TapTapLoginResult.Failed -> error = result.message
            }
        }
    }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("TapTap 账号登录", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "使用手机号验证码登录。登录接口成功后会自动保存登录态并加密存放在本机。" +
                        "游戏下载使用官方签名直链，不依赖此账号。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = countryCode,
                        onValueChange = { input -> countryCode = input.filter { it.isDigit() }.take(4) },
                        modifier = Modifier.width(84.dp),
                        singleLine = true,
                        label = { Text("区号") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { input -> phone = input.filter { it.isDigit() }.take(15) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("手机号") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { input -> code = input.filter { it.isDigit() }.take(8) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("短信验证码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { requestCode(null) },
                        enabled = canSend
                    ) {
                        Text(
                            text = when {
                                sending -> "发送中…"
                                countdown > 0 -> "${countdown}s"
                                else -> "获取验证码"
                            }
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (notice != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = notice.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { submitLogin() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canLogin
                ) {
                    Text(if (loggingIn) "登录中…" else "登录")
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = onUseWebLogin) { Text("改用网页登录") }
                }
            }
        }
    }

    val appId = captchaAppId
    if (appId != null) {
        TapTapCaptchaDialog(
            appId = appId,
            onResult = { resultJson ->
                captchaAppId = null
                requestCode(mergeCaptchaData(captchaMeta, resultJson))
            },
            onCancel = {
                captchaAppId = null
                error = "已取消人机验证，验证码未发送"
            }
        )
    }
}

/** 把滑块回调与 error_metadata 合并成接口要求的 captcha_data */
private fun mergeCaptchaData(metadataJson: String, resultJson: String): String = runCatching {
    val merged = if (metadataJson.isBlank()) JSONObject() else JSONObject(metadataJson)
    runCatching { JSONObject(resultJson) }.getOrNull()?.let { result ->
        result.keys().forEach { key -> merged.put(key, result.get(key)) }
    }
    merged.toString()
}.getOrDefault(resultJson)
