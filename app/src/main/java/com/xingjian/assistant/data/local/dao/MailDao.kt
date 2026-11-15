package com.xingjian.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xingjian.assistant.data.local.entity.MailAccountEntity
import com.xingjian.assistant.data.local.entity.MailEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MailDao {

    // ------- 账号 -------

    @Query("SELECT * FROM mail_accounts ORDER BY createdAt ASC")
    fun observeAccounts(): Flow<List<MailAccountEntity>>

    @Query("SELECT * FROM mail_accounts WHERE id = :id")
    suspend fun accountById(id: Long): MailAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: MailAccountEntity): Long

    @Query("UPDATE mail_accounts SET enabled = :enabled WHERE id = :id")
    suspend fun setAccountEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM mail_accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("DELETE FROM mail_accounts")
    suspend fun clearAccounts()

    // ------- 邮件 -------

    @Query(
        "SELECT * FROM mails WHERE (:accountId IS NULL OR accountId = :accountId) " +
            "ORDER BY receivedAt DESC LIMIT 1000"
    )
    fun observeMails(accountId: Long?): Flow<List<MailEntity>>

    @Query("SELECT COUNT(*) FROM mails")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMails(items: List<MailEntity>): List<Long>

    @Query("UPDATE mails SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE mails SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: Long, starred: Boolean)

    @Query("DELETE FROM mails WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM mails WHERE accountId = :accountId")
    suspend fun deleteMailsByAccount(accountId: Long)

    @Query("DELETE FROM mails")
    suspend fun clearMails()
}
