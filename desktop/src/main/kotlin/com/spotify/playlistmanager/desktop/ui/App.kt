package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.desktop.config.AppConfig
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen

/**
 * Korzeń aplikacji — odpowiednik `MainActivity` + `AppScaffold` z mobile.
 * Routuje: brak konfiguracji → logowanie → główna powłoka z dolną nawigacją.
 */
@Composable
fun App() {
    val clientId = remember { AppConfig.clientId }
    if (clientId == null) {
        ConfigMissingScreen()
        return
    }

    val client = remember { SpotifyClient(clientId) }
    var loggedIn by remember { mutableStateOf(client.tokenStore.isLoggedIn) }

    if (!loggedIn) {
        LoginScreen(client = client, onLoggedIn = { loggedIn = true })
    } else {
        MainShell(client = client, onLogout = { client.tokenStore.clear(); loggedIn = false })
    }
}

private val bottomTabs = listOf(Route.Playlists, Route.Generate, Route.Stepwise, Route.Profile)

private fun tabIcon(tab: Route.Tab): ImageVector = when (tab) {
    Route.Playlists -> Icons.Default.LibraryMusic
    Route.Generate -> Icons.Default.AutoAwesome
    Route.Stepwise -> Icons.Default.Queue
    Route.Profile -> Icons.Default.Person
}

/** Główna powłoka z dolnym paskiem nawigacji (odwzorowanie `AppScaffold`). */
@Composable
fun MainShell(client: SpotifyClient, onLogout: () -> Unit) {
    val nav = remember { Navigator() }
    val current = nav.current
    val showBottomBar = current is Route.Tab

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = current == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) nav.selectTab(tab) },
                            icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                            label = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SpotifyGreen,
                                selectedTextColor = SpotifyGreen,
                                indicatorColor = SpotifyGreen.copy(alpha = 0.15f),
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val route = current) {
                Route.Playlists -> PlaylistsScreen(
                    repository = client.repository,
                    onPlaylistClick = { id, name -> nav.push(Route.Tracks(id, name)) },
                    onGenerateClick = { nav.selectTab(Route.Generate) },
                    onSettingsClick = { nav.push(Route.Settings) },
                )
                Route.Generate -> GeneratorRealScreen(client)
                Route.Stepwise -> PartyPlannerScreen(client)
                Route.Profile -> ProfileScreen(client.repository)
                is Route.Tracks -> TracksScreen(
                    client = client,
                    playlistId = route.playlistId,
                    playlistName = route.playlistName,
                    onBack = { nav.pop() },
                )
                Route.Settings -> SettingsScreen(
                    client = client,
                    onBack = { nav.pop() },
                    onCsvImport = { nav.push(Route.CsvImport) },
                    onLogout = onLogout,
                )
                Route.CsvImport -> CsvImportScreen(client = client, onBack = { nav.pop() })
            }
        }
    }
}

@Composable
private fun ConfigMissingScreen() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Brak konfiguracji Spotify Client ID",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                modifier = Modifier.widthIn(max = 640.dp),
                text = "Ustaw Client ID w local.properties (spotify.clientId=...), zmiennej " +
                    "środowiskowej SPOTIFY_CLIENT_ID lub argumencie -Dspotify.clientId=...\n\n" +
                    "W Spotify Developer Dashboard dodaj Redirect URI: ${AppConfig.REDIRECT_URI}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
