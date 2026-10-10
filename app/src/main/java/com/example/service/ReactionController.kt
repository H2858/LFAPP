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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // Cached settings kept in sync with DataStore
    @Volatile
    private var cachedSettings = LivingSettings()

    // Cooldown tracking in memory (fast checks)
    private var lastGlobalTriggerTime: Long = 0L
    private val lastCategoryTriggerTimes = ConcurrentHashMap<String, Long>()

    // State deduplication and threshold crossings
    private var lastBatteryLevel: Int = -1
    private var isBattery15Triggered = false
    private var isBattery100Triggered = false

    // Charging state tracking
    private var lastKnownChargingConnected: Boolean? = null
    private var lastExplicitPowerState: Boolean? = null
    private var lastExplicitPowerTimestamp: Long = 0L

    // Late-night memory cache to prevent duplicate triggers if receiver runs multiple times
    private val lateNightProcessedToday = ConcurrentHashMap<String, Boolean>()
    private val lateNightMutex = Mutex()

    private val _latestReaction = MutableStateFlow<ReactionFeedback?>(null)
    val latestReaction: StateFlow<ReactionFeedback?> = _latestReaction.asStateFlow()

    private val _isReacting = MutableStateFlow(false)
    val isReacting: StateFlow<Boolean> = _isReacting.asStateFlow()

    init {
        scope.launch {
            preferencesRepository.settingsFlow.collect { settings ->
                cachedSettings = settings
            }
        }
        initializeBatteryAndPowerBaseline()
    }

    /**
     * Initializes physical state baseline from sticky battery broadcast.
     * This establishes the baseline for passive monitoring without fabricating events.
     */
    private fun initializeBatteryAndPowerBaseline() {
        try {
            val stickyIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (stickyIntent != null) {
                seedInitialState(stickyIntent)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to read initial sticky battery intent: ${e.message}")
        }
    }

    /**
     * Seeds initial battery and charging state baseline without triggering audio reactions.
     */
    fun seedInitialState(batteryIntent: Intent) {
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        lastKnownChargingConnected = isCharging

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            val pct = (level * 100) / scale
            lastBatteryLevel = pct
            // Seed threshold state without triggering reactions
            if (pct <= 15) isBattery15Triggered = true
            if (pct >= 100) isBattery100Triggered = true
        }
        Log.d(tag, "Initial power baseline seeded: charging=$isCharging, level=$lastBatteryLevel%")
    }

    /**
     * Explicit system power-transition event from ACTION_POWER_CONNECTED or ACTION_POWER_DISCONNECTED.
     * Unlike background snapshots, this represents a genuine hardware cable transition.
     * It is never swallowed by initial sticky state seeding during process cold starts.
     */
    fun onExplicitPowerTransition(isConnected: Boolean): Boolean {
        val now = System.currentTimeMillis()

        // Deduplicate rapid repeat broadcasts (e.g., within 3 seconds) for the same state
        if (lastExplicitPowerState == isConnected && (now - lastExplicitPowerTimestamp < 3000L)) {
            Log.d(tag, "Explicit power event duplicate suppressed (within 3s): isConnected=$isConnected")
            return false
        }

        lastExplicitPowerState = isConnected
        lastExplicitPowerTimestamp = now
        lastKnownChargingConnected = isConnected

        return if (isConnected) {
            Log.i(tag, "Genuine Charger Connected explicit transition")
            attemptReaction(AudioCategory.CHARGING_CONNECT, "Power Cable Connected")
        } else {
            Log.i(tag, "Genuine Charger Disconnected explicit transition")
            attemptReaction(AudioCategory.CHARGING_DISCONNECT, "Power Cable Disconnected")
        }
    }

    /**
     * Passive charging state changes observed from ACTION_BATTERY_CHANGED in running service.
     * Prevents duplicate reactions if an explicit power transition was already processed recently.
     */
    fun onChargingChanged(isConnected: Boolean) {
        val now = System.currentTimeMillis()

        // 1. If uninitialized, establish baseline state without fabricating an event
        if (lastKnownChargingConnected == null) {
            lastKnownChargingConnected = isConnected
            Log.d(tag, "Charging baseline initialized from snapshot: isConnected=$isConnected (no audio)")
            return
        }

        // 2. If state hasn't changed, ignore snapshot
        if (lastKnownChargingConnected == isConnected) {
            return
        }

        // 3. If an explicit power event for this state was handled within the last 4 seconds, suppress duplicate
        if (lastExplicitPowerState == isConnected && (now - lastExplicitPowerTimestamp < 4000L)) {
            lastKnownChargingConnected = isConnected
            return
        }

        // 4. Genuine state change observed via battery broadcast
        lastKnownChargingConnected = isConnected
        lastExplicitPowerState = isConnected
        lastExplicitPowerTimestamp = now

        if (isConnected) {
            Log.i(tag, "Charger Connected observed via battery status")
            attemptReaction(AudioCategory.CHARGING_CONNECT, "Power Status Connected")
        } else {
            Log.i(tag, "Charger Disconnected observed via battery status")
            attemptReaction(AudioCategory.CHARGING_DISCONNECT, "Power Status Disconnected")
        }
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
     * Asynchronously and reliably processes a late-night hourly alarm.
     * Validates delay constraints, checks DataStore persistence, and prevents duplicate playback.
     * Returns true if a clip was played, false otherwise.
     */
    suspend fun processLateNightAlarm(scheduledHour: Int?): Boolean = lateNightMutex.withLock {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val todayStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

        val targetHour = scheduledHour ?: currentHour

        // Deterministic delay check: if alarm fired more than 20 minutes after target hour started, skip it
        if (scheduledHour != null) {
            if (currentHour != scheduledHour || currentMinute > 20) {
                Log.w(tag, "Late night alarm for hour $scheduledHour arrived late (now $currentHour:$currentMinute). Skipping stale clip.")
                return false
            }
        }

        val targetCategory = when (targetHour) {
            0 -> AudioCategory.LATE_NIGHT_MIDNIGHT
            1 -> AudioCategory.LATE_NIGHT_1AM
            2 -> AudioCategory.LATE_NIGHT_2AM
            3 -> AudioCategory.LATE_NIGHT_3AM
            4 -> AudioCategory.LATE_NIGHT_4AM
            else -> null
        } ?: return false

        // Check in-memory guard for duplicate invocations
        val memoryKey = "${todayStr}_$targetHour"
        if (lateNightProcessedToday[memoryKey] == true) {
            Log.d(tag, "Late night hour $targetHour already executed in memory today. Suppressing duplicate.")
            return false
        }

        // Check persistent store to guarantee no duplicate trigger today across process restarts
        val lastRecordedDate = preferencesRepository.getLastLateNightDate(targetHour)
        if (lastRecordedDate == todayStr) {
            Log.d(tag, "Late night hour $targetHour already recorded in DataStore today ($todayStr). Suppressing duplicate.")
            return false
        }

        val settings = preferencesRepository.settingsFlow.first()
        cachedSettings = settings
        if (!settings.isLivingPhoneEnabled || !settings.isVoiceEnabled) {
            Log.d(tag, "Late night reaction suppressed: livingPhoneEnabled=${settings.isLivingPhoneEnabled}, voiceEnabled=${settings.isVoiceEnabled}")
            return false
        }

        lateNightProcessedToday[memoryKey] = true
        preferencesRepository.recordLateNightHourTriggered(targetHour, todayStr)

        executeReactionInternal(targetCategory, settings)
        return true
    }

    /**
     * Non-suspending bridge for late-night check.
     */
    fun onLateNightCheck(scheduledHour: Int? = null) {
        scope.launch {
            processLateNightAlarm(scheduledHour)
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
     * Attempts to trigger a reaction for a given category.
     * Synchronously evaluates master switch, voice switch, and cooldowns.
     * Returns true if accepted and dispatched, false if suppressed by pause/mute/cooldown.
     */
    fun attemptReaction(category: AudioCategory, reason: String = ""): Boolean {
        val settings = cachedSettings
        if (!settings.isLivingPhoneEnabled) {
            Log.d(tag, "Reaction suppressed: Living Phone is Paused.")
            return false
        }

        if (!settings.isVoiceEnabled) {
            Log.d(tag, "Voice reactions are disabled by user.")
            return false
        }

        val now = System.currentTimeMillis()

        // 1. Check Global Cooldown
        val globalCooldown = settings.frequency.globalCooldownMillis
        if (now - lastGlobalTriggerTime < globalCooldown) {
            val remainingSec = (globalCooldown - (now - lastGlobalTriggerTime)) / 1000
            Log.d(tag, "Reaction suppressed: Global cooldown active ($remainingSec s remaining). Reason: $reason")
            return false
        }

        // 2. Check Category Cooldown
        val categoryCooldown = settings.frequency.categoryCooldownMillis
        val lastCatTime = lastCategoryTriggerTimes[category.id] ?: 0L
        if (now - lastCatTime < categoryCooldown) {
            val remainingSec = (categoryCooldown - (now - lastCatTime)) / 1000
            Log.d(tag, "Reaction suppressed: Category cooldown for ${category.id} ($remainingSec s remaining). Reason: $reason")
            return false
        }

        // Record cooldown timestamps immediately to eliminate concurrency races
        lastGlobalTriggerTime = now
        lastCategoryTriggerTimes[category.id] = now

        // Dispatch playback asynchronously
        scope.launch {
            executeReactionInternal(category, settings)
        }
        return true
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
            timestamp = System.currentTimeMillis()
        )

        Log.i(tag, "Reaction executed for ${category.displayName}. Result: $playResult")
        kotlinx.coroutines.delay(3500L)
        _isReacting.value = false
    }

    fun resetCooldownsForTesting() {
        lastGlobalTriggerTime = 0L
        lastCategoryTriggerTimes.clear()
        lastExplicitPowerState = null
        lastExplicitPowerTimestamp = 0L
        lastBatteryLevel = -1
        isBattery15Triggered = false
        isBattery100Triggered = false
        lateNightProcessedToday.clear()
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
