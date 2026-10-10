package com.example.service

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
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("LivingPhone_Boot", "Boot completed or app updated. Restoring Living Phone...")

            val repository = PreferencesRepository(context)
            CoroutineScope(Dispatchers.IO).launch {
                val settings = repository.settingsFlow.first()
                if (settings.isLivingPhoneEnabled) {
                    AlarmScheduler.scheduleNextLateNightAlarm(context)

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
                        Log.e("LivingPhone_Boot", "Failed to start service after boot: ${e.message}")
                    }
                }
            }
        }
    }
}
