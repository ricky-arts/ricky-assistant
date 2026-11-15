package com.xingjian.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xingjian.assistant.data.local.entity.CallEntity
import com.xingjian.assistant.data.local.entity.SmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {

    @Query("SELECT * FROM sms ORDER BY date DESC LIMIT 1200")
    fun observeAll(): Flow<List<SmsEntity>>

    /** 自动记账扫描最近短信：一次性读取 */
    @Query("SELECT * FROM sms ORDER BY date DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<SmsEntity>

    @Query("SELECT COUNT(*) FROM sms")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT * FROM sms WHERE body LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' " +
            "ORDER BY date DESC LIMIT 500"
    )
    fun search(query: String): Flow<List<SmsEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SmsEntity>): List<Long>

    @Query("UPDATE sms SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE sms SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: Long, starred: Boolean)

    @Query("DELETE FROM sms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sms")
    suspend fun clear()
}

@Dao
interface CallDao {

    @Query("SELECT * FROM calls ORDER BY date DESC LIMIT 1200")
    fun observeAll(): Flow<List<CallEntity>>

    @Query("SELECT COUNT(*) FROM calls")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT * FROM calls WHERE number LIKE '%' || :query || '%' OR cachedName LIKE '%' || :query || '%' " +
            "ORDER BY date DESC LIMIT 500"
    )
    fun search(query: String): Flow<List<CallEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CallEntity>): List<Long>

    @Query("UPDATE calls SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE calls SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: Long, starred: Boolean)

    @Query("DELETE FROM calls WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM calls")
    suspend fun clear()
}
