package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.playlistmanager.data.model.TopArtist
import com.spotify.playlistmanager.data.model.UserProfile
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.desktop.theme.SpotifyMidGray
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Stan ekranu profilu — odpowiednik mobilnego `ProfileUiState`. */
private data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: UserProfile? = null,
    val topArtists: List<TopArtist> = emptyList(),
    val likedCount: Int = 0,
    val error: String? = null,
)

/**
 * Profil użytkownika — desktopowe odwzorowanie mobilnego `ProfileScreen`:
 * nagłówek z avatarem (nazwa, e-mail, kraj), karta statystyk (polubione,
 * obserwujący), sekcja „Ulubieni artyści" z okładkami/gatunkami/popularnością
 * oraz „Ulubione gatunki" wyliczone z top artystów. Dane z Web API przez
 * [ISpotifyRepository] (suspend), ładowane równolegle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(repository: ISpotifyRepository) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ProfileUiState()) }

    fun load() {
        scope.launch {
            state = ProfileUiState(isLoading = true)
            runCatching {
                coroutineScope {
                    val profileDeferred = async { repository.getUserProfile() }
                    val topArtistsDeferred = async { runCatching { repository.getTopArtists() }.getOrDefault(emptyList()) }
                    val likedCountDeferred = async { runCatching { repository.getLikedTracksCount() }.getOrDefault(0) }
                    Triple(profileDeferred.await(), topArtistsDeferred.await(), likedCountDeferred.await())
                }
            }.onSuccess { (profile, topArtists, likedCount) ->
                state = ProfileUiState(
                    isLoading = false,
                    profile = profile,
                    topArtists = topArtists,
                    likedCount = likedCount,
                )
            }.onFailure { e ->
                state = ProfileUiState(isLoading = false, error = e.message ?: "Nie udało się pobrać profilu.")
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Centered {
                    CircularProgressIndicator(color = SpotifyGreen)
                    Text("Pobieram profil…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                state.error != null -> ErrorView(state.error!!) { load() }

                else -> ProfileContent(state = state)
            }
        }
    }
}

@Composable
private fun ProfileContent(state: ProfileUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── Header z avatarem ───────────────────────────────────────────────
        item { ProfileHeader(profile = state.profile) }

        // ── Statystyki ──────────────────────────────────────────────────────
        item {
            StatsRow(
                likedCount = state.likedCount,
                followers = state.profile?.followers ?: 0,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // ── Ulubieni artyści ────────────────────────────────────────────────
        if (state.topArtists.isNotEmpty()) {
            item {
                SectionTitle(
                    title = "Ulubieni artyści",
                    subtitle = "Ostatnie 4 tygodnie",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(state.topArtists, key = { it.id }) { artist ->
                ArtistRow(artist = artist, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }

            // ── Ulubione gatunki z top artystów ─────────────────────────────
            val genres = state.topArtists
                .flatMap { it.genres }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(8)
                .map { it.key }

            if (genres.isNotEmpty()) {
                item {
                    SectionTitle(
                        title = "Ulubione gatunki",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                item {
                    GenreChips(genres = genres, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// ── Header profilu ─────────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(profile: UserProfile?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar
        NetworkImage(
            url = profile?.imageUrl,
            contentDescription = "Avatar",
            modifier = Modifier.size(100.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            fallback = {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(SpotifyMidGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(52.dp),
                    )
                }
            },
        )

        // Imię
        Text(
            text = profile?.displayName ?: "Użytkownik",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )

        // Email
        profile?.email?.let { email ->
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Kraj
        profile?.country?.let { country ->
            Surface(shape = RoundedCornerShape(12.dp), color = SpotifyMidGray) {
                Text(
                    text = "🌍 $country",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Statystyki ─────────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(
    likedCount: Int,
    followers: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatColumn(emoji = "❤️", value = "$likedCount", label = "Polubione")
            VerticalDivider(
                modifier = Modifier.height(48.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            )
            StatColumn(emoji = "👥", value = "$followers", label = "Obserwujący")
        }
    }
}

@Composable
private fun StatColumn(emoji: String, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = SpotifyGreen,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Wiersz artysty (okładka, gatunki, popularność) ──────────────────────────────

@Composable
private fun ArtistRow(artist: TopArtist, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NetworkImage(
            url = artist.imageUrl,
            contentDescription = artist.name,
            modifier = Modifier.size(56.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            fallback = {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(SpotifyMidGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artist.genres.isNotEmpty()) {
                Text(
                    text = artist.genres.take(3).joinToString(", ") { it.replaceFirstChar { c -> c.uppercase() } },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Popularność",
                tint = SpotifyGreen,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "${artist.popularity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Gatunki ─────────────────────────────────────────────────────────────────────

@Composable
private fun GenreChips(genres: List<String>, modifier: Modifier = Modifier) {
    // Układ zawijany — wiersze po 4 chipy (odpowiednik mobilnego FlowRow-like).
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.chunked(4).forEach { rowGenres ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowGenres.forEach { genre ->
                    Surface(shape = RoundedCornerShape(20.dp), color = SpotifyGreen.copy(alpha = 0.15f)) {
                        Text(
                            text = genre.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = SpotifyGreen,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Nagłówek sekcji ─────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Error ───────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Spróbuj ponownie") }
    }
}
