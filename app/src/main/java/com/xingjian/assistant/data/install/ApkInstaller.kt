package com.xingjian.assistant.data.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.IntentCompat
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 应用内安装：
 * 1. 单个 APK 直接写入安装会话；
 * 2. xapk 分包先解压出 base 与 split APK，再写入同一个多 APK 会话。
 * 全部走 PackageInstaller，不经过浏览器，也不再需要 FileProvider。
 */
object ApkInstaller {

    const val ACTION_STATUS = "com.xingjian.assistant.INSTALL_STATUS"

    fun install(context: Context, apkFile: File): Boolean =
        startSession(context, null) { session ->
            writeSessionFile(session, "base.apk", apkFile)
        }

    fun installBundle(context: Context, xapkFile: File, packageName: String): Boolean {
        val dir = File(context.cacheDir, "game_splits").apply { deleteRecursively(); mkdirs() }
        val apks = extractApks(xapkFile, dir)
        if (apks.isEmpty()) throw IOException("分包解压失败，请稍后重试")
        val base = apks.firstOrNull { packageNameOf(context, it) == packageName }
            ?: apks.maxByOrNull { it.length() }
            ?: throw IOException("分包解压失败，请稍后重试")
        val splits = apks.filter { it != base }
        return startSession(context, packageName) { session ->
            writeSessionFile(session, "base.apk", base)
            splits.forEach { writeSessionFile(session, it.name, it) }
        }
    }

    private fun startSession(
        context: Context,
        packageName: String?,
        write: (PackageInstaller.Session) -> Unit
    ): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (packageName != null) params.setAppPackageName(packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            write(session)
            session.commit(statusIntent(context).intentSender)
        }
        return true
    }

    private fun writeSessionFile(session: PackageInstaller.Session, name: String, file: File) {
        session.openWrite(name, 0, file.length()).use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
            session.fsync(output)
        }
    }

    private fun statusIntent(context: Context): android.app.PendingIntent {
        val intent = Intent(ACTION_STATUS).setPackage(context.packageName)
        return android.app.PendingIntent.getBroadcast(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 解压 xapk（本质是 zip），返回其中的所有 APK */
    private fun extractApks(xapk: File, dir: File): List<File> {
        val apks = ArrayList<File>()
        ZipFile(xapk).use { zip ->
            zip.entries().asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.endsWith(".apk", true) }
                .forEach { entry: ZipEntry ->
                    val target = File(dir, entry.name.substringAfterLast('/'))
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    apks.add(target)
                }
        }
        return apks
    }

    private fun packageNameOf(context: Context, apk: File): String? =
        context.packageManager
            .getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_META_DATA)
            ?.packageName

    /** 供状态广播读取用户确认 Intent 使用（Android 13 起 getParcelableExtra 需类型化） */
    fun confirmIntent(intent: Intent): Intent? =
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
}
