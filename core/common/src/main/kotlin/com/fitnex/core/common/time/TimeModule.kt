package com.fitnex.core.common.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Stable, globally unique ids generated on-device so offline records never collide when syncing. */
fun interface IdGenerator {
    fun newId(): String
}

class UuidIdGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    @Provides
    @Singleton
    fun providesClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun providesIdGenerator(impl: UuidIdGenerator): IdGenerator = impl
}
