package com.xingjian.assistant.data.repository

import com.xingjian.assistant.data.local.dao.CallDao
import com.xingjian.assistant.data.local.dao.SmsDao
import com.xingjian.assistant.data.local.entity.CallEntity
import com.xingjian.assistant.data.local.entity.SmsEntity
import com.xingjian.assistant.data.system.CallLogSystemReader
import com.xingjian.assistant.data.system.SmsSystemReader
import com.xingjian.assistant.domain.classify.CallClassifier
import com.xingjian.assistant.domain.classify.SmsClassifier
import com.xingjian.assistant.domain.model.CallCategory
import com.xingjian.assistant.domain.model.SmsCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SmsRepository(
    private val dao: SmsDao,
    private val reader: SmsSystemReader
) {
    val items: Flow<List<SmsEntity>> = dao.observeAll()
    val count: Flow<Int> = dao.observeCount()

    fun search(query: String): Flow<List<SmsEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(query.trim())

    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val raw = reader.read()
        if (raw.isEmpty()) return@withContext 0
        val rows = raw.map { item ->
            SmsEntity(
                nativeId = item.nativeId,
                address = item.address,
                body = item.body,
                date = item.date,
                category = SmsClassifier.classify(item.address, item.body).name
            )
        }
        dao.insertAll(rows).count { it != -1L }
    }

    suspend fun updateCategory(id: Long, category: SmsCategory) = dao.updateCategory(id, category.name)

    /** 供自动记账扫描最近的支付短信 */
    suspend fun latest(limit: Int = 300): List<SmsEntity> = withContext(Dispatchers.IO) { dao.latest(limit) }

    suspend fun toggleStar(id: Long, starred: Boolean) = dao.updateStarred(id, starred)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()
}

class CallRepository(
    private val dao: CallDao,
    private val reader: CallLogSystemReader
) {
    val items: Flow<List<CallEntity>> = dao.observeAll()
    val count: Flow<Int> = dao.observeCount()

    fun search(query: String): Flow<List<CallEntity>> =
        if (query.isBlank()) dao.observeAll() else dao.search(query.trim())

    suspend fun sync(): Int = withContext(Dispatchers.IO) {
        val raw = reader.read()
        if (raw.isEmpty()) return@withContext 0
        val rows = raw.map { item ->
            CallEntity(
                nativeId = item.nativeId,
                number = item.number,
                cachedName = item.cachedName,
                callType = item.type,
                date = item.date,
                durationSeconds = item.durationSeconds,
                category = CallClassifier.classify(item.number, item.cachedName, item.durationSeconds).name
            )
        }
        dao.insertAll(rows).count { it != -1L }
    }

    suspend fun updateCategory(id: Long, category: CallCategory) = dao.updateCategory(id, category.name)

    suspend fun toggleStar(id: Long, starred: Boolean) = dao.updateStarred(id, starred)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun clear() = dao.clear()
}
