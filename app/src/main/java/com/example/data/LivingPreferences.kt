package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "living_phone_settings")

enum class ReactionFrequency(
    val id: String,
    val title: String,
    val description: String,
    val globalCooldownMillis: Long,
    val categoryCooldownMillis: Long
) {
    INSTANT(
        id = "instant",
        title = "Instant (2s)",
        description = "Hyper responsive. 2s minimum interval.",
        globalCooldownMillis = 2000L,
        categoryCooldownMillis = 2000L
    ),
    LOW(
        id = "low",
        title = "Low",
        description = "Subtle & sparse reactions. 10m minimum interval.",
        globalCooldownMillis = 10 * 60 * 1000L,
        categoryCooldownMillis = 15 * 60 * 1000L
    ),
    NORMAL(
        id = "normal",
        title = "Normal",
        description = "Balanced ambient presence. 3m minimum interval.",
        globalCooldownMillis = 3 * 60 * 1000L,
        categoryCooldownMillis = 6 * 60 * 1000L
    ),
    HIGH(
        id = "high",
        title = "High",
        description = "Active awareness with strict duplicate limits. 45s interval.",
        globalCooldownMillis = 45 * 1000L,
        categoryCooldownMillis = 90 * 1000L
    );

    companion object {
        fun fromId(id: String): ReactionFrequency {
            return entries.find { it.id == id } ?: INSTANT
        }
    }
}

data class LivingSettings(
    val isLivingPhoneEnabled: Boolean = true,
    val isVoiceEnabled: Boolean = true,
    val frequency: ReactionFrequency = ReactionFrequency.INSTANT,
    val lastReactionTimestamp: Long = 0L,
    val lastReactionCategory: String = "",
    val lastReactionClip: String = ""
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val LIVING_ENABLED = booleanPreferencesKey("is_living_phone_enabled")
        val VOICE_ENABLED = booleanPreferencesKey("is_voice_enabled")
        val FREQUENCY = stringPreferencesKey("reaction_frequency")
        val LAST_REACTION_TIME = longPreferencesKey("last_reaction_time")
        val LAST_REACTION_CATEGORY = stringPreferencesKey("last_reaction_category")
        val LAST_REACTION_CLIP = stringPreferencesKey("last_reaction_clip")

        fun lateNightKey(hour: Int) = stringPreferencesKey("late_night_hour_${hour}_date")
    }

    val settingsFlow: Flow<LivingSettings> = context.dataStore.data.map { prefs ->
        val enabled = prefs[Keys.LIVING_ENABLED] ?: true
        val voice = prefs[Keys.VOICE_ENABLED] ?: true
        val freqStr = prefs[Keys.FREQUENCY] ?: ReactionFrequency.INSTANT.id
        val lastTime = prefs[Keys.LAST_REACTION_TIME] ?: 0L
        val lastCat = prefs[Keys.LAST_REACTION_CATEGORY] ?: ""
        val lastClip = prefs[Keys.LAST_REACTION_CLIP] ?: ""

        LivingSettings(
            isLivingPhoneEnabled = enabled,
            isVoiceEnabled = voice,
            frequency = ReactionFrequency.fromId(freqStr),
            lastReactionTimestamp = lastTime,
            lastReactionCategory = lastCat,
            lastReactionClip = lastClip
        )
    }

    suspend fun setLivingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIVING_ENABLED] = enabled
        }
    }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VOICE_ENABLED] = enabled
        }
    }

    suspend fun setFrequency(frequency: ReactionFrequency) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FREQUENCY] = frequency.id
        }
    }

    suspend fun recordReaction(category: String, clipPath: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_REACTION_TIME] = System.currentTimeMillis()
            prefs[Keys.LAST_REACTION_CATEGORY] = category
            prefs[Keys.LAST_REACTION_CLIP] = clipPath
        }
    }

    suspend fun getLastLateNightDate(hour: Int): String? {
        val prefs = context.dataStore.data.first()
        return prefs[Keys.lateNightKey(hour)]
    }

    suspend fun recordLateNightHourTriggered(hour: Int, dateStr: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.lateNightKey(hour)] = dateStr
        }
    }
}
