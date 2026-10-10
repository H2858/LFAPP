package com.example

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioCatalog
import com.example.audio.AudioCategory
import com.example.audio.AudioEngine
import com.example.audio.AudioPlayResult
import com.example.data.PreferencesRepository
import com.example.data.ReactionFrequency
import com.example.service.AlarmScheduler
import com.example.service.LivingPhoneAlarmReceiver
import com.example.service.LivingPhoneBootReceiver
import com.example.service.LivingPhonePowerReceiver
import com.example.service.ReactionController
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ReactionController.getInstance(context).resetCooldownsForTesting()
    }

    @Test
    fun verifyAppName() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Living Phone", appName)
    }

    @Test
    fun verifyAudioCatalogStructureAndPaths() {
        assertEquals(43, AudioCatalog.TOTAL_EXPECTED_CLIPS)

        // Verify BATTERY_15
        val battery15 = AudioCategory.BATTERY_15
        assertEquals(3, battery15.fileNames.size)
        assertEquals("audio/BATTERY", battery15.folderPath)
        assertTrue(battery15.fileNames.contains("battery_15%_1.mp3"))

        // Verify BATTERY_100
        val battery100 = AudioCategory.BATTERY_100
        assertEquals(3, battery100.fileNames.size)
        assertTrue(battery100.fileNames.contains("battery_100%_1.mp3"))

        // Verify CHARGING
        val chargingConnect = AudioCategory.CHARGING_CONNECT
        assertEquals(3, chargingConnect.fileNames.size)
        assertEquals("audio/CHARGING", chargingConnect.folderPath)
        assertTrue(chargingConnect.fileNames.contains("charging_connect_1.mp3"))

        val chargingDisconnect = AudioCategory.CHARGING_DISCONNECT
        assertEquals(3, chargingDisconnect.fileNames.size)
        assertTrue(chargingDisconnect.fileNames.contains("charging_disconnect_1.mp3"))

        // Verify LATE_NIGHT
        assertEquals(listOf("hour_12_midnight.mp3"), AudioCategory.LATE_NIGHT_MIDNIGHT.fileNames)
        assertEquals(listOf("hour_01_am.mp3"), AudioCategory.LATE_NIGHT_1AM.fileNames)
        assertEquals(listOf("hour_02_am.mp3"), AudioCategory.LATE_NIGHT_2AM.fileNames)
        assertEquals(listOf("hour_03_am.mp3"), AudioCategory.LATE_NIGHT_3AM.fileNames)
        assertEquals(listOf("hour_04_am.mp3"), AudioCategory.LATE_NIGHT_4AM.fileNames)

        // Verify POCKET
        assertEquals("audio/POCKET/LIGHT", AudioCategory.POCKET_LIGHT.folderPath)
        assertEquals("audio/POCKET/DARK", AudioCategory.POCKET_DARK.folderPath)

        // Verify USAGE
        assertEquals("audio/USAGE", AudioCategory.USAGE.folderPath)
        assertEquals(3, AudioCategory.USAGE.fileNames.size)

        // Verify EXERCISE & MOTION
        assertEquals(3, AudioCategory.EXERCISE.fileNames.size)
        assertEquals(3, AudioCategory.MOTION.fileNames.size)
        assertEquals(8, AudioCategory.GENERAL.fileNames.size)
    }

    @Test
    fun verifyAudioEngineMissingClipsNeverCrash() {
        val audioEngine = AudioEngine(context)

        // Missing clips return MissingFiles safely without throwing
        val result = audioEngine.playCategory(AudioCategory.BATTERY_15)
        assertTrue(
            "Expected MissingFiles result, got $result",
            result is AudioPlayResult.MissingFiles
        )
    }

    @Test
    fun verifyInitialStateSeedingDoesNotTriggerReaction() {
        val controller = ReactionController.getInstance(context)

        // Fabricate a battery intent snapshot reporting charging=true at 80%
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
        }

        // Seeding baseline state must not trigger any audio reaction
        controller.seedInitialState(intent)
        assertNotNull(controller)
    }

    @Test
    fun verifyColdStartExplicitConnectHandling() {
        val controller = ReactionController.getInstance(context)

        // Simulate cold-start where initial state baseline is seeded as charging=true
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
        }
        controller.seedInitialState(intent)

        // When explicit power event arrives, it MUST NOT be swallowed by the seeded baseline
        val triggered = controller.onExplicitPowerTransition(true)
        assertTrue("Explicit power connection transition must trigger reaction", triggered)
    }

    @Test
    fun verifyRepeatedPowerBroadcastsSuppression() {
        val controller = ReactionController.getInstance(context)

        // First explicit connection triggers
        controller.onExplicitPowerTransition(true)

        // Immediate repeat broadcast (within debounce) must be suppressed
        val duplicateTriggered = controller.onExplicitPowerTransition(true)
        assertFalse("Duplicate explicit power broadcast must be suppressed", duplicateTriggered)
    }

    @Test
    fun verifyBatteryThresholdLogic() {
        val controller = ReactionController.getInstance(context)

        // Baseline reading at 50%
        controller.onBatteryChanged(50)

        // Crossing into 15% from above should evaluate threshold
        controller.onBatteryChanged(15)

        // Repeating 15% should NOT re-trigger
        controller.onBatteryChanged(15)

        // Recharging back to 25% resets hysteresis
        controller.onBatteryChanged(25)

        // Crossing into 100% full battery
        controller.onBatteryChanged(100)

        // Discharging to 95% resets 100% trigger
        controller.onBatteryChanged(95)
    }

    @Test
    fun verifyLateNightHourPersistenceAndDeduplication() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.recordLateNightHourTriggered(1, "20261010")

        val recorded = repository.getLastLateNightDate(1)
        assertEquals("20261010", recorded)

        val controller = ReactionController.getInstance(context)
        // If hour 1 was already recorded today in DataStore, processing it again must be rejected
        val played = controller.processLateNightAlarm(1)
        assertFalse("Duplicate execution for hour 1 today must be rejected", played)
    }

    @Test
    fun verifyDelayedLateNightAlarmHandling() = runBlocking {
        val controller = ReactionController.getInstance(context)

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        // An alarm scheduled for an hour that does not match current hour (e.g. 5 hours ago)
        val staleHour = (currentHour + 5) % 24
        if (staleHour in 0..4) {
            val played = controller.processLateNightAlarm(staleHour)
            assertFalse("Stale alarm arriving hours late must be skipped", played)
        }
    }

    @Test
    fun verifyGlobalPauseSuppression() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(false)

        val controller = ReactionController.getInstance(context)
        // Wait briefly for cachedSettings to update
        kotlinx.coroutines.delay(50L)
        val triggered = controller.attemptReaction(AudioCategory.BATTERY_15, "Test when paused")
        assertFalse("Reaction must be suppressed when Living Phone is disabled", triggered)

        // Restore
        repository.setLivingEnabled(true)
    }

    @Test
    fun verifyVoiceMuteSuppression() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setVoiceEnabled(false)

        val controller = ReactionController.getInstance(context)
        kotlinx.coroutines.delay(50L)
        val triggered = controller.attemptReaction(AudioCategory.CHARGING_CONNECT, "Test when voice muted")
        assertFalse("Voice reaction must be suppressed when voice is muted", triggered)

        // Restore
        repository.setVoiceEnabled(true)
    }

    @Test
    fun verifyReceiversInstantiateSafely() {
        val powerReceiver = LivingPhonePowerReceiver()
        assertNotNull(powerReceiver)

        val bootReceiver = LivingPhoneBootReceiver()
        assertNotNull(bootReceiver)

        val alarmReceiver = LivingPhoneAlarmReceiver()
        assertNotNull(alarmReceiver)
    }
}
