package com.spotify.playlistmanager.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val sortOption        by viewModel.sortOption.collectAsStateWithLifecycle()
    val sortReverse       by viewModel.sortReverse.collectAsStateWithLifecycle()
    val viewMode          by viewModel.viewMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje Playlisty", fontWeight = FontWeight.Bold) },
                actions = {
                    // Przełącznik lista/siatka
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(
                            imageVector = if (viewMode == ViewMode.LIST)
                                Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = "Zmień widok"
                        )
                    }
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

            // ── Wyszukiwarka ───────────────────────────────────────────
            OutlinedTextField(
                value         = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // ── Chipy sortowania ───────────────────────────────────────
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(PlaylistSortOption.entries.toList()) { option ->
                    val isActive = sortOption == option
                    FilterChip(
                        selected = isActive,
                        onClick  = { viewModel.onSortOption(option) },
                        label    = {
                            Text(
                                text  = if (isActive && option != PlaylistSortOption.DEFAULT)
                                    "${option.label} ${if (sortReverse) "▼" else "▲"}"
                                else option.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor     = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // ── Zawartość ──────────────────────────────────────────────
            PullToRefreshBox(
                isRefreshing = uiState is PlaylistsUiState.Loading,
                onRefresh    = viewModel::loadPlaylists,
                modifier     = Modifier.fillMaxSize()
            ) {
                when {
                    uiState is PlaylistsUiState.Error -> ErrorView(
                        (uiState as PlaylistsUiState.Error).message,
                        viewModel::loadPlaylists
                    )
                    filteredPlaylists.isEmpty() && uiState is PlaylistsUiState.Success ->
                        EmptyView(searchQuery.isNotBlank())
                    viewMode == ViewMode.GRID ->
                        GridContent(
                            playlists      = filteredPlaylists,
                            onPlaylistClick = onPlaylistClick
                        )
                    else ->
                        ListContent(
                            playlists       = filteredPlaylists,
                            onPlaylistClick = onPlaylistClick
                        )
                }
            }
        }
    }
}

// ── Widok listy ───────────────────────────────────────────────────────────────

@Composable
private fun ListContent(
    playlists:       List<Playlist>,
    onPlaylistClick: (id: String, name: String) -> Unit
) {
    LazyColumn(
        contentPadding      = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            LikedSongsListItem { onPlaylistClick("__liked__", "❤ Polubione") }
        }
        items(playlists, key = { it.id }) { pl ->
            PlaylistListItem(pl) { onPlaylistClick(pl.id, pl.name) }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ── Widok siatki ──────────────────────────────────────────────────────────────

@Composable
private fun GridContent(
    playlists:       List<Playlist>,
    onPlaylistClick: (id: String, name: String) -> Unit
) {
    LazyVerticalGrid(
        columns             = GridCells.Fixed(2),
        contentPadding      = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
        verticalArrangement   = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            LikedSongsGridItem { onPlaylistClick("__liked__", "❤ Polubione") }
        }
        items(playlists, key = { it.id }) { pl ->
            PlaylistGridItem(pl) { onPlaylistClick(pl.id, pl.name) }
        }
    }
}

// ── Kafelki listy ─────────────────────────────────────────────────────────────

@Composable
private fun LikedSongsListItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SpotifyGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Favorite, null,
                tint     = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("❤ Polubione utwory",
                style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text("Wszystkie polubione",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    )
}

@Composable
private fun PlaylistListItem(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Okładka
        PlaylistThumbnail(
            imageUrl = playlist.imageUrl,
            name     = playlist.name,
            size     = 56,
            cornerRadius = 8
        )

        // Tekst
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "${playlist.trackCount} utworów",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Kafelki siatki ────────────────────────────────────────────────────────────

@Composable
private fun LikedSongsGridItem(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(SpotifyGreen)
            .clickable(onClick = onClick)
    ) {
        Icon(
            Icons.Default.Favorite, null,
            tint     = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 12.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Icon(Icons.Default.Favorite, null,
                tint     = Color.White,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                "Polubione",
                style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Wszystkie ulubione",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun PlaylistGridItem(playlist: Playlist, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        // Okładka (tło całego kafelka)
        if (playlist.imageUrl != null) {
            AsyncImage(
                model              = playlist.imageUrl,
                contentDescription = playlist.name,
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop
            )
        } else {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .background(SpotifyMidGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Gradient + tekst u dołu
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.45f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text     = playlist.name,
                style    = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp
                ),
                color    = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text  = "${playlist.trackCount} utworów",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

// ── Wspólny komponent okładki ─────────────────────────────────────────────────

@Composable
private fun PlaylistThumbnail(
    imageUrl:    String?,
    name:        String,
    size:        Int,
    cornerRadius: Int
) {
    if (imageUrl != null) {
        AsyncImage(
            model              = imageUrl,
            contentDescription = name,
            modifier           = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(cornerRadius.dp)),
            contentScale       = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape(cornerRadius.dp))
                .background(SpotifyMidGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Stany pomocnicze ──────────────────────────────────────────────────────────

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

@Composable
private fun EmptyView(hasSearch: Boolean) {
    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.SearchOff, null,
            tint     = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            if (hasSearch) "Brak wyników" else "Brak playlist",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
