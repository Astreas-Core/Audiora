package com.neww.tunebridge.core.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neww.tunebridge.core.models.TrackModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter

class DownloadRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val youtubeScraper: YouTubeScraper,
    private val gson: Gson
) {
    private val metadataFile = File(context.filesDir, "downloads_meta.json")
    private val _downloadedTracks = MutableStateFlow<List<TrackModel>>(emptyList())
    val downloadedTracks: Flow<List<TrackModel>> = _downloadedTracks.asStateFlow()

    init {
        loadMetadata()
    }

    private fun loadMetadata() {
        if (!metadataFile.exists()) return
        try {
            FileReader(metadataFile).use { reader ->
                val type = object : TypeToken<List<TrackModel>>() {}.type
                val list: List<TrackModel>? = gson.fromJson(reader, type)
                if (list != null) {
                    _downloadedTracks.value = list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveMetadata(tracks: List<TrackModel>) {
        try {
            FileWriter(metadataFile).use { writer ->
                gson.toJson(tracks, writer)
            }
            _downloadedTracks.value = tracks
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun downloadTrack(track: TrackModel): Boolean = withContext(Dispatchers.IO) {
        try {
            val videoId = track.youtubeVideoId ?: youtubeScraper.searchVideoId(track.title, track.artist) ?: return@withContext false
            val streamUrl = youtubeScraper.getStreamUrl(videoId) ?: return@withContext false
            
            val request = Request.Builder().url(streamUrl).build()
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) return@withContext false
            
            val inputStream = response.body?.byteStream() ?: return@withContext false
            
            val downloadsDir = File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdir()
            
            val trackFile = File(downloadsDir, "${track.id}.m4a")
            val outputStream = FileOutputStream(trackFile)
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            val currentList = _downloadedTracks.value.toMutableList()
            currentList.removeAll { it.id == track.id }
            currentList.add(0, track)
            saveMetadata(currentList)
            
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
    
    fun removeDownload(trackId: String) {
        val file = File(context.filesDir, "downloads/$trackId.m4a")
        if (file.exists()) file.delete()
        
        val currentList = _downloadedTracks.value.toMutableList()
        currentList.removeAll { it.id == trackId }
        saveMetadata(currentList)
    }
    
    fun getDownloadedFilePath(trackId: String): String? {
        val file = File(context.filesDir, "downloads/$trackId.m4a")
        return if (file.exists()) file.absolutePath else null
    }
}
