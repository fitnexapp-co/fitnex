package com.fitnex.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fitnex.app.MainActivity
import com.fitnex.app.R
import com.fitnex.app.widget.FitnexWidget
import com.fitnex.core.data.session.SessionRepository
import com.fitnex.core.data.today.TodayPlanRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Daily training reminder. Fires once a day at the user's hour and notifies ONLY when today has a
 * planned routine (calendar or routine weekdays), it is not a rest day and nothing was done yet.
 * Also refreshes the home-screen widget.
 */
@HiltWorker
class TrainingReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val session: SessionRepository,
    private val today: TodayPlanRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { FitnexWidget.refresh(applicationContext) }
        val uid = session.currentUserId.first() ?: return Result.success()
        val plan = today.get(uid)
        val routine = plan.routineName
        if (routine == null || plan.doneToday || plan.restDay) return Result.success()
        notify(applicationContext, routine)
        return Result.success()
    }

    companion object {
        const val CHANNEL = "training_reminders"
        private const val WORK = "daily_training_reminder"
        private const val NOTIFICATION_ID = 4100

        /** Schedules (or cancels, with null) the daily reminder at [hour]:00 local time. */
        fun schedule(context: Context, hour: Int?) {
            val wm = WorkManager.getInstance(context)
            if (hour == null) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val now = LocalDateTime.now()
            var next = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<TrainingReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm?.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }

        private fun notify(context: Context, routineName: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            ensureChannel(context)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(com.fitnex.core.designsystem.R.drawable.ic_fitnex_mark)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(context.getString(R.string.reminder_body, routineName))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
