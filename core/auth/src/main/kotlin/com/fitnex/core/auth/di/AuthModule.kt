package com.fitnex.core.auth.di

import com.fitnex.core.auth.AuthRepository
import com.fitnex.core.auth.firebase.FirebaseAuthRepository
import com.fitnex.core.network.AuthTokenProvider
import com.google.firebase.auth.FirebaseAuth
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun providesFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance().apply {
        // Firebase e-mails (verification, password reset) are sent in the app's language.
        setLanguageCode("es")
    }

    /** The backend client attaches the Firebase ID token; the server verifies it (spec §8, §11). */
    @Provides
    fun providesAuthTokenProvider(repository: AuthRepository): AuthTokenProvider =
        AuthTokenProvider { repository.idToken() }
}

@Module
@InstallIn(SingletonComponent::class)
interface AuthBindings {
    @Binds
    fun bindsAuthRepository(impl: FirebaseAuthRepository): AuthRepository
}
