package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log

class LivingPhonePowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("LivingPhone_Power", "Power/Battery broadcast received: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LivingPhone:PowerWakeLock"
        )

        try {
            wakeLock?.acquire(6000L)
            val controller = ReactionController.getInstance(context)

            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    controller.onChargingChanged(true)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    controller.onChargingChanged(false)
                }
                Intent.ACTION_BATTERY_LOW -> {
                    // Check actual percentage if available via sticky battery intent
                    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 15
                    controller.onBatteryChanged(pct)
                }
                Intent.ACTION_BATTERY_OKAY -> {
                    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    if (level >= 0 && scale > 0) {
                        val pct = (level * 100) / scale
                        controller.onBatteryChanged(pct)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("LivingPhone_Power", "Error handling broadcast: ${e.message}")
        } finally {
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock.release()
                } catch (_: Exception) {}
            }
        }
    }
}
