package com.shadow.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * MOD-74.16: always-on wake-word foreground service.
 *
 * The service listens only after the user enables the wake word. It releases
 * the microphone while the foreground conversation is active and restarts after
 * the conversation returns to idle.
 *
 * Two wake-word entries are supported by the same service contract:
 * "Hey Jarvis" via jarvis_v1.onnx and "Hey Shadow" via hey_shadow.onnx.
 * The Shadow classifier asset is pinned from a public reference project and
 * still requires physical-device phrase validation before claiming production accuracy.
 */
class ShadowWakeWordService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var engine: WakeWordEngine? = null
    private var paused = false
    private var lastTriggerAt = 0L
    private var cooling = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannels()
        try {
            val type = if (Build.VERSION.SDK_INT >= 29)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
            startForeground(NOTIFICATION_ID, ongoingNotification())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to enter microphone foreground service", t)
            stopSelf()
            return
        }
        startEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE) pauseEngine()
        if (intent?.action == ACTION_RESUME) resumeEngine()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startEngine() {
        if (engine != null || paused) return

        runCatching {
            val models = listOf(
                WakeWordModel("jarvis", "jarvis_v1.onnx", MODEL_THRESHOLD),
                WakeWordModel("shadow", "hey_shadow.onnx", MODEL_THRESHOLD)
            )
            val created = WakeWordEngine(
                this,
                models,
                DetectionMode.SINGLE_BEST,
                ENGINE_COOLDOWN_MS,
                engineScope
            )
            engine = created

            serviceScope.launch {
                created.scores.collect { score ->
                    onScore(score.score)
                }
            }

            created.start()
            Log.i(TAG, "Wake engine started")
        }.onFailure {
            Log.e(TAG, "Wake engine failed to start", it)
            stopSelf()
        }
    }

    private fun onScore(score: Float) {
        val now = SystemClock.elapsedRealtime()
        if (score < TRIGGER_THRESHOLD) return
        if (now - lastTriggerAt < REFRACTORY_MS) return
        if (cooling || paused) return

        lastTriggerAt = now
        cooling = true
        pauseEngine()

        val launch = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(EXTRA_WAKE_TRIGGERED, true)
        }

        // A normal startActivity is used when the app is already visible/unlocked.
        // The notification remains the fallback path for locked/background launches.
        try {
            startActivity(launch)
        } catch (t: Throwable) {
            notifyWake(launch)
        }

        serviceScope.launch {
            kotlinx.coroutines.delay(COOLING_MS)
            cooling = false
        }
    }

    private fun notifyWake(launch: Intent) {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(this, 100, launch, flags)
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_WAKE)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SHADOW")
            .setContentText("Wake word detected")
            .setPriority(Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build() else Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SHADOW")
            .setContentText("Wake word detected")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_WAKE_ID, n)
        serviceScope.launch {
            kotlinx.coroutines.delay(2200)
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_WAKE_ID)
        }
    }

    private fun ongoingNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SHADOW")
            .setContentText("Wake word is active")
            .setOngoing(true)
            .setPriority(Notification.PRIORITY_LOW)
            .setContentIntent(open)
            .build() else Notification.Builder(this)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("SHADOW")
            .setContentText("Wake word is active")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ONGOING,
                    "SHADOW Wake Word",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_WAKE,
                    "SHADOW Wake Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    }

    private fun pauseEngine() {
        if (paused) return
        paused = true
        runCatching { engine?.stop() }
        Log.d(TAG, "Wake engine paused")
    }

    private fun resumeEngine() {
        if (!paused) return
        paused = false
        runCatching { engine?.start() }
        Log.d(TAG, "Wake engine resumed")
    }

    override fun onDestroy() {
        instance = null
        runCatching { engine?.stop() }
        runCatching { engine?.release() }
        engine = null
        serviceScope.cancel()
        engineScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ShadowWakeWord"
        private const val CHANNEL_ONGOING = "shadow_wake_ongoing"
        private const val CHANNEL_WAKE = "shadow_wake_alert"
        private const val NOTIFICATION_ID = 7401
        private const val NOTIFICATION_WAKE_ID = 7402

        private const val MODEL_THRESHOLD = 0.95f
        private const val TRIGGER_THRESHOLD = 0.30f
        private const val REFRACTORY_MS = 2500L
        private const val ENGINE_COOLDOWN_MS = 1500L
        private const val COOLING_MS = 4000L

        const val ACTION_STOP = "com.shadow.mobile.wake.STOP"
        const val ACTION_PAUSE = "com.shadow.mobile.wake.PAUSE"
        const val ACTION_RESUME = "com.shadow.mobile.wake.RESUME"
        const val EXTRA_WAKE_TRIGGERED = "shadow_wake_triggered"

        @Volatile private var instance: ShadowWakeWordService? = null

        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, ShadowWakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            context.stopService(Intent(context, ShadowWakeWordService::class.java))
        }

        @JvmStatic
        fun pauseListening() {
            instance?.pauseEngine()
        }

        @JvmStatic
        fun resumeListening() {
            instance?.resumeEngine()
        }
    }
}
