package com.automatist.app.di

import com.automatist.app.data.offline.DataStoreOfflineModelRepository
import com.automatist.app.data.offline.OfflineModelRegistry
import com.automatist.app.domain.offline.OfflineModelRepository
import com.automatist.app.domain.offline.OfflineModelResolver
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

    /**
     * Exposes the registry as the domain-level resolver so domain consumers (e.g. the
     * readiness system) can resolve built-in *and* custom models without depending on
     * the data layer. Resolves to the same singleton as the concrete [OfflineModelRegistry].
     */
    @Binds
    @Singleton
    abstract fun bindOfflineModelResolver(
        impl: OfflineModelRegistry
    ): OfflineModelResolver
}
