package com.xingjian.assistant.domain.model

/** 榜单中的一个游戏（数据来自 TapTap 公开榜单接口） */
data class GameSummary(
    val appId: String,
    val packageName: String,
    val title: String,
    val iconUrl: String,
    val score: Double,
    val hitsTotal: Long,
    val tags: List<String>,
    val description: String
)

/** 安装包信息 */
data class GameApk(
    /** TapTap 安装包内部 ID，用于解析下载直链 */
    val apkId: String,
    val name: String,
    val sizeBytes: Long,
    val versionName: String,
    val versionCode: Long,
    val md5: String
)

/** 游戏详情 */
data class GameDetail(
    val appId: String,
    val packageName: String,
    val title: String,
    val iconUrl: String,
    val score: Double,
    val hitsTotal: Long,
    val tags: List<String>,
    val description: String,
    val screenshots: List<String>,
    val developers: List<String>,
    val apk: GameApk?
)
