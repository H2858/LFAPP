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
import kotlinx.coroutines.withTimeout

class LivingPhoneBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("LivingPhone_Boot", "System broadcast received: $action. Restoring Living Phone state...")

        // Always schedule next late night alarm immediately and synchronously
        AlarmScheduler.scheduleNextLateNightAlarm(context)

        // Use goAsync() to keep receiver alive during async DataStore check
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8000L) {
                    val repository = PreferencesRepository(context)
                    val settings = repository.settingsFlow.first()

                    if (settings.isLivingPhoneEnabled) {
                        // Confirm alarms are registered based on active user settings
                        AlarmScheduler.scheduleNextLateNightAlarm(context)

                        // Attempt foreground service start following system lifecycle rules
                        val serviceIntent = Intent(context, LivingPhoneService::class.java).apply {
                            putExtra(LivingPhoneService.EXTRA_COMMAND, LivingPhoneService.COMMAND_START)
                        }

                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            Log.d("LivingPhone_Boot", "Ambient foreground service started successfully")
                        } catch (e: Exception) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                                Log.w(
                                    "LivingPhone_Boot",
                                    "Foreground service start restricted by Android background execution policy at boot: ${e.message}. Alarms remain fully active."
                                )
                            } else {
                                Log.e("LivingPhone_Boot", "Failed to start ambient service: ${e.message}")
                            }
                        }
                    } else {
                        // If living phone is paused by user, cancel any pending alarms
                        AlarmScheduler.cancelAlarms(context)
                    }
                }
            } catch (e: Exception) {
                Log.e("LivingPhone_Boot", "Error during boot receiver async execution: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
