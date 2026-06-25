package com.neww.tunebridge.core.models

data class TrackModel(
    val id: String,
    val title: String,
    val artist: String,
    val albumName: String,
    val albumArtUrl: String?,
    val durationMs: Long,
    val youtubeVideoId: String? = null
)
