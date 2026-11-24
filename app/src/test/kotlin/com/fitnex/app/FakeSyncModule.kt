package com.fitnex.app

import com.fitnex.core.data.di.SyncModule
import com.fitnex.core.data.sync.CloudSync
import com.fitnex.core.testing.FakeCloudSync
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** UI tests never reach the real backend: the cloud copy is simulated in memory. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SyncModule::class])
object FakeSyncModule {
    @Provides
    @Singleton
    fun providesFakeCloudSync(): FakeCloudSync = FakeCloudSync()

    @Provides
    fun providesCloudSync(fake: FakeCloudSync): CloudSync = fake
}
