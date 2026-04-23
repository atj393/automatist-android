package com.automatist.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Template-level auto-retry opt-in (defaults OFF for existing workflows).
        db.execSQL("ALTER TABLE workflow_templates ADD COLUMN autoRetryEnabled INTEGER NOT NULL DEFAULT 0")
        // Per-run attempt tracking. 0 = initial / manual retry, 1..3 = auto-retries.
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN autoRetryAttempt INTEGER NOT NULL DEFAULT 0")
        // Parent points at the initial run in an auto-retry chain so history
        // can group retries; nullable for initial runs and manual retries.
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN parentRunId INTEGER")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN resumeSnapshotJson TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE provider_profiles ADD COLUMN providerPresetId TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE provider_profiles ADD COLUMN customBaseUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE provider_profiles ADD COLUMN customApiKeyId TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE provider_profiles ADD COLUMN isFallback INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN synthesisInput TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN versionsJson TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN stagesJson TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN isSocialOutput INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN outputFormat TEXT NOT NULL DEFAULT 'MARKDOWN'")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN errorDetail TEXT")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN profileName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workflow_runs ADD COLUMN modelId TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_templates ADD COLUMN notifyOnStart INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS provider_profiles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                providerType TEXT NOT NULL,
                modelId TEXT NOT NULL,
                isDefault INTEGER NOT NULL,
                isEnabled INTEGER NOT NULL,
                createdAtMillis INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("ALTER TABLE workflow_templates ADD COLUMN defaultProfileId TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workflow_templates ADD COLUMN sourceTemplateId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workflow_templates ADD COLUMN category TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workflow_templates ADD COLUMN customizationJson TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS saved_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAtMillis INTEGER NOT NULL,
                updatedAtMillis INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

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
