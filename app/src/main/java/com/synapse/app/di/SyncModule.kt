package com.synapse.app.di

import com.synapse.app.data.sync.LocalCloudSyncRepository
import com.synapse.app.domain.sync.CloudSyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindCloudSyncRepository(
        impl: LocalCloudSyncRepository
    ): CloudSyncRepository
}
