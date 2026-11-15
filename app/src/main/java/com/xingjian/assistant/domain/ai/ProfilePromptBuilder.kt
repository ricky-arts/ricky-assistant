package com.xingjian.assistant.domain.ai

import com.xingjian.assistant.core.DateTimeUtils
import com.xingjian.assistant.data.local.entity.CallEntity
import com.xingjian.assistant.data.local.entity.MailEntity
import com.xingjian.assistant.data.local.entity.SmsEntity
import com.xingjian.assistant.data.local.entity.TransactionEntity
import com.xingjian.assistant.domain.model.SmsCategory
import com.xingjian.assistant.domain.model.TransactionType

/**
 * 个人信息画像提示词构建器。
 * 只在用户手动点击“生成画像”时调用，由用户自行承担内容外发风险。
 */
object ProfilePromptBuilder {

    fun build(
        sms: List<SmsEntity>,
        calls: List<CallEntity>,
        mails: List<MailEntity>,
        transactions: List<TransactionEntity>
    ): String = buildString {
        appendLine("你是一名注重隐私的中文个人助理。请基于下方用户授权的本地数据，生成一份结构清晰的个人画像与建议。")
        appendLine("要求：")
        appendLine("1. 使用简体中文，分点输出，总长度控制在 600 字以内；")
        appendLine("2. 内容包含：通讯习惯概览、账单与消费结构、可能的待办提醒、3 条可执行建议；")
        appendLine("3. 不要虚构数据，不要输出手机号、邮箱、银行卡号等敏感原文；")
        appendLine("4. 仅基于给出的数据推断，不确定时明确说明。")
        appendLine()
        appendLine("【短信数据】共 ${sms.size} 条，以下为最近 ${minOf(sms.size, 40)} 条摘要：")
        sms.take(40).forEach { item ->
            val category = runCatching { SmsCategory.valueOf(item.category).label }.getOrDefault("未分类")
            appendLine("- [${DateTimeUtils.formatDate(item.date)}][$category] ${sanitize(item.body, 60)}")
        }
        appendLine()
        appendLine("【通话数据】共 ${calls.size} 条，以下为最近 ${minOf(calls.size, 30)} 条摘要：")
        calls.take(30).forEach { item ->
            val name = item.cachedName?.takeIf { it.isNotBlank() } ?: "未知联系人"
            appendLine("- [${DateTimeUtils.formatDate(item.date)}][${item.category}] $name 通话 ${item.durationSeconds} 秒")
        }
        appendLine()
        appendLine("【邮件数据】共 ${mails.size} 封，以下为最近 ${minOf(mails.size, 30)} 封摘要：")
        mails.take(30).forEach { item ->
            appendLine("- [${DateTimeUtils.formatDate(item.receivedAt)}][${item.category}] ${sanitize(item.subject, 60)}｜${sanitize(item.snippet, 60)}")
        }
        appendLine()
        val expense = transactions.filter { it.type == TransactionType.EXPENSE.name }
        val income = transactions.filter { it.type == TransactionType.INCOME.name }
        appendLine("【记账数据】共 ${transactions.size} 笔（支出 ${expense.size} 笔，收入 ${income.size} 笔）")
        transactions.take(50).forEach { item ->
            val typeLabel = runCatching { TransactionType.valueOf(item.type).label }.getOrDefault(item.type)
            appendLine(
                "- [${DateTimeUtils.formatDate(item.occurredAt)}][$typeLabel][${item.categoryName}] " +
                    "${DateTimeUtils.formatMoney(item.amountCents)} ${sanitize(item.note, 30)}"
            )
        }
    }

    /** 简单脱敏：去除连续数字（可能是手机号/卡号）与邮箱地址 */
    private fun sanitize(text: String, maxLength: Int): String {
        val cleaned = text
            .replace(Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+"), "[邮箱]")
            .replace(Regex("\\d{6,}"), "[数字]")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.take(maxLength)
    }
}
