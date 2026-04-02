package com.synapse.app.di

import com.synapse.app.data.providers.FakeArticleTransformProvider
import com.synapse.app.domain.providers.ArticleTransformProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    @Binds
    abstract fun bindArticleTransformProvider(
        fakeProvider: FakeArticleTransformProvider
    ): ArticleTransformProvider
}
