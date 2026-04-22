package com.automatist.app.di

import com.automatist.app.data.local.SeedingStateStore
import com.automatist.app.data.local.SettingsRepository
import com.automatist.app.platform.onboarding.AssetSeededWorkflowJsonSource
import com.automatist.app.platform.onboarding.SeededWorkflowJsonSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OnboardingModule {

    @Binds
    @Singleton
    abstract fun bindSeedingStateStore(impl: SettingsRepository): SeedingStateStore

    @Binds
    @Singleton
    abstract fun bindSeededWorkflowJsonSource(
        impl: AssetSeededWorkflowJsonSource
    ): SeededWorkflowJsonSource
}
