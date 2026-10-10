package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class LivingPhoneAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduledHour = intent.getIntExtra(AlarmScheduler.EXTRA_SCHEDULED_HOUR, -1)
        Log.i("LivingPhone_Alarm", "Late night alarm received for scheduled hour: $scheduledHour")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LivingPhone:AlarmWakeLock"
        )

        try {
            wakeLock?.acquire(6000L) // Safe 6s wake lock while evaluating audio clip

            val targetHour = if (scheduledHour in 0..4) scheduledHour else null
            ReactionController.getInstance(context).onLateNightCheck(targetHour)
            AlarmScheduler.scheduleNextLateNightAlarm(context)
        } catch (e: Exception) {
            Log.e("LivingPhone_Alarm", "Error handling alarm: ${e.message}")
        } finally {
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock.release()
                } catch (_: Exception) {}
            }
        }
    }
}
