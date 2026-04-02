package com.synapse.app.feature.history;

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
public final class HistoryDetailViewModel_Factory implements Factory<HistoryDetailViewModel> {
  private final Provider<HistoryRepository> repositoryProvider;

  public HistoryDetailViewModel_Factory(Provider<HistoryRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public HistoryDetailViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static HistoryDetailViewModel_Factory create(
      Provider<HistoryRepository> repositoryProvider) {
    return new HistoryDetailViewModel_Factory(repositoryProvider);
  }

  public static HistoryDetailViewModel newInstance(HistoryRepository repository) {
    return new HistoryDetailViewModel(repository);
  }
}
