package dev.lookup.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.lookup.MainActivity
import dev.lookup.R
import dev.lookup.data.SettingsRepository
import dev.lookup.detection.DetectionEngine
import dev.lookup.detection.EngineSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foreground service that hosts the [DetectionEngine] and the overlay warning
 * bar. All sensor delivery happens on the main thread, which is also where the
 * engine's state flows are consumed — no cross-thread coordination needed.
 *
 * Permission robustness: the overlay permission is re-checked periodically and
 * whenever the window fails. If the user revokes "display over other apps" while
 * the service runs, we publish [DetectionBus.overlayPermissionLost] and stop.
 */
class OverlayService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "walking_watch"
        private const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "dev.lookup.action.STOP"
        const val ACTION_RESET_CALIBRATION = "dev.lookup.action.RESET_CALIBRATION"
        private const val OVERLAY_CHECK_INTERVAL_TICKS = 50

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private lateinit var engine: DetectionEngine
    private lateinit var sensorManager: SensorManager
    private lateinit var windowManager: WindowManager
    private var accelerometer: Sensor? = null
    private var proximity: Sensor? = null
    private var overlayView: OverlayBarView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var overlayCheckCounter = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = DetectionEngine(SettingsRepository.settings.value)
        DetectionBus.running.value = true
        DetectionBus.overlayPermissionLost.value = false

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximity = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        startAsForeground()

        // Guard against stale restarts (e.g. sticky restart after an explicit
        // disable): an explicit stop persists systemEnabled=false, so the
        // service must never come back on its own after that.
        if (!SettingsRepository.systemEnabled) {
            stopSelf()
            return
        }

        val accel = accelerometer
        if (accel == null) {
            // Without an accelerometer there is nothing to fuse; degrade quietly.
            stopSelf()
            return
        }

        scope.launch {
            SettingsRepository.settings.collect { engine.onSettingsChanged(it) }
        }

        addOverlayIfPossible()

        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        proximity?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                engine.onScreenStateChanged(intent.action == Intent.ACTION_SCREEN_ON)
            }
        }.also { receiver ->
            registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
            )
        }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        engine.onScreenStateChanged(powerManager.isInteractive)

        scope.launch {
            engine.snapshot.collect { snapshot ->
                DetectionBus.snapshot.value = snapshot
                onEngineSnapshot(snapshot)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // An explicit stop is the user saying "off" — persist it so
                // neither the sticky restart nor the boot receiver brings the
                // service back.
                SettingsRepository.systemEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESET_CALIBRATION -> engine.resetCalibration()
        }
        if (!Settings.canDrawOverlays(this)) {
            onOverlayPermissionLost()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        overlayView?.let { view -> runCatching { windowManager.removeView(view) } }
        overlayView = null
        overlayParams = null
        DetectionBus.running.value = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER ->
                engine.onAccelerometer(
                    event.values[0],
                    event.values[1],
                    event.values[2],
                    event.timestamp,
                )
            Sensor.TYPE_PROXIMITY ->
                engine.onProximityChanged(event.values[0] < event.sensor.maximumRange * 0.5f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startAsForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lookup)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun addOverlayIfPossible() {
        if (!Settings.canDrawOverlays(this)) {
            onOverlayPermissionLost()
            return
        }
        val view = OverlayBarView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            OverlayBarView.heightFor(resources.displayMetrics.density, 0f),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        overlayView = view
        overlayParams = params
        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            overlayView = null
            overlayParams = null
            onOverlayPermissionLost()
        }
    }

    private fun onEngineSnapshot(snapshot: EngineSnapshot) {
        val view = overlayView ?: return
        val params = overlayParams ?: return
        if (++overlayCheckCounter >= OVERLAY_CHECK_INTERVAL_TICKS) {
            overlayCheckCounter = 0
            if (!Settings.canDrawOverlays(this)) {
                onOverlayPermissionLost()
                return
            }
        }
        view.setConfidence(snapshot.confidence)
        val targetHeight = OverlayBarView.heightFor(resources.displayMetrics.density, snapshot.confidence)
        if (abs(targetHeight - params.height) > 1) {
            params.height = targetHeight
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                onOverlayPermissionLost()
            }
        }
    }

    private fun onOverlayPermissionLost() {
        DetectionBus.overlayPermissionLost.value = true
        stopSelf()
    }
}
