package com.synapse.app.feature.article;

import com.synapse.app.domain.providers.ArticleTransformProvider;
import com.synapse.app.domain.repositories.HistoryRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class ArticleViewModel_Factory implements Factory<ArticleViewModel> {
  private final Provider<ArticleTransformProvider> transformProvider;

  private final Provider<HistoryRepository> historyRepositoryProvider;

  public ArticleViewModel_Factory(Provider<ArticleTransformProvider> transformProvider,
      Provider<HistoryRepository> historyRepositoryProvider) {
    this.transformProvider = transformProvider;
    this.historyRepositoryProvider = historyRepositoryProvider;
  }

  @Override
  public ArticleViewModel get() {
    return newInstance(transformProvider.get(), historyRepositoryProvider.get());
  }

  public static ArticleViewModel_Factory create(
      Provider<ArticleTransformProvider> transformProvider,
      Provider<HistoryRepository> historyRepositoryProvider) {
    return new ArticleViewModel_Factory(transformProvider, historyRepositoryProvider);
  }

  public static ArticleViewModel newInstance(ArticleTransformProvider transformProvider,
      HistoryRepository historyRepository) {
    return new ArticleViewModel(transformProvider, historyRepository);
  }
}
