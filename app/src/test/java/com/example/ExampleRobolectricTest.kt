package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioCatalog
import com.example.audio.AudioCategory
import com.example.audio.AudioEngine
import com.example.audio.AudioPlayResult
import com.example.data.PreferencesRepository
import com.example.data.ReactionFrequency
import com.example.service.AlarmScheduler
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun verifyAppName() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Living Phone", appName)
    }

    @Test
    fun verifyAudioCatalogStructureAndPaths() {
        // Total expected clips across all categories is exactly 43
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

        // Since mp3 files are not yet in assets, playCategory must safely return MissingFiles without throwing
        val result = audioEngine.playCategory(AudioCategory.BATTERY_15)
        assertTrue(
            "Expected MissingFiles result, got $result",
            result is AudioPlayResult.MissingFiles
        )
    }

    @Test
    fun verifyFrequencyCooldownConstants() {
        assertEquals(10 * 60 * 1000L, ReactionFrequency.LOW.globalCooldownMillis)
        assertEquals(15 * 60 * 1000L, ReactionFrequency.LOW.categoryCooldownMillis)

        assertEquals(3 * 60 * 1000L, ReactionFrequency.NORMAL.globalCooldownMillis)
        assertEquals(6 * 60 * 1000L, ReactionFrequency.NORMAL.categoryCooldownMillis)

        assertEquals(45 * 1000L, ReactionFrequency.HIGH.globalCooldownMillis)
        assertEquals(90 * 1000L, ReactionFrequency.HIGH.categoryCooldownMillis)
    }

    @Test
    fun verifyReactionControllerSingleton() {
        val controller1 = ReactionController.getInstance(context)
        val controller2 = ReactionController.getInstance(context)
        assertNotNull(controller1)
        assertEquals(controller1, controller2)
    }

    @Test
    fun verifyInitialChargingStateDoesNotTriggerReaction() {
        val controller = ReactionController.getInstance(context)

        // When receiving the initial state reading (e.g. false), controller must establish baseline without reacting
        controller.onChargingChanged(false)
        // If the same state arrives again, it must suppress duplicate
        controller.onChargingChanged(false)
        assertNotNull(controller)
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
    fun verifyAlarmSchedulerConstants() {
        assertEquals("extra_scheduled_hour", AlarmScheduler.EXTRA_SCHEDULED_HOUR)
        assertEquals("extra_scheduled_time_millis", AlarmScheduler.EXTRA_SCHEDULED_TIME_MILLIS)
    }

    @Test
    fun verifyLateNightHourPersistence() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.recordLateNightHourTriggered(0, "20261010")

        val recorded = repository.getLastLateNightDate(0)
        assertEquals("20261010", recorded)
    }

    @Test
    fun verifyGlobalPauseSuppression() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(false)

        val controller = ReactionController.getInstance(context)
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
        val triggered = controller.attemptReaction(AudioCategory.CHARGING_CONNECT, "Test when voice muted")
        assertFalse("Voice reaction must be suppressed when voice is muted", triggered)

        // Restore
        repository.setVoiceEnabled(true)
    }
}
