package com.xingjian.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xingjian.assistant.domain.model.MailCategory

@Entity(tableName = "mail_accounts")
data class MailAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val email: String,
    val displayName: String,
    val imapHost: String,
    val imapPort: Int,
    val useSsl: Boolean,
    val username: String,
    /** GMAIL / OUTLOOK / OTHER，仅用于界面展示 */
    val provider: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mails",
    indices = [
        Index(value = ["accountId", "messageKey"], unique = true),
        Index(value = ["receivedAt"]),
        Index(value = ["category"])
    ]
)
data class MailEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val messageKey: String,
    val subject: String,
    val sender: String,
    val recipient: String,
    val receivedAt: Long,
    val snippet: String,
    val category: String = MailCategory.NORMAL.name,
    val starred: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
)
