package com.spotify.playlistmanager.ui.screens.generate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSourceCard(
    source: PlaylistSource,
    availablePlaylists: List<Playlist>,
    onUpdate: (PlaylistSource) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
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
                // Stepper
                TrackCountStepper(
                    count = source.trackCount,
                    onCountChange = { onUpdate(source.copy(trackCount = it)) },
                    modifier = Modifier.width(110.dp)
                )

                // Dropdown krzywej energii
                ExposedDropdownMenuBox(
                    expanded = curveExpanded,
                    onExpandedChange = { curveExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = source.energyCurve.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Krzywa") },
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

                // Miniaturka Canvas 48dp
                if (source.energyCurve !is EnergyCurve.None) {
                    CurveMiniPreview(
                        curve = source.energyCurve,
                        trackCount = source.trackCount,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // ── Konfiguracja Wave (AnimatedVisibility) ──────────────────
            AnimatedVisibility(visible = source.energyCurve is EnergyCurve.Wave) {
                val wave = source.energyCurve as? EnergyCurve.Wave ?: return@AnimatedVisibility
                WaveConfiguration(
                    wave = wave,
                    trackCount = source.trackCount,
                    onWaveChange = { onUpdate(source.copy(energyCurve = it)) }
                )
            }

            // ── Sortowanie (widoczne tylko gdy krzywa = None) ────────────
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
        }
    }
}

// ── Stepper liczby utworów ────────────────────────────────────────────────────

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

// ── Konfiguracja Wave ────────────────────────────────────────────────────────

@Composable
private fun WaveConfiguration(
    wave: EnergyCurve.Wave,
    trackCount: Int,
    onWaveChange: (EnergyCurve.Wave) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Kierunek
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

        // Stepper tracksPerHalfWave
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Półfala:", style = MaterialTheme.typography.labelMedium)

            IconButton(
                onClick = { if (wave.tracksPerHalfWave > 1) onWaveChange(wave.copy(tracksPerHalfWave = wave.tracksPerHalfWave - 1)) },
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
                    if (wave.tracksPerHalfWave < 10) onWaveChange(
                        wave.copy(
                            tracksPerHalfWave = wave.tracksPerHalfWave + 1
                        )
                    )
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

        // Walidacja
        if (trackCount < wave.tracksPerHalfWave) {
            Text(
                "⚠ Zbyt mało utworów (min. ${wave.tracksPerHalfWave})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ── Miniaturka krzywej (Canvas 48dp) ─────────────────────────────────────────

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
