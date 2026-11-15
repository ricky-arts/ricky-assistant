package com.xingjian.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 厂商标识：openai / deepseek / anthropic / qwen / ernie / custom */
    val vendor: String,
    val label: String,
    val baseUrl: String,
    val model: String,
    /** AiApiStyle.name */
    val apiStyle: String,
    val enabled: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "ai_profiles", indices = [Index(value = ["createdAt"])])
data class AiProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val providerLabel: String,
    val model: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "proxies")
data class ProxyEntity(
    /** ProxyModule.MAIL / ProxyModule.AI */
    @PrimaryKey val module: String,
    val enabled: Boolean = false,
    /** ProxyType.name */
    val type: String = "NONE",
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    /** 密码不落库，仅存是否存在密码的标记；密码存 EncryptedSharedPreferences */
    val hasPassword: Boolean = false
)
