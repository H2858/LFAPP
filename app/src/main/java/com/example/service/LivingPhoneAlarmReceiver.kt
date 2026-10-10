package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
                // Hold wake lock for the duration of critical late-night evaluation and rescheduling
                wakeLock?.acquire(15000L)

                val targetHour = if (scheduledHour in 0..4) scheduledHour else null
                val controller = ReactionController.getInstance(context)

                // 1. Process the current alarm with bounded timeout.
                // Wrap in dedicated try/catch so failure or timeout cannot prevent rescheduling.
                try {
                    withTimeout(10000L) {
                        controller.processLateNightAlarm(targetHour)
                    }
                } catch (e: Exception) {
                    Log.e("LivingPhone_Alarm", "Error or timeout during late-night alarm processing: ${e.message}")
                }

                // 2. Guarantee rescheduling of future alarms if feature remains enabled.
                // Respects user enabled/paused settings so unnecessary alarms are not scheduled when paused.
                try {
                    val repository = PreferencesRepository(context)
                    val settings = repository.settingsFlow.first()
                    if (settings.isLivingPhoneEnabled) {
                        AlarmScheduler.scheduleNextLateNightAlarm(context)
                        Log.d("LivingPhone_Alarm", "Next late-night alarm rescheduled successfully.")
                    } else {
                        Log.d("LivingPhone_Alarm", "Living Phone is paused. Canceling late-night alarms.")
                        AlarmScheduler.cancelAlarms(context)
                    }
                } catch (e: Exception) {
                    Log.e("LivingPhone_Alarm", "Error reading settings during alarm rescheduling: ${e.message}")
                    // Safe fallback: ensure future schedule is not lost on transient settings read error
                    AlarmScheduler.scheduleNextLateNightAlarm(context)
                }
            } catch (e: Exception) {
                Log.e("LivingPhone_Alarm", "Unexpected error in alarm receiver execution: ${e.message}")
            } finally {
                if (wakeLock?.isHeld == true) {
                    try {
                        wakeLock.release()
                    } catch (_: Exception) {}
                }
                try {
                    pendingResult.finish()
                } catch (_: Exception) {}
            }
        }
    }
}
