package com.automatist.app.di

import com.automatist.app.platform.security.KeystoreSecureStorage
import com.automatist.app.platform.security.SecureStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecureStorage(
        keystoreSecureStorage: KeystoreSecureStorage
    ): SecureStorage
}
