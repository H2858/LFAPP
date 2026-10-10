package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class LivingPhoneAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("LivingPhone_Alarm", "Late night alarm received: ${intent.action}")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LivingPhone:AlarmWakeLock"
        )

        try {
            wakeLock?.acquire(8000L) // Safe 8s wake lock while evaluating audio clip

            ReactionController.getInstance(context).onLateNightCheck()
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
