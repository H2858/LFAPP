package com.example.service

import android.content.Context
import android.util.Log
import com.example.audio.AudioCatalog
import com.example.audio.AudioCategory
import com.example.audio.AudioEngine
import com.example.audio.AudioPlayResult
import com.example.data.LivingSettings
import com.example.data.PreferencesRepository
import com.example.data.ReactionFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ReactionFeedback(
    val category: AudioCategory,
    val clipPath: String?,
    val result: AudioPlayResult,
    val timestamp: Long = System.currentTimeMillis()
)

class ReactionController private constructor(private val context: Context) {
    private val tag = "LivingPhone_Reaction"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val preferencesRepository = PreferencesRepository(context)
    val audioEngine = AudioEngine(context)

    // Cooldown tracking in memory (fast checks)
    private var lastGlobalTriggerTime: Long = 0L
    private val lastCategoryTriggerTimes = ConcurrentHashMap<String, Long>()

    // Late night triggered days: key = hour (0..4), value = date string "yyyyMMdd"
    private val lateNightTriggeredDays = ConcurrentHashMap<Int, String>()

    // State deduplication
    private var lastBatteryLevel: Int = -1
    private var isBattery15Triggered = false
    private var isBattery100Triggered = false
    private var lastChargingConnected: Boolean? = null
    private var lastChargingStateTime: Long = 0L

    private val _latestReaction = MutableStateFlow<ReactionFeedback?>(null)
    val latestReaction: StateFlow<ReactionFeedback?> = _latestReaction.asStateFlow()

    private val _isReacting = MutableStateFlow(false)
    val isReacting: StateFlow<Boolean> = _isReacting.asStateFlow()

