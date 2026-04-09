package com.synapse.app.di

import android.content.Context
import androidx.room.Room
import com.synapse.app.data.local.HistoryDao
import com.synapse.app.data.local.MIGRATION_1_2
import com.synapse.app.data.local.MIGRATION_2_3
import com.synapse.app.data.local.MIGRATION_3_4
import com.synapse.app.data.local.MIGRATION_4_5
import com.synapse.app.data.local.MIGRATION_5_6
import com.synapse.app.data.local.MIGRATION_6_7
import com.synapse.app.data.local.MIGRATION_7_8
import com.synapse.app.data.local.MIGRATION_8_9
import com.synapse.app.data.local.SynapseDatabase
import com.synapse.app.data.local.WorkflowDao
import com.synapse.app.data.repositories.RoomHistoryRepository
import com.synapse.app.data.repositories.RoomWorkflowRepository
import com.synapse.app.domain.repositories.HistoryRepository
import com.synapse.app.domain.repositories.WorkflowRepository
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
    fun provideDatabase(@ApplicationContext context: Context): SynapseDatabase {
        return Room.databaseBuilder(
            context,
            SynapseDatabase::class.java,
            "synapse.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: SynapseDatabase): HistoryDao {
        return database.historyDao
    }

    @Provides
    @Singleton
    fun provideWorkflowDao(database: SynapseDatabase): WorkflowDao {
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
