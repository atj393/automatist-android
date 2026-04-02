package com.synapse.app.domain.repositories

import com.synapse.app.domain.models.HistoryItem
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getHistory(): Flow<List<HistoryItem>>
    suspend fun getHistoryItem(id: Long): HistoryItem?
    suspend fun saveHistoryItem(item: HistoryItem): Long
    suspend fun deleteHistoryItem(id: Long)
}
