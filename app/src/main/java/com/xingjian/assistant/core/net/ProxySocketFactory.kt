package com.xingjian.assistant.core.net

import android.util.Base64
import com.xingjian.assistant.domain.model.ProxyConfig
import com.xingjian.assistant.domain.model.ProxyType
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.SocketFactory

/**
 * 供 JavaMail 使用的 SocketFactory：
 * - JavaMail 通过反射调用静态 getDefault() 获取实例；
 * - 代理参数通过 [configure] 在发起连接前注入（同一时刻只进行一次邮件拉取）。
 */
class ProxySocketFactory : SocketFactory() {

    companion object {
        @Volatile
        private var config: ProxyConfig? = null

        @JvmStatic
        fun configure(value: ProxyConfig?) {
            config = value
        }

        @JvmStatic
        fun getDefault(): ProxySocketFactory = ProxySocketFactory()

        private const val CONNECT_TIMEOUT = 20_000
    }

    override fun createSocket(): Socket = ProxyTunnelSocket(config)

    override fun createSocket(host: String, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port), CONNECT_TIMEOUT) }

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        createSocket().apply {
            bind(InetSocketAddress(localHost, localPort))
            connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
        }

    override fun createSocket(host: InetAddress, port: Int): Socket =
        createSocket().apply { connect(InetSocketAddress(host, port), CONNECT_TIMEOUT) }

    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket = createSocket().apply {
        bind(InetSocketAddress(localAddress, localPort))
        connect(InetSocketAddress(address, port), CONNECT_TIMEOUT)
    }
}

/**
 * 未连接时创建，connect 时才真正建立到代理的 TCP 连接并完成握手。
 * 因此既能配合 JavaMail 的“createSocket() -> connect()”流程，也能在连接后正常套 SSL。
 */
internal class ProxyTunnelSocket(private val proxy: ProxyConfig?) : Socket() {

    private var delegate: Socket? = null
    private var pendingSoTimeout: Int = 0

    override fun connect(endpoint: SocketAddress, timeout: Int) {
        val target = endpoint as? InetSocketAddress
            ?: throw IOException("不支持的连接地址: $endpoint")
        val connectTimeout = if (timeout > 0) timeout else DEFAULT_TIMEOUT

        if (proxy == null || !proxy.usable) {
            val raw = Socket()
            if (pendingSoTimeout > 0) raw.soTimeout = pendingSoTimeout
            raw.connect(target, connectTimeout)
            delegate = raw
            return
        }

        val raw = Socket()
        if (pendingSoTimeout > 0) raw.soTimeout = pendingSoTimeout
        try {
            raw.connect(InetSocketAddress(proxy.host, proxy.port), connectTimeout)
            when (proxy.type) {
                ProxyType.HTTP -> httpConnect(raw, target)
                ProxyType.SOCKS5 -> socksConnect(raw, target)
                ProxyType.NONE -> Unit
            }
            delegate = raw
        } catch (t: Throwable) {
            runCatching { raw.close() }
            throw if (t is IOException) t else IOException("代理连接失败", t)
        }
    }

    override fun connect(endpoint: SocketAddress) {
        connect(endpoint, DEFAULT_TIMEOUT)
    }

    override fun getInputStream(): InputStream =
        delegate?.getInputStream() ?: throw IOException("Socket 尚未连接")

    override fun getOutputStream(): OutputStream =
        delegate?.getOutputStream() ?: throw IOException("Socket 尚未连接")

    override fun setSoTimeout(timeout: Int) {
        pendingSoTimeout = timeout
        delegate?.soTimeout = timeout
    }

    override fun getSoTimeout(): Int = delegate?.soTimeout ?: pendingSoTimeout

    override fun close() {
        runCatching { delegate?.close() }
        delegate = null
    }

    override fun isConnected(): Boolean = delegate?.isConnected == true

    override fun isClosed(): Boolean = delegate?.isClosed == true

    override fun getInetAddress(): InetAddress? = delegate?.inetAddress

    override fun getPort(): Int = delegate?.port ?: 0

    override fun getLocalAddress(): InetAddress? = delegate?.localAddress

    override fun getLocalPort(): Int = delegate?.localPort ?: 0

    override fun getRemoteSocketAddress(): SocketAddress? = delegate?.remoteSocketAddress

    override fun getLocalSocketAddress(): SocketAddress? = delegate?.localSocketAddress