    companion object {
        @Volatile
        private var instance: ReactionController? = null

        fun getInstance(context: Context): ReactionController {
            return instance ?: synchronized(this) {
                instance ?: ReactionController(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Attempts to trigger a reaction for a given category.
     * Evaluates master switch, voice switch, cooldowns, and deduplication.
     */
    fun attemptReaction(category: AudioCategory, reason: String = ""): Boolean {
        var triggered = false
        scope.launch {
            val settings = preferencesRepository.settingsFlow.first()
            if (!settings.isLivingPhoneEnabled) {
                Log.d(tag, "Reaction suppressed: Living Phone is Paused.")
                return@launch
            }

            if (!settings.isVoiceEnabled) {
                Log.d(tag, "Voice reactions are disabled by user.")
                return@launch
            }

            val now = System.currentTimeMillis()

            // 1. Check Global Cooldown
            val globalCooldown = settings.frequency.globalCooldownMillis
            if (now - lastGlobalTriggerTime < globalCooldown) {
                val remainingSec = (globalCooldown - (now - lastGlobalTriggerTime)) / 1000
                Log.d(tag, "Reaction suppressed: Global cooldown active ($remainingSec s remaining).")
                return@launch
            }

            // 2. Check Category Cooldown
            val categoryCooldown = settings.frequency.categoryCooldownMillis
            val lastCatTime = lastCategoryTriggerTimes[category.id] ?: 0L
            if (now - lastCatTime < categoryCooldown) {
                val remainingSec = (categoryCooldown - (now - lastCatTime)) / 1000
                Log.d(tag, "Reaction suppressed: Category cooldown for ${category.id} ($remainingSec s remaining).")
                return@launch
            }

            // Execute Reaction
            executeReactionInternal(category, settings)
            triggered = true
        }
        return triggered
    }

    /**
     * Handles battery level updates and triggers transitions (15% and 100%).
     */
    fun onBatteryChanged(currentLevel: Int) {
        if (currentLevel < 0) return

        val prevLevel = lastBatteryLevel
        lastBatteryLevel = currentLevel

        if (prevLevel == -1) {
            // First reading after start: initialize threshold flags
            if (currentLevel <= 15) isBattery15Triggered = true
            if (currentLevel >= 100) isBattery100Triggered = true
            return
        }

        // Low Battery 15% Transition: only when dipping to or below 15% from above
        if (prevLevel > 15 && currentLevel <= 15) {
            if (!isBattery15Triggered) {
                isBattery15Triggered = true
                Log.i(tag, "Battery reached 15% threshold ($currentLevel%)")
                attemptReaction(AudioCategory.BATTERY_15, "Battery 15%")
            }
        } else if (currentLevel > 18) {
            // Reset trigger when recharged past 18% (hysteresis)
            isBattery15Triggered = false
        }

        // Full Battery 100% Transition: only when reaching 100%
        if (prevLevel < 100 && currentLevel >= 100) {
            if (!isBattery100Triggered) {
                isBattery100Triggered = true
                Log.i(tag, "Battery reached 100% full threshold ($currentLevel%)")
                attemptReaction(AudioCategory.BATTERY_100, "Battery 100%")
            }
        } else if (currentLevel < 98) {
            // Reset trigger when discharged below 98%
            isBattery100Triggered = false
        }
    }

    /**
     * Handles charging connected / disconnected state transitions with debounce.
     */
    fun onChargingChanged(isConnected: Boolean) {
        val now = System.currentTimeMillis()
        if (lastChargingConnected == isConnected && (now - lastChargingStateTime < 10_000L)) {
            // Suppress duplicate or jitter within 10s
            return
        }

        lastChargingConnected = isConnected
        lastChargingStateTime = now

        if (isConnected) {
            Log.i(tag, "Charger connected")
            attemptReaction(AudioCategory.CHARGING_CONNECT, "Power Connected")
        } else {
            Log.i(tag, "Charger disconnected")
            attemptReaction(AudioCategory.CHARGING_DISCONNECT, "Power Disconnected")
        }
    }

    /**
     * Evaluates late night reactions (Midnight, 1, 2, 3, 4 AM).
     * Must not repeat during the same hour on the same day.
     */
    fun onLateNightCheck() {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        val targetCategory = when (currentHour) {
            0 -> AudioCategory.LATE_NIGHT_MIDNIGHT
            1 -> AudioCategory.LATE_NIGHT_1AM
            2 -> AudioCategory.LATE_NIGHT_2AM
            3 -> AudioCategory.LATE_NIGHT_3AM
            4 -> AudioCategory.LATE_NIGHT_4AM
            else -> null
        }

        if (targetCategory == null) return

        // Verify has not already triggered today for this hour
        val lastTriggeredDay = lateNightTriggeredDays[currentHour]
        if (lastTriggeredDay == todayStr) {
            Log.d(tag, "Late night hour $currentHour already triggered today ($todayStr). Suppressing.")
            return
        }

        scope.launch {
            val settings = preferencesRepository.settingsFlow.first()
            if (!settings.isLivingPhoneEnabled || !settings.isVoiceEnabled) return@launch

            lateNightTriggeredDays[currentHour] = todayStr
            executeReactionInternal(targetCategory, settings)
        }
    }

    /**
     * Test trigger invoked directly by user in Settings.
     * Bypasses cooldowns and master switch so user can test their uploaded audio files.
     */
    fun testReaction(category: AudioCategory, onResult: (AudioPlayResult) -> Unit) {
        scope.launch {
            _isReacting.value = true
            val playResult = audioEngine.playCategory(category)
            val clipPath = if (playResult is AudioPlayResult.Success) playResult.assetPath else null

            _latestReaction.value = ReactionFeedback(
                category = category,
                clipPath = clipPath,
                result = playResult
            )
            onResult(playResult)
            kotlinx.coroutines.delay(2000L)
            _isReacting.value = false
        }
    }

    private suspend fun executeReactionInternal(category: AudioCategory, settings: LivingSettings) {
        val now = System.currentTimeMillis()
        lastGlobalTriggerTime = now
        lastCategoryTriggerTimes[category.id] = now

        _isReacting.value = true

        val playResult = audioEngine.playCategory(category)
        val clipPath = if (playResult is AudioPlayResult.Success) playResult.assetPath else null

        if (clipPath != null) {
            preferencesRepository.recordReaction(category.id, clipPath)
        }

        _latestReaction.value = ReactionFeedback(
            category = category,
            clipPath = clipPath,
            result = playResult,
            timestamp = now
        )

        Log.i(tag, "Reaction executed for ${category.displayName}. Result: $playResult")
        kotlinx.coroutines.delay(3500L)
        _isReacting.value = false
    }
}
