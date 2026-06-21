package com.spotify.playlistmanager.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.playlistmanager.data.model.PlaylistStats
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.desktop.data.LIKED_ID
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.desktop.theme.SpotifyMidGray

// ════════════════════════════════════════════════════════════════════════════
//  Stan ekranu (desktopowy odpowiednik TracksUiState z mobilnego ViewModel)
// ════════════════════════════════════════════════════════════════════════════

private sealed interface TracksUi {
    data object Loading : TracksUi
    data class Error(val message: String) : TracksUi
    data class Data(val tracks: List<Track>) : TracksUi
}

/** Kolumny sortowania — 1:1 z mobilnym [SortColumn]. */
private enum class SortColumn(val label: String) {
    TITLE("Tytuł"),
    ARTIST("Artysta"),
    ALBUM("Album"),
    DURATION("Czas"),
    POPULARITY("Popularność");

    fun sort(tracks: List<Track>): List<Track> = when (this) {
        TITLE -> tracks.sortedBy { it.title.lowercase() }
        ARTIST -> tracks.sortedBy { it.artist.lowercase() }
        ALBUM -> tracks.sortedBy { it.album.lowercase() }
        DURATION -> tracks.sortedBy { it.durationMs }
        POPULARITY -> tracks.sortedByDescending { it.popularity }
    }
}

/** Rozszerzone statystyki playlisty z cechami audio (liczone na desktopie). */
private data class TracksStats(
    val base: PlaylistStats,
    val hasAudio: Boolean,
    val avgBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val avgEnergy: Int,
    val avgDance: Int,
)

