package com.xingjian.assistant.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 发送验证码的结果 */
sealed interface SendCodeResult {
    /** 已发送，[retryAfterSeconds] 为可重发倒计时 */
    data class Sent(val retryAfterSeconds: Int) : SendCodeResult

    /** 需要完成腾讯滑块人机验证后重试 */
    data class CaptchaRequired(
        val appId: String,
        val captchaType: String,
        val action: String,
        val requestId: String,
        /** error_metadata 原文，用于拼装 captcha_data */
        val metadataJson: String
    ) : SendCodeResult

    data class Failed(val message: String) : SendCodeResult
}

/** 手机号验证码登录的结果 */
sealed interface TapTapLoginResult {
    /** 登录成功，[cookie] 为可直接用于官方 CDN 的登录态 */
    data class Success(val cookie: String, val action: String) : TapTapLoginResult

    /** 手机号尚未注册，TapTap 要求先完成资料设置才能建立登录态 */
    data class NeedProfile(val message: String) : TapTapLoginResult

    data class Failed(val message: String) : TapTapLoginResult
}

/**
 * TapTap 账号站登录接口（accounts.taptap.cn）。
 *
 * - 发送验证码：POST /api/phone/send-by-guest（action=login_or_register）
 * - 验证码登录：POST /api/phone/login（phone_code + phone_number）
 *
 * 发送验证码前会被人机验证拦截（captcha.needs），需带上腾讯滑块回调的
 * ticket/randstr（JSON 形式放在 captcha_data 字段）。
 *
 * 登录成功后把响应里的 Set-Cookie 汇总成 Cookie 头返回，交由
 * [com.xingjian.assistant.data.repository.TapTapAccountRepository] 加密保存。
 */
class TapTapAuthApi {

    /** 接口返回的 Cookie（按名称覆盖，Max-Age=0 时删除） */
    private val cookieStore = LinkedHashMap<String, String>()

    private val cookies = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookieList: List<Cookie>) {
            cookieList.forEach { cookie ->
                val name = cookie.name
                if (name.isBlank()) return@forEach
                if (cookie.value.isBlank() || cookie.expiresAt <= System.currentTimeMillis()) {
                    cookieStore.remove(name)
                } else {
                    cookieStore[name] = cookie.value
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .cookieJar(cookies)
        .followRedirects(true)
        .build()

    /** 发送短信验证码；[captchaData] 为滑块通过后拼好的 JSON */
    fun sendCode(phone: String, captchaData: String?): SendCodeResult {
        val form = FormBody.Builder()
            .add("action", "login_or_register")
            .add("phone_number", phone)
        if (!captchaData.isNullOrBlank()) form.add("captcha_data", captchaData)
        val json = runCatching { post("/api/phone/send-by-guest", form.build()) }
            .getOrElse { return SendCodeResult.Failed(it.readableMessage()) }

        if (json.optBoolean("success")) {
            val data = json.optJSONObject("data")
            return SendCodeResult.Sent(data?.optInt("retry_after", 60) ?: 60)
        }
        val data = json.optJSONObject("data")
        val error = data?.optString("error").orEmpty()
        val message = data?.optString("msg").orEmpty()
        if (error == "captcha.needs") {
            val meta = data?.optJSONObject("error_metadata")
            return SendCodeResult.CaptchaRequired(
                appId = meta?.optString("captcha_app_id").orEmpty(),
                captchaType = meta?.optString("captcha_type").orEmpty(),
                action = meta?.optString("action").orEmpty(),
                requestId = meta?.optString("request_id").orEmpty(),
                metadataJson = meta?.toString().orEmpty()
            )
        }
        return SendCodeResult.Failed(message.ifBlank { "验证码发送失败，请稍后重试" })
    }

    /** 手机号 + 验证码登录；成功后带上响应 Cookie */
    fun login(phone: String, code: String): TapTapLoginResult {
        val form = FormBody.Builder()
            .add("phone_code", code)
            .add("phone_number", phone)
            .build()
        val json = runCatching { post("/api/phone/login", form) }
            .getOrElse { return TapTapLoginResult.Failed(it.readableMessage()) }

        if (!json.optBoolean("success")) {
            val message = json.optJSONObject("data")?.optString("msg").orEmpty()
            return TapTapLoginResult.Failed(message.ifBlank { "登录失败，请检查验证码" })
        }

        val data = json.optJSONObject("data")
        val action = data?.optString("action").orEmpty()
        // 只有接口明确要求注册（action=register）才判定为未注册；
        // 其余字段（preregister / ticket_token 等）在已注册登录响应里也可能存在，不能作为判定依据。
        if (action == "register") {
            return TapTapLoginResult.NeedProfile("该手机号尚未注册 TapTap，需要先设置资料，请改用网页登录完成注册")
        }

        // 以是否真的拿到登录态 Cookie 作为成功标准
        val cookie = cookieHeader()
        if (cookie.isBlank()) {
            return TapTapLoginResult.Failed("登录响应未包含登录态，请重试或改用网页登录")
        }
        return TapTapLoginResult.Success(cookie, action)
    }

    private fun post(path: String, body: FormBody): JSONObject {
        val request = Request.Builder()
            .url(BASE + path)
            .post(body)
            .header("User-Agent", WEB_UA)
            .header("Accept", "application/json")
            .header("Referer", "$BASE/login")
            .header("Origin", BASE)
            .header("x-requested-with", "XMLHttpRequest")
            .header("X-UA", X_UA)
            .apply { cookieHeader().takeIf { it.isNotBlank() }?.let { header("Cookie", it) } }
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) throw IOException("TapTap 接口返回 ${response.code}")
            return JSONObject(text)
        }
    }

    private fun cookieHeader(): String = cookieStore.entries
        .joinToString("; ") { (name, value) -> "$name=$value" }

    private fun Throwable.readableMessage(): String = when (this) {
        is IOException -> "网络异常：${message ?: "无法连接 TapTap"}"
        else -> message ?: "请求失败"
    }

    private companion object {
        const val BASE = "https://accounts.taptap.cn"
        const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        const val X_UA =
            "V=1&PN=Accounts&LANG=zh_CN&VN_CODE=10&LOC=CN&PLT=PC&DS=Android" +
                "&UID=127f3d95-248f-4575-9cc6-2b4a402c380b&OS=Windows&OSV=10&DT=PC"
    }
}
