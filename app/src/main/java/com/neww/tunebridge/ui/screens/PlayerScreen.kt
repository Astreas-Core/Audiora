package com.neww.tunebridge.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.neww.tunebridge.core.player.PlayerController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit = {},
    playerController: PlayerController = koinInject(),
    downloadRepository: com.neww.tunebridge.core.services.DownloadRepository = koinInject(),
    equalizerRepository: com.neww.tunebridge.core.services.EqualizerRepository = koinInject(),
    libraryRepository: com.neww.tunebridge.core.db.LocalLibraryRepository = koinInject()
) {
    val currentTrack by playerController.currentTrack.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val currentPosition by playerController.currentPosition.collectAsState()
    val duration by playerController.duration.collectAsState()
    val repeatMode by playerController.repeatMode.collectAsState()
    val shuffleModeEnabled by playerController.shuffleModeEnabled.collectAsState()
    val isLiked by playerController.isLiked.collectAsState()

    var sliderPosition by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    
    var showSleepTimer by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    
    val sleepTimerRemaining by playerController.sleepTimerRemaining.collectAsState()

    // Update slider position unless user is dragging it
    LaunchedEffect(currentPosition) {
        if (!isDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    val formatTime = { ms: Long ->
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        String.format("%02d:%02d", minutes, seconds)
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    var backgroundColor by remember { mutableStateOf(Color(0xFF121212)) }
    var topColor by remember { mutableStateOf(Color(0xFF2E2E2E)) }
    val animatedTopColor by animateColorAsState(targetValue = topColor, animationSpec = tween(1000), label = "topColor")
    val animatedBgColor by animateColorAsState(targetValue = backgroundColor, animationSpec = tween(1000), label = "bgColor")

    // Reset colors when track changes
    LaunchedEffect(currentTrack) {
        if (currentTrack != null) {
            topColor = Color(0xFF2E2E2E)
            backgroundColor = Color(0xFF121212)
        }
    }

    // Dynamic gradient background
    val backgroundBrush = remember(animatedTopColor, animatedBgColor) {
        Brush.verticalGradient(
            colors = listOf(animatedTopColor, animatedBgColor)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(48.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = currentTrack?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.basicMarquee()
                    )
                }
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White)
                }
            }

            // Album Art with Palette extraction
            val imageRequest = coil.request.ImageRequest.Builder(context)
                .data(currentTrack?.albumArtUrl)
                .allowHardware(false) // Required for Palette
                .crossfade(true)
                .build()

            AsyncImage(
                model = imageRequest,
                contentDescription = "Album Art",
                onSuccess = { state ->
                    try {
                        val drawable = state.result.drawable
                        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap 
                            ?: run {
                                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
                                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
                                val b = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(b)
                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                drawable.draw(canvas)
                                b
                            }
                        
                        androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                            val dominant = palette?.dominantSwatch?.rgb
                            val darkMuted = palette?.darkMutedSwatch?.rgb
                            if (dominant != null) {
                                topColor = Color(dominant)
                                backgroundColor = if (darkMuted != null) Color(darkMuted) else Color(dominant).copy(alpha = 0.3f)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.DarkGray),
                contentScale = ContentScale.Crop
            )

            // Track Info and Like/Download Buttons
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text = currentTrack?.title ?: "No Track Playing",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentTrack?.artist ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                    
                    Row {
                        val coroutineScope = rememberCoroutineScope()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        var isDownloading by remember { mutableStateOf(false) }
                        val downloads by downloadRepository.downloadedTracks.collectAsState(initial = emptyList())
                        val isTrackDownloaded = currentTrack != null && downloads.any { it.id == currentTrack?.id }

                        IconButton(
                            onClick = { 
                                currentTrack?.let { track ->
                                    if (!isTrackDownloaded) {
                                        isDownloading = true
                                        coroutineScope.launch {
                                            val success = downloadRepository.downloadTrack(track)
                                            isDownloading = false
                                            if (success) {
                                                android.widget.Toast.makeText(context, "Downloaded successfully", android.widget.Toast.LENGTH_SHORT).show()
                                            } else {
                                                android.widget.Toast.makeText(context, "Download failed", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "Already downloaded", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .size(48.dp),
                            enabled = !isDownloading
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                            } else {
                                Icon(
                                    imageVector = if (isTrackDownloaded) Icons.Default.Check else Icons.Default.Download, 
                                    contentDescription = "Download", 
                                    tint = Color.Black
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { playerController.toggleLike() },
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) Color.Red else Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Bar
                val maxDuration = if (duration > 0) duration.toFloat() else 100f
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        playerController.seekTo(sliderPosition.toLong())
                    },
                    valueRange = 0f..maxDuration,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(sliderPosition.toLong()), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    Text(text = formatTime(duration), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Playback Controls (Pill Shaped Play)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Button
                    IconButton(
                        onClick = { playerController.skipToPrevious() },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .size(64.dp)
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    
                    // Animated Pill Play/Pause Button
                    val playButtonScale by animateFloatAsState(
                        targetValue = if (isPlaying) 1.02f else 1.0f,
                        animationSpec = spring(),
                        label = "playScale"
                    )
                    
                    Box(
                        modifier = Modifier
                            .height(64.dp)
                            .width(160.dp)
                            .scale(playButtonScale)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { playerController.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPlaying) "Pause" else "Play",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    
                    // Next Button
                    IconButton(
                        onClick = { playerController.skipToNext() },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            .size(64.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))

                // Utility Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showLyrics = true }) {
                        Icon(Icons.Default.Lyrics, contentDescription = "Lyrics", tint = Color.White)
                    }
                    IconButton(onClick = { showSleepTimer = true }) {
                        val tint = if (sleepTimerRemaining != null) MaterialTheme.colorScheme.primary else Color.White
                        Icon(Icons.Default.Bedtime, contentDescription = "Sleep Timer", tint = tint)
                    }
                    IconButton(onClick = { showEqualizer = true }) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer", tint = Color.White)
                    }
                    IconButton(onClick = { playerController.toggleRepeatMode() }) {
                        val repeatIcon = when(repeatMode) {
                            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        }
                        Icon(repeatIcon, contentDescription = "Repeat", tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showLyrics) {
        LyricsScreen(onClose = { showLyrics = false })
    }

    if (showSleepTimer) {
        AlertDialog(
            onDismissRequest = { showSleepTimer = false },
            title = { Text("Sleep Timer") },
            text = {
                Column {
                    if (sleepTimerRemaining != null) {
                        Text("Time remaining: ${formatTime(sleepTimerRemaining!!)}")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { playerController.cancelSleepTimer(); showSleepTimer = false }) {
                            Text("Cancel Timer")
                        }
                    } else {
                        listOf(15L, 30L, 60L).forEach { minutes ->
                            Text(
                                text = "$minutes Minutes",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerController.startSleepTimer(minutes)
                                        showSleepTimer = false
                                    }
                                    .padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimer = false }) { Text("Close") }
            }
        )
    }

    if (showEqualizer) {
        ModalBottomSheet(onDismissRequest = { showEqualizer = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Equalizer", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                // Equalizer is already initialized by PlaybackService with the correct audioSessionId
                
                val bands = equalizerRepository.getBands()
                if (bands.isEmpty()) {
                    Text("Equalizer not supported on this device.")
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(bands.size) { index ->
                            val band = bands[index]
                            var level by remember(band) { mutableStateOf(equalizerRepository.getBandLevel(band)) }
                            val range = equalizerRepository.getBandLevelRange() ?: shortArrayOf(0, 0)
                            val freq = equalizerRepository.getCenterFreq(band) / 1000
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (freq >= 1000) "${freq/1000} kHz" else "$freq Hz",
                                    modifier = Modifier.width(60.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Slider(
                                    value = level.toFloat(),
                                    onValueChange = {
                                        level = it.toInt().toShort()
                                        equalizerRepository.setBandLevel(band, level)
                                    },
                                    valueRange = range[0].toFloat()..range[1].toFloat(),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${level / 100} dB",
                                    modifier = Modifier.width(60.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(onClick = {
                            for (b in bands) {
                                equalizerRepository.setBandLevel(b, 1000)
                            }
                        }) {
                            Text("Bass Boost")
                        }
                        Button(onClick = {
                            for (b in bands) {
                                equalizerRepository.setBandLevel(b, 0)
                            }
                        }) {
                            Text("Flat")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showMoreMenu) {
        var showPlaylistSelection by remember { mutableStateOf(false) }
        val playlists by libraryRepository.getPlaylists().collectAsState(initial = emptyList())
        val coroutineScope = rememberCoroutineScope()

        ModalBottomSheet(onDismissRequest = { showMoreMenu = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(if (showPlaylistSelection) "Select Playlist" else "Options", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                if (showPlaylistSelection) {
                    if (playlists.isEmpty()) {
                        Text("No playlists available.")
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(playlists.size) { index ->
                                val playlist = playlists[index]
                                ListItem(
                                    headlineContent = { Text(playlist.name) },
                                    leadingContent = { Icon(Icons.Default.QueueMusic, null) },
                                    modifier = Modifier.clickable {
                                        currentTrack?.let { track ->
                                            coroutineScope.launch {
                                                val updatedTracks = playlist.tracks.toMutableList()
                                                if (updatedTracks.none { it.id == track.id }) {
                                                    updatedTracks.add(track)
                                                    val updatedPlaylist = playlist.copy(
                                                        tracks = updatedTracks,
                                                        trackCount = updatedTracks.size
                                                    )
                                                    libraryRepository.savePlaylist(updatedPlaylist)
                                                    android.widget.Toast.makeText(context, "Added to ${playlist.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    android.widget.Toast.makeText(context, "Already in ${playlist.name}", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                showMoreMenu = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    ListItem(
                        headlineContent = { Text("Add to Playlist") },
                        leadingContent = { Icon(Icons.Default.PlaylistAdd, null) },
                        modifier = Modifier.clickable { showPlaylistSelection = true }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
