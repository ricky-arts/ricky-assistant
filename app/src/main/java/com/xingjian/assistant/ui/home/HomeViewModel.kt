package com.xingjian.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingjian.assistant.core.DateTimeUtils
import com.xingjian.assistant.data.local.entity.AiProfileEntity
import com.xingjian.assistant.data.local.entity.CallEntity
import com.xingjian.assistant.data.local.entity.MailEntity
import com.xingjian.assistant.data.local.entity.SmsEntity
import com.xingjian.assistant.data.local.entity.TransactionEntity
import com.xingjian.assistant.data.repository.AiRepository
import com.xingjian.assistant.data.repository.CallRepository
import com.xingjian.assistant.data.repository.LedgerRepository
import com.xingjian.assistant.data.repository.MailRepository
import com.xingjian.assistant.data.repository.SmsRepository
import com.xingjian.assistant.domain.model.CallCategory
import com.xingjian.assistant.domain.model.MailCategory
import com.xingjian.assistant.domain.model.SmsCategory
import com.xingjian.assistant.domain.model.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeHighlight(
    val title: String,
    val subtitle: String,
    val tag: String,
    val tagKind: String,
    val time: Long
)

data class HomeUiState(
    val smsTotal: Int = 0,
    val smsImportant: Int = 0,
    val smsSpam: Int = 0,
    val callTotal: Int = 0,
    val callSpam: Int = 0,
    val mailTotal: Int = 0,
    val mailBill: Int = 0,
    val todayExpense: Long = 0,
    val todayIncome: Long = 0,
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val highlights: List<HomeHighlight> = emptyList(),
    val latestProfile: AiProfileEntity? = null
) {
    val monthBalance: Long get() = monthIncome - monthExpense
}

class HomeViewModel(
    smsRepository: SmsRepository,
    callRepository: CallRepository,
    mailRepository: MailRepository,
    ledgerRepository: LedgerRepository,
    aiRepository: AiRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        smsRepository.items,
        callRepository.items,
        mailRepository.mails(null),
        ledgerRepository.transactions,
        aiRepository.profiles
    ) { sms, calls, mails, transactions, profiles ->
        val now = System.currentTimeMillis()
        val todayStart = DateTimeUtils.startOfDay(now)
        val todayEnd = DateTimeUtils.endOfDay(now)
        val monthStart = DateTimeUtils.startOfMonth(now)

        val todayTx = transactions.filter { it.occurredAt in todayStart..todayEnd }
        val monthTx = transactions.filter { it.occurredAt >= monthStart }

        HomeUiState(
            smsTotal = sms.size,
            smsImportant = sms.count { it.category == SmsCategory.IMPORTANT.name || it.starred },
            smsSpam = sms.count { it.category == SmsCategory.SPAM.name },
            callTotal = calls.size,
            callSpam = calls.count { it.category == CallCategory.SPAM.name },
            mailTotal = mails.size,
            mailBill = mails.count { it.category == MailCategory.BILL.name },
            todayExpense = sumBy(todayTx, TransactionType.EXPENSE),
            todayIncome = sumBy(todayTx, TransactionType.INCOME),
            monthExpense = sumBy(monthTx, TransactionType.EXPENSE),
            monthIncome = sumBy(monthTx, TransactionType.INCOME),
            highlights = buildHighlights(sms, calls, mails, transactions),
            latestProfile = profiles.firstOrNull()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    private fun sumBy(items: List<TransactionEntity>, type: TransactionType): Long =
        items.filter { it.type == type.name }.sumOf { it.amountCents }

    private fun buildHighlights(
        sms: List<SmsEntity>,
        calls: List<CallEntity>,
        mails: List<MailEntity>,
        transactions: List<TransactionEntity>
    ): List<HomeHighlight> {
        val highlights = mutableListOf<HomeHighlight>()

        sms.filter { it.category == SmsCategory.IMPORTANT.name || it.starred }
            .take(3)
            .forEach {
                highlights += HomeHighlight(
                    title = it.address,
                    subtitle = it.body.replace(Regex("\\s+"), " ").take(80),
                    tag = "重要短信",
                    tagKind = "SMS",
                    time = it.date
                )
            }

        mails.filter { it.category == MailCategory.BILL.name || it.category == MailCategory.NOTIFICATION.name }
            .take(3)
            .forEach {
                highlights += HomeHighlight(
                    title = it.subject.ifBlank { "(无主题)" },
                    subtitle = "${it.sender} · ${it.snippet}",
                    tag = if (it.category == MailCategory.BILL.name) "账单邮件" else "通知邮件",
                    tagKind = "MAIL",
                    time = it.receivedAt
                )
            }

        calls.filter { it.category == CallCategory.SPAM.name || it.starred }
            .take(2)
            .forEach {
                highlights += HomeHighlight(
                    title = it.cachedName?.takeIf { name -> name.isNotBlank() } ?: it.number,
                    subtitle = if (it.category == CallCategory.SPAM.name) "疑似骚扰来电" else "已标记的通话",
                    tag = "通话",
                    tagKind = "CALL",
                    time = it.date
                )
            }

        val todayStart = DateTimeUtils.startOfDay(System.currentTimeMillis())
        transactions.filter { it.occurredAt >= todayStart && it.type == TransactionType.EXPENSE.name }
            .take(2)
            .forEach {
                highlights += HomeHighlight(
                    title = "今日支出 ${DateTimeUtils.formatMoney(it.amountCents)}",
                    subtitle = "${it.categoryName}${if (it.note.isNotBlank()) " · ${it.note}" else ""}",
                    tag = "记账",
                    tagKind = "LEDGER",
                    time = it.occurredAt
                )
            }

        return highlights.sortedByDescending { it.time }.take(6)
    }
}
