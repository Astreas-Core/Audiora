package com.neww.tunebridge.core.services

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import com.neww.tunebridge.core.models.TrackModel
import org.json.JSONArray
import org.json.JSONObject
import android.util.Base64
import android.text.Html
import java.net.URLEncoder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class YouTubeScraper(private val okHttpClient: OkHttpClient, private val gson: Gson) {
    
    private fun decryptUrl(encryptedUrl: String): String {
        try {
            val key = "38346591".toByteArray()
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "DES"))
            val decoded = Base64.decode(encryptedUrl, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decoded)
            var decrypted = String(decryptedBytes)
            
            // Upgrade quality to 320kbps
            decrypted = decrypted.replace("_96_p.mp4", "_320.mp4")
                .replace("_96_p.m4a", "_320.mp4")
                .replace("_96.mp4", "_320.mp4")
            
            return decrypted
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }

    suspend fun searchVideoId(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode("$title $artist", "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$query&n=1&p=1&_format=json&_marker=0&ctx=web6dot0"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val song = results.getJSONObject(0)
                    return@withContext song.optString("encrypted_media_url")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun getStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        // Here videoId is actually the encrypted_media_url
        if (videoId.isEmpty() || !videoId.contains(Regex("[a-zA-Z0-9+/=]+"))) return@withContext null
        return@withContext decryptUrl(videoId)
    }

    suspend fun search(query: String): List<TrackModel> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&q=$encodedQuery&n=15&p=1&_format=json&_marker=0&ctx=web6dot0"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val resultsList = mutableListOf<TrackModel>()
        
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext resultsList
                val body = response.body?.string() ?: return@withContext resultsList
                
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return@withContext resultsList
                
                for (i in 0 until results.length()) {
                    val song = results.getJSONObject(i)
                    
                    val id = song.optString("id")
                    var title = song.optString("song", "Unknown")
                    title = Html.fromHtml(title, Html.FROM_HTML_MODE_LEGACY).toString()
                    
                    var artist = song.optString("primary_artists", "Unknown")
                    artist = Html.fromHtml(artist, Html.FROM_HTML_MODE_LEGACY).toString()
                    
                    var albumName = song.optString("album", "Unknown")
                    albumName = Html.fromHtml(albumName, Html.FROM_HTML_MODE_LEGACY).toString()
                    
                    var image = song.optString("image", "")
                    image = image.replace("150x150", "500x500") // Get better quality art
                    
                    val durationSecs = song.optInt("duration", 0)
                    val encryptedMediaUrl = song.optString("encrypted_media_url", "")
                    
                    resultsList.add(
                        TrackModel(
                            id = id,
                            title = title,
                            artist = artist,
                            albumName = albumName,
                            albumArtUrl = image,
                            durationMs = durationSecs * 1000L,
                            youtubeVideoId = encryptedMediaUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext resultsList
    }
}
