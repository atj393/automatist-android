package com.automatist.app.di

import com.automatist.app.data.providers.FakeArticleTransformProvider
import com.automatist.app.domain.providers.ArticleTransformProvider
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
        router: com.automatist.app.data.providers.TransformProviderRouter
    ): ArticleTransformProvider
}
