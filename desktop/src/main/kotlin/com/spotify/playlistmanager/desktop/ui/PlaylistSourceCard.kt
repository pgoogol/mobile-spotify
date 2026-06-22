package com.spotify.playlistmanager.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.ScoreAxis
import com.spotify.playlistmanager.domain.model.StableLevel
import com.spotify.playlistmanager.domain.model.WaveDirection

/**
 * Karta konfiguracji źródła playlisty — desktopowy odpowiednik
 * `ui.screens.generate.PlaylistSourceCard` z aplikacji mobilnej.
 *
 * Zawiera (jak na Androidzie):
 * - dropdown wyboru playlisty + przycisk „usuń",
 * - stepper liczby utworów (1–200) + dropdown strategii (krzywej) + miniaturkę Canvas,
 * - ostrzeżenie Arc/Valley (min. 3 utwory),
 * - hint o braku smooth join (różna oś niż poprzedni segment),
 * - konfigurację Wave (kierunek + półfala) i Stable (poziom energii),
 * - sortowanie (widoczne tylko gdy strategia = Brak),
 * - przełącznik Harmonic Mixing (widoczny tylko gdy strategia ≠ Brak),
 * - przypinanie utworów (pinned tracks) z obsługą cross-playlist — przycisk
 *   „Przypnij utwory" ([onPinTracks]) otwiera [GeneratePinTrackDialog], a wybrane
 *   utwory są pokazywane jako chipy z możliwością usunięcia ([onRemovePinnedTrack]).
 *
 * @param onPinTracks otwiera dialog przypinania dla tego segmentu.
 * @param onRemovePinnedTrack usuwa przypięty utwór po jego ID.
 * @param prevScoreAxis oś poprzedniego segmentu (null = brak poprzedniego). Gdy
 *   różni się od osi bieżącego segmentu, pokazujemy hint o twardym przejściu.
 */
