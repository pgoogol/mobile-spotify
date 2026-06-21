package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.desktop.config.AppConfig
import com.spotify.playlistmanager.desktop.data.SpotifyClient

/**
 * Korzeń aplikacji desktopowej. Routuje między:
 *  • ekranem braku konfiguracji (gdy brak Client ID),
 *  • ekranem logowania (gdy brak ważnej sesji),
 *  • ekranem głównym (po zalogowaniu).
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
        MainScreen(
            client = client,
            onLogout = {
                client.tokenStore.clear()
                loggedIn = false
            },
        )
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
                text = "Ustaw Client ID jednym ze sposobów:\n" +
                    "• dodaj do local.properties:  spotify.clientId=TWOJ_CLIENT_ID\n" +
                    "• albo zmienna środowiskowa:  SPOTIFY_CLIENT_ID=...\n" +
                    "• albo argument JVM:  -Dspotify.clientId=...\n\n" +
                    "W Spotify Developer Dashboard dodaj Redirect URI:\n" +
                    "    ${AppConfig.REDIRECT_URI}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
