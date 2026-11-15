package com.xingjian.assistant.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingjian.assistant.data.local.entity.AiProfileEntity
import com.xingjian.assistant.data.local.entity.AiProviderEntity
import com.xingjian.assistant.data.repository.AiRepository
import com.xingjian.assistant.data.repository.CallRepository
import com.xingjian.assistant.data.repository.LedgerRepository
import com.xingjian.assistant.data.repository.MailRepository
import com.xingjian.assistant.data.repository.ProxyRepository
import com.xingjian.assistant.data.repository.SmsRepository
import com.xingjian.assistant.domain.ai.ProfilePromptBuilder
import com.xingjian.assistant.domain.model.ProxyConfig
import com.xingjian.assistant.domain.model.ProxyModule
import com.xingjian.assistant.domain.model.ProxyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiDataCounts(
    val sms: Int = 0,
    val calls: Int = 0,
    val mails: Int = 0,
    val transactions: Int = 0
)

class AiViewModel(
    private val repository: AiRepository,
    private val proxyRepository: ProxyRepository,
    private val smsRepository: SmsRepository,
    private val callRepository: CallRepository,
    private val mailRepository: MailRepository,
    private val ledgerRepository: LedgerRepository
) : ViewModel() {

    val providers: StateFlow<List<AiProviderEntity>> = repository.providers.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val profiles: StateFlow<List<AiProfileEntity>> = repository.profiles.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val proxyConfig: StateFlow<ProxyConfig> = proxyRepository.observeConfig(ProxyModule.AI).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ProxyConfig()
    )

    val dataCounts: StateFlow<AiDataCounts> = combine(
        smsRepository.items,
        callRepository.items,
        mailRepository.mailCount,
        ledgerRepository.transactionCount
    ) { sms, calls, mails, transactions ->
        AiDataCounts(sms.size, calls.size, mails, transactions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiDataCounts())

    val generating = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun hasKey(providerId: Long): Boolean = repository.hasApiKey(providerId)

    fun clearMessage() {
        message.value = null
    }

    fun postMessage(text: String) {
        message.value = text
    }

    fun toggle(provider: AiProviderEntity, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(provider.id, enabled) }
    }

    fun saveProvider(provider: AiProviderEntity, apiKey: String) {
        val baseUrl = provider.baseUrl.trim()
        val model = provider.model.trim()
        if (baseUrl.isBlank()) {
            message.value = "接口地址不能为空"
            return
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            message.value = "接口地址需以 http:// 或 https:// 开头"
            return
        }
        if (model.isBlank()) {
            message.value = "模型名称不能为空"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.saveProvider(
                    provider.copy(baseUrl = baseUrl, model = model),
                    apiKey.trim().ifBlank { null }
                )
            }.onSuccess {
                message.value = "${provider.label} 配置已保存"
            }.onFailure {
                message.value = "保存失败：${it.message ?: "未知错误"}"
            }
        }
    }

    fun clearKey(providerId: Long) {
        viewModelScope.launch {
            repository.clearApiKey(providerId)
            message.value = "API Key 已移除"
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            repository.deleteProfile(id)
            message.value = "画像已删除"
        }
    }

    fun saveProxy(
        enabled: Boolean,
        type: ProxyType,
        host: String,
        port: Int,
        username: String,
        password: String
    ) {
        viewModelScope.launch {
            runCatching {
                proxyRepository.save(ProxyModule.AI, enabled, type, host, port, username, password)
            }.onSuccess {
                message.value = "AI 代理配置已保存"
            }.onFailure {
                message.value = "代理保存失败：${it.message ?: "未知错误"}"
            }
        }
    }

    /** 仅在用户点击按钮时执行：汇总本地数据并调用已启用的 AI 服务商 */
    fun generate() {
        if (generating.value) return
        viewModelScope.launch {
            generating.value = true
            runCatching {
                val sms = smsRepository.items.first()
                val calls = callRepository.items.first()
                val mails = mailRepository.mails(null).first()
                val transactions = ledgerRepository.transactions.first()
                val prompt = ProfilePromptBuilder.build(sms, calls, mails, transactions)
                repository.generateProfiles(prompt)
            }.onSuccess { result ->
                result.fold(
                    onSuccess = { created ->
                        message.value = "已生成 ${created.size} 份个人画像"
                    },
                    onFailure = { error ->
                        message.value = "生成失败：${error.message ?: "未知错误"}"
                    }
                )
            }.onFailure {
                message.value = "生成失败：${it.message ?: "未知错误"}"
            }
            generating.value = false
        }
    }
}
