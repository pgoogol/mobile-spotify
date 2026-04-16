package com.spotify.playlistmanager.ui.screens.generate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.data.model.SortOption
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.WaveDirection
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

/**
 * Karta konfiguracji źródła playlisty.
 *
 * Zawiera:
 * - Dropdown wyboru playlisty + przycisk usuń
 * - Stepper liczby utworów (1–200)
 * - Dropdown krzywej energii + miniaturka Canvas 48dp
 * - AnimatedVisibility: konfiguracja Wave (kierunek + stepper N)
 * - AnimatedVisibility: sortowanie (widoczne tylko gdy krzywa = None)
 * - Harmonic Mixing toggle (widoczny tylko gdy krzywa ≠ None)
 * - Pinned tracks z obsługą cross-playlist
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSourceCard(
    source: PlaylistSource,
    availablePlaylists: List<Playlist>,
    onUpdate: (PlaylistSource) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    onPinTracks: () -> Unit,
    onRemovePinnedTrack: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var playlistExpanded by remember { mutableStateOf(false) }
    var curveExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Wiersz 1: Wybór playlisty + usuń ────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = playlistExpanded,
                    onExpandedChange = { playlistExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = source.playlist?.name ?: "Wybierz playlistę…",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = playlistExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded = playlistExpanded,
                        onDismissRequest = { playlistExpanded = false }
                    ) {
                        availablePlaylists.forEach { pl ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        pl.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    onUpdate(source.copy(playlist = pl))
                                    playlistExpanded = false
                                }
                            )
                        }
                    }
                }

                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Close, "Usuń", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ── Wiersz 2: Stepper liczby + dropdown krzywej + miniaturka ─
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackCountStepper(
                    count = source.trackCount,
                    onCountChange = { onUpdate(source.copy(trackCount = it)) },
                    modifier = Modifier.width(110.dp)
                )

                ExposedDropdownMenuBox(
                    expanded = curveExpanded,
                    onExpandedChange = { curveExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = source.energyCurve.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Strategia") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = curveExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
                    )
                    ExposedDropdownMenu(
                        expanded = curveExpanded,
                        onDismissRequest = { curveExpanded = false }
                    ) {
                        EnergyCurve.presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.displayName) },
                                onClick = {
                                    onUpdate(source.copy(energyCurve = preset))
                                    curveExpanded = false
                                }
                            )
                        }
                    }
                }

                if (source.energyCurve !is EnergyCurve.None) {
                    CurveMiniPreview(
                        curve = source.energyCurve,
                        trackCount = source.trackCount,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // ── Ostrzeżenie Arc/Valley (min 3 utwory) ──────────────────
            val needsMinThree = source.energyCurve is EnergyCurve.Arc || source.energyCurve is EnergyCurve.Valley
            AnimatedVisibility(visible = needsMinThree && source.trackCount < 3) {
                Text(
                    "⚠ ${source.energyCurve.displayName} wymaga min. 3 utworów — z 2 przejdzie na prostszą strategię",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ── Konfiguracja Wave ───────────────────────────────────────
            AnimatedVisibility(visible = source.energyCurve is EnergyCurve.Wave) {
                val wave = source.energyCurve as? EnergyCurve.Wave ?: return@AnimatedVisibility
                WaveConfiguration(
                    wave = wave,
                    trackCount = source.trackCount,
                    onWaveChange = { onUpdate(source.copy(energyCurve = it)) }
                )
            }

            // ── Sortowanie (tylko gdy krzywa = None) ────────────────────
            AnimatedVisibility(visible = source.energyCurve is EnergyCurve.None) {
                ExposedDropdownMenuBox(
                    expanded = sortExpanded,
                    onExpandedChange = { sortExpanded = it }
                ) {
                    OutlinedTextField(
                        value = source.sortBy.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sortuj") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
                    )
                    ExposedDropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = {
                                    onUpdate(source.copy(sortBy = opt))
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // ── Harmonic Mixing toggle (tylko gdy krzywa ≠ None) ────────
            AnimatedVisibility(visible = source.energyCurve !is EnergyCurve.None) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Harmonic Mixing",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            "Sąsiednie utwory w zgodnych tonacjach (Camelot)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = source.harmonicMixing,
                        onCheckedChange = { onUpdate(source.copy(harmonicMixing = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyGreen,
                            checkedTrackColor = SpotifyGreen.copy(alpha = 0.4f)
                        )
                    )
                }
            }

            // ── Pinned Tracks ───────────────────────────────────────────
            AnimatedVisibility(visible = source.playlist != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(
                            onClick = onPinTracks,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.PushPin, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Przypnij utwory")
                        }
                        if (source.pinnedTracks.isNotEmpty()) {
                            Text(
                                "(${source.pinnedTracks.size}/${source.trackCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGreen
                            )
                        }
                    }

                    if (source.pinnedTracks.isNotEmpty()) {
                        PinnedTrackChips(
                            pinnedTracks = source.pinnedTracks,
                            onRemove = onRemovePinnedTrack
                        )
                    }
                }
            }
        }
    }

}

// ── Stepper liczby utworów ───────────────────────────────────────────────

@Composable
private fun TrackCountStepper(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(
            onClick = { if (count > 1) onCountChange(count - 1) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, "Mniej", modifier = Modifier.size(18.dp))
        }

        OutlinedTextField(
            value = count.toString(),
            onValueChange = { v ->
                v.toIntOrNull()?.coerceIn(1, 200)?.let { onCountChange(it) }
            },
            label = { Text("Ilość") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
        )

        IconButton(
            onClick = { if (count < 200) onCountChange(count + 1) },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, "Więcej", modifier = Modifier.size(18.dp))
        }
    }
}

// ── Konfiguracja Wave ────────────────────────────────────────────────────

@Composable
private fun WaveConfiguration(
    wave: EnergyCurve.Wave,
    trackCount: Int,
    onWaveChange: (EnergyCurve.Wave) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kierunek:", style = MaterialTheme.typography.labelMedium)
            WaveDirection.entries.forEach { dir ->
                FilterChip(
                    selected = wave.direction == dir,
                    onClick = { onWaveChange(wave.copy(direction = dir)) },
                    label = { Text(dir.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpotifyGreen,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Półfala:", style = MaterialTheme.typography.labelMedium)

            IconButton(
                onClick = {
                    if (wave.tracksPerHalfWave > 1)
                        onWaveChange(wave.copy(tracksPerHalfWave = wave.tracksPerHalfWave - 1))
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Mniej", modifier = Modifier.size(18.dp))
            }

            Text(
                "${wave.tracksPerHalfWave}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = SpotifyGreen
            )

            IconButton(
                onClick = {
                    if (wave.tracksPerHalfWave < 10)
                        onWaveChange(wave.copy(tracksPerHalfWave = wave.tracksPerHalfWave + 1))
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "Więcej", modifier = Modifier.size(18.dp))
            }

            Text(
                "≈ ${wave.fullWaveSize} utw./falę",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (trackCount < wave.tracksPerHalfWave) {
            Text(
                "⚠ Zbyt mało utworów dla pełnej fali (min. ${wave.tracksPerHalfWave})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ── Pinned Track Chips ───────────────────────────────────────────────────

@Composable
private fun PinnedTrackChips(
    pinnedTracks: List<PinnedTrackInfo>,
    onRemove: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pinnedTracks.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { pinned ->
                    AssistChip(
                        onClick = { onRemove(pinned.id) },
                        label = {
                            Text(
                                "📌 ${pinned.title} — ${pinned.artist}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close, "Usuń",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

// ── Miniaturka krzywej (Canvas 48dp) ─────────────────────────────────────

@Composable
fun CurveMiniPreview(
    curve: EnergyCurve,
    trackCount: Int,
    modifier: Modifier = Modifier
) {
    val previewCount = trackCount.coerceIn(4, 30)
    val targets = remember(curve, previewCount) { curve.generateTargets(previewCount) }
    val lineColor = SpotifyGreen

    Canvas(modifier = modifier) {
        if (targets.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val padding = 4f

        val path = Path()
        targets.forEachIndexed { i, score ->
            val x = padding + (w - 2 * padding) * i / (targets.size - 1).coerceAtLeast(1)
            val y = h - padding - (h - 2 * padding) * score
            if (i == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        drawPath(path, lineColor, style = Stroke(width = 2f))
    }
}
