package com.xingjian.assistant.domain.classify

import com.xingjian.assistant.domain.model.CallCategory
import com.xingjian.assistant.domain.model.MailCategory
import com.xingjian.assistant.domain.model.SmsCategory

/**
 * 本地规则分类器。完全离线，不发起任何网络请求。
 * 规则以中文 + 英文常见关键词为主，可后续按需扩展。
 */
object SmsClassifier {

    private val spamKeywords = listOf(
        "退订", "中奖", "贷款", "代开发票", "博彩", "彩票", "刷单", "返利", "免费领取",
        "点击链接", "澳门", "赌场", "私彩", "加微信", "包邮", "限时抢购", "促销", "优惠券",
        "微粒贷", "借呗", "花呗套现", "银行ka", "信用卡提额", "低息", "无抵押", "黑户"
    )

    private val importantKeywords = listOf(
        "验证码", "校验码", "动态码", "登录", "银行", "信用卡", "还款", "逾期", "扣款",
        "到账", "工资", "转账", "账单", "医疗", "医院", "挂号", "社保", "公积金",
        "税务", "法院", "公安", "违章", "航班", "立案", "预约成功"
    )

    private val noticeKeywords = listOf(
        "快递", "取件", "取货", "菜鸟驿站", "顺丰", "京东", "中通", "圆通", "韵达",
        "订单", "发货", "签收", "派件", "外卖", "骑手", "提醒", "通知", "到期",
        "积分", "会员", "活动", "预约", "排队", "叫号"
    )

    private val mobileRegex = Regex("^1[3-9]\\d{9}$")
    private val serviceNumberRegex = Regex("^(95\\d{3,5}|1\\d{5}|106\\d+)$")

    fun classify(address: String, body: String): SmsCategory {
        val text = body.lowercase()
        val hasSpam = spamKeywords.any { text.contains(it) }
        val hasImportant = importantKeywords.any { text.contains(it) }
        val hasNotice = noticeKeywords.any { text.contains(it) }

        // 含验证码/银行类关键词时优先判定为重要，避免被“促销”误伤
        if (hasSpam && !hasImportant) return SmsCategory.SPAM
        if (hasImportant) return SmsCategory.IMPORTANT
        if (hasNotice) return SmsCategory.NOTICE
        // 个人手机号发来的短信按重要处理
        if (mobileRegex.matches(address)) return SmsCategory.IMPORTANT
        if (serviceNumberRegex.matches(address)) return SmsCategory.NOTICE
        return SmsCategory.UNKNOWN
    }
}

object CallClassifier {

    private val spamNameKeywords = listOf("骚扰", "推销", "诈骗", "中介", "贷款", "广告", "保险", "催收")
    private val spamPrefixes = listOf("400", "800", "00", "+")
    private val officialKeywords = listOf("银行", "客服", "官方", "政务", "营业厅", "供电", "燃气", "水务")
    private val expressKeywords = listOf("快递", "顺丰", "京东", "中通", "圆通", "韵达", "邮政", "闪送", "骑手", "外卖", "美团", "饿了么")
    private val officialNumbers = listOf("10086", "10010", "10000", "10099", "95588", "95533", "95599", "95555")

    private val mobileRegex = Regex("^1[3-9]\\d{9}$")

    fun classify(number: String, cachedName: String?, durationSeconds: Long): CallCategory {
        val normalized = number.replace(" ", "").replace("-", "")
        if (cachedName.isNullOrBlank() && spamPrefixes.any { normalized.startsWith(it) }) {
            return CallCategory.SPAM
        }
        if (!cachedName.isNullOrBlank()) {
            if (spamNameKeywords.any { cachedName.contains(it) }) return CallCategory.SPAM
            if (expressKeywords.any { cachedName.contains(it) }) return CallCategory.EXPRESS
            if (officialKeywords.any { cachedName.contains(it) }) return CallCategory.OFFICIAL
        }
        if (officialNumbers.contains(normalized) || normalized.startsWith("95")) {
            return CallCategory.OFFICIAL
        }
        // 未知号码短时长多次呼叫无法在本地可靠判定，保持未分类
        if (mobileRegex.matches(normalized)) return CallCategory.PERSONAL
        return CallCategory.UNKNOWN
    }
}

object MailClassifier {

    private val spamKeywords = listOf(
        "unsubscribe", "促销", "优惠", "折扣", "中奖", "winner", "prize", "casino",
        "loan", "viagra", "免费领取", "点击领取", "限时", "sale", "coupon", "广告"
    )
    private val billKeywords = listOf(
        "账单", "发票", "invoice", "receipt", "payment", "付款", "支付成功", "扣款",
        "订阅", "statement", "order", "订单", "信用卡", "还款"
    )
    private val notificationKeywords = listOf(
        "验证码", "verification", "verify", "code", "security", "安全提醒", "登录提醒",
        "发货", "配送", "shipment", "提醒", "notification", "reset password"
    )

    fun classify(subject: String, sender: String, snippet: String): MailCategory {
        val text = "${subject} ${snippet}".lowercase()
        val from = sender.lowercase()
        if (spamKeywords.any { text.contains(it) } || from.contains("noreply-spam")) {
            return MailCategory.SPAM
        }
        if (billKeywords.any { text.contains(it) }) return MailCategory.BILL
        if (notificationKeywords.any { text.contains(it) }) return MailCategory.NOTIFICATION
        return MailCategory.NORMAL
    }
}
