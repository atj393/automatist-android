package com.synapse.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workflow_templates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                isEnabled INTEGER NOT NULL,
                triggerJson TEXT NOT NULL,
                actionsJson TEXT NOT NULL,
                globalInstruction TEXT NOT NULL,
                outputConfigJson TEXT NOT NULL,
                notifyOnCompletion INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL,
                lastRunAtMillis INTEGER,
                lastRunStatus TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workflow_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                templateId INTEGER NOT NULL,
                templateName TEXT NOT NULL,
                triggerType TEXT NOT NULL,
                status TEXT NOT NULL,
                currentStage TEXT NOT NULL,
                outputText TEXT NOT NULL,
                providerType TEXT,
                promptTokens INTEGER,
                completionTokens INTEGER,
                totalTokens INTEGER,
                durationMs INTEGER,
                errorMessage TEXT,
                startedAtMillis INTEGER NOT NULL,
                completedAtMillis INTEGER,
                FOREIGN KEY (templateId) REFERENCES workflow_templates(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_workflow_runs_templateId ON workflow_runs(templateId)"
        )
    }
}