/**
 * Ekran „Utwory" — wierne odwzorowanie mobilnego TracksScreen na Compose for
 * Desktop. Pobiera utwory wybranej playlisty (lub Polubionych) przez
 * [SpotifyClient.repository] oraz cechy audio przez [SpotifyClient.featuresRepository].
 *
 * Funkcje: TopAppBar (wstecz + nazwa), nagłówek statystyk (liczba, czas,
 * śr./min/max BPM, energia, taneczność), filtrowanie po tytule/artyście/albumie,
 * sortowanie przez klik nagłówka (toggle: rosnąco → malejąco → reset) oraz
 * szczegóły utworu w [ModalBottomSheet] z pełnymi cechami audio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracksScreen(
    client: SpotifyClient,
    playlistId: String,
    playlistName: String,
    onBack: () -> Unit,
) {
    var ui by remember { mutableStateOf<TracksUi>(TracksUi.Loading) }
    var featuresMap by remember { mutableStateOf<Map<String, TrackAudioFeatures>>(emptyMap()) }

    var query by remember { mutableStateOf("") }
    var sortColumn by remember { mutableStateOf<SortColumn?>(null) }
    var sortReverse by remember { mutableStateOf(false) }

    // Wybrany utwór do podglądu w bottom sheet.
    var sheetTrack by remember { mutableStateOf<Track?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(playlistId) {
        ui = TracksUi.Loading
        featuresMap = emptyMap()
        try {
            val tracks = if (playlistId == LIKED_ID) {
                client.repository.getLikedTracks()
            } else {
                client.repository.getPlaylistTracks(playlistId)
            }
            ui = TracksUi.Data(tracks)
            // Cechy audio — najlepszy wysiłek; pusta mapa gdy brak importu CSV.
            val ids = tracks.mapNotNull { it.id }
            if (ids.isNotEmpty()) {
                featuresMap = runCatching { client.featuresRepository.getFeaturesMap(ids) }
                    .getOrDefault(emptyMap())
            }
        } catch (e: Exception) {
            ui = TracksUi.Error(e.message ?: "Nie udało się pobrać utworów.")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = playlistName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = ui) {
                is TracksUi.Loading -> Centered {
                    CircularProgressIndicator(color = SpotifyGreen)
                    Text("Pobieram utwory…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                is TracksUi.Error -> Centered {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }

                is TracksUi.Data -> {
                    // Filtrowanie + sortowanie (mobile: visibleTracks).
                    val visibleTracks = remember(state.tracks, query, sortColumn, sortReverse) {
                        var list = state.tracks
                        if (query.isNotBlank()) {
                            list = list.filter {
                                it.title.contains(query, ignoreCase = true) ||
                                    it.artist.contains(query, ignoreCase = true) ||
                                    it.album.contains(query, ignoreCase = true)
                            }
                        }
                        sortColumn?.let { col ->
                            val sorted = col.sort(list)
                            list = if (sortReverse) sorted.reversed() else sorted
                        }
                        list
                    }

                    // Statystyki liczone z widocznych utworów + ich cech audio.
                    val stats = remember(visibleTracks, featuresMap) {
                        computeStats(visibleTracks, featuresMap)
                    }

                    StatsBar(stats = stats)

                    // Filtrowanie po tytule/artyście/albumie.
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Szukaj utworu…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )

                    // Sortowanie — klik nagłówka kolumny.
                    SortHeaderRow(
                        currentSort = sortColumn,
                        isReversed = sortReverse,
                        onSortSelected = { col ->
                            when {
                                sortColumn != col -> {
                                    sortColumn = col
                                    sortReverse = false
                                }
                                // Trzeci klik tej samej kolumny — reset.
                                sortReverse -> {
                                    sortColumn = null
                                    sortReverse = false
                                }
                                else -> sortReverse = true
                            }
                        },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(
                            items = visibleTracks,
                            key = { "${it.id}_${it.title}" },
                        ) { track ->
                            TrackRow(
                                track = track,
                                features = track.id?.let { featuresMap[it] },
                                onInfoClick = { sheetTrack = track },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                modifier = Modifier.padding(start = 80.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Bottom sheet ze szczegółami utworu ──────────────────────────────────
    val current = sheetTrack
    if (current != null) {
        TrackDetailSheet(
            track = current,
            features = current.id?.let { featuresMap[it] },
            sheetState = sheetState,
            onDismiss = { sheetTrack = null },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Statystyki nagłówka
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsBar(stats: TracksStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatChip(emoji = "🎵", value = "${stats.base.trackCount}", label = "utworów")
            StatChip(emoji = "⏱", value = stats.base.formattedDuration(), label = "czas")
            // Cechy audio — myślniki gdy brak importu CSV (brak crasha).
            StatChip(emoji = "🥁", value = if (stats.hasAudio) "${stats.avgBpm}" else "—", label = "śr. BPM")
            StatChip(
                emoji = "↕",
                value = if (stats.hasAudio) "${stats.minBpm}–${stats.maxBpm}" else "—",
                label = "min/max BPM",
            )
            StatChip(emoji = "⚡", value = if (stats.hasAudio) "${stats.avgEnergy}" else "—", label = "energia")
            StatChip(emoji = "💃", value = if (stats.hasAudio) "${stats.avgDance}" else "—", label = "taneczność")
        }
    }
}

@Composable
private fun StatChip(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = SpotifyGreen,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Liczy statystyki: liczba/czas zawsze, cechy audio gdy mapa niepusta. */
private fun computeStats(
    tracks: List<Track>,
    features: Map<String, TrackAudioFeatures>,
): TracksStats {
    val base = PlaylistStats(
        trackCount = tracks.size,
        totalDurationMs = tracks.sumOf { it.durationMs.toLong() },
    )
    val feats = tracks.mapNotNull { it.id?.let { id -> features[id] } }
    if (feats.isEmpty()) {
        return TracksStats(
            base = base,
            hasAudio = false,
            avgBpm = 0, minBpm = 0, maxBpm = 0,
            avgEnergy = 0, avgDance = 0,
        )
    }
    val bpms = feats.map { it.bpm }
    return TracksStats(
        base = base,
        hasAudio = true,
        avgBpm = bpms.average().toInt(),
        minBpm = (bpms.minOrNull() ?: 0f).toInt(),
        maxBpm = (bpms.maxOrNull() ?: 0f).toInt(),
        avgEnergy = feats.map { it.energy }.average().toInt(),
        avgDance = feats.map { it.danceability }.average().toInt(),
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Nagłówki sortowania (klik = toggle kierunku)
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortHeaderRow(
    currentSort: SortColumn?,
    isReversed: Boolean,
    onSortSelected: (SortColumn) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SortColumn.entries.forEach { col ->
            val isActive = currentSort == col
            FilterChip(
                selected = isActive,
                onClick = { onSortSelected(col) },
                label = {
                    Text(
                        text = if (isActive) "${col.label} ${if (isReversed) "▼" else "▲"}" else col.label,
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
}

// ════════════════════════════════════════════════════════════════════════════
//  Wiersz utworu (rozwijalny, jak na mobile)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TrackRow(
    track: Track,
    features: TrackAudioFeatures?,
    onInfoClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Okładka albumu (NetworkImage z fallbackiem na ikonę nuty).
            NetworkImage(
                url = track.albumArtUrl,
                contentDescription = track.album,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                fallback = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SpotifyMidGray),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
            )

            // Tytuł + artysta/album + pasek popularności.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${track.artist} · ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LinearProgressIndicator(
                        progress = { track.popularity / 100f },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SpotifyGreen,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    )
                    Text(
                        text = "${track.popularity}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Czas + przycisk szczegółów.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = track.formattedDuration(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Szczegóły",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Rozwijana sekcja z audio features.
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 80.dp, end = 16.dp, bottom = 8.dp),
            ) {
                track.releaseDate?.let { date ->
                    DetailChip(label = "Wydano", value = date)
                }

                if (features != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AudioFeatureMiniBar(
                            label = "BPM",
                            value = "${features.bpm.toInt()}",
                            progress = (features.bpm / 220f).coerceIn(0f, 1f),
                            modifier = Modifier.weight(1f),
                        )
                        AudioFeatureMiniBar(
                            label = "Energy",
                            value = "${features.energy.toInt()}",
                            progress = features.energy / 100f,
                            modifier = Modifier.weight(1f),
                        )
                        AudioFeatureMiniBar(
                            label = "Dance",
                            value = "${features.danceability.toInt()}",
                            progress = features.danceability / 100f,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DetailChip(label = "Camelot", value = features.camelot)
                        DetailChip(label = "Key", value = features.musicalKey)
                        DetailChip(label = "Valence", value = "${features.valence.toInt()}")
                    }
                } else {
                    Text(
                        "Brak danych audio — zaimportuj CSV w Ustawieniach",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Bottom sheet ze szczegółami (desktopowy odpowiednik TrackDetailBottomSheet)
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackDetailSheet(
    track: Track,
    features: TrackAudioFeatures?,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NetworkImage(
                    url = track.albumArtUrl,
                    contentDescription = track.album,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    fallback = {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpotifyMidGray),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.MusicNote, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            Text(
                "Informacje o utworze",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(8.dp))

            SheetInfoRow("Album", track.album)
            track.releaseDate?.let { SheetInfoRow("Data wydania", it) }
            SheetInfoRow("Czas trwania", track.formattedDuration())
            SheetInfoRow("Popularność", "${track.popularity} / 100")
            track.uri?.let { SheetInfoRow("URI", it) }
            track.id?.let { SheetInfoRow("Spotify ID", it) }

            if (features != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))

                Text(
                    "Audio Features (z CSV)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(8.dp))

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
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.Info, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Brak danych audio. Zaimportuj plik CSV w Ustawieniach, " +
                                "aby zobaczyć BPM, energię, tonację i inne cechy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Małe komponenty pomocnicze
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AudioFeatureMiniBar(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = SpotifyGreen,
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
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun AudioFeatureBar(label: String, value: String, progress: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = SpotifyGreen,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun DetailChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SheetInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}
