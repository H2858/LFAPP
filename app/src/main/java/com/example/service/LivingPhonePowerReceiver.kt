package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class LivingPhonePowerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("LivingPhone_Power", "Power/Battery broadcast caught in static receiver: $action")

        try {
            // Ensure LivingPhoneService is running to handle the event reliably
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serviceIntent = Intent(context, LivingPhoneService::class.java).apply {
                    putExtra(LivingPhoneService.EXTRA_COMMAND, LivingPhoneService.COMMAND_START)
                }
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(Intent(context, LivingPhoneService::class.java))
            }

            // Forward the event directly to ReactionController via application context safely
            val controller = ReactionController.getInstance(context)
            when (action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    controller.onExplicitPowerTransition(true)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    controller.onExplicitPowerTransition(false)
                }
            }
        } catch (e: Exception) {
            Log.e("LivingPhone_Power", "Failed to handle power broadcast in receiver: ${e.message}")
        }
    }
}
