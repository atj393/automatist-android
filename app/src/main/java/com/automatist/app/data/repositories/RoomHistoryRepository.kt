package com.automatist.app.data.repositories

import com.automatist.app.data.local.HistoryDao
import com.automatist.app.data.local.toDomain
import com.automatist.app.data.local.toEntity
import com.automatist.app.domain.models.HistoryItem
import com.automatist.app.domain.models.WorkflowType
import com.automatist.app.domain.repositories.HistoryRepository
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
