package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen

private enum class Tab(val label: String) {
    PLAYLISTS("Playlisty"),
    GENERATOR("Generator"),
    DJ("Impreza DJ"),
    PROFILE("Profil"),
    DEMO("Demo (przykładowe)"),
}

/**
 * Ekran główny po zalogowaniu: pasek górny + prosta nawigacja między
 * listą playlist (prawdziwe dane) a generatorem krzywych energii.
 */
@Composable
fun MainScreen(client: SpotifyClient, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.PLAYLISTS) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Pasek górny ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Spotify Playlist Manager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                client.tokenStore.displayName?.let { name ->
                    Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                OutlinedButton(onClick = onLogout) { Text("Wyloguj") }
            }

            // ── Nawigacja ─────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tab.entries.forEach { entry ->
                    NavButton(entry.label, selected = tab == entry) { tab = entry }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            )

            // ── Treść ─────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.PLAYLISTS -> PlaylistsScreen(client.repository)
                    Tab.GENERATOR -> GeneratorRealScreen(client)
                    Tab.DJ -> PartyPlannerScreen(client)
                    Tab.PROFILE -> ProfileScreen(client.repository)
                    Tab.DEMO -> GeneratorScreen()
                }
            }
        }
    }
}

@Composable
private fun NavButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            color = if (selected) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
