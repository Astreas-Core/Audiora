package com.neww.tunebridge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neww.tunebridge.core.db.SettingsRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository = koinInject(),
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val audioQuality by settingsRepository.audioQualityFlow.collectAsState(initial = "Auto")
    val theme by settingsRepository.themeFlow.collectAsState(initial = "System Default")

    var showQualitySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item {
                SettingsCategory(title = "Audio")
                SettingsItem(
                    icon = Icons.Default.Audiotrack,
                    title = "Audio Quality",
                    subtitle = audioQuality,
                    onClick = { showQualitySheet = true }
                )
                
                val crossfadeEnabled by settingsRepository.crossfadeEnabledFlow.collectAsState(initial = true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Crossfade Playback",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Smoothly fade volume between tracks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = crossfadeEnabled,
                        onCheckedChange = {
                            coroutineScope.launch {
                                settingsRepository.setCrossfadeEnabled(it)
                            }
                        }
                    )
                }
            }

            item {
                SettingsCategory(title = "Appearance")
                SettingsItem(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    subtitle = theme,
                    onClick = {
                        coroutineScope.launch {
                            val nextTheme = when (theme) {
                                "System Default" -> "Dark"
                                "Dark" -> "Light"
                                else -> "System Default"
                            }
                            settingsRepository.setTheme(nextTheme)
                        }
                    }
                )
            }
            
            item {
                SettingsCategory(title = "Storage")
                SettingsItem(
                    icon = Icons.Default.DeleteOutline,
                    title = "Clear Cache",
                    subtitle = "Free up space by deleting cached images and temporary files",
                    onClick = { /* Clear Cache Logic */ }
                )
            }
            
            item {
                SettingsCategory(title = "About")
                SettingsItem(
                    icon = Icons.Default.Update,
                    title = "Check for Updates",
                    subtitle = "Version 1.0.0",
                    onClick = { /* OTA Logic */ }
                )
            }
        }
    }

    if (showQualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { showQualitySheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Audio Quality",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QualityButton(
                        text = "Auto",
                        isSelected = audioQuality == "Auto",
                        onClick = { 
                            coroutineScope.launch { settingsRepository.setAudioQuality("Auto") }
                            showQualitySheet = false
                        }
                    )
                    QualityButton(
                        text = "High",
                        isSelected = audioQuality == "High",
                        onClick = { 
                            coroutineScope.launch { settingsRepository.setAudioQuality("High") }
                            showQualitySheet = false
                        }
                    )
                    QualityButton(
                        text = "Low",
                        isSelected = audioQuality == "Low",
                        onClick = { 
                            coroutineScope.launch { settingsRepository.setAudioQuality("Low") }
                            showQualitySheet = false
                        }
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun QualityButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
        )
    ) {
        Text(text)
    }
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
