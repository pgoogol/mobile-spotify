package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.desktop.config.AppConfig
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import kotlinx.coroutines.launch

/**
 * Ekran logowania. Po kliknięciu uruchamia desktopowy przepływ OAuth
 * (loopback) — otwiera przeglądarkę i czeka na redirect.
 */
@Composable
fun LoginScreen(client: SpotifyClient, onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Spotify Playlist Manager",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Wersja desktopowa",
                style = MaterialTheme.typography.titleMedium,
                color = SpotifyGreen,
            )

            if (busy) {
                CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(36.dp))
                Text(
                    "Trwa logowanie — dokończ w przeglądarce…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(onClick = {
                    error = null
                    busy = true
                    scope.launch {
                        val result = client.authenticator.login()
                        result.onSuccess {
                            runCatching { client.repository.fetchAndCacheCurrentUser() }
                            busy = false
                            onLoggedIn()
                        }.onFailure {
                            busy = false
                            error = it.message ?: "Nieznany błąd logowania"
                        }
                    }
                }) {
                    Text("Zaloguj się przez Spotify")
                }
            }

            error?.let {
                Text(
                    it,
                    modifier = Modifier.widthIn(max = 560.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                "Redirect URI do zarejestrowania w dashboardzie: ${AppConfig.REDIRECT_URI}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
