package com.xingjian.assistant.data.repository

import android.content.Context
import android.net.Uri
import com.xingjian.assistant.core.security.SecureStore
import com.xingjian.assistant.data.local.AppDatabase
import com.xingjian.assistant.data.local.dao.ProxyDao
import com.xingjian.assistant.data.local.entity.ProxyEntity
import com.xingjian.assistant.domain.model.ProxyConfig
import com.xingjian.assistant.domain.model.ProxyType
import com.xingjian.assistant.domain.model.ThemeMode
import com.xingjian.assistant.domain.model.enumOrDefault
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 邮箱代理与 AI 代理相互独立，密码均存入加密存储 */
class ProxyRepository(
    private val dao: ProxyDao,
    private val secureStore: SecureStore
) {
    fun observe(module: String): Flow<ProxyEntity?> = dao.observe(module)

    fun observeConfig(module: String): Flow<ProxyConfig> =
        dao.observe(module).map { entity -> entity?.toConfig() ?: ProxyConfig() }

    suspend fun configFor(module: String): ProxyConfig =
        dao.get(module)?.toConfig() ?: ProxyConfig()

    suspend fun save(
        module: String,
        enabled: Boolean,
        type: ProxyType,
        host: String,
        port: Int,
        username: String,
        password: String
    ) {
        secureStore.put(SecureStore.proxyPasswordKey(module), password)
        dao.upsert(
            ProxyEntity(
                module = module,
                enabled = enabled,
                type = type.name,
                host = host.trim(),
                port = port,
                username = username.trim(),
                hasPassword = password.isNotBlank()
            )
        )
    }

    private fun ProxyEntity.toConfig(): ProxyConfig = ProxyConfig(
        enabled = enabled,
        type = enumOrDefault(type, ProxyType.NONE),
        host = host,
        port = port,
        username = username,
        password = secureStore.get(SecureStore.proxyPasswordKey(module)).orEmpty()
    )
}

class SettingsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(
        enumOrDefault(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val backgroundFile = File(File(appContext.filesDir, "theme"), "background.img")

    /** 背景图版本号：每次设置/清除后自增，Compose 侧据此重新解码 */
    private val _backgroundVersion = MutableStateFlow(if (backgroundFile.length() > 0L) 1 else 0)
    val backgroundVersion: StateFlow<Int> = _backgroundVersion

    /** 自动记账总开关（通知监听 / 支付短信 / 无障碍识别三通道共用） */
    private val _autoLedgerEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_LEDGER, true))
    val autoLedgerEnabled: StateFlow<Boolean> = _autoLedgerEnabled

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    fun setAutoLedgerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LEDGER, enabled).apply()
        _autoLedgerEnabled.value = enabled
    }

    /** 当前自定义背景文件，未设置时返回 null */
    fun backgroundFile(): File? = backgroundFile.takeIf { it.length() > 0L }

    /** 把用户选择的图片拷贝到应用私有目录，避免 Uri 授权失效 */
    suspend fun setBackground(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backgroundFile.parentFile
            if (dir != null && !dir.exists()) dir.mkdirs()
            val temp = File(dir, "background.tmp")
            val input = appContext.contentResolver.openInputStream(uri)
                ?: error("无法读取所选图片")
            input.use { source ->
                temp.outputStream().use { target -> source.copyTo(target) }
            }
            if (backgroundFile.exists()) backgroundFile.delete()
            if (!temp.renameTo(backgroundFile)) {
                temp.copyTo(backgroundFile, overwrite = true)
                temp.delete()
            }
            _backgroundVersion.value += 1
        }
    }

    fun clearBackground() {
        runCatching { backgroundFile.delete() }
        _backgroundVersion.value += 1
    }

    fun reset() {
        prefs.edit().clear().apply()
        _themeMode.value = ThemeMode.SYSTEM
        _autoLedgerEnabled.value = true
        clearBackground()
    }

    private companion object {
        const val PREFS_NAME = "xj_settings"
        const val KEY_THEME = "theme_mode"
        const val KEY_AUTO_LEDGER = "auto_ledger"
    }
}

/**
 * 清空用户本地数据。
 * 保留内置记账分类与 AI 厂商模板（模板不包含任何用户隐私数据），其余全部删除。
 */
suspend fun clearAllLocalData(
    database: AppDatabase,
    secureStore: SecureStore,
    settings: SettingsRepository
) {
    database.smsDao().clear()
    database.callDao().clear()
    database.mailDao().clearMails()
    database.mailDao().clearAccounts()
    database.ledgerDao().clearTransactions()
    database.aiDao().clearProfiles()
    database.aiDao().disableAll()
    database.proxyDao().clear()
    secureStore.clear()
    settings.reset()
}
