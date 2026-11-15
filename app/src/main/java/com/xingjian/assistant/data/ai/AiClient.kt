package com.xingjian.assistant.data.ai

import com.xingjian.assistant.core.net.HttpClientFactory
import com.xingjian.assistant.data.local.entity.AiProviderEntity
import com.xingjian.assistant.domain.model.AiApiStyle
import com.xingjian.assistant.domain.model.ProxyConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * 多厂商 AI 客户端：OpenAI 兼容协议 + Anthropic 原生协议。
 * 仅在用户手动触发时调用，不进行任何后台自动请求。
 */
class AiClient(private val httpFactory: HttpClientFactory) {

    fun chat(
        provider: AiProviderEntity,
        apiKey: String,
        prompt: String,
        proxy: ProxyConfig
    ): Result<String> = runCatching {
        val client = httpFactory.build(proxy)
        val request = when (styleOf(provider)) {
            AiApiStyle.ANTHROPIC -> anthropicRequest(provider, apiKey, prompt)
            AiApiStyle.OPENAI -> openAiRequest(provider, apiKey, prompt)
        }
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(parseError(body).ifBlank { "HTTP ${response.code}" })
            }
            parseContent(body, styleOf(provider))
        }
    }

    private fun styleOf(provider: AiProviderEntity): AiApiStyle =
        runCatching { AiApiStyle.valueOf(provider.apiStyle) }.getOrDefault(AiApiStyle.OPENAI)

    private fun openAiRequest(
        provider: AiProviderEntity,
        apiKey: String,
        prompt: String
    ): Request {
        val base = provider.baseUrl.trimEnd('/')
        val payload = JSONObject()
            .put("model", provider.model)
            .put("temperature", 0.3)
            .put("stream", false)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt)
                )
            )
        return Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun anthropicRequest(
        provider: AiProviderEntity,
        apiKey: String,
        prompt: String
    ): Request {
        val base = provider.baseUrl.trimEnd('/')
        val payload = JSONObject()
            .put("model", provider.model)
            .put("max_tokens", 1200)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
            )
        return Request.Builder()
            .url("$base/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun parseContent(body: String, style: AiApiStyle): String {
        val json = JSONObject(body)
        return when (style) {
            AiApiStyle.OPENAI -> {
                val choices = json.optJSONArray("choices")
                    ?: throw IllegalStateException("响应格式异常：缺少 choices")
                choices.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?: throw IllegalStateException("响应格式异常：缺少 message")
            }

            AiApiStyle.ANTHROPIC -> {
                val content = json.optJSONArray("content")
                    ?: throw IllegalStateException("响应格式异常：缺少 content")
                buildString {
                    for (i in 0 until content.length()) {
                        val block = content.optJSONObject(i) ?: continue
                        if (block.optString("type") == "text") append(block.optString("text"))
                    }
                }
            }
        }.trim().ifBlank { throw IllegalStateException("AI 返回内容为空") }
    }

    private fun parseError(body: String): String = runCatching {
        val json = JSONObject(body)
        val error = json.optJSONObject("error")
        error?.optString("message") ?: json.optString("message")
    }.getOrDefault("")

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
