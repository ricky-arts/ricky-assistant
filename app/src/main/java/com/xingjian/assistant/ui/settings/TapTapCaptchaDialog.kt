package com.xingjian.assistant.ui.settings

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 腾讯滑块人机验证弹窗。
 *
 * 直接在应用内渲染 TapTap 使用的腾讯验证码组件（TCaptcha），通过后把
 * 回调 JSON（ticket / randstr）交给调用方拼进 captcha_data 再次请求接口。
 * 这里只加载验证码组件本身，登录表单仍是应用自绘的。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TapTapCaptchaDialog(
    appId: String,
    onResult: (String) -> Unit,
    onCancel: () -> Unit
) {
    var holder by remember { mutableStateOf<WebView?>(null) }
    var failed by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    Dialog(
        onDismissRequest = onCancel,
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
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭人机验证")
                    }
                    Text(
                        text = "人机验证",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "TapTap 发送短信前需要完成一次人机验证，验证通过后会自动继续发送验证码。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (failed) {
                    Text(
                        text = "验证页面加载失败，请检查网络后重试，或改用网页登录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
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
                            settings.loadsImagesAutomatically = true
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            addJavascriptInterface(
                                CaptchaBridge(
                                    resultListener = { json ->
                                        mainHandler.post { onResult(json) }
                                    },
                                    cancelListener = {
                                        mainHandler.post { onCancel() }
                                    }
                                ),
                                "TapTapBridge"
                            )
                            webViewClient = object : WebViewClient() {
                                override fun onReceivedError(
                                    view: WebView,
                                    request: WebResourceRequest,
                                    error: WebResourceError
                                ) {
                                    if (request.isForMainFrame) failed = true
                                }
                            }
                            loadDataWithBaseURL(
                                "$CAPTCHA_BASE_URL/login",
                                captchaHtml(appId),
                                "text/html",
                                "UTF-8",
                                null
                            )
                            holder = this
                        }
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            holder?.apply {
                stopLoading()
                destroy()
            }
        }
    }
}

private class CaptchaBridge(
    private val resultListener: (String) -> Unit,
    private val cancelListener: () -> Unit
) {
    @JavascriptInterface
    fun onResult(json: String) {
        if (json.isNotBlank()) resultListener(json)
    }

    @JavascriptInterface
    fun onCancel() {
        cancelListener()
    }
}

private fun captchaHtml(appId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
<style>
html, body { margin: 0; padding: 0; height: 100%; background: #ffffff; }
</style>
<script src="https://turing.captcha.qcloud.com/TCaptcha.js"></script>
</head>
<body>
<script>
(function () {
  var called = false;
  function finish(result) {
    if (called) return;
    called = true;
    TapTapBridge.onResult(JSON.stringify(result));
  }
  function cancel() {
    if (called) return;
    called = true;
    TapTapBridge.onCancel();
  }
  try {
    var captcha = new TencentCaptcha("$appId", function (res) {
      if (res && res.ticket) { finish(res); } else { cancel(); }
    }, {});
    captcha.show();
  } catch (e) {
    cancel();
  }
})();
</script>
</body>
</html>
""".trimIndent()

private const val CAPTCHA_BASE_URL = "https://accounts.taptap.cn"
