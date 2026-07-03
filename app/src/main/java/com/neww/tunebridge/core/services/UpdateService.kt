package com.neww.tunebridge.core.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class UpdateInfo(
    val version: String,
    val releaseNotes: String,
    val downloadUrl: String
)

class UpdateService {
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/Astreas-Core/Audiora/releases/latest")
            val connection = url.openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            
            val version = json.getString("tag_name")
            val body = json.getString("body")
            val assets = json.getJSONArray("assets")
            var downloadUrl = json.getString("html_url") // fallback to release page
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }
            
            UpdateInfo(version, body, downloadUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
