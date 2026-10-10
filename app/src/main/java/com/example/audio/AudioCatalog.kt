package com.example.audio

import android.content.Context
import android.util.Log

/**
 * Exact catalog of all voice clip assets expected by Living Phone.
 * Paths are case-sensitive and match the physical asset directory tree.
 */
enum class AudioCategory(
    val id: String,
    val displayName: String,
    val folderPath: String,
    val fileNames: List<String>
) {
    BATTERY_15(
        id = "battery_15",
        displayName = "Battery Low (15%)",
        folderPath = "audio/BATTERY",
        fileNames = listOf(
            "battery_15%_1.mp3",
            "battery_15%_2.mp3",
            "battery_15%_3.mp3"
        )
    ),
    BATTERY_100(
        id = "battery_100",
        displayName = "Battery Full (100%)",
        folderPath = "audio/BATTERY",
        fileNames = listOf(
            "battery_100%_1.mp3",
            "battery_100%_2.mp3",
            "battery_100%_3.mp3"
        )
    ),
    CHARGING_CONNECT(
        id = "charging_connect",
        displayName = "Charger Connected",
        folderPath = "audio/CHARGING",
        fileNames = listOf(
            "charging_connect_1.mp3",
            "charging_connect_2.mp3",
            "charging_connect_3.mp3"
        )
    ),
    CHARGING_DISCONNECT(
        id = "charging_disconnect",
        displayName = "Charger Disconnected",
        folderPath = "audio/CHARGING",
        fileNames = listOf(
            "charging_disconnect_1.mp3",
            "charging_disconnect_2.mp3",
            "charging_disconnect_3.mp3"
        )
    ),
    EXERCISE(
        id = "exercise",
        displayName = "Exercise",
        folderPath = "audio/EXERCISE",
        fileNames = listOf(
            "exercise_1.mp3",
            "exercise_2.mp3",
            "exercise_3.mp3"
        )
    ),
    GENERAL(
        id = "general",
        displayName = "General Reaction",
        folderPath = "audio/GENERAL",
        fileNames = listOf(
            "general_1.mp3",
            "general_2.mp3",
            "general_3.mp3",
            "general_4.mp3",
            "general_5.mp3",
            "general_6.mp3",
            "general_7.mp3",
            "general_8.mp3"
        )
    ),
    MOTION(
        id = "motion",
        displayName = "Motion",
        folderPath = "audio/MOTION",
        fileNames = listOf(
            "motion_1.mp3",
            "motion_2.mp3",
            "motion_3.mp3"
        )
    ),
    POCKET_LIGHT(
        id = "pocket_light",
        displayName = "Pocket (Light)",
        folderPath = "audio/POCKET/LIGHT",
        fileNames = listOf(
            "pocket_light_1.mp3",
            "pocket_light_2.mp3",
            "pocket_light_3.mp3"
        )
    ),
    POCKET_DARK(
        id = "pocket_dark",
        displayName = "Pocket (Dark)",
        folderPath = "audio/POCKET/DARK",
        fileNames = listOf(
            "pocket_dark_1.mp3",
            "pocket_dark_2.mp3",
            "pocket_dark_3.mp3"
        )
    ),
    LATE_NIGHT_MIDNIGHT(
        id = "late_night_midnight",
        displayName = "Late Night: Midnight (12:00 AM)",
        folderPath = "audio/SLEEP/LATE_NIGHT",
        fileNames = listOf("hour_12_midnight.mp3")
    ),
    LATE_NIGHT_1AM(
        id = "late_night_1am",
        displayName = "Late Night: 1:00 AM",
        folderPath = "audio/SLEEP/LATE_NIGHT",
        fileNames = listOf("hour_01_am.mp3")
    ),
    LATE_NIGHT_2AM(
        id = "late_night_2am",
        displayName = "Late Night: 2:00 AM",
        folderPath = "audio/SLEEP/LATE_NIGHT",
        fileNames = listOf("hour_02_am.mp3")
    ),
    LATE_NIGHT_3AM(
        id = "late_night_3am",
        displayName = "Late Night: 3:00 AM",
        folderPath = "audio/SLEEP/LATE_NIGHT",
        fileNames = listOf("hour_03_am.mp3")
    ),
    LATE_NIGHT_4AM(
        id = "late_night_4am",
        displayName = "Late Night: 4:00 AM",
        folderPath = "audio/SLEEP/LATE_NIGHT",
        fileNames = listOf("hour_04_am.mp3")
    ),
    SLEEP_REMINDER(
        id = "sleep_reminder",
        displayName = "Sleep Reminder",
        folderPath = "audio/SLEEP/SLEEP_REMINDER",
        fileNames = listOf(
            "sleep_sleep_reminder_1.mp3",
            "sleep_sleep_reminder_2.mp3",
            "sleep_sleep_reminder_3.mp3"
        )
    ),
    USAGE(
        id = "usage",
        displayName = "Usage Rhythms",
        folderPath = "audio/USAGE",
        fileNames = listOf(
            "usage_1.mp3",
            "usage_2.mp3",
            "usage_3.mp3"
        )
    );

    val fullAssetPaths: List<String>
        get() = fileNames.map { "$folderPath/$it" }
}

