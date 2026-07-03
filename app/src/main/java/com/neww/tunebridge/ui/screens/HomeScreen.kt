package com.neww.tunebridge.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neww.tunebridge.core.db.LocalLibraryRepository
import com.neww.tunebridge.core.models.PlaylistModel
import com.neww.tunebridge.core.models.TrackModel
import com.neww.tunebridge.core.player.PlayerController
import com.neww.tunebridge.core.services.SpotifyScraper
import org.koin.compose.koinInject
import java.util.Calendar
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    spotifyScraper: SpotifyScraper = koinInject(),
    repository: LocalLibraryRepository = koinInject(),
    playerController: PlayerController = koinInject(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: (String) -> Unit = {},
    onNavigateToPlaylist: (String) -> Unit = {}
) {
    val recentTracks by repository.getRecentTracks().collectAsState(initial = emptyList())
    val playlists by repository.getPlaylists().collectAsState(initial = emptyList())
    val likedSongs by repository.getLikedSongs().collectAsState(initial = emptyList())

    var greeting by remember { mutableStateOf("Good Morning") }
    LaunchedEffect(Unit) {
        while (true) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            greeting = when {
                hour < 12 -> "Good Morning"
                hour < 18 -> "Good Afternoon"
                else -> "Good Evening"
            }
            kotlinx.coroutines.delay(60000)
        }
    }
    
    val isPlaying by playerController.isPlaying.collectAsState()

    val genres = listOf(
        "Top Hits" to ("top hits" to com.neww.tunebridge.R.drawable.mood_top_hits),
        "Workout" to ("workout mix" to com.neww.tunebridge.R.drawable.mood_workout),
        "Commute" to ("lofi commute" to com.neww.tunebridge.R.drawable.mood_commute),
        "Feel Good" to ("feel good songs" to com.neww.tunebridge.R.drawable.mood_feel_good),
        "Relax" to ("relaxing music" to com.neww.tunebridge.R.drawable.mood_relax)
    )

    val featuredItems = remember(playlists, likedSongs) {
        val items = mutableListOf<Any>()
        items.addAll(playlists)
        
        val tracksInPlaylists = playlists.flatMap { it.tracks }.map { it.id }.toSet()
        val filteredLiked = likedSongs.filter { it.id !in tracksInPlaylists }
        items.addAll(filteredLiked.take(5))
        
        items.distinctBy { 
            when (it) {
                is PlaylistModel -> it.id
                is TrackModel -> it.id
                else -> it.hashCode()
            }
        }.shuffled().take(6)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(greeting, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
                        AudioVisualizer(isPlaying = isPlaying)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
        ) {
            // 1. Trending / Featured Carousel
            if (featuredItems.isNotEmpty()) {
                item {
                    Text(
                        text = "Featured for You",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(featuredItems) { item ->
                            val title = when (item) {
                                is PlaylistModel -> item.name
                                is TrackModel -> item.title
                                else -> ""
                            }
                            val subtitle = when (item) {
                                is PlaylistModel -> "Playlist • ${item.trackCount} tracks"
                                is TrackModel -> "Song • ${item.artist}"
                                else -> ""
                            }
                            val imageUrl = when (item) {
                                is PlaylistModel -> item.imageUrl
                                is TrackModel -> item.albumArtUrl
                                else -> null
                            }
                            FeaturedCard(
                                title = title,
                                subtitle = subtitle,
                                imageUrl = imageUrl,
                                onClick = {
                                    when (item) {
                                        is TrackModel -> playerController.playTrack(item)
                                        is PlaylistModel -> onNavigateToPlaylist(item.id)
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // 2. Genre / Mood Cards
            item {
                Text(
                    text = "Mood & Genres",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(genres) { (label, data) ->
                        val (query, drawableResId) = data
                        GenreCard(
                            label = label,
                            drawableResId = drawableResId,
                            onClick = { onNavigateToSearch(query) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // 3. Recently Played List
            if (recentTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently Played",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                items(recentTracks.take(15)) { track ->
                    RecentlyPlayedItem(
                        track = track,
                        onClick = { playerController.playTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
fun FeaturedCard(title: String, subtitle: String, imageUrl: String?, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .width(260.dp)
            .height(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Bottom Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        startY = 150f
                    )
                )
        )
        // Text Overlaid
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GenreCard(label: String, drawableResId: Int, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = drawableResId),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun RecentlyPlayedItem(track: TrackModel, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.albumArtUrl,
            contentDescription = null,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { /* Handle More */ }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AudioVisualizer(isPlaying: Boolean) {
    val barCount = 4
    Row(
        modifier = Modifier.height(24.dp).padding(start = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0 until barCount) {
            var targetHeight by remember { mutableStateOf(4f) }
            val height by animateFloatAsState(targetValue = targetHeight, animationSpec = spring(), label = "height")
            
            LaunchedEffect(isPlaying) {
                if (isPlaying) {
                    while (true) {
                        targetHeight = Random.nextInt(6, 24).toFloat()
                        kotlinx.coroutines.delay(Random.nextLong(100, 300))
                    }
                } else {
                    targetHeight = 4f
                }
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
