package com.synapse.app.data.repositories

import com.synapse.app.data.local.HistoryDao
import com.synapse.app.data.local.toDomain
import com.synapse.app.data.local.toEntity
import com.synapse.app.domain.models.HistoryItem
import com.synapse.app.domain.models.WorkflowType
import com.synapse.app.domain.repositories.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomHistoryRepository @Inject constructor(
    private val dao: HistoryDao
) : HistoryRepository {
    
    override fun getHistory(): Flow<List<HistoryItem>> {
        return dao.getAllHistory().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getHistoryByType(type: WorkflowType): Flow<List<HistoryItem>> {
        return dao.getHistoryByType(type).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getHistoryItem(id: Long): HistoryItem? {
        return dao.getHistoryById(id)?.toDomain()
    }

    override suspend fun saveHistoryItem(item: HistoryItem): Long {
        return dao.insertHistoryItem(item.toEntity())
    }

    override suspend fun deleteHistoryItem(id: Long) {
        dao.deleteHistoryItem(id)
    }
}
