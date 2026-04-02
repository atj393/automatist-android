package com.synapse.app.di

import com.synapse.app.data.providers.FakeArticleTransformProvider
import com.synapse.app.domain.providers.ArticleTransformProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    @Binds
    @Singleton
    abstract fun bindArticleTransformProvider(
        router: com.synapse.app.data.providers.TransformProviderRouter
    ): ArticleTransformProvider
}
