package com.neww.tunebridge.core.services

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.net.URLEncoder

data class LyricLine(
    val timeMs: Long,
    val text: String
)

class LyricsRepository(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson
) {
    private val cacheDir = File(context.filesDir, "lyrics_cache").apply { if (!exists()) mkdirs() }

    private fun cleanTitle(title: String): String {
        var clean = title.replace(Regex("\\(feat\\..*?\\)", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("\\[.*?\\]"), "")
        clean = clean.replace(Regex("\\(Official.*?\\)", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("\\(Lyric.*?\\)", RegexOption.IGNORE_CASE), "")
        return clean.trim()
    }

    suspend fun getSyncedLyrics(trackName: String, artistName: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        val safeFileName = "${trackName}_${artistName}".replace(Regex("[^a-zA-Z0-9.-]"), "_") + ".json"
        val cacheFile = File(cacheDir, safeFileName)
        
        if (cacheFile.exists()) {
            try {
                FileReader(cacheFile).use { reader ->
                    val type = object : TypeToken<List<LyricLine>>() {}.type
                    return@withContext gson.fromJson<List<LyricLine>>(reader, type)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val cleanName = cleanTitle(trackName)
            val trackEncoded = URLEncoder.encode(cleanName, "UTF-8")
            val artistEncoded = URLEncoder.encode(artistName.split(",").first().trim(), "UTF-8")
            val url = "https://lrclib.net/api/get?track_name=$trackEncoded&artist_name=$artistEncoded"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TuneBridge")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            
            val bodyString = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyString)
            val syncedLyrics = json.optString("syncedLyrics")
            
            if (syncedLyrics.isNullOrEmpty()) return@withContext null
            
            val lines = parseLrc(syncedLyrics)
            
            try {
                FileWriter(cacheFile).use { writer ->
                    gson.toJson(lines, writer)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            return@withContext lines
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseLrc(lrcContent: String): List<LyricLine> {
        val lines = lrcContent.split("\n")
        val lyricLines = mutableListOf<LyricLine>()
        
        // Regex to match [mm:ss.xx]
        val timeRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
        
        for (line in lines) {
            val matchResult = timeRegex.find(line)
            if (matchResult != null) {
                val minutes = matchResult.groupValues[1].toLong()
                val seconds = matchResult.groupValues[2].toLong()
                val millis = if (matchResult.groupValues[3].length == 2) {
                    matchResult.groupValues[3].toLong() * 10
                } else {
                    matchResult.groupValues[3].toLong()
                }
                
                val timeMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                val text = matchResult.groupValues[4].trim()
                
                lyricLines.add(LyricLine(timeMs, text))
            }
        }
        
        return lyricLines
    }
}
