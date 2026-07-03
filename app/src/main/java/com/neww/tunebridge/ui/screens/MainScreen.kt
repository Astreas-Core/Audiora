package com.neww.tunebridge.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neww.tunebridge.ui.components.MiniPlayer

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "splash"
    val isPlayerScreen = currentRoute == "player"
    val isSplashScreen = currentRoute == "splash"

    Scaffold(
        bottomBar = {
            if (!isPlayerScreen && !isSplashScreen) {
                Column {
                    MiniPlayer(onExpand = { navController.navigate("player") })
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            selected = currentRoute == "home",
                            onClick = {
                                if (currentRoute != "home") {
                                    navController.navigate("home") {
                                        popUpTo(navController.graph.startDestinationId) { saveState = false }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            selected = currentRoute.startsWith("search"),
                            onClick = {
                                if (!currentRoute.startsWith("search")) {
                                    navController.navigate("search?query=") {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                            label = { Text("Library") },
                            selected = currentRoute == "library",
                            onClick = {
                                if (currentRoute != "library") {
                                    navController.navigate("library") {
                                        popUpTo(navController.graph.startDestinationId) { saveState = false }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            NavHost(navController = navController, startDestination = "splash") {
                composable("splash") {
                    SplashScreen(onTimeout = {
                        navController.navigate("home") {
                            popUpTo("splash") { inclusive = true }
                        }
                    })
                }
                composable("home") { 
                    HomeScreen(
                        onNavigateToSettings = { navController.navigate("settings") },
                        onNavigateToSearch = { query -> navController.navigate("search?query=$query") },
                        onNavigateToPlaylist = { id -> navController.navigate("playlist/$id") }
                    ) 
                }
                composable("settings") {
                    SettingsScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("library") { 
                    LibraryScreen(onNavigateToPlaylist = { id -> navController.navigate("playlist/$id") }) 
                }
                composable(
                    "search?query={query}",
                    arguments = listOf(androidx.navigation.navArgument("query") { defaultValue = ""; type = androidx.navigation.NavType.StringType })
                ) { backStackEntry ->
                    val query = backStackEntry.arguments?.getString("query") ?: ""
                    SearchScreen(initialQuery = query) 
                }
                composable(
                    route = "player",
                    enterTransition = {
                        slideIntoContainer(
                            towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Up,
                            animationSpec = androidx.compose.animation.core.tween(300)
                        )
                    },
                    exitTransition = {
                        slideOutOfContainer(
                            towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Down,
                            animationSpec = androidx.compose.animation.core.tween(300)
                        )
                    }
                ) { 
                    PlayerScreen(onNavigateBack = { navController.popBackStack() }) 
                }
                composable("playlist/{playlistId}") { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("playlistId") ?: return@composable
                    PlaylistDetailScreen(playlistId = id, onNavigateBack = { navController.popBackStack() })
                }
            }
        }
    }
}
