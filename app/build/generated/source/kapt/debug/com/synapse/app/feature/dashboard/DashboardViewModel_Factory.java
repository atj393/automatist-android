package com.synapse.app.feature.dashboard;

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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<HistoryRepository> historyRepositoryProvider;

  public DashboardViewModel_Factory(Provider<HistoryRepository> historyRepositoryProvider) {
    this.historyRepositoryProvider = historyRepositoryProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(historyRepositoryProvider.get());
  }

  public static DashboardViewModel_Factory create(
      Provider<HistoryRepository> historyRepositoryProvider) {
    return new DashboardViewModel_Factory(historyRepositoryProvider);
  }

  public static DashboardViewModel newInstance(HistoryRepository historyRepository) {
    return new DashboardViewModel(historyRepository);
  }
}
