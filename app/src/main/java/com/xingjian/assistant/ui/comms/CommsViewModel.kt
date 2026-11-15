package com.xingjian.assistant.ui.comms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingjian.assistant.data.local.entity.CallEntity
import com.xingjian.assistant.data.local.entity.SmsEntity
import com.xingjian.assistant.data.repository.CallRepository
import com.xingjian.assistant.data.repository.LedgerAutoImporter
import com.xingjian.assistant.data.repository.SmsRepository
import com.xingjian.assistant.domain.model.CallCategory
import com.xingjian.assistant.domain.model.SmsCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CommsTab(val label: String) {
    SMS("短信"),
    CALL("通话"),
    MAIL("邮件")
}

class CommsViewModel(
    private val smsRepository: SmsRepository,
    private val callRepository: CallRepository,
    private val autoImporter: LedgerAutoImporter
) : ViewModel() {

    val sms: StateFlow<List<SmsEntity>> = smsRepository.items.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val calls: StateFlow<List<CallEntity>> = callRepository.items.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val tab = MutableStateFlow(CommsTab.SMS)
    val smsFilter = MutableStateFlow<SmsCategory?>(null)
    val callFilter = MutableStateFlow<CallCategory?>(null)
    val query = MutableStateFlow("")
    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun selectTab(value: CommsTab) {
        tab.value = value
    }

    fun setSmsFilter(value: SmsCategory?) {
        smsFilter.value = value
    }

    fun setCallFilter(value: CallCategory?) {
        callFilter.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun postMessage(text: String) {
        message.value = text
    }

    fun clearMessage() {
        message.value = null
    }

    fun sync(readSms: Boolean, readCalls: Boolean) {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            runCatching {
                var added = 0
                var autoLedger = 0
                if (readSms) {
                    added += smsRepository.sync()
                    // 短信同步后顺便把历史支付短信补记为账目（内部去重，不会重复入库）
                    autoLedger += autoImporter.importFromSms(smsRepository.latest(300))
                }
                if (readCalls) added += callRepository.sync()
                message.value = buildString {
                    append("同步完成，本次新增 $added 条记录")
                    if (autoLedger > 0) append("，自动记账 $autoLedger 笔")
                }
            }.onFailure {
                message.value = "同步失败：${it.message ?: "未知错误"}"
            }
            syncing.value = false
        }
    }

    fun updateSmsCategory(id: Long, category: SmsCategory) {
        viewModelScope.launch { smsRepository.updateCategory(id, category) }
    }

    fun toggleSmsStar(item: SmsEntity) {
        viewModelScope.launch { smsRepository.toggleStar(item.id, !item.starred) }
    }

    fun deleteSms(id: Long) {
        viewModelScope.launch { smsRepository.delete(id) }
    }

    fun updateCallCategory(id: Long, category: CallCategory) {
        viewModelScope.launch { callRepository.updateCategory(id, category) }
    }

    fun toggleCallStar(item: CallEntity) {
        viewModelScope.launch { callRepository.toggleStar(item.id, !item.starred) }
    }

    fun deleteCall(id: Long) {
        viewModelScope.launch { callRepository.delete(id) }
    }

    fun clearSms() {
        viewModelScope.launch {
            smsRepository.clear()
            message.value = "已清除本地短信缓存"
        }
    }

    fun clearCalls() {
        viewModelScope.launch {
            callRepository.clear()
            message.value = "已清除本地通话缓存"
        }
    }
}
