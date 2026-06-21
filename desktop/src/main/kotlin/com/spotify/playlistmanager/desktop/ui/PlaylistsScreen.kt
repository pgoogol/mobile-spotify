package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.desktop.data.LIKED_ID
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository

private sealed interface PlaylistsUi {
    data object Loading : PlaylistsUi
    data class Error(val message: String) : PlaylistsUi
    data class Data(val playlists: List<Playlist>, val likedCount: Int) : PlaylistsUi
}

/**
 * Zakładka playlist: lista playlist użytkownika (prawdziwe dane z Web API przez
 * współdzielony [ISpotifyRepository]) z nawigacją do [TracksScreen] po kliknięciu.
 */
@Composable
fun PlaylistsScreen(repository: ISpotifyRepository) {
    var selected by remember { mutableStateOf<Playlist?>(null) }

    val current = selected
    if (current == null) {
        PlaylistsList(repository, onOpen = { selected = it })
    } else {
        TracksScreen(repository, current, onBack = { selected = null })
    }
}

@Composable
private fun PlaylistsList(repository: ISpotifyRepository, onOpen: (Playlist) -> Unit) {
    var ui by remember { mutableStateOf<PlaylistsUi>(PlaylistsUi.Loading) }

    LaunchedEffect(Unit) {
        ui = try {
            val playlists = repository.getUserPlaylists()
            val likedCount = runCatching { repository.getLikedTracksCount() }.getOrDefault(0)
            PlaylistsUi.Data(playlists, likedCount)
        } catch (e: Exception) {
            PlaylistsUi.Error(e.message ?: "Nie udało się pobrać playlist.")
        }
    }

    when (val state = ui) {
        is PlaylistsUi.Loading -> Centered {
            CircularProgressIndicator(color = SpotifyGreen)
            Text("Pobieram playlisty…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is PlaylistsUi.Error -> Centered {
            Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is PlaylistsUi.Data -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val liked = Playlist(
                id = LIKED_ID,
                name = "❤ Polubione utwory",
                description = null,
                imageUrl = null,
                trackCount = state.likedCount,
                ownerId = "",
            )
            item {
                PlaylistRow(liked, onClick = { onOpen(liked) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
            items(state.playlists) { playlist ->
                PlaylistRow(playlist, onClick = { onOpen(playlist) })
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${playlist.trackCount} utworów",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}
