package com.xingjian.assistant.data.repository

import com.xingjian.assistant.core.security.SecureStore
import com.xingjian.assistant.data.local.dao.MailDao
import com.xingjian.assistant.data.local.entity.MailAccountEntity
import com.xingjian.assistant.data.local.entity.MailEntity
import com.xingjian.assistant.data.mail.ImapMailClient
import com.xingjian.assistant.domain.classify.MailClassifier
import com.xingjian.assistant.domain.model.MailCategory
import com.xingjian.assistant.domain.model.ProxyModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MailRepository(
    private val dao: MailDao,
    private val client: ImapMailClient,
    private val secureStore: SecureStore,
    private val proxyRepository: ProxyRepository
) {
    val accounts: Flow<List<MailAccountEntity>> = dao.observeAccounts()
    val mailCount: Flow<Int> = dao.observeCount()

    fun mails(accountId: Long?): Flow<List<MailEntity>> = dao.observeMails(accountId)

    suspend fun addAccount(account: MailAccountEntity, password: String): Long {
        val id = dao.insertAccount(account)
        secureStore.put(SecureStore.mailPasswordKey(id), password)
        return id
    }

    suspend fun setAccountEnabled(id: Long, enabled: Boolean) = dao.setAccountEnabled(id, enabled)

    suspend fun deleteAccount(id: Long) {
        dao.deleteMailsByAccount(id)
        dao.deleteAccount(id)
        secureStore.remove(SecureStore.mailPasswordKey(id))
    }

    /** 用户手动触发一次拉取，返回新增邮件数量 */
    suspend fun fetch(accountId: Long, limit: Int = 40): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val account = dao.accountById(accountId) ?: error("邮箱账号不存在")
            val password = secureStore.get(SecureStore.mailPasswordKey(accountId))
                ?: error("未找到邮箱密码，请删除后重新配置")
            val proxy = proxyRepository.configFor(ProxyModule.MAIL)
            val raws = client.fetch(account, password, proxy, limit)
            if (raws.isEmpty()) return@runCatching 0
            val rows = raws.map { mail ->
                MailEntity(
                    accountId = accountId,
                    messageKey = mail.messageKey,
                    subject = mail.subject,
                    sender = mail.sender,
                    recipient = mail.recipient,
                    receivedAt = mail.receivedAt,
                    snippet = mail.snippet,
                    category = MailClassifier.classify(
                        mail.subject,
                        mail.sender,
                        mail.snippet
                    ).name
                )
            }
            dao.insertMails(rows).count { it != -1L }
        }
    }

    suspend fun updateCategory(id: Long, category: MailCategory) = dao.updateCategory(id, category.name)

    suspend fun toggleStar(id: Long, starred: Boolean) = dao.updateStarred(id, starred)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clearMails() = dao.clearMails()
}
