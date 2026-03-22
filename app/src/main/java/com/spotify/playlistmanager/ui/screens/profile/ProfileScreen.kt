package com.spotify.playlistmanager.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spotify.playlistmanager.data.model.TopArtist
import com.spotify.playlistmanager.data.model.UserProfile
import com.spotify.playlistmanager.ui.theme.SpotifyGreen
import com.spotify.playlistmanager.ui.theme.SpotifyMidGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profil", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh    = viewModel::loadProfile,
            modifier     = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SpotifyGreen)
                    }
                }
                state.error != null -> {
                    ErrorView(state.error!!, viewModel::loadProfile)
                }
                else -> {
                    ProfileContent(state = state)
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(state: ProfileUiState) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ── Header z avatarem ───────────────────────────────────────────
        item {
            ProfileHeader(profile = state.profile)
        }

        // ── Statystyki ──────────────────────────────────────────────────
        item {
            StatsRow(
                playlistCount = state.playlistCount,
                likedCount    = state.likedCount,
                modifier      = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ── Top Artyści ─────────────────────────────────────────────────
        if (state.topArtists.isNotEmpty()) {
            item {
                SectionTitle(
                    title    = "Ulubieni artyści",
                    subtitle = "Ostatnie 4 tygodnie",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.topArtists) { artist ->
                        ArtistCard(artist = artist)
                    }
                }
            }

            // ── Ulubione gatunki z top artystów ─────────────────────────
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
                        title    = "Ulubione gatunki",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                item {
                    GenreChips(
                        genres   = genres,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

// ── Header profilu ───────────────────────────────────────────────────────────

@Composable
private fun ProfileHeader(profile: UserProfile?) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        if (profile?.imageUrl != null) {
            AsyncImage(
                model              = profile.imageUrl,
                contentDescription = "Avatar",
                modifier           = Modifier
                    .size(100.dp)
                    .clip(CircleShape),
                contentScale       = ContentScale.Crop
            )
        } else {
            Box(
                modifier         = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(SpotifyMidGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        // Imię
        Text(
            text       = profile?.displayName ?: "Użytkownik",
            style      = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign  = TextAlign.Center
        )

        // Email
        profile?.email?.let { email ->
            Text(
                text  = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Kraj
        profile?.country?.let { country ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SpotifyMidGray
            ) {
                Text(
                    text     = "🌍 $country",
                    style    = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Statystyki ───────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(
    playlistCount: Int,
    likedCount:    Int,
    modifier:      Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatColumn(
                emoji = "🎵",
                value = "$playlistCount",
                label = "Playlisty"
            )
            VerticalDivider(
                modifier = Modifier.height(48.dp),
                color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            StatColumn(
                emoji = "❤️",
                value = "$likedCount",
                label = "Polubione"
            )
        }
    }
}

@Composable
private fun StatColumn(emoji: String, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(
            text  = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = SpotifyGreen
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Karta artysty ────────────────────────────────────────────────────────────

@Composable
private fun ArtistCard(artist: TopArtist) {
    Column(
        modifier            = Modifier.width(90.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (artist.imageUrl != null) {
            AsyncImage(
                model              = artist.imageUrl,
                contentDescription = artist.name,
                modifier           = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                contentScale       = ContentScale.Crop
            )
        } else {
            Box(
                modifier         = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(SpotifyMidGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text      = artist.name,
            style     = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            maxLines  = 2,
            overflow  = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ── Gatunki ──────────────────────────────────────────────────────────────────

@Composable
private fun GenreChips(genres: List<String>, modifier: Modifier = Modifier) {
    // Wrap layout – używamy FlowRow-like z LazyRow per wiersz
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.chunked(4).forEach { rowGenres ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowGenres.forEach { genre ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SpotifyGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text     = genre.replaceFirstChar { it.uppercase() },
                            style    = MaterialTheme.typography.labelMedium,
                            color    = SpotifyGreen,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── Nagłówek sekcji ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(
    title:    String,
    subtitle: String?  = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        subtitle?.let {
            Text(
                text  = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Error ────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null,
            tint     = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Spróbuj ponownie") }
    }
}
