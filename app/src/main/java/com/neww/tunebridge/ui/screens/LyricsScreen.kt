package com.neww.tunebridge.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neww.tunebridge.core.player.PlayerController
import com.neww.tunebridge.core.services.LyricLine
import com.neww.tunebridge.core.services.LyricsRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    playerController: PlayerController = koinInject(),
    lyricsRepository: LyricsRepository = koinInject(),
    onClose: () -> Unit
) {
    val currentTrack by playerController.currentTrack.collectAsState()
    val currentPosition by playerController.currentPosition.collectAsState()
    var lyrics by remember { mutableStateOf<List<LyricLine>?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(currentTrack) {
        val track = currentTrack ?: return@LaunchedEffect
        isLoading = true
        lyrics = lyricsRepository.getSyncedLyrics(track.title, track.artist)
        isLoading = false
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Find active lyric index
    val activeIndex = remember(currentPosition, lyrics) {
        lyrics?.indexOfLast { it.timeMs <= currentPosition } ?: -1
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            coroutineScope.launch {
                listState.animateScrollToItem(activeIndex.coerceAtLeast(0))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lyrics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.9f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else if (lyrics == null || lyrics!!.isEmpty()) {
                Text(
                    text = "Lyrics not available.",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 150.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(lyrics!!) { index, line ->
                        val isActive = index == activeIndex
                        
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.15f else 1f,
                            animationSpec = tween(300),
                            label = "scale"
                        )
                        
                        val color by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            animationSpec = tween(300),
                            label = "color"
                        )

                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 24.sp
                            ),
                            color = color,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .scale(scale)
                        )
                    }
                }
            }
        }
    }
}
