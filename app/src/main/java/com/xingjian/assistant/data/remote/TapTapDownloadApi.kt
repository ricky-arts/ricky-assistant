package com.xingjian.assistant.data.remote

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * TapTap 安装包直链解析（无需登录）。
 *
 * 与 TapTap 客户端的下载流程一致：
 * 1. GET  /app/v2/detail-by-id/{appId} 取 download.apk_id；
 * 2. POST /apk/v1/detail，用 apk_id + 时间戳 + nonce + 固定盐计算 MD5 签名，
 *    返回 data.apk.download —— 带时效签名的 CDN 直链，支持 Range 断点续传。
 *
 * 官方 api.taptap.cn 对非客户端请求有 WAF 拦截，这里使用协议一致的
 * 社区镜像 api.taptapdada.com。
 */
class TapTapDownloadApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 解析安装包直链；[apkId] 为空时自动按 [appId] 查询。
     * 游戏未上线/仅预约时返回 null。
     */
    fun resolve(appId: String, apkId: String?): String? {
        val id = apkId?.takeIf { it.isNotBlank() && it != "0" } ?: lookupApkId(appId) ?: return null
        return signedDownloadUrl(id)
    }

    private fun lookupApkId(appId: String): String? {
        val uid = UUID.randomUUID().toString()
        val xua = xua(uid)
        val request = Request.Builder()
            .url("$BASE/app/v2/detail-by-id/$appId?X-UA=${encode(xua)}")
            .header("User-Agent", APP_UA)
            .header("Accept", "application/json")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful || text.isBlank()) return@use null
                val download = JSONObject(text).optJSONObject("data")?.optJSONObject("download")
                download?.opt("apk_id")?.toString()?.takeIf { it.isNotBlank() && it != "0" }
            }
        }.getOrNull()
    }

    private fun signedDownloadUrl(apkId: String): String? {
        val uid = UUID.randomUUID().toString()
        val xua = xua(uid)
        val nonce = buildString {
            repeat(5) { append(NONCE_CHARS.random()) }
        }
        val timestamp = System.currentTimeMillis() / 1000
        val param = "abi=arm64-v8a,armeabi-v7a,armeabi&id=$apkId&node=$uid&nonce=$nonce" +
            "&sandbox=1&screen_densities=xhdpi&time=$timestamp"
        val sign = md5("X-UA=$xua&$param$SIGN_SALT")

        val request = Request.Builder()
            .url("$BASE/apk/v1/detail?X-UA=${encode(xua)}")
            .post("$param&sign=$sign".toRequestBody(FORM))
            .header("User-Agent", APP_UA)
            .header("Accept", "application/json")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful || text.isBlank()) return@use null
                JSONObject(text)
                    .optJSONObject("data")
                    ?.optJSONObject("apk")
                    ?.optString("download")
                    ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            }
        }.getOrNull()
    }

    private fun xua(uid: String) =
        "V=1&PN=TapTap&VN=2.40.1-rel.100000&VN_CODE=240011000&LOC=CN&LANG=zh_CN" +
            "&CH=default&UID=$uid&NT=1&SR=1080x2030&DEB=Xiaomi&DEM=Redmi+Note+5&OSV=9"

    private fun encode(raw: String): String = URLEncoder.encode(raw, "UTF-8")

    private fun md5(raw: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(raw.toByteArray(Charsets.UTF_8))
        return buildString { bytes.forEach { append("%02x".format(it)) } }
    }

    private companion object {
        const val BASE = "https://api.taptapdada.com"
        const val APP_UA = "okhttp/3.12.1"
        /** 客户端签名固定盐 */
        const val SIGN_SALT = "PeCkE6Fu0B10Vm9BKfPfANwCUAn5POcs"
        const val NONCE_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"
        val FORM = "application/x-www-form-urlencoded".toMediaType()
    }
}
