package com.xingjian.assistant.data.remote

import com.xingjian.assistant.domain.model.GameApk
import com.xingjian.assistant.domain.model.GameDetail
import com.xingjian.assistant.domain.model.GameSummary
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TapTap 公开 webapi 客户端（无需登录）。
 * 榜单：/webapiv2/app-top/v2/hits
 * 详情：/webapiv2/app/v2/detail-by-id/{appId}
 */
class TapTapApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * 榜单每页最多 10 条（接口对 limit 有上限校验），这里连续拉取 [PAGES] 页。
     */
    fun ranking(typeName: String): List<GameSummary> {
        val result = ArrayList<GameSummary>()
        var offset = 0
        repeat(PAGES) { page ->
            val body = try {
                get(
                    "https://www.taptap.cn/webapiv2/app-top/v2/hits" +
                        "?type_name=$typeName&platform=android&from=$offset&limit=$PAGE_SIZE&X-UA=$X_UA"
                )
            } catch (e: IOException) {
                if (page == 0) throw e else return@repeat
            }
            val data = JSONObject(body).optJSONObject("data")
            val list = data?.optJSONArray("list")
            if (list == null) {
                if (page == 0) {
                    throw IOException(data?.optString("msg").orEmpty().ifBlank { "榜单加载失败" })
                }
                return@repeat
            }
            for (i in 0 until list.length()) {
                val app = list.optJSONObject(i)?.optJSONObject("app") ?: continue
                summaryOf(app)?.let(result::add)
            }
            offset += list.length()
            if (list.length() < PAGE_SIZE) return result
        }
        return result.distinctBy { it.appId }
    }

    fun detail(appId: String): GameDetail {
        val data = JSONObject(
            get("https://www.taptap.cn/webapiv2/app/v2/detail-by-id/$appId?X-UA=$X_UA")
        ).optJSONObject("data") ?: throw IOException("游戏详情解析失败")

        val summary = summaryOf(data) ?: throw IOException("游戏详情解析失败")
        val download = data.optJSONObject("download")
        val apk = download?.optJSONObject("apk")
            ?.let { apkOf(it, download.opt("apk_id")?.toString().orEmpty()) }
        return GameDetail(
            appId = summary.appId,
            packageName = summary.packageName,
            title = summary.title,
            iconUrl = summary.iconUrl,
            score = summary.score,
            hitsTotal = summary.hitsTotal,
            tags = summary.tags,
            description = summary.description,
            screenshots = screenshotsOf(data),
            developers = developersOf(data),
            apk = apk
        )
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", WEB_UA)
            .header("Referer", "https://www.taptap.cn/top")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful || text.isBlank()) {
                throw IOException("TapTap 接口返回 ${response.code}")
            }
            return text
        }
    }

    private fun summaryOf(app: JSONObject): GameSummary? {
        val appId = app.optString("id")
        val title = app.optString("title")
        if (appId.isBlank() || title.isBlank()) return null
        val stat = app.optJSONObject("stat")
        return GameSummary(
            appId = appId,
            packageName = app.optString("identifier"),
            title = title,
            iconUrl = app.optJSONObject("icon")?.optString("url").orEmpty(),
            score = scoreOf(stat),
            hitsTotal = stat?.optLong("hits_total") ?: 0L,
            tags = tagsOf(app),
            description = descriptionOf(app)
        )
    }

    private fun scoreOf(stat: JSONObject?): Double {
        val rating = stat?.optJSONObject("rating") ?: return 0.0
        return when (val raw = rating.opt("score")) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun tagsOf(app: JSONObject): List<String> {
        val array = app.optJSONArray("tags") ?: return emptyList()
        val tags = ArrayList<String>()
        for (i in 0 until array.length()) {
            val value = array.optJSONObject(i)?.optString("value").orEmpty()
            if (value.isNotBlank()) tags.add(value)
            if (tags.size >= 4) break
        }
        return tags
    }

    private fun descriptionOf(app: JSONObject): String {
        val raw = app.opt("description")
        val text = when (raw) {
            is String -> raw
            is JSONObject -> raw.optString("text")
            else -> ""
        }
        return cleanHtml(text)
    }

    private fun screenshotsOf(data: JSONObject): List<String> {
        val array = data.optJSONArray("screenshots") ?: return emptyList()
        val urls = ArrayList<String>()
        for (i in 0 until array.length()) {
            val url = when (val item = array.opt(i)) {
                is JSONObject -> item.optString("url")
                is String -> item
                else -> ""
            }
            if (url.isNotBlank()) urls.add(url)
            if (urls.size >= 8) break
        }
        return urls
    }

    private fun developersOf(data: JSONObject): List<String> {
        data.optJSONArray("developers")?.let { array ->
            val names = ArrayList<String>()
            for (i in 0 until array.length()) {
                val name = array.optJSONObject(i)?.optString("name").orEmpty()
                if (name.isNotBlank()) names.add(name)
            }
            return names
        }
        val single = data.optJSONObject("developers")?.optString("name").orEmpty()
        return if (single.isNotBlank()) listOf(single) else emptyList()
    }

    private fun apkOf(apk: JSONObject, apkId: String): GameApk = GameApk(
        apkId = apkId,
        name = apk.optString("name"),
        sizeBytes = apk.optLong("size"),
        versionName = apk.optString("version_name"),
        versionCode = apk.optLong("version_code"),
        md5 = apk.optString("md5")
    )

    private fun cleanHtml(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private companion object {
        const val PAGE_SIZE = 10
        const val PAGES = 3
        const val WEB_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0 Safari/537.36"
        const val X_UA =
            "V%3D1%26PN%3DWebApp%26LANG%3Dzh_CN%26VN_CODE%3D100000000%26LOC%3DCN" +
                "%26PLT%3DPC%26DS%3DAndroid%26UID%3D127f3d95-248f-4575-9cc6-2b4a402c380b" +
                "%26OS%3DWindows%26OSV%3D10%26DT%3DPC"
    }
}
