package com.automatist.app.di

import com.automatist.app.BuildConfig
import com.automatist.app.data.providers.openai.OpenAIApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the OkHttpClient used by [ModelDownloadManager][com.automatist.app.data.offline.ModelDownloadManager]
 * to download large model files.
 *
 * This client differs from the default API client:
 * - No body-level logging (Level.BODY buffers the entire response into memory before streaming,
 *   which causes OOM or indefinite stall on a 500+ MB file download)
 * - Extended read/write timeouts for large file transfers over mobile networks
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FileDownloadClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * OkHttpClient for large file downloads (model files).
     *
     * Key differences from the default API client:
     * - HEADERS-only logging: avoids buffering the entire response body into memory.
     *   Level.BODY on the default client causes it to consume the full InputStream before
     *   the caller's read loop ever executes, stalling progress at 0 bytes.
     * - 5-minute read timeout: mobile networks can stall briefly mid-transfer.
     * - 30-second connect timeout: CDN redirects may add latency.
     */
    @Provides
    @Singleton
    @FileDownloadClient
    fun provideFileDownloadClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenAIApi(retrofit: Retrofit): OpenAIApi {
        return retrofit.create(OpenAIApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAnthropicApi(okHttpClient: OkHttpClient): com.automatist.app.data.providers.anthropic.AnthropicApi {
        return Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.automatist.app.data.providers.anthropic.AnthropicApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeminiApi(okHttpClient: OkHttpClient): com.automatist.app.data.providers.gemini.GeminiApi {
        return Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.automatist.app.data.providers.gemini.GeminiApi::class.java)
    }
}
