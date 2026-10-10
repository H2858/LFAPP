package com.example.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LivingPhoneBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("LivingPhone_Boot", "System broadcast received: $action. Restoring state...")

        val repository = PreferencesRepository(context)
        CoroutineScope(Dispatchers.IO).launch {
            val settings = repository.settingsFlow.first()
            if (settings.isLivingPhoneEnabled) {
                // Restore scheduled alarms immediately
                AlarmScheduler.scheduleNextLateNightAlarm(context)

                // Start ambient service if permitted by system
                val serviceIntent = Intent(context, LivingPhoneService::class.java).apply {
                    putExtra(LivingPhoneService.EXTRA_COMMAND, LivingPhoneService.COMMAND_START)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                        Log.w("LivingPhone_Boot", "Foreground service start restricted by system at this moment: ${e.message}")
                    } else {
                        Log.e("LivingPhone_Boot", "Failed to start service: ${e.message}")
                    }
                }
            }
        }
    }
}
