package com.xingjian.assistant.core.net

import com.xingjian.assistant.domain.model.ProxyConfig
import com.xingjian.assistant.domain.model.ProxyType
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.OkHttpClient

/**
 * 为 AI 请求构建 OkHttpClient。
 * 代理完全由 App 内配置控制，不复用系统全局代理。
 */
class HttpClientFactory {

    fun build(config: ProxyConfig): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (config.usable) {
            when (config.type) {
                ProxyType.HTTP -> {
                    builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port)))
                    if (config.username.isNotBlank()) {
                        builder.proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) {
                                null
                            } else {
                                response.request.newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        Credentials.basic(config.username, config.password)
                                    )
                                    .build()
                            }
                        }
                    }
                }

                ProxyType.SOCKS5 -> {
                    builder.proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(config.host, config.port)))
                    if (config.username.isNotBlank()) {
                        // java.net 的 SOCKS5 账号认证只能通过全局 Authenticator 注入。
                        // 这里仅在用户配置了 SOCKS5 账号时启用，并在 App 生命周期内保持。
                        Authenticator.setDefault(object : Authenticator() {
                            override fun getPasswordAuthentication(): PasswordAuthentication? {
                                return if (requestorType == RequestorType.PROXY) {
                                    PasswordAuthentication(
                                        config.username,
                                        config.password.toCharArray()
                                    )
                                } else {
                                    null
                                }
                            }
                        })
                    }
                }

                ProxyType.NONE -> Unit
            }
        }
        return builder.build()
    }
}
