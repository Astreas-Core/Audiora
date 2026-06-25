package com.neww.tunebridge.core.db

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val AUDIO_QUALITY_KEY = stringPreferencesKey("audio_quality")
        val THEME_KEY = stringPreferencesKey("theme")
        val CROSSFADE_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("crossfade")
    }

    val audioQualityFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[AUDIO_QUALITY_KEY] ?: "Auto"
        }

    val themeFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: "System Default"
        }

    val crossfadeEnabledFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { preferences ->
            preferences[CROSSFADE_KEY] ?: true
        }

    suspend fun setAudioQuality(quality: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[AUDIO_QUALITY_KEY] = quality
        }
    }

    suspend fun setTheme(theme: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[CROSSFADE_KEY] = enabled
        }
    }
}
