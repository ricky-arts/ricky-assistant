package com.xingjian.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.xingjian.assistant.data.local.dao.AiDao
import com.xingjian.assistant.data.local.dao.CallDao
import com.xingjian.assistant.data.local.dao.LedgerDao
import com.xingjian.assistant.data.local.dao.MailDao
import com.xingjian.assistant.data.local.dao.ProxyDao
import com.xingjian.assistant.data.local.dao.SmsDao
import com.xingjian.assistant.data.local.entity.AiProfileEntity
import com.xingjian.assistant.data.local.entity.AiProviderEntity
import com.xingjian.assistant.data.local.entity.CallEntity
import com.xingjian.assistant.data.local.entity.CategoryEntity
import com.xingjian.assistant.data.local.entity.DefaultCategories
import com.xingjian.assistant.data.local.entity.MailAccountEntity
import com.xingjian.assistant.data.local.entity.MailEntity
import com.xingjian.assistant.data.local.entity.ProxyEntity
import com.xingjian.assistant.data.local.entity.SmsEntity
import com.xingjian.assistant.data.local.entity.TransactionEntity

@Database(
    entities = [
        SmsEntity::class,
        CallEntity::class,
        MailAccountEntity::class,
        MailEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        AiProviderEntity::class,
        AiProfileEntity::class,
        ProxyEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsDao(): SmsDao
    abstract fun callDao(): CallDao
    abstract fun mailDao(): MailDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun aiDao(): AiDao
    abstract fun proxyDao(): ProxyDao

    companion object {
        const val NAME = "xingjian.db"

        /** v2：记账记录增加来源字段（手动 / 微信 / 支付宝 / 抖音 / 银行短信） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedCategories(db)
                        seedProviders(db)
                    }
                })
                .build()

        private fun seedCategories(db: SupportSQLiteDatabase) {
            DefaultCategories.seeds().forEach { category ->
                db.execSQL(
                    "INSERT OR REPLACE INTO categories (id, name, type, sortOrder, builtIn) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(category.id, category.name, category.type, category.sortOrder, if (category.builtIn) 1 else 0)
                )
            }
        }

        private fun seedProviders(db: SupportSQLiteDatabase) {
            val providers = listOf(
                arrayOf(1L, "openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "OPENAI", 0, 1),
                arrayOf(2L, "deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "OPENAI", 0, 2),
                arrayOf(3L, "anthropic", "Anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-latest", "ANTHROPIC", 0, 3),
                arrayOf(4L, "qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", "OPENAI", 0, 4),
                arrayOf(5L, "ernie", "文心一言", "https://qianfan.baidubce.com/v2", "ernie-4.0-8k", "OPENAI", 0, 5)
            )
            providers.forEach { row ->
                db.execSQL(
                    "INSERT OR REPLACE INTO ai_providers (id, vendor, label, baseUrl, model, apiStyle, enabled, sortOrder) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    row
                )
            }
        }
    }
}
