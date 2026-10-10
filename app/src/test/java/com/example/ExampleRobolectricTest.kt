package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioCatalog
import com.example.audio.AudioCategory
import com.example.audio.AudioEngine
import com.example.audio.AudioPlayResult
import com.example.data.ReactionFrequency
import com.example.service.ReactionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun verifyAppName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Living Phone", appName)
    }

    @Test
    fun verifyAudioCatalogStructure() {
        assertEquals(43, AudioCatalog.TOTAL_EXPECTED_CLIPS)

        // Verify Battery 15% paths
        val battery15 = AudioCategory.BATTERY_15
        assertEquals(3, battery15.fileNames.size)
        assertTrue(battery15.fileNames.contains("battery_15%_1.mp3"))

        // Verify Charging paths
        val chargingConnect = AudioCategory.CHARGING_CONNECT
        assertEquals(3, chargingConnect.fileNames.size)
        assertTrue(chargingConnect.fileNames.contains("charging_connect_1.mp3"))

        // Verify Late Night paths
        val midnight = AudioCategory.LATE_NIGHT_MIDNIGHT
        assertEquals(1, midnight.fileNames.size)
        assertEquals("hour_12_midnight.mp3", midnight.fileNames[0])

        val hour4 = AudioCategory.LATE_NIGHT_4AM
        assertEquals(1, hour4.fileNames.size)
        assertEquals("hour_04_am.mp3", hour4.fileNames[0])
    }

    @Test
    fun verifyAudioEngineSafeHandlingOfMissingClips() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioEngine = AudioEngine(context)

        // Attempting to play an empty category must safely return MissingFiles and NEVER throw or play fake audio
        val result = audioEngine.playCategory(AudioCategory.BATTERY_15)
        assertTrue(
            "Expected MissingFiles result for missing assets, got: $result",
            result is AudioPlayResult.MissingFiles
        )
    }

    @Test
    fun verifyFrequencyCooldownConstants() {
        assertEquals(10 * 60 * 1000L, ReactionFrequency.LOW.globalCooldownMillis)
        assertEquals(3 * 60 * 1000L, ReactionFrequency.NORMAL.globalCooldownMillis)
        assertEquals(45 * 1000L, ReactionFrequency.HIGH.globalCooldownMillis)
    }

    @Test
    fun verifyReactionControllerSingleton() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller1 = ReactionController.getInstance(context)
        val controller2 = ReactionController.getInstance(context)
        assertNotNull(controller1)
        assertEquals(controller1, controller2)
    }
}
