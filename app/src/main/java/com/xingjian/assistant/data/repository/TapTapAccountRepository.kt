package com.xingjian.assistant.data.repository

import android.webkit.CookieManager
import com.xingjian.assistant.core.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 用户自配置的 TapTap 登录态，仅以加密 Cookie 保存在本机 */
data class TapTapSession(val cookie: String)

/**
 * TapTap 账号登录态存储：
 * 1) 应用内 WebView 完成登录后，从 CookieManager 读取 Cookie；
 * 2) 也支持手动粘贴 Cookie（网页登录不可用时的兜底）。
 */
class TapTapAccountRepository(private val store: SecureStore) {

    private val _session = MutableStateFlow(load())
    val session: StateFlow<TapTapSession?> = _session.asStateFlow()

    /** 读取 WebView 中已登录的 Cookie（多域合并，按 Cookie 名去重） */
    fun captureCookieFromWebView(): String = COOKIE_HOSTS
        .mapNotNull { host -> runCatching { CookieManager.getInstance().getCookie(host) }.getOrNull() }
        .flatMap { it.split(';') }
        .map { it.trim() }
        .filter { it.isNotBlank() && it.contains('=') }
        .distinctBy { it.substringBefore('=') }
        .joinToString("; ")

    fun save(rawCookie: String): Boolean {
        val normalized = rawCookie.trim().removePrefix("Cookie:").trim()
        if (normalized.length < MIN_LENGTH || !normalized.contains('=')) return false
        store.put(KEY_COOKIE, normalized)
        _session.value = TapTapSession(normalized)
        return true
    }

    fun clear() {
        store.remove(KEY_COOKIE)
        runCatching { CookieManager.getInstance().removeAllCookies(null) }
        _session.value = null
    }

    /** 本地数据被整体清空后重新同步状态 */
    fun refresh() {
        _session.value = load()
    }

    private fun load(): TapTapSession? =
        store.get(KEY_COOKIE)?.takeIf { it.isNotBlank() }?.let { TapTapSession(it) }

    private companion object {
        const val KEY_COOKIE = "taptap_cookie"
        const val MIN_LENGTH = 16
        val COOKIE_HOSTS = listOf(
            "https://www.taptap.cn",
            "https://accounts.taptap.cn",
            "https://d.taptap.cn"
        )
    }
}
