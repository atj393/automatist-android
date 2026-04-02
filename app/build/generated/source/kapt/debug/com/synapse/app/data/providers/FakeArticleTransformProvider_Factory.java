package com.synapse.app.data.providers;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class FakeArticleTransformProvider_Factory implements Factory<FakeArticleTransformProvider> {
  @Override
  public FakeArticleTransformProvider get() {
    return newInstance();
  }

  public static FakeArticleTransformProvider_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static FakeArticleTransformProvider newInstance() {
    return new FakeArticleTransformProvider();
  }

  private static final class InstanceHolder {
    private static final FakeArticleTransformProvider_Factory INSTANCE = new FakeArticleTransformProvider_Factory();
  }
}
