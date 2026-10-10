package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.LivingSettings
import com.example.data.PreferencesRepository
import com.example.data.ReactionFrequency
import com.example.service.AlarmScheduler
import com.example.service.LivingPhoneService
import com.example.service.ReactionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = PreferencesRepository(application)
    val reactionController = ReactionController.getInstance(application)

    val settings: StateFlow<LivingSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LivingSettings()
    )

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    init {
        // Sync service and alarms on startup
        viewModelScope.launch {
            settings.collect { currentSettings ->
                if (currentSettings.isLivingPhoneEnabled) {
                    LivingPhoneService.start(getApplication())
                    AlarmScheduler.scheduleNextLateNightAlarm(getApplication())
                } else {
                    LivingPhoneService.stop(getApplication())
                    AlarmScheduler.cancelAlarms(getApplication())
                }
            }
        }
    }

    fun setLivingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setLivingEnabled(enabled)
            if (enabled) {
                LivingPhoneService.start(getApplication())
                AlarmScheduler.scheduleNextLateNightAlarm(getApplication())
            } else {
                LivingPhoneService.stop(getApplication())
                AlarmScheduler.cancelAlarms(getApplication())
            }
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setVoiceEnabled(enabled)
        }
    }

    fun setFrequency(frequency: ReactionFrequency) {
        viewModelScope.launch {
            repository.setFrequency(frequency)
        }
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }
}
