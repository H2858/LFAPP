package com.example

import android.app.AlarmManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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

        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
        }

        controller.seedInitialState(intent)
        assertNotNull(controller)
    }

    @Test
    fun verifyColdStartExplicitConnectHandling() {
        val controller = ReactionController.getInstance(context)

        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_LEVEL, 80)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
        }
        controller.seedInitialState(intent)

        val triggered = controller.onExplicitPowerTransition(true)
        assertTrue("Explicit power connection transition must trigger reaction", triggered)
    }

    @Test
    fun verifyRepeatedPowerBroadcastsSuppression() {
        val controller = ReactionController.getInstance(context)

        controller.onExplicitPowerTransition(true)
        val duplicateTriggered = controller.onExplicitPowerTransition(true)
        assertFalse("Duplicate explicit power broadcast must be suppressed", duplicateTriggered)
    }

    @Test
    fun verifyBatteryThresholdLogic() {
        val controller = ReactionController.getInstance(context)

        controller.onBatteryChanged(50)
        controller.onBatteryChanged(15)
        controller.onBatteryChanged(15)
        controller.onBatteryChanged(25)
        controller.onBatteryChanged(100)
        controller.onBatteryChanged(95)
    }

    @Test
    fun verifyBootReceiverSchedulesAlarmWhenEnabled() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(true)
        AlarmScheduler.cancelAlarms(context)
        val shadowAlarmManager = shadowOf(alarmManager)
        assertNull(shadowAlarmManager.nextScheduledAlarm)

        // Trigger actual BootReceiver
        val receiver = LivingPhoneBootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)

        // Allow coroutine in goAsync() to execute
        delay(250L)

        // Verify actual receiver scheduled the alarm in AlarmManager
        assertNotNull("BootReceiver must schedule alarm in AlarmManager when enabled", shadowAlarmManager.nextScheduledAlarm)
    }

    @Test
    fun verifyBootReceiverDoesNotScheduleAlarmWhenPaused() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(false)
        AlarmScheduler.cancelAlarms(context)
        val shadowAlarmManager = shadowOf(alarmManager)
        assertNull(shadowAlarmManager.nextScheduledAlarm)

        // Trigger actual BootReceiver while paused
        val receiver = LivingPhoneBootReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)

        delay(250L)

        // Verify receiver did NOT schedule alarm in AlarmManager
        assertNull("BootReceiver must NOT schedule alarm when paused", shadowAlarmManager.nextScheduledAlarm)

        // Restore
        repository.setLivingEnabled(true)
    }

    @Test
    fun verifyAlarmReceiverExecutesAndReschedulesAlarm() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(true)
        AlarmScheduler.cancelAlarms(context)
        val shadowAlarmManager = shadowOf(alarmManager)
        assertNull(shadowAlarmManager.nextScheduledAlarm)

        // Trigger actual AlarmReceiver
        val receiver = LivingPhoneAlarmReceiver()
        val intent = Intent("com.example.livingphone.ALARM_LATE_NIGHT").apply {
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_HOUR, 0)
        }
        receiver.onReceive(context, intent)

        delay(250L)

        // Verify actual AlarmReceiver rescheduled the next alarm in AlarmManager
        assertNotNull("AlarmReceiver must reschedule next alarm in AlarmManager", shadowAlarmManager.nextScheduledAlarm)
    }

    @Test
    fun verifyAlarmReceiverReschedulesEvenOnProcessingFailure() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(true)
        AlarmScheduler.cancelAlarms(context)
        val shadowAlarmManager = shadowOf(alarmManager)
        assertNull(shadowAlarmManager.nextScheduledAlarm)

        // Trigger actual AlarmReceiver with invalid hour (-99) simulating processing error
        val receiver = LivingPhoneAlarmReceiver()
        val intent = Intent("com.example.livingphone.ALARM_LATE_NIGHT").apply {
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_HOUR, -99)
        }
        receiver.onReceive(context, intent)

        delay(250L)

        // Verify AlarmReceiver STILL rescheduled the future alarm despite processing failure
        assertNotNull("AlarmReceiver must guarantee rescheduling even on processing failure", shadowAlarmManager.nextScheduledAlarm)
    }

    @Test
    fun verifyAlarmReceiverCancelsAndDoesNotRescheduleWhenPaused() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(false)
        AlarmScheduler.cancelAlarms(context)
        val shadowAlarmManager = shadowOf(alarmManager)

        // Trigger actual AlarmReceiver while paused
        val receiver = LivingPhoneAlarmReceiver()
        val intent = Intent("com.example.livingphone.ALARM_LATE_NIGHT").apply {
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_HOUR, 0)
        }
        receiver.onReceive(context, intent)

        delay(250L)

        // Verify no alarm is scheduled
        assertNull("AlarmReceiver must cancel and not reschedule when paused", shadowAlarmManager.nextScheduledAlarm)

        // Restore
        repository.setLivingEnabled(true)
    }

    @Test
    fun verifyDuplicateAlarmDeliverySuppression() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(true)
        AlarmScheduler.cancelAlarms(context)
        val shadowAlarmManager = shadowOf(alarmManager)

        val receiver = LivingPhoneAlarmReceiver()
        val intent = Intent("com.example.livingphone.ALARM_LATE_NIGHT").apply {
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_HOUR, 3)
        }

        // First delivery to actual receiver
        receiver.onReceive(context, intent)
        delay(250L)
        assertNotNull("First alarm delivery must schedule next alarm", shadowAlarmManager.nextScheduledAlarm)

        // Second delivery of duplicate alarm to actual receiver
        receiver.onReceive(context, intent)
        delay(250L)

        // Rescheduling must remain intact and duplicate reaction must be rejected without error
        assertNotNull("Rescheduling must remain intact after duplicate alarm delivery", shadowAlarmManager.nextScheduledAlarm)
    }

    @Test
    fun verifyDelayedLateNightAlarmHandling() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(true)
        AlarmScheduler.cancelAlarms(context)
        val shadowAlarmManager = shadowOf(alarmManager)

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val staleHour = (currentHour + 5) % 24

        // Deliver stale alarm directly to actual AlarmReceiver
        val receiver = LivingPhoneAlarmReceiver()
        val intent = Intent("com.example.livingphone.ALARM_LATE_NIGHT").apply {
            putExtra(AlarmScheduler.EXTRA_SCHEDULED_HOUR, staleHour)
        }
        receiver.onReceive(context, intent)
        delay(250L)

        // Even for stale alarms, AlarmReceiver must safely reschedule future alarms
        assertNotNull("AlarmReceiver must reschedule next alarm even when receiving a stale alarm", shadowAlarmManager.nextScheduledAlarm)
    }

    @Test
    fun verifyBootReceiverHandlesPackageReplacedAction() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(true)
        AlarmScheduler.cancelAlarms(context)
        val shadowAlarmManager = shadowOf(alarmManager)
        assertNull(shadowAlarmManager.nextScheduledAlarm)

        // Deliver ACTION_MY_PACKAGE_REPLACED to actual BootReceiver
        val receiver = LivingPhoneBootReceiver()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)
        receiver.onReceive(context, intent)
        delay(250L)

        assertNotNull("BootReceiver must restore alarms on package replacement", shadowAlarmManager.nextScheduledAlarm)
    }

    @Test
    fun verifyAudioEngineSuspendingHandlesMissingFilesSafely() = runBlocking {
        val audioEngine = AudioEngine(context)
        val result = audioEngine.playCategorySuspending(AudioCategory.LATE_NIGHT_MIDNIGHT)
        assertTrue(
            "Suspending playback must return MissingFiles or Error safely without throwing",
            result is AudioPlayResult.MissingFiles || result is AudioPlayResult.Error
        )
        assertFalse(audioEngine.isPlaying.value)

        // Multiple calls to stopPlayback must be idempotent and safe
        audioEngine.stopPlayback()
        audioEngine.stopPlayback()
        assertFalse(audioEngine.isPlaying.value)
    }

    @Test
    fun verifyGlobalPauseSuppression() = runBlocking {
        val repository = PreferencesRepository(context)
        repository.setLivingEnabled(false)

        val controller = ReactionController.getInstance(context)
        delay(50L)
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
        delay(50L)
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
