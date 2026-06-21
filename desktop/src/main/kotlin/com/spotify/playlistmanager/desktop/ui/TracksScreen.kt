package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.spotify.playlistmanager.data.model.PlaylistStats
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.desktop.data.LIKED_ID
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository

private sealed interface TracksUi {
    data object Loading : TracksUi
    data class Error(val message: String) : TracksUi
    data class Data(val tracks: List<Track>) : TracksUi
}

/**
 * Lista utworów wybranej playlisty (lub Polubionych) z podstawowymi
 * statystykami — pobrana ze Spotify przez [ISpotifyRepository].
 */
@Composable
fun TracksScreen(repository: ISpotifyRepository, playlist: Playlist, onBack: () -> Unit) {
    var ui by remember { mutableStateOf<TracksUi>(TracksUi.Loading) }

    LaunchedEffect(playlist.id) {
        ui = try {
            val tracks = if (playlist.id == LIKED_ID) {
                repository.getLikedTracks()
            } else {
                repository.getPlaylistTracks(playlist.id)
            }
            TracksUi.Data(tracks)
        } catch (e: Exception) {
            TracksUi.Error(e.message ?: "Nie udało się pobrać utworów.")
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Wstecz") }
            Spacer(Modifier.width(8.dp))
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        when (val state = ui) {
            is TracksUi.Loading -> Centered {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text("Pobieram utwory…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            is TracksUi.Error -> Centered {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }

            is TracksUi.Data -> {
                val stats = PlaylistStats(
                    trackCount = state.tracks.size,
                    totalDurationMs = state.tracks.sumOf { it.durationMs.toLong() },
                )
                Text(
                    "${stats.trackCount} utworów · ${stats.formattedDuration()}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    itemsIndexed(state.tracks) { index, track ->
                        TrackRow(index + 1, track)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(position: Int, track: Track) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "$position",
            modifier = Modifier.width(32.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${track.artist} · ${track.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            track.formattedDuration(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
