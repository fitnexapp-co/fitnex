package com.fitnex.core.network.di

import com.fitnex.core.network.AuthInterceptor
import com.fitnex.core.network.AuthTokenProvider
import com.fitnex.core.network.BuildConfig
import com.fitnex.core.network.ConnectivityNetworkMonitor
import com.fitnex.core.network.FitnexBackendApi
import com.fitnex.core.network.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun providesOkHttp(tokenProvider: AuthTokenProvider): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // Multimodal analysis can take a while; the backend enforces its own deadline.
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(tokenProvider))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC // never log bodies (health data)
                            redactHeader("Authorization")
                        },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    fun providesBackendApi(client: OkHttpClient, json: Json): FitnexBackendApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FitnexBackendApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
interface NetworkBindings {
    @Binds
    fun bindsNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
}
