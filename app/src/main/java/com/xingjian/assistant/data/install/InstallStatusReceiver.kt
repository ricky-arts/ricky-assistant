package com.xingjian.assistant.data.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * 接收 PackageInstaller 的安装状态：
 * - STATUS_PENDING_USER_ACTION：拉起系统安装确认页（应用内完成）
 * - STATUS_SUCCESS：提示安装完成
 * - 其他：展示系统返回的失败原因
 */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                ApkInstaller.confirmIntent(intent)?.let { confirm ->
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> toast(context, "安装完成")
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                toast(context, message?.let { "安装失败：$it" } ?: "安装失败（code=$status）")
            }
        }
    }

    private fun toast(context: Context, text: String) {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}
