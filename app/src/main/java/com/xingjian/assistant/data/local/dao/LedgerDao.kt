package com.xingjian.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xingjian.assistant.data.local.entity.CategoryEntity
import com.xingjian.assistant.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    // ------- 分类 -------

    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    /** 自动记账一次性读取分类（用于匹配分类） */
    @Query("SELECT * FROM categories ORDER BY type ASC, sortOrder ASC")
    suspend fun categoriesOnce(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("SELECT MAX(id) FROM categories")
    suspend fun maxCategoryId(): Long?

    @Query("SELECT COUNT(*) FROM categories WHERE type = :type AND name = :name")
    suspend fun countByName(type: String, name: String): Int

    @Query("UPDATE transactions SET categoryId = :newId, categoryName = :newName WHERE categoryId = :oldId")
    suspend fun reassignTransactions(oldId: Long, newId: Long, newName: String)

    // ------- 交易 -------

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE amountCents = :amountCents AND occurredAt BETWEEN :from AND :to")
    suspend fun countSimilar(amountCents: Long, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM transactions")
    fun observeTransactionCount(): Flow<Int>

    @Insert
    suspend fun insertTransaction(item: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()
}
