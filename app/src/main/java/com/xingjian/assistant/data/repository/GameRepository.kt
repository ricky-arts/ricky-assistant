package com.xingjian.assistant.data.repository

import android.content.Context
import com.xingjian.assistant.data.install.ApkInstaller
import com.xingjian.assistant.data.remote.TapTapApi
import com.xingjian.assistant.domain.model.GameDetail
import com.xingjian.assistant.domain.model.GameSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 免登录安装包直链 */
data class DownloadLink(
    val url: String,
    /** xapk 分包，需要拆出 base + split 后多 APK 安装 */
    val isBundle: Boolean
)

/**
 * 游戏数据仓库。
 * 榜单与详情来自 TapTap 公开 webapi；
 * 安装包改由 APKPure 解析免鉴权直链，并在应用内下载、安装
 *（TapTap 官方 CDN 需要登录态，请求会返回 401）。
 */
class GameRepository(
    private val api: TapTapApi,
    private val context: Context
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Volatile
    private var runningCall: okhttp3.Call? = null

    suspend fun ranking(typeName: String): List<GameSummary> = withContext(Dispatchers.IO) {
        api.ranking(typeName)
    }

    suspend fun detail(appId: String): GameDetail = withContext(Dispatchers.IO) {
        api.detail(appId)
    }

    /** 解析免鉴权直链：APKPure 的 302 会跳到真实 CDN，无需任何 Cookie/Token */
    suspend fun resolveDownload(packageName: String): DownloadLink? =
        withContext(Dispatchers.IO) {
            if (packageName.isBlank()) return@withContext null
            runCatching {
                val request = Request.Builder()
                    .url("https://d.apkpure.com/b/APK/$packageName?version=latest")
                    .header("User-Agent", MOBILE_UA)
                    .header("Accept", "*/*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val finalUrl = response.request.url.toString()
                    if (finalUrl.isBlank()) return@use null
                    val isBundle = finalUrl.contains(".xapk", ignoreCase = true)
                    DownloadLink(finalUrl, isBundle)
                }
            }.getOrNull()
        }

    /** 应用内下载并调起安装（单 APK 或 xapk 分包） */
    suspend fun downloadAndInstall(
        link: DownloadLink,
        packageName: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = download(link, onProgress)
            if (link.isBundle) {
                ApkInstaller.installBundle(context, file, packageName)
            } else {
                ApkInstaller.install(context, file)
            }
            Unit
        }
    }

    /** 仅下载，返回落盘的安装包 */
    private fun download(link: DownloadLink, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, "game_download").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, if (link.isBundle) "game.xapk" else "game.apk")

        val request = Request.Builder()
            .url(link.url)
            .header("User-Agent", MOBILE_UA)
            .header("Accept", "*/*")
            .build()
        val call = client.newCall(request)
        runningCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("下载失败（HTTP ${response.code}）")
                val body = response.body ?: throw IOException("安装包内容为空")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress(written.toFloat() / total)
                        }
                    }
                }
            }
        } finally {
            runningCall = null
        }
        if (target.length() <= 0L) throw IOException("安装包下载不完整")
        return target
    }

    /** 取消正在进行的下载 */
    fun cancelDownload() {
        runningCall?.cancel()
    }

    private companion object {
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
