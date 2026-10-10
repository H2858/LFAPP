package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class LivingPhonePowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("LivingPhone_Power", "Power/Battery broadcast received: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LivingPhone:PowerWakeLock"
        )

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                wakeLock?.acquire(5000L)
                withTimeout(4500L) {
                    val controller = ReactionController.getInstance(context)

                    when (action) {
                        Intent.ACTION_POWER_CONNECTED -> {
                            controller.onExplicitPowerTransition(true)
                        }
                        Intent.ACTION_POWER_DISCONNECTED -> {
                            controller.onExplicitPowerTransition(false)
                        }
                        Intent.ACTION_BATTERY_LOW -> {
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
                }
            } catch (e: Exception) {
                Log.e("LivingPhone_Power", "Error handling power broadcast: ${e.message}")
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
