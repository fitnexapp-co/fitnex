package com.fitnex.app

import com.fitnex.core.auth.AuthRepository
import com.fitnex.core.auth.di.AuthBindings
import com.fitnex.core.testing.FakeAuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** UI tests never reach the real Firebase project: identity is simulated in memory. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AuthBindings::class])
object FakeAuthModule {
    @Provides
    @Singleton
    fun providesFakeAuth(): FakeAuthRepository = FakeAuthRepository()

    @Provides
    fun providesAuthRepository(fake: FakeAuthRepository): AuthRepository = fake
}
