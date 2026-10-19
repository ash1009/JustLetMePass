package app.jlpt.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.jlpt.JlptApp
import app.jlpt.R
import app.jlpt.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the audio playlist going with the screen off. Android needs a "Now playing" control
 * while it plays; it has play/pause and stop, and goes away when the audio stops.
 */
class PlaylistService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as JlptApp
        when (intent?.action) {
            ACTION_PAUSE -> app.speech.pause()
            ACTION_RESUME -> app.speech.resume(KEY)
            ACTION_STOP -> { app.speech.stop(); finish(); return START_NOT_STICKY }
        }
        if (!started) {
            started = true
            channel(this)
            ServiceCompat.startForeground(
                this, ID, notification(app.speech.now.value, app),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
            wake = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jlpt:playlist").apply { acquire(3 * 60 * 60 * 1000L) }
            scope.launch {
                app.speech.now.collectLatest { n ->
                    if (n == null || n.key != KEY || n.done) { finish(); return@collectLatest }
                    getSystemService(NotificationManager::class.java).notify(ID, notification(n, app))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        runCatching { wake?.release() }
        wake = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { wake?.release() }
        super.onDestroy()
    }

    private fun action(action: String): PendingIntent =
        PendingIntent.getService(this, action.hashCode(), Intent(this, PlaylistService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun notification(n: Speech.Playing?, app: JlptApp) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_study)
        .setContentTitle(if (n?.playing == false) "Paused" else "Japanese audio")
        .setContentText(n?.let { app.speech.partAt(it.index)?.second?.let(Furigana::plain) } ?: "")
        .setOngoing(n?.playing == true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(PendingIntent.getActivity(this, 2,
            Intent(this, MainActivity::class.java).putExtra("route", "listen").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .addAction(
            0,
            if (n?.playing == false) "Play" else "Pause",
            action(if (n?.playing == false) ACTION_RESUME else ACTION_PAUSE),
        )
        .addAction(0, "Stop", action(ACTION_STOP))
        .build()

    companion object {
        const val KEY = "playlist"
        private const val ID = 21
        private const val CHANNEL = "playback"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_STOP = "stop"

        private fun channel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Now playing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Play and pause for the Japanese audio playlist, only while it plays"
                    setShowBadge(false)
                },
            )
        }

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PlaylistService::class.java))
        }
    }
}