    override fun setKeepAlive(on: Boolean) {
        delegate?.keepAlive = on
    }

    override fun setTcpNoDelay(on: Boolean) {
        delegate?.tcpNoDelay = on
    }

    override fun toString(): String =
        "ProxyTunnelSocket(proxy=${proxy?.type}, connected=${isConnected()})"

    // ---------------- 代理握手 ----------------

    private fun httpConnect(raw: Socket, target: InetSocketAddress) {
        val host = target.hostString
        val port = target.port
        val builder = StringBuilder()
        builder.append("CONNECT $host:$port HTTP/1.1\r\n")
        builder.append("Host: $host:$port\r\n")
        builder.append("Proxy-Connection: keep-alive\r\n")
        if (proxy?.username?.isNotBlank() == true) {
            val token = Base64.encodeToString(
                "${proxy.username}:${proxy.password}".toByteArray(Charsets.UTF_8),
                Base64.NO_WRAP
            )
            builder.append("Proxy-Authorization: Basic $token\r\n")
        }
        builder.append("\r\n")

        val output = raw.getOutputStream()
        output.write(builder.toString().toByteArray(Charsets.UTF_8))
        output.flush()

        val statusLine = readAsciiLine(raw.getInputStream())
        // 读取并丢弃剩余响应头
        while (true) {
            val line = readAsciiLine(raw.getInputStream())
            if (line.isEmpty()) break
        }
        if (!statusLine.contains(" 200")) {
            throw IOException("HTTP 代理连接失败: $statusLine")
        }
    }

    private fun socksConnect(raw: Socket, target: InetSocketAddress) {
        val out = raw.getOutputStream()
        val input = raw.getInputStream()
        val hasAuth = !proxy?.username.isNullOrBlank()

        // 1. 协商认证方式
        out.write(byteArrayOf(0x05, if (hasAuth) 0x02 else 0x01, 0x00, if (hasAuth) 0x02 else 0x00))
        out.flush()
        val methodResponse = input.readExact(2)
        if (methodResponse[0].toInt() != 0x05) throw IOException("SOCKS5 握手失败")
        when (methodResponse[1].toInt()) {
            0x00 -> Unit
            0x02 -> {
                if (!hasAuth) throw IOException("SOCKS5 代理要求认证，但未配置账号密码")
                val username = proxy!!.username.toByteArray(Charsets.UTF_8)
                val password = proxy.password.toByteArray(Charsets.UTF_8)
                val authRequest = byteArrayOf(
                    0x01,
                    username.size.toByte(),
                    *username,
                    password.size.toByte(),
                    *password
                )
                out.write(authRequest)
                out.flush()
                val authResponse = input.readExact(2)
                if (authResponse[0].toInt() != 0x01 || authResponse[1].toInt() != 0x00) {
                    throw IOException("SOCKS5 认证失败")
                }
            }
            else -> throw IOException("SOCKS5 不支持的认证方式: ${methodResponse[1]}")
        }

        // 2. 发送 CONNECT 请求（使用域名寻址，让代理侧解析）
        val hostBytes = target.hostString.toByteArray(Charsets.UTF_8)
        val port = target.port
        val request = byteArrayOf(
            0x05, 0x01, 0x00, 0x03,
            hostBytes.size.toByte(),
            *hostBytes,
            (port shr 8 and 0xFF).toByte(),
            (port and 0xFF).toByte()
        )
        out.write(request)
        out.flush()

        // 3. 读取响应
        val head = input.readExact(4)
        if (head[0].toInt() != 0x05 || head[1].toInt() != 0x00) {
            throw IOException("SOCKS5 连接被拒绝，CODE=${head[1]}")
        }
        when (head[3].toInt()) {
            0x01 -> input.readExact(4)
            0x03 -> input.readExact(input.readExact(1)[0].toInt().coerceAtLeast(0))
            0x04 -> input.readExact(16)
        }
        input.readExact(2)
    }

    private fun InputStream.readExact(size: Int): ByteArray {
        val buffer = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = read(buffer, offset, size - offset)
            if (read < 0) throw IOException("代理响应被意外关闭")
            offset += read
        }
        return buffer
    }

    private fun readAsciiLine(input: InputStream): String {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) break
            if (value == '\r'.code) continue
            if (value == '\n'.code) break
            builder.append(value.toChar())
        }
        return builder.toString()
    }

    private companion object {
        const val DEFAULT_TIMEOUT = 20_000
    }
}
