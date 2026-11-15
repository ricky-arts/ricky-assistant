package com.xingjian.assistant.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.xingjian.assistant.XjApplication
import com.xingjian.assistant.data.repository.LedgerAutoImporter
import com.xingjian.assistant.domain.ledger.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 支付通知监听：微信 / 支付宝 / 抖音 / 银行 App 的支付通知统一解析。
 * 只读取通知标题与正文，不做任何网络请求，也不保存原始通知文本。
 */
class PaymentNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val importer: LedgerAutoImporter?
        get() = (application as? XjApplication)?.container?.autoLedgerImporter

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val source = TransactionParser.sourceForPackage(sbn.packageName) ?: return
        val text = collectText(notification)
        if (!TransactionParser.mightBeTransaction(text)) return
        val importer = importer ?: return
        scope.launch { importer.importText(text, source, sbn.postTime) }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun collectText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val parts = ArrayList<String>(5)
        extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let { parts.add(it) }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { parts.add(it) }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { parts.add(it) }
        extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.let { parts.add(it) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(" ") { it?.toString().orEmpty() }
            ?.let { parts.add(it) }
        return parts.joinToString(" ")
    }
}
