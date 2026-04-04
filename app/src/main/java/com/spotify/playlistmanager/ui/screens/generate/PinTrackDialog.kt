package com.spotify.playlistmanager.ui.screens.generate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

/**
 * Dialog do wyboru utworów do przypięcia w segmencie generatora.
 *
 * Funkcjonalności:
 *  - Multi-select z limitem (maxSelection = trackCount segmentu)
 *  - Wyszukiwanie/filtrowanie po tytule i artyście
 *  - Przycisk odświeżenia (ponowne pobranie z API)
 *  - Wizualne oznaczenie wybranych utworów (CheckCircle / Circle)
 *  - Okładki albumów (Coil AsyncImage)
 *
 * @param tracks       lista utworów z playlisty źródłowej
 * @param currentPinned aktualnie przypięte ID (do pre-select)
 * @param maxSelection  maksymalna liczba wyborów (= trackCount)
 * @param onConfirm     callback z listą wybranych ID
 * @param onRefresh     callback odświeżenia listy z API
 * @param onDismiss     callback zamknięcia dialogu
 */
@Composable
fun PinTrackDialog(
    tracks: List<Track>,
    currentPinned: List<PinnedTrackInfo>,
    maxSelection: Int,
    onConfirm: (List<String>) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember {
        mutableStateOf(currentPinned.map { it.id }.toSet())
    }
    var filterQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(tracks, filterQuery) {
        if (filterQuery.isBlank()) tracks
        else {
            val q = filterQuery.lowercase()
            tracks.filter { track ->
                track.title.lowercase().contains(q) ||
                        track.artist.lowercase().contains(q)
            }
        }
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
                Text(
                    "${selected.size}/$maxSelection",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected.size >= maxSelection)
                        MaterialTheme.colorScheme.error
                    else SpotifyGreen
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Wyszukiwarka + refresh
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("Szukaj…") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyGreen
                        )
                    )
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "Odśwież listę")
                    }
                }

                HorizontalDivider()

                // Lista utworów
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredTracks, key = { it.id ?: it.title }) { track ->
                        val trackId = track.id ?: return@items
                        val isSelected = trackId in selected
                        val canSelect = selected.size < maxSelection || isSelected

                        PinTrackRow(
                            track = track,
                            isSelected = isSelected,
                            enabled = canSelect,
                            onClick = {
                                selected = if (isSelected) {
                                    selected - trackId
                                } else if (canSelect) {
                                    selected + trackId
                                } else selected
                            }
                        )
                    }

                    if (filteredTracks.isEmpty()) {
                        item {
                            Text(
                                "Brak wyników",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }) {
                Text("Zatwierdź")
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
        // Checkbox wizualny
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

        // Okładka albumu
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
                    .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "🎵",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Tytuł + artysta
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

        // Czas trwania
        Text(
            track.formattedDuration(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}