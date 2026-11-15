package com.xingjian.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xingjian.assistant.domain.model.TransactionType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Long,
    val name: String,
    /** TransactionType.name */
    val type: String,
    val sortOrder: Int,
    val builtIn: Boolean
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["type"]),
        Index(value = ["categoryId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 金额以“分”为单位，避免浮点误差 */
    val amountCents: Long,
    /** TransactionType.name：EXPENSE / INCOME */
    val type: String,
    val categoryId: Long,
    /** 冗余保存分类名，便于导出与分类删除后的历史展示 */
    val categoryName: String,
    val note: String,
    val occurredAt: Long,
    /** TransactionSource.name：MANUAL / WECHAT / ALIPAY / DOUYIN / BANK ... */
    val source: String = "MANUAL",
    val createdAt: Long = System.currentTimeMillis()
)

/** 默认分类种子数据 */
object DefaultCategories {
    const val OTHER_EXPENSE_ID = 8L
    const val OTHER_INCOME_ID = 104L

    fun seeds(): List<CategoryEntity> = listOf(
        CategoryEntity(1, "餐饮", TransactionType.EXPENSE.name, 1, true),
        CategoryEntity(2, "交通", TransactionType.EXPENSE.name, 2, true),
        CategoryEntity(3, "购物", TransactionType.EXPENSE.name, 3, true),
        CategoryEntity(4, "居住", TransactionType.EXPENSE.name, 4, true),
        CategoryEntity(5, "娱乐", TransactionType.EXPENSE.name, 5, true),
        CategoryEntity(6, "医疗", TransactionType.EXPENSE.name, 6, true),
        CategoryEntity(7, "教育", TransactionType.EXPENSE.name, 7, true),
        CategoryEntity(OTHER_EXPENSE_ID, "其他支出", TransactionType.EXPENSE.name, 8, true),
        CategoryEntity(101, "工资", TransactionType.INCOME.name, 1, true),
        CategoryEntity(102, "奖金", TransactionType.INCOME.name, 2, true),
        CategoryEntity(103, "理财", TransactionType.INCOME.name, 3, true),
        CategoryEntity(OTHER_INCOME_ID, "其他收入", TransactionType.INCOME.name, 4, true)
    )
}
