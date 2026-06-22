package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.desktop.theme.SpotifyMidGray
import com.spotify.playlistmanager.domain.usecase.SuggestNextTrackUseCase
import kotlin.math.abs

// ══════════════════════════════════════════════════════════════════════
//  Zaawansowane sekcje ekranu „Krok" (parytet z mobile StepwiseScreen).
//  Plik dodatkowy do PartyPlannerScreen.kt — bez zmian sygnatur głównych.
// ══════════════════════════════════════════════════════════════════════

// ──────────────────────────────────────────────────────────────────────
//  APPEND: tryb kontynuacji (dopisywanie do istniejącej playlisty)
// ──────────────────────────────────────────────────────────────────────

@Composable
internal fun StepwiseAppendModeSection(
    playlists: List<Playlist>,
    appendMode: AppendMode?,
    loadingAnchors: Boolean,
    onEnable: (Playlist) -> Unit,
    onDisable: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    StepwiseSectionCard(title = "Tryb") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = appendMode == null,
                onClick = { if (appendMode != null) onDisable() },
                label = { Text("Nowa playlista") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = appendMode != null,
                onClick = { showPicker = true },
                label = { Text("Dokończ istniejącą") },
                modifier = Modifier.weight(1f),
            )
        }

        if (loadingAnchors) StepwiseLoadingRow("Ładowanie utworów playlisty…")

        if (appendMode != null) {
            Surface(color = SpotifyGreen.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text("📌", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(appendMode.playlistName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${appendMode.originalTrackCount} utworów — zostaną jako kotwice",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                        )
                    }
                    TextButton(onClick = { showPicker = true }) { Text("Zmień") }
                    IconButton(onClick = onDisable) {
                        Icon(Icons.Filled.Close, contentDescription = "Wyłącz tryb dopisywania", modifier = Modifier.size(18.dp))
                    }
                }
            }
        } else if (!loadingAnchors) {
            Text(
                "Wybierz tryb: utwórz nową playlistę albo dopisz do już istniejącej.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showPicker) {
        StepwisePlaylistPickDialog(
            title = "Wybierz playlistę do dokończenia",
            // Polubione nie da się dopisać → odfiltruj.
            playlists = playlists.filter { it.id != com.spotify.playlistmanager.desktop.data.LIKED_ID },
            onSelect = { onEnable(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

/** Prosty dialog z listą playlist (nazwa + liczba utworów). */
@Composable
internal fun StepwisePlaylistPickDialog(
    title: String,
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (playlists.isEmpty()) {
                Text("Brak dostępnych playlist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(playlists) { playlist ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(playlist) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${playlist.trackCount} utworów", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// ──────────────────────────────────────────────────────────────────────
//  TANDA: struktura auto-switch ze stepperami countA / countB
// ──────────────────────────────────────────────────────────────────────

@Composable
internal fun StepwiseTandaStructureSection(
    current: TandaStructure?,
    counter: TandaCounter,
    activePool: ActivePool,
    onSetStructure: (TandaStructure?) -> Unit,
) {
    StepwiseSectionCard(title = "Struktura tandy") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = current == null,
                onClick = { onSetStructure(null) },
                label = { Text("Ręcznie") },
            )
            FilterChip(
                selected = current != null,
                onClick = { if (current == null) onSetStructure(TandaStructure(3, 3)) },
                label = { Text("Auto-switch") },
            )
        }

        if (current != null) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                StepwiseTandaCountStepper(label = "Pula A", value = current.countA, onChange = { onSetStructure(current.copy(countA = it)) })
                StepwiseTandaCountStepper(label = "Pula B", value = current.countB, onChange = { onSetStructure(current.copy(countB = it)) })
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Szybkie:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StepwiseQuickTandaChip(2, 3, current, onSetStructure)
                StepwiseQuickTandaChip(3, 3, current, onSetStructure)
                StepwiseQuickTandaChip(3, 4, current, onSetStructure)
            }

            Spacer(Modifier.height(8.dp))
            val limit = if (activePool == ActivePool.A) current.countA else current.countB
            StepwiseTandaProgressRow(counter, activePool, limit)
        }
    }
}

@Composable
private fun StepwiseTandaCountStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    min: Int = 1,
    max: Int = 50,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = "Mniej", modifier = Modifier.size(18.dp))
            }
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SpotifyGreen,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Więcej", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun StepwiseQuickTandaChip(a: Int, b: Int, current: TandaStructure?, onSetStructure: (TandaStructure?) -> Unit) {
    val selected = current?.countA == a && current.countB == b
    FilterChip(
        selected = selected,
        onClick = { onSetStructure(TandaStructure(a, b)) },
        label = { Text("$a:$b", fontSize = 11.sp) },
        modifier = Modifier.heightIn(min = 28.dp),
    )
}

