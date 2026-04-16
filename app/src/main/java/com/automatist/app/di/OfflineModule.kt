package com.automatist.app.di

import com.automatist.app.data.offline.DataStoreOfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OfflineModule {

    @Binds
    @Singleton
    abstract fun bindOfflineModelRepository(
        impl: DataStoreOfflineModelRepository
    ): OfflineModelRepository
}
