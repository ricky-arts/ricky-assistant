package com.xingjian.assistant.domain.model

/** 短信本地分类 */
enum class SmsCategory(val label: String) {
    SPAM("垃圾短信"),
    NOTICE("通知短信"),
    IMPORTANT("重要短信"),
    UNKNOWN("未分类")
}

/** 通话本地分类 */
enum class CallCategory(val label: String) {
    SPAM("骚扰电话"),
    EXPRESS("快递来电"),
    OFFICIAL("官方来电"),
    PERSONAL("私人通话"),
    UNKNOWN("未分类")
}

/** 邮件本地分类 */
enum class MailCategory(val label: String) {
    SPAM("垃圾邮件"),
    NOTIFICATION("通知邮件"),
    BILL("账单邮件"),
    NORMAL("普通邮件")
}

enum class TransactionType(val label: String) {
    EXPENSE("支出"),
    INCOME("收入")
}

/** 记账记录来源：手动录入或自动识别通道 */
enum class TransactionSource(val label: String) {
    MANUAL("手动"),
    WECHAT("微信支付"),
    ALIPAY("支付宝"),
    DOUYIN("抖音支付"),
    BANK("银行短信"),
    NOTIFICATION("通知识别"),
    ACCESSIBILITY("页面识别")
}

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

enum class ProxyType(val label: String) {
    NONE("不使用代理"),
    SOCKS5("SOCKS5"),
    HTTP("HTTP")
}

enum class AiApiStyle(val label: String) {
    OPENAI("OpenAI 兼容"),
    ANTHROPIC("Anthropic")
}

/** 代理模块：邮箱与 AI 各自独立配置 */
object ProxyModule {
    const val MAIL = "MAIL"
    const val AI = "AI"
}

data class ProxyConfig(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.NONE,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
) {
    val usable: Boolean
        get() = enabled && type != ProxyType.NONE && host.isNotBlank() && port in 1..65535
}

inline fun <reified T : Enum<T>> enumOrDefault(value: String?, fallback: T): T =
    if (value == null) fallback else runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)

/** 从系统读取出来的原始邮件 */
data class RawMail(
    val messageKey: String,
    val subject: String,
    val sender: String,
    val recipient: String,
    val receivedAt: Long,
    val snippet: String
)

/** 从系统读取出来的原始短信 */
data class RawSms(
    val nativeId: Long,
    val address: String,
    val body: String,
    val date: Long
)

/** 从系统读取出来的原始通话 */
data class RawCall(
    val nativeId: Long,
    val number: String,
    val cachedName: String?,
    val type: Int,
    val date: Long,
    val durationSeconds: Long
)
