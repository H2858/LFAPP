package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val ALARM_REQUEST_CODE = 9021

    /**
     * Schedules the next exact alarm for one of the target late-night hours:
     * 00:00 (Midnight), 01:00, 02:00, 03:00, or 04:00 AM.
     */
    fun scheduleNextLateNightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val nextTimeMillis = calculateNextLateNightTimeMillis()

        val intent = Intent(context, LivingPhoneAlarmReceiver::class.java).apply {
            action = "com.example.livingphone.ALARM_LATE_NIGHT"
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTimeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        nextTimeMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTimeMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Next late night alarm scheduled for: ${java.util.Date(nextTimeMillis)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule late night alarm: ${e.message}")
        }
    }

    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, LivingPhoneAlarmReceiver::class.java).apply {
            action = "com.example.livingphone.ALARM_LATE_NIGHT"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun calculateNextLateNightTimeMillis(): Long {
        val now = Calendar.getInstance()
        val targetHours = listOf(0, 1, 2, 3, 4)

        // Find the earliest upcoming hour today or tomorrow
        for (hour in targetHours) {
            val candidate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis > now.timeInMillis + 10_000L) { // at least 10s in future
                return candidate.timeInMillis
            }
        }

        // None left today, schedule midnight tomorrow
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
