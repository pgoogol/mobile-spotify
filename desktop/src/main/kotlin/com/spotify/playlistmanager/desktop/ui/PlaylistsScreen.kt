package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.desktop.data.LIKED_ID
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.desktop.theme.SpotifyMidGray
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import kotlinx.coroutines.launch

private enum class ViewMode { LIST, GRID }

private enum class PlaylistSortOption(val label: String) {
    DEFAULT("Domyślnie"),
    NAME("Nazwa"),
    TRACKS("Utwory"),
}

/**
 * Lista playlist — odwzorowanie mobilnego `PlaylistsScreen`: pasek z akcjami
 * (lista/siatka, Generuj, Ustawienia), wyszukiwarka, chipy sortowania, widoki
 * lista/siatka z okładkami i kafelkiem „Polubione".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    repository: ISpotifyRepository,
    onPlaylistClick: (id: String, name: String) -> Unit,
    onGenerateClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(PlaylistSortOption.DEFAULT) }
    var reverse by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { repository.getUserPlaylists() }
                .onSuccess { playlists = it; loading = false }
                .onFailure { error = it.message ?: "Nie udało się pobrać playlist."; loading = false }
        }
    }
    LaunchedEffect(Unit) { load() }

    val displayed = remember(playlists, query, sort, reverse) {
        val filtered = playlists.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        val sorted = when (sort) {
            PlaylistSortOption.DEFAULT -> filtered
            PlaylistSortOption.NAME -> filtered.sortedBy { it.name.lowercase() }
            PlaylistSortOption.TRACKS -> filtered.sortedBy { it.trackCount }
        }
        if (reverse && sort != PlaylistSortOption.DEFAULT) sorted.reversed() else sorted
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje Playlisty", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST }) {
                        Icon(
                            if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = "Zmień widok",
                        )
                    }
                    IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Odśwież") }
                    IconButton(onClick = onGenerateClick) { Icon(Icons.Default.AutoAwesome, "Generuj", tint = SpotifyGreen) }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Ustawienia") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Szukaj playlist…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Default.Close, null) }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(PlaylistSortOption.entries.toList()) { option ->
                    val isActive = sort == option
                    FilterChip(
                        selected = isActive,
                        onClick = {
                            if (sort == option) reverse = !reverse else { sort = option; reverse = false }
                        },
                        label = {
                            Text(
                                if (isActive && option != PlaylistSortOption.DEFAULT)
                                    "${option.label} ${if (reverse) "▼" else "▲"}" else option.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpotifyGreen,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = SpotifyGreen) }
                error != null -> ErrorView(error!!) { load() }
                displayed.isEmpty() -> EmptyView(query.isNotBlank())
                viewMode == ViewMode.GRID -> GridContent(displayed, onPlaylistClick)
                else -> ListContent(displayed, onPlaylistClick)
            }
        }
    }
}

@Composable
private fun ListContent(playlists: List<Playlist>, onPlaylistClick: (String, String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { LikedSongsListItem { onPlaylistClick(LIKED_ID, "❤ Polubione") } }
        items(playlists, key = { it.id }) { pl ->
            PlaylistListItem(pl) { onPlaylistClick(pl.id, pl.name) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun GridContent(playlists: List<Playlist>, onPlaylistClick: (String, String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { LikedSongsGridItem { onPlaylistClick(LIKED_ID, "❤ Polubione") } }
        items(playlists, key = { it.id }) { pl ->
            PlaylistGridItem(pl) { onPlaylistClick(pl.id, pl.name) }
        }
    }
}

@Composable
private fun LikedSongsListItem(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(SpotifyGreen), Alignment.Center) {
            Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("❤ Polubione utwory", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Wszystkie polubione", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}

@Composable
private fun PlaylistListItem(playlist: Playlist, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaylistThumbnail(playlist.imageUrl, playlist.name, 56)
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount} utworów", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LikedSongsGridItem(onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(SpotifyGreen).clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .padding(10.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Icon(Icons.Default.Favorite, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text("Polubione", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)
            Text("Wszystkie ulubione", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun PlaylistGridItem(playlist: Playlist, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        NetworkImage(
            url = playlist.imageUrl,
            contentDescription = playlist.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            fallback = {
                Box(Modifier.fillMaxSize().background(SpotifyMidGray), Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                }
            },
        )
        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
                .padding(10.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(playlist.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${playlist.trackCount} utworów", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun PlaylistThumbnail(imageUrl: String?, name: String, size: Int) {
    NetworkImage(
        url = imageUrl,
        contentDescription = name,
        modifier = Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop,
        fallback = {
            Box(Modifier.size(size.dp).clip(RoundedCornerShape(8.dp)).background(SpotifyMidGray), Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Spróbuj ponownie") }
    }
}

@Composable
private fun EmptyView(hasSearch: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(if (hasSearch) "Brak wyników" else "Brak playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
