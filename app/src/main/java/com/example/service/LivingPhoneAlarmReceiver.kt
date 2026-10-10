package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class LivingPhoneAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledHour = intent.getIntExtra(AlarmScheduler.EXTRA_SCHEDULED_HOUR, -1)
        Log.i("LivingPhone_Alarm", "Late night alarm received for scheduled hour: $scheduledHour")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LivingPhone:AlarmWakeLock"
        )

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Hold wake lock for the duration of critical late-night evaluation and playback
                wakeLock?.acquire(15000L)

                withTimeout(12000L) {
                    val targetHour = if (scheduledHour in 0..4) scheduledHour else null
                    val controller = ReactionController.getInstance(context)

                    // Synchronously processes alarm: stale check, deduplication, DataStore persistence & audio playback
                    controller.processLateNightAlarm(targetHour)

                    // Reschedule the next late-night alarm
                    AlarmScheduler.scheduleNextLateNightAlarm(context)
                }
            } catch (e: Exception) {
                Log.e("LivingPhone_Alarm", "Error handling late night alarm async execution: ${e.message}")
            } finally {
                if (wakeLock?.isHeld == true) {
                    try {
                        wakeLock.release()
                    } catch (_: Exception) {}
                }
                pendingResult.finish()
            }
        }
    }
}
