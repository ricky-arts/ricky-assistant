package com.xingjian.assistant.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.xingjian.assistant.XjApplication
import com.xingjian.assistant.data.repository.LedgerAutoImporter
import com.xingjian.assistant.domain.ledger.TransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 无障碍支付结果页识别。
 * 微信 / 支付宝 / 抖音的部分支付完成后不会弹出通知，只能从支付成功结果页读取金额与状态，
 * 这也是市面记账 App 的通用做法。仅在白名单包名的「支付成功」页面生效，
 * 跳过聊天页、账单列表等页面，避免误记。
 */
class PaymentAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastScanAt = 0L

    private val importer: LedgerAutoImporter?
        get() = (application as? XjApplication)?.container?.autoLedgerImporter

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        val source = TransactionParser.sourceForPackage(event.packageName?.toString() ?: return) ?: return

        val now = System.currentTimeMillis()
        if (now - lastScanAt < SCAN_INTERVAL_MS) return
        val text = collectText(rootInActiveWindow ?: return)
        if (!TransactionParser.isPaymentResultPage(text)) return
        lastScanAt = now

        val importer = importer ?: return
        scope.launch { importer.importText(text, source, now) }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun collectText(root: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            if (label != null) builder.append(label).append(' ')
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { queue.addLast(it) }
            }
        }
        return builder.toString()
    }

    private companion object {
        const val SCAN_INTERVAL_MS = 700L
        const val MAX_NODES = 300
    }
}
