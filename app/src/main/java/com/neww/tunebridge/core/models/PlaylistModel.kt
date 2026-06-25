package com.neww.tunebridge.core.models

data class PlaylistModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val imageUrl: String?,
    val trackCount: Int,
    val ownerName: String,
    val tracks: List<TrackModel> = emptyList()
)
