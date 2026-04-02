package com.synapse.app.data.repositories;

import com.synapse.app.data.local.HistoryDao;
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
public final class RoomHistoryRepository_Factory implements Factory<RoomHistoryRepository> {
  private final Provider<HistoryDao> daoProvider;

  public RoomHistoryRepository_Factory(Provider<HistoryDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public RoomHistoryRepository get() {
    return newInstance(daoProvider.get());
  }

  public static RoomHistoryRepository_Factory create(Provider<HistoryDao> daoProvider) {
    return new RoomHistoryRepository_Factory(daoProvider);
  }

  public static RoomHistoryRepository newInstance(HistoryDao dao) {
    return new RoomHistoryRepository(dao);
  }
}
