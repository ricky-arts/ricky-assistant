package com.xingjian.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.xingjian.assistant.data.local.entity.AiProfileEntity
import com.xingjian.assistant.data.local.entity.AiProviderEntity
import com.xingjian.assistant.data.local.entity.ProxyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDao {

    @Query("SELECT * FROM ai_providers ORDER BY sortOrder ASC, id ASC")
    fun observeProviders(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun providerById(id: Long): AiProviderEntity?

    @Query("SELECT * FROM ai_providers WHERE enabled = 1")
    suspend fun enabledProviders(): List<AiProviderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(provider: AiProviderEntity): Long

    @Query("UPDATE ai_providers SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun deleteProvider(id: Long)

    @Query("DELETE FROM ai_providers")
    suspend fun clearProviders()

    @Query("UPDATE ai_providers SET enabled = 0")
    suspend fun disableAll()

    @Query("SELECT * FROM ai_profiles ORDER BY createdAt DESC")
    fun observeProfiles(): Flow<List<AiProfileEntity>>

    @Insert
    suspend fun insertProfile(profile: AiProfileEntity): Long

    @Query("DELETE FROM ai_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("DELETE FROM ai_profiles")
    suspend fun clearProfiles()
}

@Dao
interface ProxyDao {

    @Query("SELECT * FROM proxies WHERE module = :module")
    fun observe(module: String): Flow<ProxyEntity?>

    @Query("SELECT * FROM proxies WHERE module = :module")
    suspend fun get(module: String): ProxyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProxyEntity)

    @Query("DELETE FROM proxies")
    suspend fun clear()
}
