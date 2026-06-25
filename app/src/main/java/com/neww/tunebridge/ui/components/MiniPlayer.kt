package com.neww.tunebridge.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neww.tunebridge.core.player.PlayerController
import org.koin.compose.koinInject

@Composable
fun MiniPlayer(
    playerController: PlayerController = koinInject(),
    onExpand: () -> Unit
) {
    val currentTrack by playerController.currentTrack.collectAsState()
    val isPlaying by playerController.isPlaying.collectAsState()
    val isLiked by playerController.isLiked.collectAsState()
    val currentPosition by playerController.currentPosition.collectAsState()
    val duration by playerController.duration.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    var backgroundColor by remember { mutableStateOf(Color(0xFF2E2E2E)) }
    val animatedBackgroundColor by animateColorAsState(targetValue = backgroundColor, label = "miniPlayerColor")

    LaunchedEffect(currentTrack) {
        backgroundColor = Color(0xFF2E2E2E)
    }

    if (currentTrack == null) return

    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(CircleShape)
            .background(animatedBackgroundColor)
            .clickable(onClick = onExpand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White.copy(alpha = 0.5f),
                    trackColor = Color.Transparent,
                    strokeWidth = 2.dp
                )
                
                val imageRequest = coil.request.ImageRequest.Builder(context)
                    .data(currentTrack?.albumArtUrl)
                    .allowHardware(false)
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
                                val darkMuted = palette?.darkMutedSwatch?.rgb
                                val dominant = palette?.dominantSwatch?.rgb
                                val color = darkMuted ?: dominant
                                if (color != null) {
                                    backgroundColor = Color(color)
                                }
                            }
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = currentTrack?.title ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = currentTrack?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
            
            IconButton(
                onClick = { playerController.togglePlayPause() },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = { playerController.toggleLike() },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) Color.Red else Color.White
                )
            }
        }
    }
}
