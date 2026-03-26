package com.spotify.playlistmanager.ui.screens.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spotify.playlistmanager.data.model.PlaylistStats
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.ui.theme.SpotifyGreen
import com.spotify.playlistmanager.ui.theme.SpotifyMidGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    playlistId:   String,
    playlistName: String,
    onBack:       () -> Unit,
    viewModel:    TracksViewModel = hiltViewModel()
) {
    val state         by viewModel.state.collectAsStateWithLifecycle()
    val filterQuery   by viewModel.filterQuery.collectAsStateWithLifecycle()
    val visibleTracks by viewModel.visibleTracks.collectAsStateWithLifecycle()
    val sortColumn    by viewModel.sortColumn.collectAsStateWithLifecycle()
    val sortReverse   by viewModel.sortReverse.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) { viewModel.loadTracks(playlistId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text       = playlistName,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Statystyki ─────────────────────────────────────────────────
            state.stats?.let { StatsBar(stats = it) }

            // ── Filtr ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value         = filterQuery,
                    onValueChange = viewModel::onFilterChange,
                    modifier      = Modifier.weight(1f),
                    placeholder   = { Text("Filtruj…") },
                    leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon  = {
                        if (filterQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onFilterChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape      = RoundedCornerShape(20.dp),
                    colors     = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen
                    )
                )
                Text(
                    text  = "${visibleTracks.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Nagłówki sortowania ────────────────────────────────────────
            SortHeaderRow(
                currentSort    = sortColumn,
                isReversed     = sortReverse,
                onSortSelected = viewModel::onSortColumn
            )

            // ── Lista / Loading / Error ────────────────────────────────────
            when {
                state.isLoading -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SpotifyGreen)
                    }
                }
                state.error != null -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = state.error!!,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = visibleTracks,
                            key   = { "${it.id}_${it.title}" }
                        ) { track ->
                            TrackRow(track = track)
                            HorizontalDivider(
                                color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                modifier = Modifier.padding(start = 80.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Statystyki ──────────────────────────────────────────────────────────────

@Composable
private fun StatsBar(stats: PlaylistStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            StatChip(emoji = "🎵", value = "${stats.trackCount}", label = "utworów")
            StatChip(emoji = "⏱", value = stats.formattedDuration(), label = "czas")
        }
    }
}

@Composable
private fun StatChip(
    emoji: String,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = SpotifyGreen
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Nagłówki sortowania ──────────────────────────────────────────────────────

@Composable
private fun SortHeaderRow(
    currentSort:    SortColumn?,
    isReversed:     Boolean,
    onSortSelected: (SortColumn) -> Unit
) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items = SortColumn.entries.toList()) { col ->
            val isActive = currentSort == col
            FilterChip(
                selected = isActive,
                onClick  = { onSortSelected(col) },
                label    = {
                    Text(
                        text  = if (isActive) "${col.label} ${if (isReversed) "▼" else "▲"}"
                                else col.label,
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
}

// ── Wiersz utworu ────────────────────────────────────────────────────────────

@Composable
private fun TrackRow(track: Track) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Okładka albumu
        if (track.albumArtUrl != null) {
            AsyncImage(
                model              = track.albumArtUrl,
                contentDescription = track.album,
                modifier           = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale       = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SpotifyMidGray),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(24.dp)
                )
            }
        }

        // Tekst główny
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text     = track.title,
                style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text     = "${track.artist} · ${track.album}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Czas trwania
        Text(
            text  = track.formattedDuration(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
