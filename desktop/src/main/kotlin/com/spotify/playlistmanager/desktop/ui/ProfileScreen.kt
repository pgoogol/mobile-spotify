package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
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
import com.spotify.playlistmanager.data.model.TopArtist
import com.spotify.playlistmanager.data.model.UserProfile
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository

private sealed interface ProfileUi {
    data object Loading : ProfileUi
    data class Error(val message: String) : ProfileUi
    data class Data(val profile: UserProfile, val topArtists: List<TopArtist>) : ProfileUi
}

/** Profil użytkownika + top artyści (Web API przez [ISpotifyRepository]). */
@Composable
fun ProfileScreen(repository: ISpotifyRepository) {
    var ui by remember { mutableStateOf<ProfileUi>(ProfileUi.Loading) }

    LaunchedEffect(Unit) {
        ui = try {
            val profile = repository.getUserProfile()
            val artists = runCatching { repository.getTopArtists() }.getOrDefault(emptyList())
            ProfileUi.Data(profile, artists)
        } catch (e: Exception) {
            ProfileUi.Error(e.message ?: "Nie udało się pobrać profilu.")
        }
    }

    when (val state = ui) {
        is ProfileUi.Loading -> Centered {
            CircularProgressIndicator(color = SpotifyGreen)
            Text("Pobieram profil…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is ProfileUi.Error -> Centered {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }

        is ProfileUi.Data -> LazyColumn(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            item {
                Text(
                    state.profile.displayName ?: state.profile.id,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                val meta = buildList {
                    state.profile.email?.let { add(it) }
                    state.profile.country?.let { add("Kraj: $it") }
                    add("Obserwujący: ${state.profile.followers}")
                }.joinToString("  ·  ")
                Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text(
                    "Top artyści",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SpotifyGreen,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
            items(state.topArtists) { artist ->
                ArtistRow(artist)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: TopArtist) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (artist.genres.isNotEmpty()) {
                Text(
                    artist.genres.take(3).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            "★ ${artist.popularity}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
