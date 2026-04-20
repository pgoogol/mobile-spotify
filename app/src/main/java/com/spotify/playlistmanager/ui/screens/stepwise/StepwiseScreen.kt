@file:OptIn(ExperimentalMaterial3Api::class)

package com.spotify.playlistmanager.ui.screens.stepwise

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.model.NextTrackTarget
import com.spotify.playlistmanager.domain.model.ScoreAxis
import com.spotify.playlistmanager.domain.usecase.SuggestNextTrackUseCase
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

/**
 * Ekran trybu „krok po kroku" — interaktywny kreator playlisty.
 *
 * User wybiera pulę (opcjonalnie dwie — salsa + bachata), klika przyciski
 * nastrojowe, dostaje 5 sugestii, tapie kandydata → trafia do sesji.
 * Na końcu zapis jako nowa playlista Spotify.
 *
 * Dedup: algorytm nigdy nie powtórzy utworu; user może ręcznie dodać dowolny
 * utwór z puli (bypasses dedup, z ostrzeżeniem) — feature przyszły.
 */
@Composable
fun StepwiseScreen(
    onBack: () -> Unit,
    viewModel: StepwiseViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Dialog zapisu
    var showSaveDialog by remember { mutableStateOf(false) }

    // Error snackbar
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onClearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Krok po kroku", fontWeight = FontWeight.Bold)
                        if (state.sessionTracks.isNotEmpty()) {
                            Text(
                                "${state.sessionTracks.size} utworów w sesji",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::onUndoLast,
                        enabled = state.sessionTracks.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Cofnij")
                    }
                    IconButton(
                        onClick = { showSaveDialog = true },
                        enabled = state.canSave
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Zapisz")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isLoadingPlaylists) {
                LoadingRow("Ładowanie playlist…")
            }

            PoolSelectorsSection(
                availablePlaylists = state.availablePlaylists,
                poolA = state.poolA,
                poolB = state.poolB,
                activePool = state.activePool,
                onSelectPoolA = viewModel::onSelectPoolA,
                onSelectPoolB = viewModel::onSelectPoolB,
                onAddSecondPool = viewModel::onAddSecondPool,
                onRemoveSecondPool = viewModel::onRemoveSecondPool,
                onSetActivePool = viewModel::onSetActivePool
            )

            if (state.hasPoolB) {
                TandaStructureSection(
                    current = state.tandaStructure,
                    counter = state.tandaCounter,
                    activePool = state.activePool,
                    onSetStructure = viewModel::onSetTandaStructure
                )
            }

            SessionTracksSection(
                tracks = state.sessionTracks,
                onUndoLast = viewModel::onUndoLast,
                onClear = viewModel::onClearSession
            )

            ResolvedTargetBadge(
                score = state.resolvedTargetScore,
                axis = state.resolvedAxis,
                axisOfLast = state.currentAxis,
                hasContext = state.sessionTracks.isNotEmpty()
            )

            MoodButtonsSection(
                currentTarget = state.currentTarget,
                hasContext = state.sessionTracks.any { it.pool == state.activePool },
                onTargetClick = viewModel::onTargetClick
            )

            CandidatesSection(
                candidates = state.candidates,
                isLoading = state.isComputingCandidates,
                poolSelected = state.activePoolSlot.playlist != null,
                onPick = viewModel::onPickCandidate
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // Dialog zapisu
    if (showSaveDialog) {
        SaveDialog(
            currentName = state.newPlaylistName,
            trackCount = state.sessionTracks.size,
            onNameChange = viewModel::onPlaylistNameChange,
            onConfirm = {
                viewModel.onSaveAsNewPlaylist()
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    // Success dialog
    when (val save = state.saveState) {
        is SaveState.Success -> SaveSuccessDialog(
            url = save.playlistUrl,
            trackCount = save.trackCount,
            onOpenSpotify = {
                val intent = Intent(Intent.ACTION_VIEW, save.playlistUrl.toUri())
                context.startActivity(intent)
                viewModel.onSaveStateConsumed()
            },
            onDismiss = viewModel::onSaveStateConsumed
        )
        is SaveState.Error -> AlertDialog(
            onDismissRequest = viewModel::onSaveStateConsumed,
            title = { Text("Błąd zapisu") },
            text = { Text(save.message) },
            confirmButton = {
                TextButton(onClick = viewModel::onSaveStateConsumed) { Text("OK") }
            }
        )
        else -> Unit
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: selektory pul
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun PoolSelectorsSection(
    availablePlaylists: List<Playlist>,
    poolA: PoolSlot,
    poolB: PoolSlot?,
    activePool: ActivePool,
    onSelectPoolA: (Playlist) -> Unit,
    onSelectPoolB: (Playlist) -> Unit,
    onAddSecondPool: () -> Unit,
    onRemoveSecondPool: () -> Unit,
    onSetActivePool: (ActivePool) -> Unit
) {
    SectionCard(title = "Pule") {
        PoolRow(
            label = "Pula A",
            slot = poolA,
            availablePlaylists = availablePlaylists,
            isActive = activePool == ActivePool.A,
            canActivate = poolB != null && poolA.playlist != null,
            onSelect = onSelectPoolA,
            onActivate = { onSetActivePool(ActivePool.A) },
            onRemove = null
        )
        Spacer(Modifier.height(8.dp))

        if (poolB != null) {
            PoolRow(
                label = "Pula B",
                slot = poolB,
                availablePlaylists = availablePlaylists,
                isActive = activePool == ActivePool.B,
                canActivate = poolA.playlist != null && poolB.playlist != null,
                onSelect = onSelectPoolB,
                onActivate = { onSetActivePool(ActivePool.B) },
                onRemove = onRemoveSecondPool
            )
        } else {
            OutlinedButton(
                onClick = onAddSecondPool,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Dodaj drugą pulę (tandy salsa+bachata)")
            }
        }
    }
}

@Composable
private fun PoolRow(
    label: String,
    slot: PoolSlot,
    availablePlaylists: List<Playlist>,
    isActive: Boolean,
    canActivate: Boolean,
    onSelect: (Playlist) -> Unit,
    onActivate: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlaylistPickerDropdown(
            label = label,
            selected = slot.playlist,
            available = availablePlaylists,
            onSelect = onSelect,
            modifier = Modifier.weight(1f)
        )
        if (canActivate) {
            FilterChip(
                selected = isActive,
                onClick = onActivate,
                label = { Text(if (isActive) "Aktywna" else "Aktywuj") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen.copy(alpha = 0.25f),
                    selectedLabelColor = SpotifyGreen
                )
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Usuń pulę")
            }
        }
    }
    if (slot.playlist != null) {
        Text(
            "${slot.tracks.size} utworów",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp)
        )
    }
}

@Composable
private fun PlaylistPickerDropdown(
    label: String,
    selected: Playlist?,
    available: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected?.name ?: "Wybierz…",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            available.forEach { playlist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            playlist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSelect(playlist)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: tanda structure
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun TandaStructureSection(
    current: TandaStructure?,
    counter: TandaCounter,
    activePool: ActivePool,
    onSetStructure: (TandaStructure?) -> Unit
) {
    SectionCard(title = "Struktura tandy") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = current == null,
                onClick = { onSetStructure(null) },
                label = { Text("Ręcznie") }
            )
            FilterChip(
                selected = current == TandaStructure.TWO_THREE,
                onClick = { onSetStructure(TandaStructure.TWO_THREE) },
                label = { Text("2:3") }
            )
            FilterChip(
                selected = current == TandaStructure.THREE_THREE,
                onClick = { onSetStructure(TandaStructure.THREE_THREE) },
                label = { Text("3:3") }
            )
            FilterChip(
                selected = current == TandaStructure.THREE_FOUR,
                onClick = { onSetStructure(TandaStructure.THREE_FOUR) },
                label = { Text("3:4") }
            )
        }
        if (current != null) {
            Spacer(Modifier.height(8.dp))
            val limit = if (activePool == ActivePool.A) current.countA else current.countB
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tanda ${counter.tandaNumber} · ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${activePool.name}: ${counter.progressInCurrentBlock}/$limit",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = SpotifyGreen
                )
                Spacer(Modifier.width(8.dp))
                // Kropki postępu
                repeat(limit) { i ->
                    val filled = i < counter.progressInCurrentBlock
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (filled) SpotifyGreen
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                shape = CircleShape
                            )
                    )
                    if (i < limit - 1) Spacer(Modifier.width(3.dp))
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: zbudowana sesja
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun SessionTracksSection(
    tracks: List<SessionTrack>,
    onUndoLast: () -> Unit,
    onClear: () -> Unit
) {
    SectionCard(title = if (tracks.isEmpty()) "Sesja (pusta)" else "Sesja · ${tracks.size} utworów") {
        if (tracks.isEmpty()) {
            Text(
                "Wybierz pulę i kliknij przycisk nastrojowy, aby zacząć.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(tracks.size) {
                if (tracks.isNotEmpty()) {
                    listState.animateScrollToItem(tracks.size - 1)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(tracks) { index, sessionTrack ->
                    SessionTrackRow(index + 1, sessionTrack)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUndoLast) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Cofnij ostatni")
                }
                TextButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Wyczyść sesję")
                }
            }
        }
    }
}

@Composable
private fun SessionTrackRow(number: Int, sessionTrack: SessionTrack) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "$number.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                sessionTrack.track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sessionTrack.track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                sessionTrack.bpm?.let {
                    Spacer(Modifier.width(6.dp))
                    MiniBadge("${it.toInt()} BPM")
                }
                sessionTrack.camelot?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.width(4.dp))
                    MiniBadge(it)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Surface(
            color = poolColor(sessionTrack.pool).copy(alpha = 0.2f),
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text(
                sessionTrack.pool.name,
                style = MaterialTheme.typography.labelSmall,
                color = poolColor(sessionTrack.pool),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: przyciski nastrojowe
// ══════════════════════════════════════════════════════════════════════

private data class MoodButtonDef(
    val target: NextTrackTarget,
    val emoji: String,
    val label: String,
    val requiresContext: Boolean
)

private val MOOD_BUTTONS = listOf(
    MoodButtonDef(NextTrackTarget.Peak, "\uD83D\uDD25", "Peak", false),
    MoodButtonDef(NextTrackTarget.Warmup, "\u2B06", "Podgrzej", true),
    MoodButtonDef(NextTrackTarget.Hold, "\u27A1", "Trzymaj", true),
    MoodButtonDef(NextTrackTarget.Chill, "\u2B07", "Schłódź", true),
    MoodButtonDef(NextTrackTarget.Cooldown, "\uD83C\uDF19", "Cooldown", false),
    MoodButtonDef(NextTrackTarget.SwitchAxis, "\uD83C\uDFAD", "Zmień klimat", true)
)

@Composable
private fun MoodButtonsSection(
    currentTarget: NextTrackTarget,
    hasContext: Boolean,
    onTargetClick: (NextTrackTarget) -> Unit
) {
    SectionCard(title = "Dokąd dalej?") {
        // Siatka 3x2 ręcznie
        val rows = MOOD_BUTTONS.chunked(3)
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { def ->
                    val enabled = !def.requiresContext || hasContext
                    val selected = def.target == currentTarget
                    MoodButton(
                        emoji = def.emoji,
                        label = def.label,
                        selected = selected,
                        enabled = enabled,
                        onClick = { onTargetClick(def.target) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Dopełnij pustymi boxami gdy ostatni wiersz niepełny
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MoodButton(
    emoji: String,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        selected -> SpotifyGreen.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val border = if (selected) SpotifyGreen else Color.Transparent

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, border) else null,
        modifier = modifier
            .height(60.dp)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(emoji, fontSize = 18.sp)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: resolved target badge
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun ResolvedTargetBadge(
    score: Float,
    axis: ScoreAxis,
    axisOfLast: ScoreAxis,
    hasContext: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        AssistChip(
            onClick = {},
            enabled = false,
            label = {
                Text(
                    "Cel: ${axis.name} ${"%.2f".format(score)}",
                    style = MaterialTheme.typography.labelSmall
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                disabledContainerColor = SpotifyGreen.copy(alpha = 0.12f),
                disabledLabelColor = SpotifyGreen
            )
        )
        if (hasContext && axis != axisOfLast) {
            Text(
                "(zmiana osi vs ostatni)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: kandydaci
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun CandidatesSection(
    candidates: List<SuggestNextTrackUseCase.Candidate>,
    isLoading: Boolean,
    poolSelected: Boolean,
    onPick: (SuggestNextTrackUseCase.Candidate) -> Unit
) {
    SectionCard(title = "Sugestie") {
        when {
            !poolSelected -> Text(
                "Wybierz pulę powyżej, żeby zobaczyć sugestie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            isLoading -> LoadingRow("Liczenie kandydatów…")
            candidates.isEmpty() -> Text(
                "Brak kandydatów — pula wyczerpana albo bez audio features.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                candidates.forEachIndexed { idx, candidate ->
                    CandidateRow(idx + 1, candidate, onClick = { onPick(candidate) })
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(
    rank: Int,
    candidate: SuggestNextTrackUseCase.Candidate,
    onClick: () -> Unit
) {
    val compatChip = when {
        candidate.harmonicCompat >= 0.85f -> "\u2705"
        candidate.harmonicCompat >= 0.5f -> "\u26A0"
        else -> "\u274C"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            Surface(
                color = SpotifyGreen.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$rank",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = SpotifyGreen
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    candidate.track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    candidate.track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "score %.2f".format(candidate.score),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!candidate.bpmDelta.isNaN()) {
                        Spacer(Modifier.width(6.dp))
                        val delta = candidate.bpmDelta.toInt()
                        val sign = if (delta > 0) "+" else ""
                        Text(
                            "$sign$delta BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(compatChip, fontSize = 12.sp)
                }
            }
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = "Dodaj",
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Dialog zapisu
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun SaveDialog(
    currentName: String,
    trackCount: Int,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zapisz jako playlistę") },
        text = {
            Column {
                Text(
                    "Utworzy nową playlistę Spotify z $trackCount utworami.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = currentName,
                    onValueChange = onNameChange,
                    label = { Text("Nazwa playlisty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) { Text("Utwórz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

@Composable
private fun SaveSuccessDialog(
    url: String,
    trackCount: Int,
    onOpenSpotify: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlista utworzona") },
        text = {
            Text("Zapisano $trackCount utworów do Spotify.")
        },
        confirmButton = {
            Button(
                onClick = onOpenSpotify,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) { Text("Otwórz w Spotify") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Zamknij") }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════
//  Wspólne helpery UI
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            content()
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = SpotifyGreen
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MiniBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 10.sp
        )
    }
}

private fun poolColor(pool: ActivePool): Color = when (pool) {
    ActivePool.A -> SpotifyGreen
    ActivePool.B -> Color(0xFFFFA726) // Bursztynowy (jak w EnergyCurveChart)
}
