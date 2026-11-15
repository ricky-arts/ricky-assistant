package com.xingjian.assistant.data.repository

import com.xingjian.assistant.data.local.entity.SmsEntity
import com.xingjian.assistant.domain.ledger.ParsedTransaction
import com.xingjian.assistant.domain.ledger.TransactionParser
import com.xingjian.assistant.domain.model.TransactionSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 自动记账入库器。
 * 通知监听、支付短信、无障碍支付结果页三条通道统一走这里写入，串行执行，
 * 并用「内存签名 + 数据库金额/时间窗口」双层去重，避免同一次支付被重复记账。
 */
class LedgerAutoImporter(
    private val ledgerRepository: LedgerRepository,
    private val settings: SettingsRepository
) {

    private val mutex = Mutex()

    /** 最近处理过的交易签名，防止同一笔支付被多条通道短时间内重复写入 */
    private val recentSignatures = ArrayDeque<String>()

    /** 解析一段文本并入库，成功返回 true（重复或非交易返回 false） */
    suspend fun importText(
        text: String,
        source: TransactionSource,
        occurredAt: Long = System.currentTimeMillis()
    ): Boolean = mutex.withLock {
        if (!settings.autoLedgerEnabled.value) return@withLock false
        val parsed = TransactionParser.parse(text, source, occurredAt) ?: return@withLock false
        insert(text, parsed)
    }

    /** 扫描本地短信缓存，把支付/扣款类短信批量补记为交易 */
    suspend fun importFromSms(messages: List<SmsEntity>): Int {
        var imported = 0
        messages.sortedBy { it.date }.forEach { sms ->
            if (!TransactionParser.mightBeTransaction(sms.body)) return@forEach
            val source = TransactionParser.sourceForSms(sms.body, sms.address)
            if (importText(sms.body, source, sms.date)) imported++
        }
        return imported
    }

    private suspend fun insert(text: String, parsed: ParsedTransaction): Boolean {
        val signature = buildSignature(parsed)
        if (signature in recentSignatures) return false

        val hasSimilar = ledgerRepository.countSimilar(
            parsed.amountCents,
            parsed.occurredAt - DEDUP_WINDOW_MS,
            parsed.occurredAt + DEDUP_WINDOW_MS
        )
        if (hasSimilar > 0) {
            remember(signature)
            return false
        }

        val category = TransactionParser.pickCategory(
            ledgerRepository.categoriesOnce(),
            text,
            parsed.type
        ) ?: return false

        ledgerRepository.addTransaction(
            amountCents = parsed.amountCents,
            type = parsed.type,
            category = category,
            note = "${parsed.source.label} · 自动记账",
            occurredAt = parsed.occurredAt,
            source = parsed.source
        )
        remember(signature)
        return true
    }

    private fun buildSignature(parsed: ParsedTransaction): String =
        "${parsed.source.name}|${parsed.type.name}|${parsed.amountCents}|${parsed.occurredAt / 60_000L}"

    private fun remember(signature: String) {
        recentSignatures.addLast(signature)
        while (recentSignatures.size > MAX_SIGNATURES) recentSignatures.removeFirst()
    }

    private companion object {
        /** 同金额去重时间窗口（±90 秒） */
        const val DEDUP_WINDOW_MS = 90_000L
        const val MAX_SIGNATURES = 64
    }
}
