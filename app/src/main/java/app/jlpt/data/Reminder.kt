package app.jlpt.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.jlpt.JlptApp
import app.jlpt.R
import app.jlpt.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The evening nudge to study, once a day at the time set in Settings. */
object Reminder {
    const val CHANNEL = "reminder"

    fun channel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A nudge in the evening if you haven't studied yet"
        })
    }

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context, prefs: Prefs) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context))
        if (!prefs.reminderOn.value) return
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(prefs.reminderHour.value, prefs.reminderMinute.value)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // An inexact alarm needs no special permission; it may come a few minutes late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pending(context))
    }

    private val lines = listOf(
        "Your kanji are waiting. They're very patient, but they're not getting any younger.",
        "December is coming. So is the JLPT. Quick session?",
        "勉強しましょう! (That means: let's study. You knew that. Probably.)",
        "Just Let me Pass This, says your future self. A few minutes now helps.",
        "Your streak is looking at you with big sad eyes.",
        "Ten minutes of Japanese, then you can go back to doing nothing. Deal?",
        "がんばって! The exam paper on the icon is begging you.",
        "Your reviews are piling up like dishes in a sink.",
        "A little study tonight, a little less panic in December.",
    )

    fun show(context: Context, reviews: Int, minutesLeft: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val line = lines[(LocalDate.now().dayOfYear) % lines.size]
        val detail = buildString {
            if (reviews > 0) append("$reviews reviews due. ")
            if (minutesLeft > 0) append("$minutesLeft minutes to today's goal.")
        }.trim()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_study)
            .setContentTitle("Time for Japanese")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (detail.isEmpty()) line else "$line\n$detail"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as JlptApp
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Reminder.schedule(context, app.prefs)
                val today = LocalDate.now().toString()
                if (app.prefs.lastReminderDay != today) {
                    app.prefs.lastReminderDay = today
                    app.study.load()
                    val log = app.db.dao().day(today)
                    val minutes = (log?.seconds ?: 0) / 60
                    val left = app.prefs.dailyMinutes.value - minutes
                    // Only nudge if today's goal is not met yet.
                    if (left > 0) Reminder.show(context, app.study.dueIds().size, left)
                }
            } finally {
                done.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as JlptApp
        Reminder.schedule(context, app.prefs)
    }
}
