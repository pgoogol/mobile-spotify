package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen

/**
 * Dialog wyboru utworów do przypięcia w segmencie generatora — desktopowy
 * odpowiednik mobilnego `ui.screens.generate.PinTrackDialog`.
 *
 * Funkcje (odwzorowane 1:1 z mobile):
 *  - Multi-select z limitem (maxSelection = trackCount segmentu),
 *  - wybór playlisty źródłowej z dropdownu (DOWOLNA playlista usera + Liked),
 *  - selekcja trzymana między przełączeniami playlist (cross-playlist!),
 *  - filtrowanie po tytule/artyście w obrębie wybranej playlisty,
 *  - „Odśwież" wymusza ponowne pobranie aktualnie wybranej playlisty,
 *  - wizualne oznaczenie wybranych (CheckCircle / Circle), okładki przez [NetworkImage].
 *
 * @param availablePlaylists wszystkie playlisty (z „Polubione" na czele)
 * @param selectedPlaylistId aktualnie wyświetlana playlista
 * @param tracks utwory aktualnie wybranej playlisty
 * @param isLoadingTracks true gdy ładowane są tracki dla nowo wybranej playlisty
 * @param selectedTracks aktualnie wybrane pinned (cross-playlist!) — draft
 * @param maxSelection maksymalna liczba wyborów (= trackCount segmentu)
 */
@Composable
fun GeneratePinTrackDialog(
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
    onDismiss: () -> Unit,
) {
    var filterQuery by remember { mutableStateOf("") }
    var showPlaylistDropdown by remember { mutableStateOf(false) }

    val filteredTracks = remember(tracks, filterQuery) {
        if (filterQuery.isBlank()) {
            tracks
        } else {
            val q = filterQuery.lowercase()
            tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Przypnij utwory")
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${selectedTracks.size}/$maxSelection",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedTracks.size >= maxSelection) {
                            MaterialTheme.colorScheme.error
                        } else {
                            SpotifyGreen
                        },
                    )
                    if (sourcePlaylistsCount > 1) {
                        Text(
                            "z $sourcePlaylistsCount playlist",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // ── Picker playlisty (dropdown) ──────────────────────────────
                Box {
                    OutlinedButton(
                        onClick = { showPlaylistDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = SpotifyGreen,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = currentPlaylist?.name ?: "Wybierz playlistę",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = showPlaylistDropdown,
                        onDismissRequest = { showPlaylistDropdown = false },
                        modifier = Modifier.heightIn(max = 320.dp),
                    ) {
                        availablePlaylists.forEach { pl ->
                            val pinnedFromHere = selectedTracks.count { it.sourcePlaylistId == pl.id }
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            pl.name,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (pl.id == selectedPlaylistId) {
                                                FontWeight.Bold
                                            } else {
                                                FontWeight.Normal
                                            },
                                        )
                                        if (pinnedFromHere > 0) {
                                            Text(
                                                "📌$pinnedFromHere",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SpotifyGreen,
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showPlaylistDropdown = false
                                    if (pl.id != selectedPlaylistId) onPickPlaylist(pl.id)
                                },
                            )
                        }
                    }
                }

                // ── Wyszukiwarka + odśwież ───────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("Szukaj w tej playliście…") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
                    )
                    IconButton(onClick = onRefresh, enabled = !isLoadingTracks) {
                        Icon(Icons.Default.Refresh, "Odśwież listę")
                    }
                }

                HorizontalDivider()

                // ── Lista utworów ────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 400.dp),
                ) {
                    when {
                        isLoadingTracks -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = SpotifyGreen)
                            }
                        }

                        filteredTracks.isEmpty() -> {
                            Text(
                                if (filterQuery.isBlank()) {
                                    "Brak utworów w tej playliście"
                                } else {
                                    "Brak wyników"
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                items(filteredTracks, key = { it.id ?: it.title }) { track ->
                                    val trackId = track.id ?: return@items
                                    val isSelected = trackId in selectedIds
                                    val canSelect = selectedTracks.size < maxSelection || isSelected

                                    PinTrackRow(
                                        track = track,
                                        isSelected = isSelected,
                                        enabled = canSelect,
                                        onClick = { onToggleTrack(track, selectedPlaylistId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Zatwierdź") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}

@Composable
private fun PinTrackRow(
    track: Track,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = if (isSelected) "Wybrany" else "Nie wybrany",
            tint = when {
                isSelected -> SpotifyGreen
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp),
        )

        NetworkImage(
            url = track.albumArtUrl,
            contentDescription = track.album,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop,
            fallback = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                track.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            track.formattedDuration(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
