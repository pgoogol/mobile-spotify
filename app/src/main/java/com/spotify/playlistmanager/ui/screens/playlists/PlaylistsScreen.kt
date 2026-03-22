package com.spotify.playlistmanager.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.ui.theme.SpotifyGreen
import com.spotify.playlistmanager.ui.theme.SpotifyMidGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onPlaylistClick: (id: String, name: String) -> Unit,
    onGenerateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel:       PlaylistsViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery       by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredPlaylists by viewModel.filteredPlaylists.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje Playlisty", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onGenerateClick) {
                        Icon(Icons.Default.AutoAwesome, "Generuj", tint = SpotifyGreen)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Ustawienia")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value         = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder   = { Text("Szukaj playlist…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                trailingIcon  = {
                    if (searchQuery.isNotEmpty())
                        IconButton({ viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Close, null)
                        }
                },
                singleLine    = true,
                shape         = RoundedCornerShape(24.dp),
                colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
            )

            PullToRefreshBox(
                isRefreshing = uiState is PlaylistsUiState.Loading,
                onRefresh    = viewModel::loadPlaylists,
                modifier     = Modifier.fillMaxSize()
            ) {
                when {
                    uiState is PlaylistsUiState.Error -> ErrorView(
                        (uiState as PlaylistsUiState.Error).message, viewModel::loadPlaylists
                    )
                    filteredPlaylists.isEmpty() && uiState is PlaylistsUiState.Success ->
                        EmptyView(searchQuery.isNotBlank())
                    else -> LazyColumn(
                        contentPadding      = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        item {
                            LikedSongsItem { onPlaylistClick("__liked__", "❤ Polubione") }
                        }
                        items(filteredPlaylists, key = { it.id }) { pl ->
                            PlaylistItem(pl) { onPlaylistClick(pl.id, pl.name) }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LikedSongsItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                .background(SpotifyGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Favorite, null,
                tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
        }
        Column {
            Text("❤ Polubione utwory",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text("Wszystkie polubione",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
private fun PlaylistItem(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (playlist.imageUrl != null) {
            AsyncImage(
                model = playlist.imageUrl, contentDescription = playlist.name,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(SpotifyMidGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.QueueMusic, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1)
            Text("${playlist.trackCount} utworów",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Spróbuj ponownie") }
    }
}

@Composable
private fun EmptyView(hasSearch: Boolean) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            if (hasSearch) "Brak wyników" else "Brak playlist",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
