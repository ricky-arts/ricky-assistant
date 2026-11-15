package com.xingjian.assistant.domain.ledger

import com.xingjian.assistant.data.local.entity.CategoryEntity
import com.xingjian.assistant.data.local.entity.DefaultCategories
import com.xingjian.assistant.domain.model.TransactionSource
import com.xingjian.assistant.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

data class ParsedTransaction(
    val amountCents: Long,
    val type: TransactionType,
    val source: TransactionSource,
    val occurredAt: Long
)

/**
 * 本地交易文本解析：通知、支付短信、无障碍读取到的支付结果页面共用一套规则。
 * 全部在设备本地完成，不做任何网络请求。
 */
object TransactionParser {

    private const val MIN_AMOUNT_CENTS = 1L
    private const val MAX_AMOUNT_CENTS = 100_000_000L // 单笔上限 100 万元，过滤误识别

    private val EXPENSE_SIGNALS = listOf(
        "支付成功", "付款成功", "成功付款", "已支付", "支付完成", "付款完成",
        "付款给", "转账给", "转出", "消费", "支出", "扣款", "实付", "付款金额",
        "交易金额", "交易成功", "已付款"
    )

    private val INCOME_SIGNALS = listOf(
        "收款成功", "收款到账", "已到账", "到账", "收入", "入账", "退款", "已退款",
        "收到转账", "已收到", "转入", "存入", "红包到账"
    )

    private val BLOCK_SIGNALS = listOf(
        "验证码", "校验码", "动态码", "优惠券", "红包封面", "开通", "签约",
        "额度", "失败", "取消", "撤销", "冻结", "报销", "还款提醒", "账单已出"
    )

    /** 支付结果页的强确认短语：用于无障碍实时识别，避免把聊天/列表内容当成交易 */
    private val CONFIRM_SIGNALS = listOf(
        "支付成功", "付款成功", "收款成功", "转账成功", "支付完成",
        "付款完成", "收款到账", "已支付", "到账通知"
    )

    private val AMOUNT_REGEXES = listOf(
        Regex("""(?:¥|￥|RMB|CNY|人民币)\s*([0-9]+(?:,[0-9]{3})*(?:\.\d{1,2})?)"""),
        Regex("""([0-9]+(?:,[0-9]{3})*(?:\.\d{1,2}?))\s*元"""),
        Regex("""(?:金额|实付|付款)\s*[:：]?\s*([0-9]+(?:,[0-9]{3})*(?:\.\d{1,2})?)""")
    )

    private val EXPENSE_RULES = listOf(
        "餐饮" to listOf(
            "美团", "饿了么", "肯德基", "麦当劳", "星巴克", "瑞幸", "咖啡", "奶茶",
            "餐厅", "饭店", "早餐", "午餐", "晚餐", "外卖", "烧烤", "火锅", "食堂"
        ),
        "交通" to listOf(
            "滴滴", "高德", "地铁", "公交", "打车", "加油", "中石油", "中石化", "充电桩",
            "停车", "高速", "铁路", "12306", "航空", "机票", "出租车"
        ),
        "购物" to listOf(
            "淘宝", "天猫", "京东", "拼多多", "唯品会", "超市", "便利店", "商场",
            "盒马", "山姆", "沃尔玛", "优衣库", "抖音商城", "商城"
        ),
        "居住" to listOf(
            "房租", "物业", "电费", "水费", "燃气", "话费", "宽带", "流量",
            "中国移动", "中国联通", "中国电信", "充电费"
        ),
        "娱乐" to listOf(
            "电影", "影院", "游戏", "腾讯视频", "爱奇艺", "优酷", "哔哩哔哩",
            "网易云", "KTV", "演出", "门票", "会员", "健身"
        ),
        "医疗" to listOf("医院", "药房", "药店", "诊所", "体检", "挂号", "医疗"),
        "教育" to listOf("学费", "课程", "书店", "培训", "学校", "教育")
    )

    private val INCOME_RULES = listOf(
        "工资" to listOf("工资", "薪资", "代发", "薪酬"),
        "奖金" to listOf("奖金", "绩效", "津贴", "补贴"),
        "理财" to listOf("利息", "理财", "基金", "分红", "收益")
    )

    private val BANK_PACKAGES = setOf(
        "com.icbc", "com.chinamworld.main", "cmb.pb", "com.ccb.longjiLife",
        "com.bankcomm.Bankcomm", "com.unionpay", "com.spdb.mobilebank.per",
        "com.ecitic.bank.mobile", "com.cebbank.mobile.cemb", "com.boc.bocsoft.mobile.bocmobile"
    )

