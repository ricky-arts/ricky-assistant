package com.xingjian.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingjian.assistant.data.repository.CallRepository
import com.xingjian.assistant.data.repository.LedgerRepository
import com.xingjian.assistant.data.repository.MailRepository
import com.xingjian.assistant.data.repository.SettingsRepository
import com.xingjian.assistant.data.repository.SmsRepository
import com.xingjian.assistant.data.repository.TapTapSession
import com.xingjian.assistant.di.AppContainer
import com.xingjian.assistant.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LocalDataCounts(
    val sms: Int = 0,
    val calls: Int = 0,
    val mails: Int = 0,
    val transactions: Int = 0
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    smsRepository: SmsRepository,
    callRepository: CallRepository,
    mailRepository: MailRepository,
    ledgerRepository: LedgerRepository,
    private val container: AppContainer
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode

    val backgroundVersion: StateFlow<Int> = settings.backgroundVersion

    val autoLedgerEnabled: StateFlow<Boolean> = settings.autoLedgerEnabled

    /** TapTap 账号登录态（用于游戏模块官方直链下载） */
    val tapTapSession: StateFlow<TapTapSession?> = container.tapTapAccount.session

    val counts: StateFlow<LocalDataCounts> = combine(
        smsRepository.count,
        callRepository.count,
        mailRepository.mailCount,
        ledgerRepository.transactionCount
    ) { sms, calls, mails, transactions ->
        LocalDataCounts(sms, calls, mails, transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocalDataCounts())

    val clearing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun setAutoLedgerEnabled(enabled: Boolean) = settings.setAutoLedgerEnabled(enabled)

    fun setBackground(uri: android.net.Uri) {
        viewModelScope.launch {
            settings.setBackground(uri)
                .onSuccess { message.value = "背景图片已更新" }
                .onFailure { message.value = "设置失败：${it.message ?: "无法读取图片"}" }
        }
    }

    fun clearBackground() {
        settings.clearBackground()
        message.value = "已恢复纯色背景"
    }

    /** 从应用内 WebView 读取已登录的 TapTap Cookie 并加密保存 */
    fun saveTapTapCookieFromWebView() {
        val cookie = container.tapTapAccount.captureCookieFromWebView()
        if (container.tapTapAccount.save(cookie)) {
            message.value = "TapTap 账号已保存，下载时将优先使用官方直链"
        } else {
            message.value = "未检测到 TapTap 登录态，请先在网页中完成登录"
        }
    }

    /** 手动粘贴 Cookie（网页登录异常时的兜底方式） */
    fun saveTapTapCookie(raw: String) {
        if (container.tapTapAccount.save(raw)) {
            message.value = "TapTap 账号已保存，下载时将优先使用官方直链"
        } else {
            message.value = "Cookie 无效，请复制完整的 Cookie 后重试"
        }
    }

    fun clearTapTapAccount() {
        container.tapTapAccount.clear()
        message.value = "已退出 TapTap 账号"
    }

    /** 进入设置页时同步 WebView 中轮换后的登录态 */
    fun refreshTapTapSession() {
        container.tapTapAccount.refreshFromWebView()
    }

    fun clearMessage() {
        message.value = null
    }

    /** 清空全部本地用户数据（含数据库、加密存储、偏好设置） */
    fun clearAllData() {
        if (clearing.value) return
        viewModelScope.launch {
            clearing.value = true
            runCatching { container.clearAllData() }
                .onSuccess {
                    container.tapTapAccount.refresh()
                    message.value = "本地数据已全部清空"
                }
                .onFailure { message.value = "清空失败：${it.message ?: "未知错误"}" }
            clearing.value = false
        }
    }
}
