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
public final class HistoryViewModel_Factory implements Factory<HistoryViewModel> {
  private final Provider<HistoryRepository> historyRepositoryProvider;

  public HistoryViewModel_Factory(Provider<HistoryRepository> historyRepositoryProvider) {
    this.historyRepositoryProvider = historyRepositoryProvider;
  }

  @Override
  public HistoryViewModel get() {
    return newInstance(historyRepositoryProvider.get());
  }

  public static HistoryViewModel_Factory create(
      Provider<HistoryRepository> historyRepositoryProvider) {
    return new HistoryViewModel_Factory(historyRepositoryProvider);
  }

  public static HistoryViewModel newInstance(HistoryRepository historyRepository) {
    return new HistoryViewModel(historyRepository);
  }
}
