package com.spotify.playlistmanager.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.spotify.playlistmanager.ui.screens.csv.CsvImportScreen
import com.spotify.playlistmanager.ui.screens.generate.GenerateScreen
import com.spotify.playlistmanager.ui.screens.generate.GenerateViewModel
import com.spotify.playlistmanager.ui.screens.login.LoginScreen
import com.spotify.playlistmanager.ui.screens.playlists.PlaylistsScreen
import com.spotify.playlistmanager.ui.screens.profile.ProfileScreen
import com.spotify.playlistmanager.ui.screens.settings.SettingsScreen
import com.spotify.playlistmanager.ui.screens.stepwise.StepwiseScreen
import com.spotify.playlistmanager.ui.screens.templates.TemplatesScreen
import com.spotify.playlistmanager.ui.screens.tracks.TracksScreen
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

// ── Definicje ekranów ────────────────────────────────────────────────────────

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Playlists : Screen("playlists")
    data object Tracks : Screen("tracks/{playlistId}/{playlistName}") {
        fun createRoute(id: String, name: String) =
            "tracks/${java.net.URLEncoder.encode(id, "UTF-8")}/${
                java.net.URLEncoder.encode(
                    name,
                    "UTF-8"
                )
            }"
    }

    data object Generate : Screen("generate")
    data object Stepwise : Screen("stepwise")
    data object Templates : Screen("templates")
    data object Settings : Screen("settings")
    data object Profile : Screen("profile")
    data object CsvImport : Screen("csv_import")
}

// ── Zakładki dolnej nawigacji ────────────────────────────────────────────────

private data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Playlists, "Playlisty", Icons.Default.LibraryMusic),
    BottomNavItem(Screen.Generate, "Generuj", Icons.Default.AutoAwesome),
    BottomNavItem(Screen.Stepwise, "Krok", Icons.Default.Queue),
    BottomNavItem(Screen.Profile, "Profil", Icons.Default.Person)
)

private val bottomBarRoutes = setOf(
    Screen.Playlists.route,
    Screen.Generate.route,
    Screen.Stepwise.route,
    Screen.Profile.route
)

// ── Główny szkielet z BottomNavigationBar ────────────────────────────────────

@Composable
fun AppScaffold(
    navController: NavHostController,
    startDestination: String,
    bottomContent: @Composable () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                // tonalElevation używa standardowego 8.dp z importu androidx.compose.ui.unit
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentRoute == item.screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(Screen.Playlists.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold
                                        else FontWeight.Normal
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SpotifyGreen,
                                selectedTextColor = SpotifyGreen,
                                indicatorColor = SpotifyGreen.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            } else {
                bottomContent()
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AppNavGraph(navController = navController, startDestination = startDestination)
        }
    }
}

// ── Graf nawigacji ────────────────────────────────────────────────────────────

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Playlists.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Playlists.route) {
            PlaylistsScreen(
                onPlaylistClick = { id, name ->
                    navController.navigate(Screen.Tracks.createRoute(id, name))
                },
                onGenerateClick = { navController.navigate(Screen.Generate.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Tracks.route) { back ->
            val id = back.arguments?.getString("playlistId")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: return@composable
            val name = back.arguments?.getString("playlistName")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            TracksScreen(
                playlistId = id,
                playlistName = name,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Generate.route) {
            // GenerateViewModel współdzielony z TemplatesScreen przez back-stack
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Screen.Generate.route)
            }
            val viewModel: GenerateViewModel = hiltViewModel(parentEntry)

            GenerateScreen(
                onBack = { navController.popBackStack() },
                onTemplates = { navController.navigate(Screen.Templates.route) },
                viewModel = viewModel
            )
        }

        composable(Screen.Templates.route) {
            // Współdzielony ViewModel z GenerateScreen
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Screen.Generate.route)
            }
            val viewModel: GenerateViewModel = hiltViewModel(parentEntry)
            val templates by viewModel.templates.collectAsStateWithLifecycle()

            TemplatesScreen(
                templates = templates,
                onLoadTemplate = { template ->
                    viewModel.loadTemplate(template)
                    navController.popBackStack()
                },
                onRenameTemplate = viewModel::renameTemplate,
                onDeleteTemplate = viewModel::deleteTemplate,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onCsvImport = { navController.navigate(Screen.CsvImport.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Stepwise.route) {
            StepwiseScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Profile.route) {
            ProfileScreen()
        }
        composable(Screen.CsvImport.route) {
            CsvImportScreen(onBack = { navController.popBackStack() })
        }
    }
}