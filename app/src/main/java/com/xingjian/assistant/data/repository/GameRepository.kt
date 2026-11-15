package com.xingjian.assistant.data.repository

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import com.xingjian.assistant.data.remote.TapTapApi
import com.xingjian.assistant.domain.model.GameDetail
import com.xingjian.assistant.domain.model.GameSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 游戏数据仓库。
 * 榜单与详情来自 TapTap 公开 webapi；
 * 安装包直链由 APKPure 解析（TapTap 官方直链需要登录鉴权，无法直接拉取）。
 */
class GameRepository(
    private val api: TapTapApi,
    private val context: Context
) {

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun ranking(typeName: String): List<GameSummary> = withContext(Dispatchers.IO) {
        api.ranking(typeName)
    }

    suspend fun detail(appId: String): GameDetail = withContext(Dispatchers.IO) {
        api.detail(appId)
    }

    /**
     * 解析安装包直链。
     * @return 直链地址 + 是否为分包（xapk，无法用系统安装器直接安装）
     */
    suspend fun resolveDownload(packageName: String): Pair<String, Boolean>? =
        withContext(Dispatchers.IO) {
            if (packageName.isBlank()) return@withContext null
            runCatching {
                val request = Request.Builder()
                    .url("https://d.apkpure.com/b/APK/$packageName?version=latest")
                    .header("User-Agent", MOBILE_UA)
                    .header("Accept", "*/*")
                    .build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val finalUrl = response.request.url.toString()
                    if (finalUrl.isBlank()) return@use null
                    val isBundle = finalUrl.contains("/XAPK/", ignoreCase = true) ||
                        finalUrl.contains(".xapk", ignoreCase = true)
                    finalUrl to isBundle
                }
            }.getOrNull()
        }

    /** 交给系统下载器下载，完成后通知栏可直接点击安装 */
    fun enqueueApkDownload(url: String, title: String, packageName: String): Boolean = runCatching {
        val safeName = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "$safeName-${System.currentTimeMillis()}.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("安装包下载中")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        val manager = context.getSystemService(DownloadManager::class.java)
        manager?.enqueue(request)
        manager != null
    }.getOrDefault(false)

    /** 打开 TapTap 游戏页作为兜底下载入口 */
    fun openTapTapPage(appId: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.taptap.cn/app/$appId"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private companion object {
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    }
}
