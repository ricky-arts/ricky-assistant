package com.xingjian.assistant.data.repository

import com.xingjian.assistant.core.security.SecureStore
import com.xingjian.assistant.data.ai.AiClient
import com.xingjian.assistant.data.local.dao.AiDao
import com.xingjian.assistant.data.local.entity.AiProfileEntity
import com.xingjian.assistant.data.local.entity.AiProviderEntity
import com.xingjian.assistant.domain.model.ProxyModule
import com.xingjian.assistant.domain.model.ProxyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AiRepository(
    private val dao: AiDao,
    private val secureStore: SecureStore,
    private val proxyRepository: ProxyRepository,
    private val client: AiClient
) {
    val providers: Flow<List<AiProviderEntity>> = dao.observeProviders()
    val profiles: Flow<List<AiProfileEntity>> = dao.observeProfiles()

    fun hasApiKey(providerId: Long): Boolean =
        secureStore.get(SecureStore.aiKeyKey(providerId))?.isNotBlank() == true

    suspend fun saveProvider(provider: AiProviderEntity, apiKey: String?): Long {
        val id = dao.upsertProvider(provider)
        val targetId = if (provider.id == 0L) id else provider.id
        if (!apiKey.isNullOrBlank()) {
            secureStore.put(SecureStore.aiKeyKey(targetId), apiKey.trim())
        }
        return targetId
    }

    suspend fun clearApiKey(providerId: Long) = secureStore.remove(SecureStore.aiKeyKey(providerId))

    suspend fun setEnabled(providerId: Long, enabled: Boolean) = dao.setEnabled(providerId, enabled)

    suspend fun deleteProfile(profileId: Long) = dao.deleteProfile(profileId)

    suspend fun generateProfiles(prompt: String): Result<List<AiProfileEntity>> =
        withContext(Dispatchers.IO) {
            val enabled = dao.enabledProviders()
            if (enabled.isEmpty()) {
                return@withContext Result.failure(
                    IllegalStateException("请先在下方开启至少一个已配置 Key 的 AI 服务商")
                )
            }
            val proxy = proxyRepository.configFor(ProxyModule.AI)
            if (proxy.type != ProxyType.NONE && !proxy.usable) {
                return@withContext Result.failure(IllegalStateException("AI 代理配置不完整"))
            }

            val created = mutableListOf<AiProfileEntity>()
            var lastError: String? = null
            for (provider in enabled) {
                val key = secureStore.get(SecureStore.aiKeyKey(provider.id))
                if (key.isNullOrBlank()) {
                    lastError = "${provider.label} 未配置 API Key"
                    continue
                }
                client.chat(provider, key, prompt, proxy)
                    .onSuccess { text ->
                        val profile = AiProfileEntity(
                            title = "个人画像 ${com.xingjian.assistant.core.DateTimeUtils.formatDate(System.currentTimeMillis())}",
                            content = text,
                            providerLabel = provider.label,
                            model = provider.model
                        )
                        val id = dao.insertProfile(profile)
                        created += profile.copy(id = id)
                    }
                    .onFailure { lastError = "${provider.label}: ${it.message ?: "请求失败"}" }
            }

            if (created.isEmpty()) {
                Result.failure(IllegalStateException(lastError ?: "AI 汇总失败，请检查网络与配置"))
            } else {
                Result.success(created)
            }
        }
}
