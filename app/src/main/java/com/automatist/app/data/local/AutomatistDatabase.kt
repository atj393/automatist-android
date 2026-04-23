package com.automatist.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryEntity::class,
        WorkflowTemplateEntity::class,
        WorkflowRunEntity::class,
        SavedNoteEntity::class,
        ProviderProfileEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AutomatistDatabase : RoomDatabase() {
    abstract val historyDao: HistoryDao
    abstract val workflowDao: WorkflowDao
}
