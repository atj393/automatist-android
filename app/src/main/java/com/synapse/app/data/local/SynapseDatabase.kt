package com.synapse.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        WorkflowTemplateEntity::class,
        WorkflowRunEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SynapseDatabase : RoomDatabase() {
    abstract val historyDao: HistoryDao
    abstract val workflowDao: WorkflowDao
}