    /** 通知/无障碍包名 -> 来源；不认识的包返回 null，避免误抓其它 App 内容 */
    fun sourceForPackage(packageName: String): TransactionSource? = when (packageName) {
        "com.tencent.mm" -> TransactionSource.WECHAT
        "com.eg.android.AlipayGphone" -> TransactionSource.ALIPAY
        "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite" -> TransactionSource.DOUYIN
        else -> if (packageName in BANK_PACKAGES) TransactionSource.BANK else null
    }

    /** 支付短信来源判断 */
    fun sourceForSms(body: String, address: String): TransactionSource = when {
        body.contains("支付宝") || address.contains("支付宝") -> TransactionSource.ALIPAY
        body.contains("微信") || body.contains("财付通") -> TransactionSource.WECHAT
        body.contains("抖音") -> TransactionSource.DOUYIN
        else -> TransactionSource.BANK
    }

    /** 无障碍页面的强信号校验：只处理支付结果页，跳过聊天与账单列表 */
    fun isPaymentResultPage(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.contains("账单") || text.contains("交易记录") || text.contains("明细")) return false
        if (BLOCK_SIGNALS.any { text.contains(it) }) return false
        if (CONFIRM_SIGNALS.none { text.contains(it) }) return false
        return collectAmounts(text).isNotEmpty()
    }

    /** 快速判断一段短信/通知是否值得进入完整解析 */
    fun mightBeTransaction(text: String): Boolean {
        if (text.isBlank()) return false
        if (BLOCK_SIGNALS.any { text.contains(it) }) return false
        val hasSignal = EXPENSE_SIGNALS.any { text.contains(it) } || INCOME_SIGNALS.any { text.contains(it) }
        return hasSignal && collectAmounts(text).isNotEmpty()
    }

    fun parse(text: String, source: TransactionSource, occurredAt: Long): ParsedTransaction? {
        if (text.isBlank()) return null
        if (BLOCK_SIGNALS.any { text.contains(it) }) return null

        val expenseHits = EXPENSE_SIGNALS.count { text.contains(it) }
        val incomeHits = INCOME_SIGNALS.count { text.contains(it) }
        if (expenseHits == 0 && incomeHits == 0) return null

        val amounts = collectAmounts(text)
        if (amounts.isEmpty()) return null

        val type = when {
            text.contains("退款") || text.contains("收款") || text.contains("到账") ||
                text.contains("收入") || text.contains("收到") -> TransactionType.INCOME
            expenseHits > 0 -> TransactionType.EXPENSE
            incomeHits > 0 -> TransactionType.INCOME
            else -> return null
        }

        val anchors = (EXPENSE_SIGNALS + INCOME_SIGNALS)
            .map { text.indexOf(it) }
            .filter { it >= 0 }
        val anchor = anchors.minOrNull() ?: 0
        val amountCents = amounts.minByOrNull { abs(it.second - anchor) }?.first ?: return null

        return ParsedTransaction(
            amountCents = amountCents,
            type = type,
            source = source,
            occurredAt = occurredAt
        )
    }

    fun pickCategory(
        categories: List<CategoryEntity>,
        text: String,
        type: TransactionType
    ): CategoryEntity? {
        if (categories.isEmpty()) return null
        if (type == TransactionType.INCOME) {
            INCOME_RULES.forEach { (name, keywords) ->
                if (keywords.any { text.contains(it, ignoreCase = true) }) {
                    categories.firstOrNull { it.type == type.name && it.name == name }?.let { return it }
                }
            }
            return categories.firstOrNull { it.type == type.name && it.id == DefaultCategories.OTHER_INCOME_ID }
                ?: categories.firstOrNull { it.type == type.name }
                ?: categories.first()
        }
        EXPENSE_RULES.forEach { (name, keywords) ->
            if (keywords.any { text.contains(it, ignoreCase = true) }) {
                categories.firstOrNull { it.type == type.name && it.name == name }?.let { return it }
            }
        }
        return categories.firstOrNull { it.id == DefaultCategories.OTHER_EXPENSE_ID }
            ?: categories.firstOrNull { it.type == type.name }
            ?: categories.first()
    }

    /** 返回 (金额分, 在文本中的位置) */
    private fun collectAmounts(text: String): List<Pair<Long, Int>> {
        val results = ArrayList<Pair<Long, Int>>()
        AMOUNT_REGEXES.forEach { regex ->
            regex.findAll(text).forEach { match ->
                val cents = toCents(match.groupValues[1]) ?: return@forEach
                if (cents in MIN_AMOUNT_CENTS..MAX_AMOUNT_CENTS) {
                    results.add(cents to match.range.first)
                }
            }
        }
        return results
    }

    private fun toCents(raw: String): Long? = runCatching {
        BigDecimal(raw.replace(",", ""))
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }.getOrNull()
}
