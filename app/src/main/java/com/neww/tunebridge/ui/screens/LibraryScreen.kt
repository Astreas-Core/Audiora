package com.neww.tunebridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import com.neww.tunebridge.core.db.LocalLibraryRepository
import com.neww.tunebridge.core.models.PlaylistModel
import com.neww.tunebridge.core.models.TrackModel
import com.neww.tunebridge.core.player.PlayerController
import com.neww.tunebridge.core.services.DownloadRepository
import com.neww.tunebridge.core.services.SpotifyScraper
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToPlaylist: (String) -> Unit,
    playerController: PlayerController = koinInject(),
    repository: LocalLibraryRepository = koinInject(),
    downloadRepository: DownloadRepository = koinInject(),
    spotifyScraper: SpotifyScraper = koinInject()
) {
    val tabs = listOf("Liked Songs", "Playlists", "Downloads")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val selectedTabIndex = pagerState.currentPage

    val likedSongs by repository.getLikedSongs().collectAsState(initial = emptyList())
    val playlists by repository.getPlaylists().collectAsState(initial = emptyList())
    val downloads by downloadRepository.downloadedTracks.collectAsState(initial = emptyList())
    
    val coroutineScope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var spotifyUrl by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Your Library", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = { 
                                Text(
                                    text = title, 
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                ) 
                            }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTabIndex == 1) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    FloatingActionButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Playlist")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Create Empty Playlist") },
                            onClick = {
                                showMenu = false
                                showCreateDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Import from Spotify") },
                            onClick = {
                                showMenu = false
                                showImportDialog = true
                            }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> LikedSongsTab(
                        songs = likedSongs, 
                        playerController = playerController, 
                        repository = repository
                    )
                    1 -> PlaylistsTab(
                        playlists = playlists,
                        onNavigateToPlaylist = onNavigateToPlaylist,
                        repository = repository
                    )
                    2 -> DownloadsTab(
                        songs = downloads,
                        playerController = playerController,
                        downloadRepository = downloadRepository
                    )
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showImportDialog = false },
            title = { Text("Import Spotify Playlist") },
            text = {
                Column {
                    Text("Paste a Spotify playlist URL below to import it into TuneBridge.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = spotifyUrl,
                        onValueChange = { spotifyUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://open.spotify.com/playlist/...") },
                        singleLine = true,
                        enabled = !isImporting
                    )
                    if (isImporting) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isImporting = true
                        coroutineScope.launch {
                            try {
                                val imported = spotifyScraper.importFromUrl(spotifyUrl)
                                repository.savePlaylist(imported.second)
                                showImportDialog = false
                                spotifyUrl = ""
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            isImporting = false
                        }
                    },
                    enabled = !isImporting && spotifyUrl.isNotBlank()
                ) {
                    Text("Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }, enabled = !isImporting) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Playlist") },
            text = {
                Column {
                    Text("Enter a name for your new playlist.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Playlist Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val newPlaylist = PlaylistModel(
                                id = java.util.UUID.randomUUID().toString(),
                                name = newPlaylistName,
                                description = "Created by you",
                                imageUrl = "https://via.placeholder.com/300?text=Playlist",
                                ownerName = "You",
                                trackCount = 0
                            )
                            repository.savePlaylist(newPlaylist)
                            showCreateDialog = false
                            newPlaylistName = ""
                        }
                    },
                    enabled = newPlaylistName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DownloadsTab(
    songs: List<TrackModel>,
    playerController: PlayerController,
    downloadRepository: DownloadRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var trackToDelete by remember { mutableStateOf<TrackModel?>(null) }

    if (trackToDelete != null) {
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Delete Download") },
            text = { Text("Are you sure you want to remove '${trackToDelete?.title}' from downloads?") },
            confirmButton = {
                TextButton(onClick = {
                    val id = trackToDelete?.id
                    if (id != null) coroutineScope.launch { downloadRepository.removeDownload(id) }
                    trackToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No downloads yet.\nDownload tracks to listen offline.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(songs.size) { index ->
                val song = songs[index]
                TrackItem(
                    track = song,
                    onClick = { playerController.playQueue(songs, index) },
                    onDelete = {
                        trackToDelete = song
                    }
                )
            }
        }
    }
}

@Composable
fun LikedSongsTab(
    songs: List<TrackModel>,
    playerController: PlayerController,
    repository: LocalLibraryRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var trackToDelete by remember { mutableStateOf<TrackModel?>(null) }

    if (trackToDelete != null) {
        AlertDialog(
            onDismissRequest = { trackToDelete = null },
            title = { Text("Remove Liked Song") },
            text = { Text("Are you sure you want to remove '${trackToDelete?.title}' from Liked Songs?") },
            confirmButton = {
                TextButton(onClick = {
                    val id = trackToDelete?.id
                    if (id != null) coroutineScope.launch { repository.removeLikedSong(id) }
                    trackToDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { trackToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No liked songs yet.\nImport single tracks to see them here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(songs.size) { index ->
                val song = songs[index]
                TrackItem(
                    track = song,
                    onClick = { playerController.playQueue(songs, index) },
                    onDelete = {
                        trackToDelete = song
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistsTab(
    playlists: List<PlaylistModel>,
    onNavigateToPlaylist: (String) -> Unit,
    repository: LocalLibraryRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var playlistToDelete by remember { mutableStateOf<PlaylistModel?>(null) }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlistToDelete?.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    val id = playlistToDelete?.id
                    if (id != null) coroutineScope.launch { repository.removePlaylist(id) }
                    playlistToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (playlists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No playlists saved yet.\nImport from Home to see them here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(playlists) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onNavigateToPlaylist(playlist.id) },
                    onDelete = {
                        playlistToDelete = playlist
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistCard(playlist: PlaylistModel, onClick: () -> Unit, onDelete: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
        ) {
            val isPlaceholder = playlist.imageUrl == null || playlist.imageUrl.contains("via.placeholder.com")
            if (isPlaceholder) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFF8E24AA)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = playlist.name.firstOrNull()?.uppercase() ?: "P",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
            } else {
                AsyncImage(
                    model = playlist.imageUrl,
                    contentDescription = "Playlist Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                text = "${playlist.trackCount} tracks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), CircleShape)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete Playlist",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun TrackItem(track: TrackModel, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.albumArtUrl,
            contentDescription = "Album Art",
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