@Composable
fun PlaylistSourceCard(
    index: Int,
    source: PlaylistSource,
    availablePlaylists: List<Playlist>,
    onUpdate: (PlaylistSource) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean,
    onPinTracks: () -> Unit,
    onRemovePinnedTrack: (String) -> Unit,
    prevScoreAxis: ScoreAxis? = null,
    modifier: Modifier = Modifier,
) {
    var playlistExpanded by remember { mutableStateOf(false) }
    var curveExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Wiersz 1: numer + wybór playlisty + usuń ────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "${index + 1}.",
                    modifier = Modifier.width(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { playlistExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            source.playlist?.name ?: "Wybierz playlistę…",
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = playlistExpanded,
                        onDismissRequest = { playlistExpanded = false },
                    ) {
                        availablePlaylists.forEach { pl ->
                            DropdownMenuItem(
                                text = { Text("${pl.name}  (${pl.trackCount})") },
                                onClick = {
                                    onUpdate(source.copy(playlist = pl))
                                    playlistExpanded = false
                                },
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

            // ── Wiersz 2: stepper liczby + dropdown strategii + miniaturka ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackCountStepper(
                    count = source.trackCount,
                    onCountChange = { onUpdate(source.copy(trackCount = it)) },
                )

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { curveExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(source.energyCurve.displayName, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Strategia")
                    }
                    DropdownMenu(
                        expanded = curveExpanded,
                        onDismissRequest = { curveExpanded = false },
                    ) {
                        EnergyCurve.presets.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(preset.displayName)
                                        Text(
                                            preset.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    onUpdate(source.copy(energyCurve = preset))
                                    curveExpanded = false
                                },
                            )
                        }
                    }
                }

                if (source.energyCurve !is EnergyCurve.None) {
                    CurveMiniPreview(
                        curve = source.energyCurve,
                        trackCount = source.trackCount,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            // ── Ostrzeżenie Arc/Valley (min. 3 utwory) ──────────────────────
            val needsMinThree =
                source.energyCurve is EnergyCurve.Arc || source.energyCurve is EnergyCurve.Valley
            AnimatedVisibility(visible = needsMinThree && source.trackCount < 3) {
                Text(
                    "⚠ ${source.energyCurve.displayName} wymaga min. 3 utworów — z 2 przejdzie na prostszą strategię",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ── Hint: brak smooth join (różna oś niż poprzedni segment) ──────
            val axisChanged = prevScoreAxis != null &&
                source.energyCurve !is EnergyCurve.None &&
                prevScoreAxis != source.energyCurve.scoreAxis
            AnimatedVisibility(visible = axisChanged) {
                val prevName = if (prevScoreAxis == ScoreAxis.DANCE) "tanecznej" else "nastroju"
                val curName = if (source.energyCurve.scoreAxis == ScoreAxis.DANCE) "tanecznej" else "nastroju"
                Text(
                    "ℹ Poprzedni segment używa osi $prevName, ten — $curName. Przejście będzie twarde (bez smooth join).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            // ── Konfiguracja Wave ───────────────────────────────────────────
            AnimatedVisibility(visible = source.energyCurve is EnergyCurve.Wave) {
                val wave = source.energyCurve as? EnergyCurve.Wave
                if (wave != null) {
                    WaveConfiguration(
                        wave = wave,
                        trackCount = source.trackCount,
                        onWaveChange = { onUpdate(source.copy(energyCurve = it)) },
                    )
                }
            }

            // ── Konfiguracja Stable (poziom energii) ────────────────────────
            AnimatedVisibility(visible = source.energyCurve is EnergyCurve.Stable) {
                val stable = source.energyCurve as? EnergyCurve.Stable
                if (stable != null) {
                    StableConfiguration(
                        stable = stable,
                        onStableChange = { onUpdate(source.copy(energyCurve = it)) },
                    )
                }
            }

            // ── Sortowanie (tylko gdy strategia = Brak) ─────────────────────
            AnimatedVisibility(visible = source.energyCurve is EnergyCurve.None) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sortuj", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(onClick = { sortExpanded = true }) {
                            Text(source.sortBy.label, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Sortuj")
                        }
                        DropdownMenu(
                            expanded = sortExpanded,
                            onDismissRequest = { sortExpanded = false },
                        ) {
                            SortOption.entries.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    onClick = {
                                        onUpdate(source.copy(sortBy = opt))
                                        sortExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // ── Harmonic Mixing toggle (tylko gdy strategia ≠ Brak) ─────────
            AnimatedVisibility(visible = source.energyCurve !is EnergyCurve.None) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Harmonic Mixing",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "Sąsiednie utwory w zgodnych tonacjach (Camelot)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = source.harmonicMixing,
                        onCheckedChange = { onUpdate(source.copy(harmonicMixing = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyGreen,
                            checkedTrackColor = SpotifyGreen.copy(alpha = 0.4f),
                        ),
                    )
                }
            }

            // ── Pinned Tracks (cross-playlist) ──────────────────────────────
            AnimatedVisibility(visible = source.playlist != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TextButton(
                            onClick = onPinTracks,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Icon(Icons.Default.PushPin, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Przypnij utwory")
                        }
                        if (source.pinnedTracks.isNotEmpty()) {
                            Text(
                                "(${source.pinnedTracks.size}/${source.trackCount})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpotifyGreen,
                            )
                        }
                    }

                    if (source.pinnedTracks.isNotEmpty()) {
                        PinnedTrackChips(
                            pinnedTracks = source.pinnedTracks,
                            onRemove = onRemovePinnedTrack,
                        )
                    }
                }
            }
        }
    }
}

// ── Pinned Track Chips ───────────────────────────────────────────────────────

@Composable
private fun PinnedTrackChips(
    pinnedTracks: List<PinnedTrackInfo>,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        pinnedTracks.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { pinned ->
                    AssistChip(
                        onClick = { onRemove(pinned.id) },
                        label = {
                            Text(
                                "📌 ${pinned.title} — ${pinned.artist}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, "Usuń", modifier = Modifier.size(14.dp))
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

// ── Stepper liczby utworów ───────────────────────────────────────────────────

@Composable
private fun TrackCountStepper(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = { if (count > 1) onCountChange(count - 1) },
            modifier = Modifier.size(28.dp),
        ) {
            Icon(Icons.Default.KeyboardArrowDown, "Mniej", modifier = Modifier.size(18.dp))
        }
        Text(
            "$count",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = SpotifyGreen,
            modifier = Modifier.width(32.dp),
        )
        IconButton(
            onClick = { if (count < 200) onCountChange(count + 1) },
            modifier = Modifier.size(28.dp),
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
    onWaveChange: (EnergyCurve.Wave) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Kierunek:", style = MaterialTheme.typography.labelMedium)
            WaveDirection.entries.forEach { dir ->
                FilterChip(
                    selected = wave.direction == dir,
                    onClick = { onWaveChange(wave.copy(direction = dir)) },
                    label = { Text(dir.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpotifyGreen,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Półfala:", style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = {
                    if (wave.tracksPerHalfWave > 1) {
                        onWaveChange(wave.copy(tracksPerHalfWave = wave.tracksPerHalfWave - 1))
                    }
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowDown, "Mniej", modifier = Modifier.size(18.dp))
            }
            Text(
                "${wave.tracksPerHalfWave}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = SpotifyGreen,
            )
            IconButton(
                onClick = {
                    if (wave.tracksPerHalfWave < 10) {
                        onWaveChange(wave.copy(tracksPerHalfWave = wave.tracksPerHalfWave + 1))
                    }
                },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(Icons.Default.KeyboardArrowUp, "Więcej", modifier = Modifier.size(18.dp))
            }
            Text(
                "≈ ${wave.fullWaveSize} utw./falę",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (trackCount < wave.tracksPerHalfWave) {
            Text(
                "⚠ Zbyt mało utworów dla pełnej fali (min. ${wave.tracksPerHalfWave})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ── Konfiguracja Stable (poziom energii) ─────────────────────────────────────

@Composable
private fun StableConfiguration(
    stable: EnergyCurve.Stable,
    onStableChange: (EnergyCurve.Stable) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Poziom energii:", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StableLevel.entries.forEach { level ->
                FilterChip(
                    selected = stable.level == level,
                    onClick = { onStableChange(stable.copy(level = level)) },
                    label = { Text(level.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpotifyGreen,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}

// ── Miniaturka krzywej (Canvas 48dp) ─────────────────────────────────────────

/**
 * Mała podglądowa krzywa strategii — rysuje [EnergyCurve.generateTargets] dla
 * podglądowej liczby pozycji. Odpowiednik `CurveMiniPreview` z aplikacji mobilnej.
 */
@Composable
fun CurveMiniPreview(
    curve: EnergyCurve,
    trackCount: Int,
    modifier: Modifier = Modifier,
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
            val y = h - padding - (h - 2 * padding) * score.coerceIn(0f, 1f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 2f))
    }
}
