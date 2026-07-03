package com.neww.tunebridge.core.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

// UpdateInfo data class removed to avoid redeclaration with UpdateService.kt
class UpdateRepository(private val okHttpClient: OkHttpClient) {

    // Placeholder repository name - user can update this later
    private val githubRepo = "TuneBridge/TuneBridge" 

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$githubRepo/releases"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val bodyString = response.body?.string() ?: return@withContext null
            val releases = JSONArray(bodyString)
            
            if (releases.length() == 0) return@withContext null

            val latestRelease = releases.getJSONObject(0)
            val tagName = latestRelease.optString("tag_name", "").removePrefix("v")
            
            // Very simple version comparison string vs string
            if (tagName.isNotEmpty() && tagName != currentVersion) {
                val assets = latestRelease.optJSONArray("assets")
                var downloadUrl = ""
                
                if (assets != null && assets.length() > 0) {
                    // Find the first APK asset
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        if (asset.optString("name", "").endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }
                
                return@withContext UpdateInfo(
                    version = tagName,
                    releaseNotes = latestRelease.optString("body", "Bug fixes and improvements."),
                    downloadUrl = downloadUrl
                )
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
