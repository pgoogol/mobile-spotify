package com.spotify.playlistmanager.ui.screens.tracks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
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
    val featuresMap   by viewModel.featuresMap.collectAsStateWithLifecycle()
    val sortColumn    by viewModel.sortColumn.collectAsStateWithLifecycle()
    val sortReverse   by viewModel.sortReverse.collectAsStateWithLifecycle()

    LaunchedEffect(playlistId) { viewModel.loadTracks(playlistId) }

    // Bottom sheet ze szczegółami
    var sheetTrack by remember { mutableStateOf<Track?>(null) }
    var sheetFeatures by remember { mutableStateOf<TrackAudioFeatures?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Statystyki
            state.stats?.let { StatsBar(stats = it) }

            // Filtrowanie
            OutlinedTextField(
                value         = filterQuery,
                onValueChange = viewModel::onFilterChanged,
                placeholder   = { Text("Szukaj utworu…") },
                leadingIcon   = { Icon(Icons.Default.Search, null) },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine    = true,
                shape         = RoundedCornerShape(12.dp)
            )

            // Sortowanie
            SortHeaderRow(
                currentSort    = sortColumn,
                isReversed     = sortReverse,
                onSortSelected = viewModel::onSortToggled
            )

            // Lista
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SpotifyGreen)
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                            TrackRow(
                                track = track,
                                features = featuresMap[track.id],
                                onInfoClick = {
                                    sheetTrack = track
                                    sheetFeatures = featuresMap[track.id]
                                }
                            )
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

    // ── Bottom Sheet ze wszystkimi informacjami ─────────────────────────
    if (sheetTrack != null) {
        TrackDetailBottomSheet(
            track = sheetTrack!!,
            features = sheetFeatures,
            sheetState = sheetState,
            onDismiss = {
                sheetTrack = null
                sheetFeatures = null
            }
        )
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

// ── Wiersz utworu (rozwijalny) ───────────────────────────────────────────────

@Composable
private fun TrackRow(
    track: Track,
    features: TrackAudioFeatures?,
    onInfoClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
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

            // Tekst główny + pasek popularności
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
                // Pasek popularności
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { track.popularity / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SpotifyGreen,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    Text(
                        text  = "${track.popularity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Czas + przycisk info
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text  = track.formattedDuration(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick  = onInfoClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Szczegóły",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Rozwijana sekcja z audio features ────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 80.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // Data wydania
                track.releaseDate?.let { date ->
                    DetailChip(label = "Wydano", value = date)
                }

                if (features != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioFeatureMiniBar(
                            label = "BPM",
                            value = "${features.bpm.toInt()}",
                            progress = (features.bpm / 220f).coerceIn(0f, 1f),
                            modifier = Modifier.weight(1f)
                        )
                        AudioFeatureMiniBar(
                            label = "Energy",
                            value = "${features.energy.toInt()}",
                            progress = features.energy / 100f,
                            modifier = Modifier.weight(1f)
                        )
                        AudioFeatureMiniBar(
                            label = "Dance",
                            value = "${features.danceability.toInt()}",
                            progress = features.danceability / 100f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailChip(label = "Camelot", value = features.camelot)
                        DetailChip(label = "Key", value = features.musicalKey)
                        DetailChip(label = "Valence", value = "${features.valence.toInt()}")
                    }
                } else {
                    Text(
                        "Brak danych audio — zaimportuj CSV w Ustawieniach",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ── Małe komponenty pomocnicze ──────────────────────────────────────────────

@Composable
private fun AudioFeatureMiniBar(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = SpotifyGreen
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = SpotifyGreen,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun DetailChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Bottom Sheet ze wszystkimi szczegółami ──────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackDetailBottomSheet(
    track: Track,
    features: TrackAudioFeatures?,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Nagłówek: okładka + tytuł ───────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (track.albumArtUrl != null) {
                    AsyncImage(
                        model = track.albumArtUrl,
                        contentDescription = track.album,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpotifyMidGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MusicNote, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            // ── Informacje z modelu Track ────────────────────────────────
            Text(
                "Informacje o utworze",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))

            SheetInfoRow("Album", track.album)
            track.releaseDate?.let { SheetInfoRow("Data wydania", it) }
            SheetInfoRow("Czas trwania", track.formattedDuration())
            SheetInfoRow("Popularność", "${track.popularity} / 100")
            track.uri?.let { SheetInfoRow("URI", it) }
            track.id?.let { SheetInfoRow("Spotify ID", it) }

            // ── Audio features (jeśli dostępne) ─────────────────────────
            if (features != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))

                Text(
                    "Audio Features (z CSV)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))

                // Paski wizualne dla głównych parametrów
                AudioFeatureBar("BPM", "${features.bpm}", (features.bpm / 220f).coerceIn(0f, 1f))
                AudioFeatureBar("Energy", "${features.energy}", features.energy / 100f)
                AudioFeatureBar("Danceability", "${features.danceability}", features.danceability / 100f)
                AudioFeatureBar("Valence", "${features.valence}", features.valence / 100f)
                AudioFeatureBar("Acousticness", "${features.acousticness}", features.acousticness / 100f)
                AudioFeatureBar("Instrumentalness", "${features.instrumentalness}", features.instrumentalness / 100f)
                AudioFeatureBar("Speechiness", "${features.speechiness}", features.speechiness / 100f)
                AudioFeatureBar("Liveness", "${features.liveness}", features.liveness / 100f)

                Spacer(Modifier.height(8.dp))
                SheetInfoRow("Loudness", "${features.loudness} dB")
                SheetInfoRow("Camelot", features.camelot)
                SheetInfoRow("Tonacja", features.musicalKey)
                SheetInfoRow("Metrum", "${features.timeSignature}/4")
                if (features.genres.isNotBlank()) SheetInfoRow("Gatunki", features.genres)
                if (features.label.isNotBlank()) SheetInfoRow("Wytwórnia", features.label)
                if (features.isrc.isNotBlank()) SheetInfoRow("ISRC", features.isrc)
            } else {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Brak danych audio. Zaimportuj plik CSV w Ustawieniach, " +
                                    "aby zobaczyć BPM, energię, tonację i inne cechy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioFeatureBar(label: String, value: String, progress: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = SpotifyGreen,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun SheetInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
    }
}