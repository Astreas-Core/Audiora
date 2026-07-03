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
        val FADE_IN_KEY = androidx.datastore.preferences.core.intPreferencesKey("fade_in")
        val FADE_OUT_KEY = androidx.datastore.preferences.core.intPreferencesKey("fade_out")
        val ACCENT_COLOR_KEY = stringPreferencesKey("accent_color")
    }

    val audioQualityFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[AUDIO_QUALITY_KEY] ?: "Auto"
        }

    val themeFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: "System Default"
        }

    val fadeInFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FADE_IN_KEY] ?: 0 }
        
    val fadeOutFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences -> preferences[FADE_OUT_KEY] ?: 0 }
        
    val accentColorFlow: Flow<String> = context.settingsDataStore.data
        .map { preferences ->
            preferences[ACCENT_COLOR_KEY] ?: "#1DA1F2" // Default accent
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

    suspend fun setFadeIn(seconds: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FADE_IN_KEY] = seconds
        }
    }
    
    suspend fun setFadeOut(seconds: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[FADE_OUT_KEY] = seconds
        }
    }
    
    suspend fun setAccentColor(hexColor: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[ACCENT_COLOR_KEY] = hexColor
        }
    }
}
