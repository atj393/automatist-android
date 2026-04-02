package com.synapse.app.di;

@dagger.Module()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0018\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\b\'\u0018\u00002\u00020\u0001B\u0005\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006H\'\u00a8\u0006\u0007"}, d2 = {"Lcom/synapse/app/di/ProviderModule;", "", "()V", "bindArticleTransformProvider", "Lcom/synapse/app/domain/providers/ArticleTransformProvider;", "fakeProvider", "Lcom/synapse/app/data/providers/FakeArticleTransformProvider;", "app_debug"})
@dagger.hilt.InstallIn(value = {dagger.hilt.components.SingletonComponent.class})
public abstract class ProviderModule {
    
    public ProviderModule() {
        super();
    }
    
    @dagger.Binds()
    @org.jetbrains.annotations.NotNull()
    public abstract com.synapse.app.domain.providers.ArticleTransformProvider bindArticleTransformProvider(@org.jetbrains.annotations.NotNull()
    com.synapse.app.data.providers.FakeArticleTransformProvider fakeProvider);
}