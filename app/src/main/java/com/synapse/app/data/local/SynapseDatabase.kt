package com.synapse.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class SynapseDatabase : RoomDatabase() {
    abstract val historyDao: HistoryDao
}
