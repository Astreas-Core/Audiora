package com.neww.tunebridge.core.services

import com.google.gson.Gson
import com.neww.tunebridge.core.models.PlaylistModel
import com.neww.tunebridge.core.models.TrackModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

class SpotifyScraper(private val okHttpClient: OkHttpClient, private val gson: Gson) {
    private val userAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private val nextDataPattern = Pattern.compile("<script\\s+id=\"__NEXT_DATA__\"\\s+type=\"application/json\">\\s*(\\{.+?\\})\\s*</script>", Pattern.DOTALL)

    data class ParsedUrl(val type: String, val id: String)

    fun parseSpotifyUrl(input: String): ParsedUrl? {
        val uriRegex = Regex("^spotify:(track|album|playlist):([A-Za-z0-9]+)")
        uriRegex.find(input.trim())?.let { match ->
            return ParsedUrl(match.groupValues[1], match.groupValues[2])
        }

        val urlRegex = Regex("open\\.spotify\\.com/(track|album|playlist)/([A-Za-z0-9]+)")
        urlRegex.find(input.trim())?.let { match ->
            return ParsedUrl(match.groupValues[1], match.groupValues[2])
        }
        return null
    }

    private suspend fun fetchEntity(type: String, id: String): Map<String, Any>? = withContext(Dispatchers.IO) {
        val url = "https://open.spotify.com/embed/$type/$id"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Spotify embed returned ${response.code} for $type/$id")
            val body = response.body?.string() ?: throw Exception("Empty body")
            
            val matcher = nextDataPattern.matcher(body)
            if (matcher.find()) {
                val jsonStr = matcher.group(1)
                val json = gson.fromJson(jsonStr, Map::class.java) as Map<String, Any>
                val props = json["props"] as? Map<String, Any>
                val pageProps = props?.get("pageProps") as? Map<String, Any>
                val state = pageProps?.get("state") as? Map<String, Any>
                val data = state?.get("data") as? Map<String, Any>
                return@withContext data?.get("entity") as? Map<String, Any>
            }
            throw Exception("No __NEXT_DATA__ found in embed page")
        }
    }

    private fun getLargestImageUrl(coverArt: List<Any>?): String? {
        if (coverArt.isNullOrEmpty()) return null
        var largestImage: Map<String, Any>? = null
        var maxWidth = -1

        for (source in coverArt) {
            val map = source as? Map<String, Any> ?: continue
            val w = (map["width"] as? Double)?.toInt() ?: 0
            if (w >= maxWidth) {
                maxWidth = w
                largestImage = map
            }
        }
        return (largestImage ?: coverArt.firstOrNull() as? Map<String, Any>)?.get("url") as? String
    }

    private fun extractArtistFromEntity(entity: Map<String, Any>): String {
        val authors = entity["authors"] as? List<Map<String, Any>>
        if (!authors.isNullOrEmpty()) {
            return authors.first()["name"] as? String ?: "Unknown"
        }
        val artists = entity["artists"] as? List<Any>
        if (!artists.isNullOrEmpty()) {
            val first = artists.first()
            if (first is Map<*, *>) return first["name"] as? String ?: "Unknown"
            return first.toString()
        }
        return "Unknown"
    }

    suspend fun getTracks(type: String, id: String): Pair<PlaylistModel, List<TrackModel>> = withContext(Dispatchers.IO) {
        val entity = fetchEntity(type, id) ?: throw Exception("Could not fetch entity for $type")
        
        val coverArtSources = (entity["coverArt"] as? Map<String, Any>)?.get("sources") as? List<Any>
        var playlistImageUrl = getLargestImageUrl(coverArtSources)
        
        if (playlistImageUrl == null) {
            val images = entity["images"] as? List<Any>
            playlistImageUrl = getLargestImageUrl(images)
        }
        if (playlistImageUrl == null) {
            playlistImageUrl = entity["thumbnailUrl"] as? String
        }
        
        // Extract trackList carefully since Gson parses it as ArrayList<LinkedTreeMap>
        val trackListRaw = entity["trackList"] as? List<*>
        val trackList = trackListRaw?.mapNotNull { it as? Map<String, Any> }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(entity)
        
        val playlistName = entity["name"] as? String ?: entity["title"] as? String ?: "Unknown"
        val playlist = PlaylistModel(
            id = entity["id"] as? String ?: id,
            name = playlistName,
            imageUrl = playlistImageUrl,
            trackCount = trackList.size,
            ownerName = extractArtistFromEntity(entity),
            tracks = emptyList() // will copy after collecting
        )

        val tracks = coroutineScope {
            trackList.map { item ->
                async(Dispatchers.IO) {
                    val uri = item["uri"] as? String ?: ""
                    val trackId = if (uri.contains(":")) uri.split(":").last() else "embed_${java.util.UUID.randomUUID()}"
                    
                    var trackArtwork: String? = null
                    val coverArt = item["coverArt"] as? Map<String, Any>
                        ?: (item["album"] as? Map<String, Any>)?.get("coverArt") as? Map<String, Any>
                    val sources = coverArt?.get("sources") as? List<Any>
                    trackArtwork = getLargestImageUrl(sources)

                    if (trackArtwork == null && uri.startsWith("spotify:track:")) {
                        try {
                            val req = Request.Builder()
                                .url("https://open.spotify.com/oembed?url=$uri")
                                .header("User-Agent", userAgent)
                                .build()
                            okHttpClient.newCall(req).execute().use { res ->
                                if (res.isSuccessful) {
                                    val body = res.body?.string()
                                    if (!body.isNullOrEmpty()) {
                                        val json = gson.fromJson(body, Map::class.java) as Map<String, Any>
                                        trackArtwork = json["thumbnail_url"] as? String
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    trackArtwork = trackArtwork ?: playlistImageUrl

                    TrackModel(
                        id = trackId,
                        title = item["title"] as? String ?: item["name"] as? String ?: "Unknown",
                        artist = item["subtitle"] as? String ?: extractArtistFromEntity(item),
                        albumName = "", 
                        albumArtUrl = trackArtwork,
                        durationMs = (item["duration"] as? Double)?.toLong() ?: 0L
                    )
                }
            }.awaitAll()
        }
        
        return@withContext Pair(playlist.copy(tracks = tracks.toList()), tracks.toList())
    }

    suspend fun importFromUrl(url: String): Triple<String, PlaylistModel, List<TrackModel>> {
        val parsed = parseSpotifyUrl(url) ?: throw Exception("Invalid Spotify URL")
        val (playlist, tracks) = getTracks(parsed.type, parsed.id)
        return Triple(parsed.type, playlist, tracks)
    }
}
