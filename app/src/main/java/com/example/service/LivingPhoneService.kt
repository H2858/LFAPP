package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.math.sqrt

class LivingPhoneService : Service(), SensorEventListener {
    private val tag = "LivingPhone_Service"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var controller: ReactionController
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var accelerometer: Sensor? = null

    // Ambient light state
    private var lastLux: Float = -1f
    private var wasDark = false
    private var darkEnterTimestamp = 0L

    // Motion state
    private var lastMotionCheckTime = 0L
    private var motionActivityCounter = 0

    // Dynamic battery receiver
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = (level * 100) / scale
                    controller.onBatteryChanged(pct)
                }

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                controller.onChargingChanged(isCharging)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "living_phone_ambient_channel"
        const val NOTIFICATION_ID = 1001

        const val EXTRA_COMMAND = "extra_command"
        const val COMMAND_START = "command_start"
        const val COMMAND_STOP = "command_stop"

        fun start(context: Context) {
            val intent = Intent(context, LivingPhoneService::class.java).apply {
                putExtra(EXTRA_COMMAND, COMMAND_START)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LivingPhoneService::class.java).apply {
                putExtra(EXTRA_COMMAND, COMMAND_STOP)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "LivingPhoneService onCreate")
        controller = ReactionController.getInstance(this)

        createNotificationChannel()
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Register dynamic battery updates
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)

        // Initialize hardware sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Schedule late night alarm checks
        AlarmScheduler.scheduleNextLateNightAlarm(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.getStringExtra(EXTRA_COMMAND) ?: COMMAND_START
        if (command == COMMAND_STOP) {
            Log.i(tag, "Stopping LivingPhoneService per command")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "LivingPhoneService onDestroy")
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}

        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}

        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // SensorEventListener
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0]
                val now = System.currentTimeMillis()

                if (lux < 2.5f) {
                    // Entered darkness (e.g. pocket or bag)
                    if (!wasDark) {
                        wasDark = true
                        darkEnterTimestamp = now
                        // Pocket dark reaction
                        controller.attemptReaction(AudioCategory.POCKET_DARK, "Entered Dark/Pocket")
                    }
                } else if (lux > 35.0f) {
                    // Transitioned to bright light from darkness
                    if (wasDark && (now - darkEnterTimestamp > 3_000L)) {
                        wasDark = false
                        // Pocket light reaction
                        controller.attemptReaction(AudioCategory.POCKET_LIGHT, "Exited Pocket to Light")
                    } else {
                        wasDark = false
                    }
                }
                lastLux = lux
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gForce = sqrt((x * x + y * y + z * z).toDouble()).toFloat() / SensorManager.GRAVITY_EARTH

                val now = System.currentTimeMillis()
                if (gForce > 1.4f || gForce < 0.6f) {
                    motionActivityCounter++
                }

                // Periodic check every 15 seconds
                if (now - lastMotionCheckTime > 15_000L) {
                    lastMotionCheckTime = now
                    if (motionActivityCounter > 18) {
                        // High sustained movement -> Exercise
                        controller.attemptReaction(AudioCategory.EXERCISE, "Sustained Physical Movement")
                    } else if (motionActivityCounter > 4) {
                        // Light movement -> Motion
                        controller.attemptReaction(AudioCategory.MOTION, "Device Motion")
                    }
                    motionActivityCounter = 0
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.living_phone_icon)
            .setContentTitle(getString(R.string.notification_service_active))
            .setContentText(getString(R.string.notification_service_monitoring))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }
}
