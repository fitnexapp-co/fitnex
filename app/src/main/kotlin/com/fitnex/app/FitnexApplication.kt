package com.fitnex.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fitnex.app.reminders.TrainingReminderWorker
import com.fitnex.app.widget.FitnexWidget
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.stats.GlobalStatisticsRepository
import com.fitnex.core.data.sync.CloudSync
import com.fitnex.core.datastore.UserPreferencesDataSource
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltAndroidApp
class FitnexApplication : Application(), Configuration.Provider {
    @Inject lateinit var cloudSync: CloudSync
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var preferences: UserPreferencesDataSource
    @Inject lateinit var session: SessionRepository
    @Inject lateinit var stats: GlobalStatisticsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Sync runs only while the app is alive: every change is made in-app, and anything pending
        // is pushed the next time it opens with a connection.
        cloudSync.start()
        TrainingReminderWorker.ensureChannel(this)
        scope.launch {
            // Keep the daily reminder in step with the user's setting.
            preferences.preferences.map { it.reminderHour }.distinctUntilChanged().collect { TrainingReminderWorker.schedule(this@FitnexApplication, it) }
        }
        scope.launch {
            // The widget follows finished workouts (and sign-in / sign-out).
            session.currentUserId.flatMapLatest { uid -> uid?.let { stats.observeCompletedSessions(it).map { s -> s.size } } ?: emptyFlow() }
                .debounce(1_000)
                .collect { runCatching { FitnexWidget.refresh(this@FitnexApplication) } }
        }
    }
}