@Composable
private fun StepwiseTandaProgressRow(counter: TandaCounter, activePool: ActivePool, limit: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Tanda ${counter.tandaNumber} · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${activePool.name}: ${counter.progressInCurrentBlock}/$limit",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = SpotifyGreen,
        )
        Spacer(Modifier.width(8.dp))
        if (limit in 1..10) {
            repeat(limit) { i ->
                val filled = i < counter.progressInCurrentBlock
                Box(
                    modifier = Modifier.size(8.dp).background(
                        color = if (filled) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = CircleShape,
                    ),
                )
                if (i < limit - 1) Spacer(Modifier.width(3.dp))
            }
        } else if (limit > 10) {
            LinearProgressIndicator(
                progress = { counter.progressInCurrentBlock / limit.toFloat() },
                modifier = Modifier.width(120.dp).height(6.dp),
                color = SpotifyGreen,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  Baner: auto-wypełniono tandę (akceptuj / cofnij grupę)
// ──────────────────────────────────────────────────────────────────────

@Composable
internal fun StepwiseAutoFillBanner(
    snapshot: AutoFillSnapshot,
    onAccept: () -> Unit,
    onUndo: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SpotifyGreen.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DoneAll, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Auto-wypełniono tandę: ${snapshot.addedCount} " + if (snapshot.addedCount == 1) "utwór" else "utwory",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "Algorytm uzupełnił blok tandy wg ostatniego nastroju. Akceptujesz wybór czy cofamy grupę?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAccept, colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen), modifier = Modifier.weight(1f)) {
                    Text("Akceptuj")
                }
                OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cofnij grupę")
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  SWAP: picker zamiennika dla wybranego utworu (dialog z kandydatami)
// ──────────────────────────────────────────────────────────────────────

@Composable
internal fun StepwiseSwapPickerDialog(
    picking: SwapState.Picking,
    onPick: (SuggestNextTrackUseCase.Candidate) -> Unit,
    onShowDetail: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wymień utwór") },
        text = {
            Column {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            if (picking.original.isAnchor) "Zastępowany · kotwica na playliście" else "Zastępowany",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(picking.original.track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(picking.original.track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Kandydaci z puli ${picking.original.pool.name} (wg dopasowania)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(picking.candidates) { idx, candidate ->
                        StepwiseSwapCandidateRow(
                            rank = idx + 1,
                            candidate = candidate,
                            onClick = { onPick(candidate) },
                            onShowDetail = { onShowDetail(candidate.track) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/** Wiersz kandydata zamiennika (self-contained — z przyciskiem szczegółów). */
@Composable
private fun StepwiseSwapCandidateRow(
    rank: Int,
    candidate: SuggestNextTrackUseCase.Candidate,
    onClick: () -> Unit,
    onShowDetail: () -> Unit,
) {
    val compatChip = when {
        candidate.harmonicCompat >= 0.85f -> "✅"
        candidate.harmonicCompat >= 0.5f -> "⚠"
        else -> "❌"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Surface(color = SpotifyGreen.copy(alpha = 0.15f), shape = CircleShape, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SpotifyGreen)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(candidate.track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("score %.2f".format(candidate.score), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!candidate.bpmDelta.isNaN()) {
                        Spacer(Modifier.width(6.dp))
                        val delta = candidate.bpmDelta.toInt()
                        Text("${if (delta > 0) "+" else ""}$delta BPM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(compatChip, fontSize = 12.sp)
                }
            }
            IconButton(onClick = onShowDetail, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MusicNote, contentDescription = "Szczegóły", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────
//  MANUAL PICKER: dodaj utwór z dowolnej playlisty (duplikaty dozwolone)
// ──────────────────────────────────────────────────────────────────────

@Composable
internal fun StepwiseManualPickPlaylistDialog(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onDismiss: () -> Unit,
) {
    StepwisePlaylistPickDialog(
        title = "Wybierz playlistę",
        playlists = playlists,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun StepwiseManualPickTrackDialog(
    playlistName: String,
    tracks: List<Track>,
    isLoading: Boolean,
    onPick: (Track) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(tracks, query) {
        if (query.isBlank()) tracks
        else tracks.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(playlistName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoading) {
                    StepwiseLoadingRow("Ładowanie utworów…")
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Szukaj") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (filtered.isEmpty()) {
                        Text(
                            if (query.isBlank()) "Playlista jest pusta." else "Brak wyników dla \"$query\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(filtered) { track ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { onPick(track) }.padding(vertical = 6.dp, horizontal = 4.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
        dismissButton = { TextButton(onClick = onBack) { Text("Zmień playlistę") } },
    )
}

// ──────────────────────────────────────────────────────────────────────
//  Zaawansowane — dostrojenie wag algorytmu (panel rozwijany)
// ──────────────────────────────────────────────────────────────────────

@Composable
internal fun StepwiseAdvancedWeightsSection(
    weights: SuggestNextTrackUseCase.Weights,
    onUpdateWeight: ((SuggestNextTrackUseCase.Weights) -> SuggestNextTrackUseCase.Weights) -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Zaawansowane — wagi algorytmu", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Dostrojenie kosztu. Większa waga = mocniejszy wpływ. 0 = ignorowane.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                StepwiseWeightSlider("Dopasowanie do celu", weights.wFit, SuggestNextTrackUseCase.W_FIT) { onUpdateWeight { w -> w.copy(wFit = it) } }
                StepwiseWeightSlider("Harmonia (Camelot)", weights.wHarmonic, SuggestNextTrackUseCase.W_HARMONIC) { onUpdateWeight { w -> w.copy(wHarmonic = it) } }
                StepwiseWeightSlider("Kara za skok BPM", weights.wBpmJump, SuggestNextTrackUseCase.W_BPM_JUMP) { onUpdateWeight { w -> w.copy(wBpmJump = it) } }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Przywróć domyślne (%.1f / %.1f / %.1f)".format(
                            SuggestNextTrackUseCase.W_FIT,
                            SuggestNextTrackUseCase.W_HARMONIC,
                            SuggestNextTrackUseCase.W_BPM_JUMP,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StepwiseWeightSlider(
    label: String,
    value: Float,
    default: Float,
    min: Float = 0f,
    max: Float = 2f,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                "%.2f".format(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (abs(value - default) > 0.01f) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (abs(value - default) > 0.01f) {
                Spacer(Modifier.width(4.dp))
                Text("(dom. %.1f)".format(default), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
            }
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            steps = 19,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
        )
    }
}

// ──────────────────────────────────────────────────────────────────────
//  TRACK DETAIL: dialog ze szczegółami utworu + cechami audio
// ──────────────────────────────────────────────────────────────────────

@Composable
internal fun StepwiseTrackDetailDialog(
    track: Track,
    features: TrackAudioFeatures?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NetworkImage(
                        url = track.albumArtUrl,
                        contentDescription = track.album,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        fallback = {
                            Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(SpotifyMidGray), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                            }
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(track.album, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))

                StepwiseSheetInfoRow("Czas trwania", track.formattedDuration())
                StepwiseSheetInfoRow("Popularność", "${track.popularity} / 100")
                track.releaseDate?.let { StepwiseSheetInfoRow("Data wydania", it) }
                track.id?.let { StepwiseSheetInfoRow("Spotify ID", it) }

                if (features != null) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))
                    Text("Audio Features (z CSV)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.heightIn(max = 260.dp).fillMaxWidth()) {
                        StepwiseAudioFeatureBar("BPM", "${features.bpm}", (features.bpm / 220f).coerceIn(0f, 1f))
                        StepwiseAudioFeatureBar("Energy", "${features.energy}", features.energy / 100f)
                        StepwiseAudioFeatureBar("Danceability", "${features.danceability}", features.danceability / 100f)
                        StepwiseAudioFeatureBar("Valence", "${features.valence}", features.valence / 100f)
                        StepwiseAudioFeatureBar("Acousticness", "${features.acousticness}", features.acousticness / 100f)
                        Spacer(Modifier.height(6.dp))
                        StepwiseSheetInfoRow("Camelot", features.camelot)
                        StepwiseSheetInfoRow("Tonacja", features.musicalKey)
                        if (features.genres.isNotBlank()) StepwiseSheetInfoRow("Gatunki", features.genres)
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Brak danych audio. Zaimportuj plik CSV, aby zobaczyć BPM, energię, tonację i inne cechy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

@Composable
private fun StepwiseAudioFeatureBar(label: String, value: String, progress: Float) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = SpotifyGreen,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun StepwiseSheetInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
    }
}
