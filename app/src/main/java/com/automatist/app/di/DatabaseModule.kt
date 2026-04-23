package com.automatist.app.di

import android.content.Context
import androidx.room.Room
import com.automatist.app.data.local.HistoryDao
import com.automatist.app.data.local.MIGRATION_1_2
import com.automatist.app.data.local.MIGRATION_2_3
import com.automatist.app.data.local.MIGRATION_3_4
import com.automatist.app.data.local.MIGRATION_4_5
import com.automatist.app.data.local.MIGRATION_5_6
import com.automatist.app.data.local.MIGRATION_6_7
import com.automatist.app.data.local.MIGRATION_7_8
import com.automatist.app.data.local.MIGRATION_8_9
import com.automatist.app.data.local.MIGRATION_9_10
import com.automatist.app.data.local.MIGRATION_10_11
import com.automatist.app.data.local.MIGRATION_11_12
import com.automatist.app.data.local.MIGRATION_12_13
import com.automatist.app.data.local.MIGRATION_13_14
import com.automatist.app.data.local.MIGRATION_14_15
import com.automatist.app.data.local.MIGRATION_15_16
import com.automatist.app.data.local.MIGRATION_16_17
import com.automatist.app.data.local.AutomatistDatabase
import com.automatist.app.data.local.WorkflowDao
import com.automatist.app.data.repositories.RoomHistoryRepository
import com.automatist.app.data.repositories.RoomWorkflowRepository
import com.automatist.app.domain.repositories.HistoryRepository
import com.automatist.app.domain.repositories.WorkflowRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutomatistDatabase {
        return Room.databaseBuilder(
            context,
            AutomatistDatabase::class.java,
            "automatist.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
            .build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: AutomatistDatabase): HistoryDao {
        return database.historyDao
    }

    @Provides
    @Singleton
    fun provideWorkflowDao(database: AutomatistDatabase): WorkflowDao {
        return database.workflowDao
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        roomRepository: RoomHistoryRepository
    ): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindWorkflowRepository(
        roomRepository: RoomWorkflowRepository
    ): WorkflowRepository
}
