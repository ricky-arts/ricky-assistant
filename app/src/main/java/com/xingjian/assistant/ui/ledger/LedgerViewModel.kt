package com.xingjian.assistant.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xingjian.assistant.core.DateTimeUtils
import com.xingjian.assistant.data.local.entity.CategoryEntity
import com.xingjian.assistant.data.local.entity.TransactionEntity
import com.xingjian.assistant.data.repository.LedgerRepository
import com.xingjian.assistant.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode

enum class LedgerRange(val label: String) {
    DAY("日"),
    MONTH("月"),
    YEAR("年")
}

data class LedgerFilter(
    val range: LedgerRange = LedgerRange.MONTH,
    val type: TransactionType? = null,
    val categoryId: Long? = null,
    val query: String = ""
)

data class LedgerUiState(
    val rangeStart: Long = 0,
    val rangeLabel: String = "",
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val incomeCents: Long = 0,
    val expenseCents: Long = 0
)

class LedgerViewModel(private val repository: LedgerRepository) : ViewModel() {

    val filter = MutableStateFlow(LedgerFilter())

    val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LedgerUiState> = combine(
        repository.transactions,
        repository.categories,
        filter
    ) { all, categories, current ->
        val now = System.currentTimeMillis()
        val start = when (current.range) {
            LedgerRange.DAY -> DateTimeUtils.startOfDay(now)
            LedgerRange.MONTH -> DateTimeUtils.startOfMonth(now)
            LedgerRange.YEAR -> DateTimeUtils.startOfYear(now)
        }
        val end = DateTimeUtils.endOfDay(now)
        val rangeItems = all.filter { it.occurredAt in start..end }
        val visible = rangeItems.filter { item ->
            val byType = current.type == null || item.type == current.type?.name
            val byCategory = current.categoryId == null || item.categoryId == current.categoryId
            val byQuery = current.query.isBlank() ||
                item.note.contains(current.query, ignoreCase = true) ||
                item.categoryName.contains(current.query, ignoreCase = true)
            byType && byCategory && byQuery
        }
        LedgerUiState(
            rangeStart = start,
            rangeLabel = when (current.range) {
                LedgerRange.DAY -> "今日"
                LedgerRange.MONTH -> DateTimeUtils.formatMonth(now)
                LedgerRange.YEAR -> "${DateTimeUtils.toLocalDate(now).year} 年"
            },
            transactions = visible,
            categories = categories,
            incomeCents = visible.filter { it.type == TransactionType.INCOME.name }.sumOf { it.amountCents },
            expenseCents = visible.filter { it.type == TransactionType.EXPENSE.name }.sumOf { it.amountCents }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LedgerUiState()
    )

    fun setRange(range: LedgerRange) {
        filter.value = filter.value.copy(range = range, categoryId = null)
    }

    fun setType(type: TransactionType?) {
        filter.value = filter.value.copy(type = type)
    }

    fun setCategory(categoryId: Long?) {
        filter.value = filter.value.copy(categoryId = categoryId)
    }

    fun setQuery(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    fun clearMessage() {
        message.value = null
    }

    fun postMessage(text: String) {
        message.value = text
    }

    fun addTransaction(
        amountText: String,
        type: TransactionType,
        categoryId: Long,
        note: String,
        occurredAt: Long
    ) {
        val amount = amountText.trim().toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            message.value = "请输入正确的金额"
            return
        }
        val cents = amount.multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
        if (cents <= 0) {
            message.value = "金额必须大于 0"
            return
        }
        val category = uiState.value.categories.firstOrNull {
            it.id == categoryId && it.type == type.name
        }
        if (category == null) {
            message.value = "请选择分类"
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.addTransaction(cents, type, category, note, occurredAt)
            }.onSuccess {
                message.value = "已记录${type.label} ${DateTimeUtils.formatMoney(cents)}"
            }.onFailure {
                message.value = "保存失败：${it.message ?: "未知错误"}"
            }
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            message.value = "记录已删除"
        }
    }

    fun addCategory(name: String, type: TransactionType) {
        viewModelScope.launch {
            repository.addCategory(name, type).fold(
                onSuccess = { message.value = "分类已添加" },
                onFailure = { message.value = it.message ?: "添加失败" }
            )
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category).fold(
                onSuccess = { message.value = "分类已删除，关联记录已归入其他分类" },
                onFailure = { message.value = it.message ?: "删除失败" }
            )
        }
    }

    fun clearTransactions() {
        viewModelScope.launch {
            repository.clearTransactions()
            message.value = "全部记账记录已清空"
        }
    }
}
