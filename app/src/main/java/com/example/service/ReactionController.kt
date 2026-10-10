package com.example.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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

    // State deduplication and threshold crossings
    private var lastBatteryLevel: Int = -1
    private var isBattery15Triggered = false
    private var isBattery100Triggered = false

    private var lastChargingConnected: Boolean? = null
    private var lastChargingStateTime: Long = 0L

    private val _latestReaction = MutableStateFlow<ReactionFeedback?>(null)
    val latestReaction: StateFlow<ReactionFeedback?> = _latestReaction.asStateFlow()

    private val _isReacting = MutableStateFlow(false)
    val isReacting: StateFlow<Boolean> = _isReacting.asStateFlow()

    init {
        initializeBatteryAndPowerState()
    }

    /**
     * Initializes initial physical state from the system's sticky battery broadcast
     * so that app startup does NOT fabricate false charging or battery reactions.
     */
    private fun initializeBatteryAndPowerState() {
        try {
            val stickyIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (stickyIntent != null) {
                val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                lastChargingConnected = isCharging
                lastChargingStateTime = System.currentTimeMillis()

                val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val pct = (level * 100) / scale
                    lastBatteryLevel = pct
                    // Seed threshold state without triggering reactions
                    if (pct <= 15) isBattery15Triggered = true
                    if (pct >= 100) isBattery100Triggered = true
                }
                Log.d(tag, "Initial power state baseline set: charging=$isCharging, level=$lastBatteryLevel%")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to read initial sticky battery intent: ${e.message}")
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
                Log.d(tag, "Reaction suppressed: Global cooldown active ($remainingSec s remaining). Reason: $reason")
                return@launch
            }

            // 2. Check Category Cooldown
            val categoryCooldown = settings.frequency.categoryCooldownMillis
            val lastCatTime = lastCategoryTriggerTimes[category.id] ?: 0L
            if (now - lastCatTime < categoryCooldown) {
                val remainingSec = (categoryCooldown - (now - lastCatTime)) / 1000
                Log.d(tag, "Reaction suppressed: Category cooldown for ${category.id} ($remainingSec s remaining). Reason: $reason")
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
     * Enforces strict directionality and hysteresis to prevent duplicate triggers.
     */
    fun onBatteryChanged(currentLevel: Int) {
        if (currentLevel < 0) return

        val prevLevel = lastBatteryLevel
        lastBatteryLevel = currentLevel

        if (prevLevel == -1) {
            // Baseline initialization on first reading
            if (currentLevel <= 15) isBattery15Triggered = true
            if (currentLevel >= 100) isBattery100Triggered = true
            return
        }

        // Low Battery 15% Transition: only when dipping from > 15 to <= 15
        if (prevLevel > 15 && currentLevel <= 15) {
            if (!isBattery15Triggered) {
                isBattery15Triggered = true
                Log.i(tag, "Battery reached 15% threshold ($prevLevel% -> $currentLevel%)")
                attemptReaction(AudioCategory.BATTERY_15, "Battery 15%")
            }
        } else if (currentLevel > 18) {
            // Reset trigger when recharged past 18% (hysteresis)
            isBattery15Triggered = false
        }

        // Full Battery 100% Transition: only when crossing into 100% from < 100%
        if (prevLevel < 100 && currentLevel >= 100) {
            if (!isBattery100Triggered) {
                isBattery100Triggered = true
                Log.i(tag, "Battery reached 100% full threshold ($prevLevel% -> $currentLevel%)")
                attemptReaction(AudioCategory.BATTERY_100, "Battery 100%")
            }
        } else if (currentLevel < 98) {
            // Reset trigger when discharged below 98%
            isBattery100Triggered = false
        }
    }

    /**
     * Handles charging connected / disconnected state transitions.
     * Prevents false triggers on initialization or repeated broadcasts.
     */
    fun onChargingChanged(isConnected: Boolean) {
        val now = System.currentTimeMillis()

        // 1. If uninitialized, establish baseline state without fabricating an event
        if (lastChargingConnected == null) {
            lastChargingConnected = isConnected
            lastChargingStateTime = now
            Log.d(tag, "Initial charging baseline established: isConnected=$isConnected (no audio)")
            return
        }

        // 2. If state hasn't genuinely changed, ignore broadcast snapshot
        if (lastChargingConnected == isConnected) {
            return
        }

        // 3. Debounce rapid physical connection jitter (at least 2.5s between transitions)
        if (now - lastChargingStateTime < 2500L) {
            Log.d(tag, "Charging state jitter ignored (${now - lastChargingStateTime}ms)")
            return
        }

        lastChargingConnected = isConnected
        lastChargingStateTime = now

        if (isConnected) {
            Log.i(tag, "Genuine Charger Connected transition detected")
            attemptReaction(AudioCategory.CHARGING_CONNECT, "Power Connected")
        } else {
            Log.i(tag, "Genuine Charger Disconnected transition detected")
            attemptReaction(AudioCategory.CHARGING_DISCONNECT, "Power Disconnected")
        }
    }

    /**
     * Evaluates late night reactions (Midnight, 1, 2, 3, 4 AM).
     * Compares scheduled hour with actual clock time to reject delayed/stale alarms deterministically.
     * Persists triggered calendar dates so reactions do not repeat within the same calendar day.
     */
    fun onLateNightCheck(scheduledHour: Int? = null) {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        // Target hour determination
        val targetHour = scheduledHour ?: currentHour

        // Deterministic delay check: if alarm fired more than 20 minutes after target hour started, skip it
        if (scheduledHour != null) {
            if (currentHour != scheduledHour || currentMinute > 20) {
                Log.w(tag, "Late night alarm for hour $scheduledHour arrived late (now $currentHour:$currentMinute). Skipping stale clip.")
                return
            }
        }

        val targetCategory = when (targetHour) {
            0 -> AudioCategory.LATE_NIGHT_MIDNIGHT
            1 -> AudioCategory.LATE_NIGHT_1AM
            2 -> AudioCategory.LATE_NIGHT_2AM
            3 -> AudioCategory.LATE_NIGHT_3AM
            4 -> AudioCategory.LATE_NIGHT_4AM
            else -> null
        }

        if (targetCategory == null) return

        scope.launch {
            // Check persistent store to guarantee no duplicate trigger today across process restarts
            val lastRecordedDate = preferencesRepository.getLastLateNightDate(targetHour)
            if (lastRecordedDate == todayStr) {
                Log.d(tag, "Late night hour $targetHour already triggered today ($todayStr). Suppressing duplicate.")
                return@launch
            }

            val settings = preferencesRepository.settingsFlow.first()
            if (!settings.isLivingPhoneEnabled || !settings.isVoiceEnabled) return@launch

            preferencesRepository.recordLateNightHourTriggered(targetHour, todayStr)
            executeReactionInternal(targetCategory, settings)
        }
    }

    /**
     * Triggered when the device is unlocked and active interaction resumes.
     * Evaluates USAGE category with standard cooldown rules.
     */
    fun onUserPresent() {
        attemptReaction(AudioCategory.USAGE, "Device Unlocked / Active Usage")
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

    companion object {
        @Volatile
        private var instance: ReactionController? = null

        fun getInstance(context: Context): ReactionController {
            return instance ?: synchronized(this) {
                instance ?: ReactionController(context.applicationContext).also { instance = it }
            }
        }
    }
}
