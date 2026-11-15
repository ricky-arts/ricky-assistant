package com.xingjian.assistant.data.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.xingjian.assistant.domain.model.RawCall
import com.xingjian.assistant.domain.model.RawSms

/** 本机短信读取（只读收件箱，按时间倒序） */
class SmsSystemReader(private val context: Context) {

    fun read(limit: Int = 800): List<RawSms> {
        if (!hasPermission(Manifest.permission.READ_SMS)) return emptyList()
        val result = mutableListOf<RawSms>()
        runCatching {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext() && result.size < limit) {
                    val address = cursor.getString(addressIndex) ?: continue
                    val body = cursor.getString(bodyIndex) ?: ""
                    result += RawSms(
                        nativeId = cursor.getLong(idIndex),
                        address = address,
                        body = body,
                        date = cursor.getLong(dateIndex)
                    )
                }
            }
        }
        return result
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** 本机通话记录读取 */
class CallLogSystemReader(private val context: Context) {

    fun read(limit: Int = 800): List<RawCall> {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val result = mutableListOf<RawCall>()
        runCatching {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                while (cursor.moveToNext() && result.size < limit) {
                    result += RawCall(
                        nativeId = cursor.getLong(idIndex),
                        number = cursor.getString(numberIndex) ?: "未知号码",
                        cachedName = cursor.getString(nameIndex),
                        type = cursor.getInt(typeIndex),
                        date = cursor.getLong(dateIndex),
                        durationSeconds = cursor.getLong(durationIndex)
                    )
                }
            }
        }
        return result
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
