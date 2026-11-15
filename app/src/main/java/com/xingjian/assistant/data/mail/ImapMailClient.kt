package com.xingjian.assistant.data.mail

import com.sun.mail.imap.IMAPFolder
import com.xingjian.assistant.core.net.ProxySocketFactory
import com.xingjian.assistant.data.local.entity.MailAccountEntity
import com.xingjian.assistant.domain.model.ProxyConfig
import com.xingjian.assistant.domain.model.RawMail
import java.util.Properties
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store

/**
 * 基于 JavaMail 的 IMAP 拉取。
 * - SSL：imaps（默认 993）
 * - 非 SSL：imap + STARTTLS（满足即用，服务端不支持时可回退明文）
 * - 代理：通过自定义 SocketFactory 走 App 内独立代理配置
 */
class ImapMailClient {

    fun fetch(
        account: MailAccountEntity,
        password: String,
        proxy: ProxyConfig,
        limit: Int = 40
    ): List<RawMail> {
        ProxySocketFactory.configure(proxy.takeIf { it.usable })
        var store: Store? = null
        var folder: Folder? = null
        try {
            val protocol = if (account.useSsl) "imaps" else "imap"
            val prefix = "mail.$protocol"
            val props = Properties().apply {
                put("mail.store.protocol", protocol)
                put("$prefix.host", account.imapHost)
                put("$prefix.port", account.imapPort.toString())
                put("$prefix.connectiontimeout", "20000")
                put("$prefix.timeout", "30000")
                put("$prefix.peek", "true")
                if (proxy.usable) {
                    put("$prefix.socketFactory.class", ProxySocketFactory::class.java.name)
                    put("$prefix.socketFactory.fallback", "false")
                }
                if (!account.useSsl) {
                    put("mail.imap.starttls.enable", "true")
                    put("mail.imap.starttls.required", "false")
                }
            }
            val session = Session.getInstance(props)
            store = session.getStore(protocol)
            store.connect(account.imapHost, account.imapPort, account.username, password)

            folder = store.getFolder("INBOX")
            folder.open(Folder.READ_ONLY)

            val total = folder.messageCount
            if (total <= 0) return emptyList()
            val start = maxOf(1, total - limit + 1)
            val messages = folder.getMessages(start, total) ?: return emptyList()

            val result = mutableListOf<RawMail>()
            for (message in messages) {
                runCatching {
                    result += message.toRawMail(folder)
                }
            }
            return result
        } finally {
            runCatching { folder?.close(false) }
            runCatching { store?.close() }
            ProxySocketFactory.configure(null)
        }
    }

    private fun Message.toRawMail(folder: Folder): RawMail {
        val key = runCatching {
            val uid = (folder as? IMAPFolder)?.getUID(this)
            if (uid != null && uid > 0) "uid:$uid" else null
        }.getOrNull() ?: "num:$messageNumber:${sentDate?.time ?: 0L}"

        val subject = runCatching { subject ?: "(无主题)" }.getOrDefault("(无主题)")
        val sender = runCatching {
            from?.joinToString(",") { it.toString() } ?: "(未知发件人)"
        }.getOrDefault("(未知发件人)")
        val recipient = runCatching {
            allRecipients?.joinToString(",") { it.toString() } ?: ""
        }.getOrDefault("")
        val timestamp = runCatching { sentDate?.time }.getOrNull()
            ?: runCatching { receivedDate?.time }.getOrNull()
            ?: System.currentTimeMillis()
        val text = runCatching { extractText(this) }.getOrDefault("")
            .replace(Regex("\\s+"), " ")
            .trim()

        return RawMail(
            messageKey = key,
            subject = subject.take(200),
            sender = sender.take(200),
            recipient = recipient.take(200),
            receivedAt = timestamp,
            snippet = text.take(180)
        )
    }

    private fun extractText(part: Part): String {
        val content = runCatching { part.content }.getOrNull() ?: return ""
        return when (content) {
            is String -> content
            is Multipart -> {
                if (content.count == 0) return ""
                (0 until content.count).joinToString(" ") { index ->
                    runCatching { extractText(content.getBodyPart(index)) }.getOrDefault("")
                }
            }

            else -> ""
        }
    }
}
