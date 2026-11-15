package com.xingjian.assistant.data.repository

import com.xingjian.assistant.data.local.dao.LedgerDao
import com.xingjian.assistant.data.local.entity.CategoryEntity
import com.xingjian.assistant.data.local.entity.DefaultCategories
import com.xingjian.assistant.data.local.entity.TransactionEntity
import com.xingjian.assistant.domain.model.TransactionSource
import com.xingjian.assistant.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val dao: LedgerDao) {

    val transactions: Flow<List<TransactionEntity>> = dao.observeTransactions()
    val categories: Flow<List<CategoryEntity>> = dao.observeCategories()
    val transactionCount: Flow<Int> = dao.observeTransactionCount()

    suspend fun addTransaction(
        amountCents: Long,
        type: TransactionType,
        category: CategoryEntity,
        note: String,
        occurredAt: Long,
        source: TransactionSource = TransactionSource.MANUAL
    ): Long = dao.insertTransaction(
        TransactionEntity(
            amountCents = amountCents,
            type = type.name,
            categoryId = category.id,
            categoryName = category.name,
            note = note.trim(),
            occurredAt = occurredAt,
            source = source.name
        )
    )

    /** 自动识别去重：同金额、时间窗口内是否已存在记录 */
    suspend fun countSimilar(amountCents: Long, from: Long, to: Long): Int =
        dao.countSimilar(amountCents, from, to)

    suspend fun categoriesOnce(): List<CategoryEntity> = dao.categoriesOnce()

    suspend fun deleteTransaction(id: Long) = dao.deleteTransaction(id)

    suspend fun addCategory(name: String, type: TransactionType): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return Result.failure(IllegalArgumentException("分类名称不能为空"))
        if (trimmed.length > 12) return Result.failure(IllegalArgumentException("分类名称过长"))
        if (dao.countByName(type.name, trimmed) > 0) {
            return Result.failure(IllegalArgumentException("该分类已存在"))
        }
        val nextId = (dao.maxCategoryId() ?: 0L) + 1 + type.ordinal * 1000L
        dao.upsertCategory(
            CategoryEntity(
                id = nextId,
                name = trimmed,
                type = type.name,
                sortOrder = 100,
                builtIn = false
            )
        )
        return Result.success(Unit)
    }

    suspend fun deleteCategory(category: CategoryEntity): Result<Unit> {
        if (category.builtIn) return Result.failure(IllegalArgumentException("内置分类不可删除"))
        val isIncome = category.type == TransactionType.INCOME.name
        val fallbackId = if (isIncome) DefaultCategories.OTHER_INCOME_ID else DefaultCategories.OTHER_EXPENSE_ID
        val fallbackName = if (isIncome) "其他收入" else "其他支出"
        dao.reassignTransactions(category.id, fallbackId, fallbackName)
        dao.deleteCategory(category)
        return Result.success(Unit)
    }

    suspend fun clearTransactions() = dao.clearTransactions()
}
