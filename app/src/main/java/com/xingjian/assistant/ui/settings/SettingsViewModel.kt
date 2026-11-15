package com.xingjian.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingjian.assistant.data.repository.CallRepository
import com.xingjian.assistant.data.repository.LedgerRepository
import com.xingjian.assistant.data.repository.MailRepository
import com.xingjian.assistant.data.repository.SettingsRepository
import com.xingjian.assistant.data.repository.SmsRepository
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

    fun clearMessage() {
        message.value = null
    }

    /** 清空全部本地用户数据（含数据库、加密存储、偏好设置） */
    fun clearAllData() {
        if (clearing.value) return
        viewModelScope.launch {
            clearing.value = true
            runCatching { container.clearAllData() }
                .onSuccess { message.value = "本地数据已全部清空" }
                .onFailure { message.value = "清空失败：${it.message ?: "未知错误"}" }
            clearing.value = false
        }
    }
}
