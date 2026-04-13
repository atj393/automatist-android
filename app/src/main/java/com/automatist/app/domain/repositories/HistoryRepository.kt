package com.automatist.app.domain.repositories

import com.automatist.app.domain.models.HistoryItem
import com.automatist.app.domain.models.WorkflowType
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getHistory(): Flow<List<HistoryItem>>
    fun getHistoryByType(type: WorkflowType): Flow<List<HistoryItem>>
    suspend fun getHistoryItem(id: Long): HistoryItem?
    suspend fun saveHistoryItem(item: HistoryItem): Long
    suspend fun deleteHistoryItem(id: Long)
}
