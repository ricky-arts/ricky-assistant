package com.xingjian.assistant.ui.mail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingjian.assistant.data.local.entity.MailAccountEntity
import com.xingjian.assistant.data.local.entity.MailEntity
import com.xingjian.assistant.data.repository.MailRepository
import com.xingjian.assistant.data.repository.ProxyRepository
import com.xingjian.assistant.domain.model.MailCategory
import com.xingjian.assistant.domain.model.ProxyConfig
import com.xingjian.assistant.domain.model.ProxyModule
import com.xingjian.assistant.domain.model.ProxyType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MailViewModel(
    private val repository: MailRepository,
    private val proxyRepository: ProxyRepository
) : ViewModel() {

    val accounts: StateFlow<List<MailAccountEntity>> = repository.accounts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val totalCount: StateFlow<Int> = repository.mailCount.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0
    )

    val selectedAccountId = MutableStateFlow<Long?>(null)

    val mails: StateFlow<List<MailEntity>> = selectedAccountId
        .flatMapLatest { id -> repository.mails(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val proxyConfig: StateFlow<ProxyConfig> = proxyRepository.observeConfig(ProxyModule.MAIL).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), ProxyConfig()
    )

    /** 正在拉取的账号 id，null 表示空闲 */
    val fetching = MutableStateFlow<Long?>(null)
    val message = MutableStateFlow<String?>(null)

    fun clearMessage() {
        message.value = null
    }

    fun selectAccount(id: Long?) {
        selectedAccountId.value = id
    }

    fun addAccount(
        email: String,
        displayName: String,
        provider: String,
        host: String,
        port: Int,
        useSsl: Boolean,
        username: String,
        password: String
    ) {
        if (email.isBlank() || !email.contains("@")) {
            message.value = "请输入正确的邮箱地址"
            return
        }
        if (host.isBlank() || port !in 1..65535) {
            message.value = "IMAP 服务器或端口不合法"
            return
        }
        if (password.isBlank()) {
            message.value = "请输入邮箱密码或应用专用密码"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.addAccount(
                    MailAccountEntity(
                        email = email.trim(),
                        displayName = displayName.trim(),
                        imapHost = host.trim(),
                        imapPort = port,
                        useSsl = useSsl,
                        username = username.trim().ifBlank { email.trim() },
                        provider = provider
                    ),
                    password
                )
            }.onSuccess {
                message.value = "邮箱账号已保存，密码已加密存储"
            }.onFailure {
                message.value = "保存失败：${it.message ?: "未知错误"}"
            }
        }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteAccount(id) }
                .onSuccess {
                    if (selectedAccountId.value == id) selectedAccountId.value = null
                    message.value = "账号与其邮件缓存已删除"
                }
                .onFailure { message.value = "删除失败：${it.message ?: "未知错误"}" }
        }
    }

    fun setAccountEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setAccountEnabled(id, enabled) }
    }

    fun fetch(accountId: Long) {
        if (fetching.value != null) return
        viewModelScope.launch {
            fetching.value = accountId
            runCatching { repository.fetch(accountId) }
                .onSuccess { result ->
                    result.fold(
                        onSuccess = { count ->
                            message.value = if (count > 0) "拉取完成，新增 $count 封邮件" else "没有新邮件"
                        },
                        onFailure = { error ->
                            message.value = "拉取失败：${error.message ?: "未知错误"}"
                        }
                    )
                }
                .onFailure { message.value = "拉取失败：${it.message ?: "未知错误"}" }
            fetching.value = null
        }
    }

    fun updateCategory(id: Long, category: MailCategory) {
        viewModelScope.launch { repository.updateCategory(id, category) }
    }

    fun toggleStar(item: MailEntity) {
        viewModelScope.launch { repository.toggleStar(item.id, !item.starred) }
    }

    fun deleteMail(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearMails() {
        viewModelScope.launch {
            repository.clearMails()
            message.value = "邮件缓存已清空"
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
                proxyRepository.save(ProxyModule.MAIL, enabled, type, host, port, username, password)
            }.onSuccess {
                message.value = "邮箱代理配置已保存"
            }.onFailure {
                message.value = "代理保存失败：${it.message ?: "未知错误"}"
            }
        }
    }
}
