package com.xingjian.assistant.core

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.xingjian.assistant.service.PaymentAccessibilityService
import com.xingjian.assistant.service.PaymentNotificationListener

/** 敏感权限的工具方法：只做状态查询与跳转，不主动申请（申请由 Compose 层发起） */
object Permissions {

    fun hasSms(context: Context): Boolean = granted(context, Manifest.permission.READ_SMS)

    fun hasCallLog(context: Context): Boolean = granted(context, Manifest.permission.READ_CALL_LOG)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** 通知监听是否已对本应用开启 */
    fun hasNotificationListener(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val target = ComponentName(context, PaymentNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
    }

    /** 无障碍服务是否已对本应用开启 */
    fun hasAccessibility(context: Context): Boolean {
        if (Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            ) != 1
        ) {
            return false
        }
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = ComponentName(context, PaymentAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == target }
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
    }

    /** 打开本应用的系统设置页（用于权限被永久拒绝后的手动开启） */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
