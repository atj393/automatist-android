package com.synapse.app.di

import com.synapse.app.data.access.BillingProductAccessRepository
import com.synapse.app.domain.access.ProductAccessRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessModule {

    @Binds
    @Singleton
    abstract fun bindProductAccessRepository(
        impl: BillingProductAccessRepository
    ): ProductAccessRepository
}
