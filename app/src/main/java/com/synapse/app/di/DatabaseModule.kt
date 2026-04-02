package com.synapse.app.di

import android.content.Context
import androidx.room.Room
import com.synapse.app.data.local.HistoryDao
import com.synapse.app.data.local.SynapseDatabase
import com.synapse.app.data.repositories.RoomHistoryRepository
import com.synapse.app.domain.repositories.HistoryRepository
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
        ).build()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: SynapseDatabase): HistoryDao {
        return database.historyDao
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
}
