package com.xingjian.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xingjian.assistant.domain.model.CallCategory

@Entity(
    tableName = "calls",
    indices = [
        Index(value = ["nativeId"], unique = true),
        Index(value = ["category"]),
        Index(value = ["date"])
    ]
)
data class CallEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nativeId: Long,
    val number: String,
    val cachedName: String?,
    /** 系统通话类型：1 来电 2 呼出 3 未接 4 语音信箱 5 拒接 6 拦截 */
    val callType: Int,
    val date: Long,
    val durationSeconds: Long,
    val category: String = CallCategory.UNKNOWN.name,
    val starred: Boolean = false,
    val syncedAt: Long = System.currentTimeMillis()
)
