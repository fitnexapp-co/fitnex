package com.fitnex.app

import com.fitnex.feature.workout.live.RestAlerts
import com.fitnex.feature.workout.live.RestAlertsModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Instant
import javax.inject.Singleton

/** Records rest-timer side effects instead of scheduling alarms or playing sounds. */
class FakeRestAlerts : RestAlerts {
    val scheduled = mutableListOf<Pair<Instant, String>>()
    var cancels = 0
    override fun schedule(endsAt: Instant, exerciseName: String, now: Instant) { scheduled += endsAt to exerciseName }
    override fun cancel() { cancels++ }
    override fun playEnd(sound: Boolean) = Unit
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RestAlertsModule::class])
object FakeRestAlertsModule {
    @Provides
    @Singleton
    fun providesFake(): FakeRestAlerts = FakeRestAlerts()

    @Provides
    fun providesRestAlerts(fake: FakeRestAlerts): RestAlerts = fake
}
