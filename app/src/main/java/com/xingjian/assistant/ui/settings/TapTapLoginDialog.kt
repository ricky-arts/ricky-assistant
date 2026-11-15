package com.xingjian.assistant.ui.settings

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 应用内 TapTap 登录页：WebView 完成登录后读取 Cookie，
 * 同时保留手动粘贴 Cookie 的兜底入口。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TapTapLoginDialog(
    onDismiss: () -> Unit,
    onSaveFromWebView: () -> Unit,
    onSaveCookie: (String) -> Unit
) {
    var manualCookie by remember { mutableStateOf("") }
    var webViewHolder by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭登录页")
                    }
                    Text(
                        text = "TapTap 账号登录",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = onSaveFromWebView) {
                        Text("保存登录态")
                    }
                }
                Text(
                    text = "在下方网页完成登录后，点右上角“保存登录态”；若网页登录异常，可直接粘贴 Cookie 保存。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                AndroidView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = WebViewClient()
                            loadUrl(LOGIN_URL)
                            webViewHolder = this
                        }
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualCookie,
                        onValueChange = { manualCookie = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("手动粘贴 Cookie") },
                        placeholder = { Text("例如 token=…; user_id=…") },
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = { onSaveCookie(manualCookie) },
                        enabled = manualCookie.contains('=')
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewHolder?.apply {
                stopLoading()
                destroy()
            }
        }
    }
}

private const val LOGIN_URL = "https://www.taptap.cn/login"
