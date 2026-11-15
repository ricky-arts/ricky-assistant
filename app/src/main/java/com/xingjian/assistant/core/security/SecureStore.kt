package com.xingjian.assistant.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 敏感信息（邮箱密码、代理密码、API Key）统一加密存储。
 * 严禁把这些内容写入普通 SharedPreferences 或 Room。
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        FILE_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun get(key: String): String? = prefs.getString(key, null)

    fun put(key: String, value: String?) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(key) else putString(key, value)
        }.apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "xj_secure_store"

        fun mailPasswordKey(accountId: Long) = "mail_password_$accountId"
        fun aiKeyKey(providerId: Long) = "ai_key_$providerId"
        fun proxyPasswordKey(module: String) = "proxy_pwd_$module"
    }
}
