package com.xingjian.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xingjian.assistant.domain.model.SmsCategory

@Entity(
    tableName = "sms",
    indices = [
        Index(value = ["nativeId"], unique = true),
        Index(value = ["category"]),
        Index(value = ["date"])
    ]
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nativeId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val category: String = SmsCategory.UNKNOWN.name,
    val starred: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
)
