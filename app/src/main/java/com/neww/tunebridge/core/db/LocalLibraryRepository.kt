package com.neww.tunebridge.core.db

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neww.tunebridge.core.models.TrackModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "library_prefs")

class LocalLibraryRepository(private val context: Context, private val gson: Gson) {
    
    private val LIKED_SONGS_KEY = stringPreferencesKey("saved_songs")
    private val PLAYLISTS_KEY = stringPreferencesKey("saved_playlists")
    private val RECENT_SONGS_KEY = stringPreferencesKey("recent_songs")
    private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")

    fun getLikedSongs(): Flow<List<TrackModel>> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[LIKED_SONGS_KEY] ?: "[]"
            val type = object : TypeToken<List<TrackModel>>() {}.type
            gson.fromJson(json, type)
        }
    }

    suspend fun saveLikedSong(song: TrackModel) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[LIKED_SONGS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TrackModel>>() {}.type
            val currentList: MutableList<TrackModel> = gson.fromJson(currentJson, type)
            
            // Avoid duplicates
            if (currentList.none { it.id == song.id }) {
                currentList.add(0, song) // Add to top
                preferences[LIKED_SONGS_KEY] = gson.toJson(currentList)
            }
        }
    }

    suspend fun removeLikedSong(songId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[LIKED_SONGS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TrackModel>>() {}.type
            val currentList: MutableList<TrackModel> = gson.fromJson(currentJson, type)
            
            currentList.removeAll { it.id == songId }
            preferences[LIKED_SONGS_KEY] = gson.toJson(currentList)
        }
    }

    fun getPlaylists(): Flow<List<com.neww.tunebridge.core.models.PlaylistModel>> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[PLAYLISTS_KEY] ?: "[]"
            val type = object : TypeToken<List<com.neww.tunebridge.core.models.PlaylistModel>>() {}.type
            gson.fromJson(json, type)
        }
    }

    suspend fun savePlaylist(playlist: com.neww.tunebridge.core.models.PlaylistModel) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PLAYLISTS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<com.neww.tunebridge.core.models.PlaylistModel>>() {}.type
            val currentList: MutableList<com.neww.tunebridge.core.models.PlaylistModel> = gson.fromJson(currentJson, type)
            
            // Remove if exists to update
            currentList.removeAll { it.id == playlist.id }
            currentList.add(0, playlist)
            preferences[PLAYLISTS_KEY] = gson.toJson(currentList)
        }
    }

    suspend fun removePlaylist(playlistId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PLAYLISTS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<com.neww.tunebridge.core.models.PlaylistModel>>() {}.type
            val currentList: MutableList<com.neww.tunebridge.core.models.PlaylistModel> = gson.fromJson(currentJson, type)
            
            currentList.removeAll { it.id == playlistId }
            preferences[PLAYLISTS_KEY] = gson.toJson(currentList)
        }
    }

    fun getRecentTracks(): Flow<List<TrackModel>> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[RECENT_SONGS_KEY] ?: "[]"
            val type = object : TypeToken<List<TrackModel>>() {}.type
            gson.fromJson(json, type)
        }
    }

    suspend fun saveToHistory(song: TrackModel) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[RECENT_SONGS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<TrackModel>>() {}.type
            val currentList: MutableList<TrackModel> = gson.fromJson(currentJson, type)
            
            // Remove if exists to move it to the top
            currentList.removeAll { it.id == song.id }
            currentList.add(0, song)
            
            // Keep history limited to 50 tracks
            if (currentList.size > 50) {
                currentList.removeLast()
            }
            
            preferences[RECENT_SONGS_KEY] = gson.toJson(currentList)
        }
    }

    fun getSearchHistory(): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            val json = preferences[SEARCH_HISTORY_KEY] ?: "[]"
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        }
    }

    suspend fun saveSearchQuery(query: String) {
        if (query.isBlank()) return
        context.dataStore.edit { preferences ->
            val currentJson = preferences[SEARCH_HISTORY_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<String>>() {}.type
            val currentList: MutableList<String> = gson.fromJson(currentJson, type)
            
            currentList.removeAll { it.equals(query, ignoreCase = true) }
            currentList.add(0, query.trim())
            
            if (currentList.size > 15) {
                currentList.removeLast()
            }
            
            preferences[SEARCH_HISTORY_KEY] = gson.toJson(currentList)
        }
    }
    
    suspend fun clearSearchHistory() {
        context.dataStore.edit { preferences ->
            preferences[SEARCH_HISTORY_KEY] = "[]"
        }
    }
}