/**
 * High-level group for Settings and verification UI.
 */
enum class CategoryGroup(
    val groupId: String,
    val title: String,
    val description: String,
    val categories: List<AudioCategory>
) {
    BATTERY(
        groupId = "battery",
        title = "Battery Levels",
        description = "Reactions when battery reaches 15% (low) or 100% (full)",
        categories = listOf(AudioCategory.BATTERY_15, AudioCategory.BATTERY_100)
    ),
    CHARGING(
        groupId = "charging",
        title = "Charging State",
        description = "Reactions on power cable connection & disconnection",
        categories = listOf(AudioCategory.CHARGING_CONNECT, AudioCategory.CHARGING_DISCONNECT)
    ),
    LATE_NIGHT(
        groupId = "late_night",
        title = "Late Night Hours",
        description = "Specific hourly reactions at Midnight, 1 AM, 2 AM, 3 AM, and 4 AM",
        categories = listOf(
            AudioCategory.LATE_NIGHT_MIDNIGHT,
            AudioCategory.LATE_NIGHT_1AM,
            AudioCategory.LATE_NIGHT_2AM,
            AudioCategory.LATE_NIGHT_3AM,
            AudioCategory.LATE_NIGHT_4AM
        )
    ),
    SLEEP_REMINDER(
        groupId = "sleep_reminder",
        title = "Sleep Reminder",
        description = "Gentle nocturnal notifications to rest",
        categories = listOf(AudioCategory.SLEEP_REMINDER)
    ),
    POCKET_LIGHT(
        groupId = "pocket_light",
        title = "Pocket (Light)",
        description = "Triggered when device transitions from darkness into light",
        categories = listOf(AudioCategory.POCKET_LIGHT)
    ),
    POCKET_DARK(
        groupId = "pocket_dark",
        title = "Pocket (Dark)",
        description = "Triggered when device enters darkness or pocket",
        categories = listOf(AudioCategory.POCKET_DARK)
    ),
    MOTION(
        groupId = "motion",
        title = "Motion",
        description = "Triggered upon physical movement",
        categories = listOf(AudioCategory.MOTION)
    ),
    EXERCISE(
        groupId = "exercise",
        title = "Exercise",
        description = "Physical activity and workout rhythm reactions",
        categories = listOf(AudioCategory.EXERCISE)
    ),
    USAGE(
        groupId = "usage",
        title = "Usage",
        description = "Screen unlocking and continuous phone interaction",
        categories = listOf(AudioCategory.USAGE)
    ),
    GENERAL(
        groupId = "general",
        title = "General",
        description = "Ambient personality and spontaneous presence clips",
        categories = listOf(AudioCategory.GENERAL)
    );

    fun totalExpectedClips(): Int = categories.sumOf { it.fileNames.size }
}

object AudioCatalog {
    private const val TAG = "AudioCatalog"

    /**
     * Checks if a specific asset path exists in the APK asset manager.
     */
    fun assetExists(context: Context, assetPath: String): Boolean {
        return try {
            context.assets.open(assetPath).use { }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns list of asset paths that actually exist inside this category.
     */
    fun getAvailableClips(context: Context, category: AudioCategory): List<String> {
        return category.fullAssetPaths.filter { assetExists(context, it) }
    }

    /**
     * Returns count of installed clips for a category group.
     */
    fun getInstalledCount(context: Context, group: CategoryGroup): Int {
        var count = 0
        for (category in group.categories) {
            count += category.fullAssetPaths.count { assetExists(context, it) }
        }
        return count
    }

    /**
     * Total number of expected clips across all categories (40 clips).
     */
    val TOTAL_EXPECTED_CLIPS: Int = AudioCategory.entries.sumOf { it.fileNames.size }

    /**
     * Total number of currently available clips in the app.
     */
    fun getTotalInstalledClips(context: Context): Int {
        return AudioCategory.entries.sumOf { category ->
            category.fullAssetPaths.count { assetExists(context, it) }
        }
    }
}
