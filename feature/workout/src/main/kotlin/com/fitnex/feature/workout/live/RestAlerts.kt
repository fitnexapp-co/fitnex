package com.fitnex.feature.workout.live

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitnex.feature.workout.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Side effects of the rest timer, behind an interface so tests never touch alarms or audio. */
interface RestAlerts {
    /** Schedules a notification for when the rest ends (shown only if the app is not visible). */
    fun schedule(endsAt: Instant, exerciseName: String, now: Instant)
    fun cancel()
    /** Rest ended while the app is on screen: optional beep + short vibration. */
    fun playEnd(sound: Boolean)
}

@Singleton
class AndroidRestAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
) : RestAlerts {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(endsAt: Instant, exerciseName: String, now: Instant) {
        if (!canNotify(context)) return
        val delay = Duration.between(now, endsAt).toMillis().coerceAtLeast(0)
        val at = SystemClock.elapsedRealtime() + delay
        val pending = pendingIntent(exerciseName)
        // No SCHEDULE_EXACT_ALARM permission is requested (Play policy reserves it for clock apps):
        // use an exact alarm only when the system already allows it, otherwise the inexact one.
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exactAllowed) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
    }

    override fun cancel() {
        alarms.cancel(pendingIntent(""))
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    override fun playEnd(sound: Boolean) {
        if (sound) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                // Release the native audio resource once the beep has played.
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 600)
            }
        }
        runCatching {
            context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun pendingIntent(exerciseName: String): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, RestTimerReceiver::class.java).putExtra(EXTRA_EXERCISE, exerciseName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_ID = "rest_timer"
        const val NOTIFICATION_ID = 4101
        private const val REQUEST_CODE = 4100
        const val EXTRA_EXERCISE = "exercise"

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

/** Posts the "rest finished" notification; tapping it brings FITNEX back to the live workout. */
class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AndroidRestAlerts.canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                AndroidRestAlerts.CHANNEL_ID,
                context.getString(R.string.live_rest_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        }
        val exercise = intent.getStringExtra(AndroidRestAlerts.EXTRA_EXERCISE).orEmpty()
        val notification = NotificationCompat.Builder(context, AndroidRestAlerts.CHANNEL_ID)
            .setSmallIcon(com.fitnex.core.designsystem.R.drawable.ic_fitnex_mark)
            .setContentTitle(context.getString(R.string.live_rest_done_title))
            .setContentText(if (exercise.isBlank()) context.getString(R.string.live_rest_done_body_generic) else context.getString(R.string.live_rest_done_body, exercise))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        manager.notify(AndroidRestAlerts.NOTIFICATION_ID, notification)
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface RestAlertsModule {
    @Binds
    fun bindsRestAlerts(impl: AndroidRestAlerts): RestAlerts
}
