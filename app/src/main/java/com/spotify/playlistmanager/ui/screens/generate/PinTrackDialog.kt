package com.spotify.playlistmanager.ui.screens.generate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

/**
 * Dialog do wyboru utworów do przypięcia w segmencie generatora.
 *
 * Funkcjonalności:
 *  - Multi-select z limitem (maxSelection = trackCount segmentu)
 *  - Wybór playlisty źródłowej z dropdownu (DOWOLNA playlista użytkownika + Liked)
 *  - Selekcja trzymana między przełączeniami playlist (akumulator w ViewModelu)
 *  - Wyszukiwanie/filtrowanie po tytule i artyście w obrębie wybranej playlisty
 *  - Refresh = wymusza ponowne pobranie aktualnie wybranej playlisty z API
 *  - Wizualne oznaczenie wybranych utworów (CheckCircle / Circle)
 *  - Okładki albumów (Coil AsyncImage)
 *
 * @param availablePlaylists wszystkie playlisty użytkownika (z Liked Songs na czele)
 * @param selectedPlaylistId aktualnie wyświetlana playlista
 * @param tracks utwory aktualnie wybranej playlisty
 * @param isLoadingTracks true gdy ładowane są tracki dla nowo wybranej playlisty
 * @param selectedTracks aktualnie wybrane pinned (cross-playlist!) — z ViewModelu
 * @param maxSelection maksymalna liczba wyborów (= trackCount segmentu)
 * @param onPickPlaylist callback gdy user wybiera inną playlistę z dropdownu
 * @param onToggleTrack callback gdy user klika utwór (toggle select). Drugi
 *                      argument to ID playlisty z której pochodzi utwór.
 * @param onConfirm callback gdy user zatwierdza
 * @param onRefresh callback wymuszający fetch obecnej playlisty z API
 * @param onDismiss callback zamknięcia dialogu
 */
@Composable
fun PinTrackDialog(
    availablePlaylists: List<Playlist>,
    selectedPlaylistId: String,
    tracks: List<Track>,
    isLoadingTracks: Boolean,
    selectedTracks: List<PinnedTrackInfo>,
    maxSelection: Int,
    onPickPlaylist: (String) -> Unit,
    onToggleTrack: (Track, String) -> Unit,
    onConfirm: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }
    var showPlaylistDropdown by remember { mutableStateOf(false) }

    val filteredTracks = remember(tracks, filterQuery) {
        if (filterQuery.isBlank()) tracks
        else {
            val q = filterQuery.lowercase()
            tracks.filter { t ->
                t.title.lowercase().contains(q) || t.artist.lowercase().contains(q)
            }
        }
    }

    val selectedIds = remember(selectedTracks) { selectedTracks.map { it.id }.toHashSet() }
    val sourcePlaylistsCount = remember(selectedTracks) {
        selectedTracks.mapNotNull { it.sourcePlaylistId }.distinct().size
    }
    val currentPlaylist = remember(availablePlaylists, selectedPlaylistId) {
        availablePlaylists.find { it.id == selectedPlaylistId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Przypnij utwory")
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${selectedTracks.size}/$maxSelection",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedTracks.size >= maxSelection)
                            MaterialTheme.colorScheme.error
                        else SpotifyGreen
                    )
                    if (sourcePlaylistsCount > 1) {
                        Text(
                            "z $sourcePlaylistsCount playlist",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── Picker playlisty (dropdown) ──────────────────────────────
                Box {
                    OutlinedButton(
                        onClick = { showPlaylistDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            Icons.Default.LibraryMusic, null,
                            modifier = Modifier.size(18.dp),
                            tint = SpotifyGreen
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = currentPlaylist?.name ?: "Wybierz playlistę",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Icon(Icons.Default.ArrowDropDown, null)
                    }

                    DropdownMenu(
                        expanded = showPlaylistDropdown,
                        onDismissRequest = { showPlaylistDropdown = false },
                        modifier = Modifier.heightIn(max = 320.dp)
                    ) {
                        availablePlaylists.forEach { pl ->
                            val pinnedFromHere = selectedTracks.count { it.sourcePlaylistId == pl.id }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            pl.name,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (pl.id == selectedPlaylistId)
                                                FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (pinnedFromHere > 0) {
                                            Text(
                                                "📌$pinnedFromHere",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SpotifyGreen
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showPlaylistDropdown = false
                                    if (pl.id != selectedPlaylistId) onPickPlaylist(pl.id)
                                }
                            )
                        }
                    }
                }

                // ── Wyszukiwarka + refresh ───────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("Szukaj w tej playliscie...") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen
                        )
                    )
                    IconButton(onClick = onRefresh, enabled = !isLoadingTracks) {
                        Icon(Icons.Default.Refresh, "Odswiez liste")
                    }
                }

                HorizontalDivider()

                // ── Lista utworów ────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp)
                ) {
                    when {
                        isLoadingTracks -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = SpotifyGreen)
                            }
                        }
                        filteredTracks.isEmpty() -> {
                            Text(
                                if (filterQuery.isBlank()) "Brak utworow w tej playliscie"
                                else "Brak wynikow",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(filteredTracks, key = { it.id ?: it.title }) { track ->
                                    val trackId = track.id ?: return@items
                                    val isSelected = trackId in selectedIds
                                    val canSelect = selectedTracks.size < maxSelection || isSelected

                                    PinTrackRow(
                                        track = track,
                                        isSelected = isSelected,
                                        enabled = canSelect,
                                        onClick = { onToggleTrack(track, selectedPlaylistId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Zatwierdz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

// ── Wiersz pojedynczego utworu w dialogu ─────────────────────────────────────

@Composable
private fun PinTrackRow(
    track: Track,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.CheckCircle
            else Icons.Outlined.Circle,
            contentDescription = if (isSelected) "Wybrany" else "Nie wybrany",
            tint = when {
                isSelected -> SpotifyGreen
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp)
        )

        if (track.albumArtUrl != null) {
            AsyncImage(
                model = track.albumArtUrl,
                contentDescription = track.album,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83C\uDFB5", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            track.formattedDuration(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}