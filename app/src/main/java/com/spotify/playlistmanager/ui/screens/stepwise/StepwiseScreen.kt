@file:OptIn(ExperimentalMaterial3Api::class)

package com.spotify.playlistmanager.ui.screens.stepwise

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
                            val subtitle = state.appendMode?.let {
                                "${state.newSessionTracks.size} nowych \u2192 ${it.playlistName}"
                            } ?: "${state.sessionTracks.size} utworów w sesji"
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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

            AppendModeSection(
                availablePlaylists = state.availablePlaylists,
                appendMode = state.appendMode,
                isLoadingAnchors = state.isLoadingAppendAnchors,
                onEnable = viewModel::onEnableAppendMode,
                onDisable = viewModel::onDisableAppendMode
            )

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

            state.autoFillSnapshot?.let { snapshot ->
                AutoFillBanner(
                    snapshot = snapshot,
                    onAccept = viewModel::onAcceptAutoFill,
                    onUndo = viewModel::onUndoAutoFill
                )
            }

            if (state.sessionTracks.isNotEmpty()) {
                SessionEnergyMiniSection(tracks = state.sessionTracks)
            }

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
                canAutoFill = state.canAutoFill,
                remainingInBlock = state.remainingInBlock,
                isAutoFilling = state.isAutoFilling,
                onPick = viewModel::onPickCandidate,
                onAutoFill = viewModel::onAutoFillBlock
            )

            AdvancedWeightsSection(
                weights = state.weights,
                onUpdateWeight = viewModel::onUpdateWeight,
                onReset = viewModel::onResetWeights
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // Dialog zapisu
    if (showSaveDialog) {
        val appendMode = state.appendMode
        if (appendMode != null) {
            AppendConfirmDialog(
                playlistName = appendMode.playlistName,
                newTrackCount = state.newSessionTracks.size,
                existingTrackCount = appendMode.originalTrackCount,
                onConfirm = {
                    viewModel.onSaveAsNewPlaylist()
                    showSaveDialog = false
                },
                onDismiss = { showSaveDialog = false }
            )
        } else {
            SaveDialog(
                currentName = state.newPlaylistName,
                currentDescription = state.newPlaylistDescription,
                trackCount = state.sessionTracks.size,
                onNameChange = viewModel::onPlaylistNameChange,
                onDescriptionChange = viewModel::onPlaylistDescriptionChange,
                onConfirm = {
                    viewModel.onSaveAsNewPlaylist()
                    showSaveDialog = false
                },
                onDismiss = { showSaveDialog = false }
            )
        }
    }

    // Success dialog
    when (val save = state.saveState) {
        is SaveState.Success -> SaveSuccessDialog(
            url = save.playlistUrl,
            trackCount = save.trackCount,
            isAppend = state.appendMode != null,
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
//  Sekcja: tryb kontynuacji (append do istniejącej playlisty)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun AppendModeSection(
    availablePlaylists: List<Playlist>,
    appendMode: AppendMode?,
    isLoadingAnchors: Boolean,
    onEnable: (Playlist) -> Unit,
    onDisable: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SectionCard(title = "Tryb") {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = appendMode == null,
                onClick = { if (appendMode != null) onDisable() },
                label = { Text("Nowa playlista") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = appendMode != null,
                onClick = { expanded = true },
                label = { Text("Dokończ istniejącą") },
                modifier = Modifier.weight(1f)
            )
        }

        if (isLoadingAnchors) {
            Spacer(Modifier.height(4.dp))
            LoadingRow("Ładowanie utworów playlisty…")
        }

        if (appendMode != null) {
            Spacer(Modifier.height(4.dp))
            Surface(
                color = SpotifyGreen.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("\uD83D\uDCCC", fontSize = 14.sp)
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            appendMode.playlistName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${appendMode.originalTrackCount} utworów — zostaną jako kotwice",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                    TextButton(onClick = { expanded = true }) { Text("Zmień") }
                    IconButton(onClick = onDisable) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Wyłącz tryb dopisywania",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else if (!isLoadingAnchors) {
            Text(
                "Wybierz tryb: utwórz nową playlistę albo dopisz do już istniejącej.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (expanded) {
        AppendTargetPickerDialog(
            playlists = availablePlaylists,
            onSelect = {
                onEnable(it)
                expanded = false
            },
            onDismiss = { expanded = false }
        )
    }
}

@Composable
private fun AppendTargetPickerDialog(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onDismiss: () -> Unit
) {
    // Odfiltruj Polubione — nie można do nich dopisywać
    val targets = playlists.filter {
        it.id != com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase.LIKED_SONGS_ID
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz playlistę do dokończenia") },
        text = {
            if (targets.isEmpty()) {
                Text(
                    "Brak dostępnych playlist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(targets) { playlist ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(playlist) }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${playlist.trackCount} utworów",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
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
        // ── Tryb ────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = current == null,
                onClick = { onSetStructure(null) },
                label = { Text("Ręcznie") }
            )
            FilterChip(
                selected = current != null,
                onClick = {
                    if (current == null) onSetStructure(TandaStructure.THREE_THREE)
                },
                label = { Text("Auto-switch") }
            )
        }

        if (current != null) {
            Spacer(Modifier.height(10.dp))

            // ── Steppery A / B ────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TandaCountStepper(
                    label = "Pula A",
                    value = current.countA,
                    onChange = { onSetStructure(current.copy(countA = it)) }
                )
                TandaCountStepper(
                    label = "Pula B",
                    value = current.countB,
                    onChange = { onSetStructure(current.copy(countB = it)) }
                )
            }

            Spacer(Modifier.height(6.dp))

            // ── Szybkie presety ───────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Szybkie:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                QuickTandaChip(2, 3, current, onSetStructure)
                QuickTandaChip(3, 3, current, onSetStructure)
                QuickTandaChip(3, 4, current, onSetStructure)
            }

            Spacer(Modifier.height(8.dp))

            // ── Licznik bloku ─────────────────────────────────
            val limit = if (activePool == ActivePool.A) current.countA else current.countB
            TandaProgressRow(counter, activePool, limit)
        }
    }
}

@Composable
private fun TandaCountStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    min: Int = 1,
    max: Int = 50
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > min) onChange(value - 1) },
                enabled = value > min,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Mniej", modifier = Modifier.size(18.dp))
            }
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SpotifyGreen,
                modifier = Modifier.width(32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(
                onClick = { if (value < max) onChange(value + 1) },
                enabled = value < max,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Więcej", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun QuickTandaChip(
    a: Int,
    b: Int,
    current: TandaStructure?,
    onSetStructure: (TandaStructure?) -> Unit
) {
    val selected = current?.countA == a && current.countB == b
    FilterChip(
        selected = selected,
        onClick = { onSetStructure(TandaStructure(a, b)) },
        label = { Text("$a:$b", fontSize = 11.sp) },
        modifier = Modifier.heightIn(min = 28.dp)
    )
}

@Composable
private fun TandaProgressRow(
    counter: TandaCounter,
    activePool: ActivePool,
    limit: Int
) {
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
        if (limit <= 10) {
            // Kropki dla małych
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
        } else {
            // Pasek dla dużych
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (limit == 0) 0f else counter.progressInCurrentBlock / limit.toFloat() },
                modifier = Modifier.width(120.dp).height(6.dp),
                color = SpotifyGreen,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
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
    val anchorCount = tracks.count { it.isAnchor }
    val newCount = tracks.size - anchorCount
    val title = when {
        tracks.isEmpty() -> "Sesja (pusta)"
        anchorCount > 0 -> "Sesja · $newCount nowych + $anchorCount kotwic"
        else -> "Sesja · $newCount utworów"
    }
    SectionCard(title = title) {
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
                    // Numeracja tylko dla nowych utworów (kotwice mają 📌)
                    val newNumber = if (sessionTrack.isAnchor) 0
                    else tracks.take(index + 1).count { !it.isAnchor }
                    SessionTrackRow(newNumber, sessionTrack)
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
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (sessionTrack.isAnchor)
                    Modifier.background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                        RoundedCornerShape(4.dp)
                    )
                else Modifier
            )
            .padding(vertical = if (sessionTrack.isAnchor) 2.dp else 0.dp)
    ) {
        if (sessionTrack.isAnchor) {
            Text(
                "\uD83D\uDCCC",
                fontSize = 12.sp,
                modifier = Modifier.width(24.dp)
            )
        } else {
            Text(
                "$number.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                sessionTrack.track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (sessionTrack.isAnchor)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface
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
        if (!sessionTrack.isAnchor) {
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
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: banner potwierdzenia auto-fill
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun AutoFillBanner(
    snapshot: AutoFillSnapshot,
    onAccept: () -> Unit,
    onUndo: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = SpotifyGreen.copy(alpha = 0.15f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.DoneAll,
                    contentDescription = null,
                    tint = SpotifyGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Dodano ${snapshot.addedCount} " +
                        if (snapshot.addedCount == 1) "utwór" else "utwory",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "Algorytm uzupełnił blok tandy wg ostatniego nastroju. " +
                    "Akceptujesz wybór czy cofamy grupę?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    modifier = Modifier.weight(1f)
                ) { Text("Akceptuj") }
                OutlinedButton(
                    onClick = onUndo,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Cofnij grupę")
                }
            }
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
    canAutoFill: Boolean,
    remainingInBlock: Int,
    isAutoFilling: Boolean,
    onPick: (SuggestNextTrackUseCase.Candidate) -> Unit,
    onAutoFill: () -> Unit
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

        if (canAutoFill || isAutoFilling) {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAutoFill,
                enabled = canAutoFill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen.copy(alpha = 0.85f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAutoFilling) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Uzupełniam…")
                } else {
                    Icon(
                        Icons.Filled.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Uzupełnij tandę ($remainingInBlock)")
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
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(10.dp)
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
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Zwiń szczegóły" else "Dlaczego ten utwór?",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = "Dodaj",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                CandidateExplainRow(candidate)
            }
        }
    }
}

@Composable
private fun CandidateExplainRow(candidate: SuggestNextTrackUseCase.Candidate) {
    val bpmJumpNorm = if (!candidate.bpmDelta.isNaN()) {
        (kotlin.math.abs(candidate.bpmDelta) / SuggestNextTrackUseCase.BPM_JUMP_NORMALIZER)
            .coerceIn(0f, 1f)
    } else 0f

    val fitCost = SuggestNextTrackUseCase.W_FIT * candidate.fitDistance
    val harmonicCost = SuggestNextTrackUseCase.W_HARMONIC * (1f - candidate.harmonicCompat)
    val bpmCost = SuggestNextTrackUseCase.W_BPM_JUMP * bpmJumpNorm

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Dlaczego ten utwór?",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExplainLine(
            label = "Dopasowanie do celu",
            detail = "odległość %.2f od targetu".format(candidate.fitDistance),
            cost = fitCost,
            weight = SuggestNextTrackUseCase.W_FIT
        )
        ExplainLine(
            label = "Harmonia (Camelot)",
            detail = "kompatybilność %.2f".format(candidate.harmonicCompat),
            cost = harmonicCost,
            weight = SuggestNextTrackUseCase.W_HARMONIC
        )
        ExplainLine(
            label = "Skok BPM",
            detail = if (candidate.bpmDelta.isNaN()) "brak kontekstu"
            else "Δ %+d BPM".format(candidate.bpmDelta.toInt()),
            cost = bpmCost,
            weight = SuggestNextTrackUseCase.W_BPM_JUMP
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            modifier = Modifier.padding(vertical = 2.dp)
        )
        Row {
            Text(
                "Koszt łączny",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "%.3f".format(candidate.totalCost),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SpotifyGreen
            )
        }
        Text(
            "Mniejszy koszt = lepsze dopasowanie. Waga pokazuje jak mocno dany czynnik wpływa na decyzję.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ExplainLine(
    label: String,
    detail: String,
    cost: Float,
    weight: Float
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
        Text(
            "waga %.1f".format(weight),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "+%.3f".format(cost),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (cost > 0.3f) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: mini wykres energii sesji (live)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun SessionEnergyMiniSection(tracks: List<SessionTrack>) {
    SectionCard(title = "Krzywa energii sesji") {
        if (tracks.size < 2) {
            Text(
                "Dodaj co najmniej 2 utwory, żeby zobaczyć przebieg.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SectionCard
        }
        SessionEnergyMiniChart(
            tracks = tracks,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(color = SpotifyGreen, label = "Pula A")
            LegendDot(color = Color(0xFFFFA726), label = "Pula B")
        }
    }
}

@Composable
private fun SessionEnergyMiniChart(
    tracks: List<SessionTrack>,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val aColor = SpotifyGreen
    val bColor = Color(0xFFFFA726)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = 8f
        val padY = 6f
        val plotW = w - padX * 2
        val plotH = h - padY * 2

        // Siatka pozioma: 0 / 0.5 / 1.0
        for (v in listOf(0f, 0.5f, 1f)) {
            val y = padY + plotH * (1f - v)
            drawLine(
                color = gridColor,
                start = Offset(padX, y),
                end = Offset(w - padX, y),
                strokeWidth = 1f
            )
        }

        if (tracks.size < 2) return@Canvas

        // Buduj ścieżkę łączącą punkty
        val points = tracks.mapIndexed { idx, t ->
            val x = padX + plotW * (idx.toFloat() / (tracks.size - 1))
            val y = padY + plotH * (1f - t.score.coerceIn(0f, 1f))
            Offset(x, y) to t.pool
        }

        // Linia łącząca (szara) — żeby widzieć przebieg
        val linePath = Path().apply {
            points.forEachIndexed { i, (p, _) ->
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 2f)
        )

        // Kropki kolorowane wg puli
        points.forEach { (p, pool) ->
            val color = if (pool == ActivePool.A) aColor else bColor
            drawCircle(color = color, radius = 4f, center = p)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: Zaawansowane — dostrojenie wag algorytmu
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun AdvancedWeightsSection(
    weights: SuggestNextTrackUseCase.Weights,
    onUpdateWeight: ((SuggestNextTrackUseCase.Weights) -> SuggestNextTrackUseCase.Weights) -> Unit,
    onReset: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp)
        ) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Zaawansowane — wagi algorytmu",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Dostrojenie kosztu. Większa waga = mocniejszy wpływ. 0 = ignorowane.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )

                WeightSlider(
                    label = "Dopasowanie do celu",
                    value = weights.wFit,
                    default = SuggestNextTrackUseCase.W_FIT,
                    onChange = { onUpdateWeight { w -> w.copy(wFit = it) } }
                )
                WeightSlider(
                    label = "Harmonia (Camelot)",
                    value = weights.wHarmonic,
                    default = SuggestNextTrackUseCase.W_HARMONIC,
                    onChange = { onUpdateWeight { w -> w.copy(wHarmonic = it) } }
                )
                WeightSlider(
                    label = "Kara za skok BPM",
                    value = weights.wBpmJump,
                    default = SuggestNextTrackUseCase.W_BPM_JUMP,
                    onChange = { onUpdateWeight { w -> w.copy(wBpmJump = it) } }
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Przywróć domyślne (%.1f / %.1f / %.1f)".format(
                        SuggestNextTrackUseCase.W_FIT,
                        SuggestNextTrackUseCase.W_HARMONIC,
                        SuggestNextTrackUseCase.W_BPM_JUMP
                    ))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WeightSlider(
    label: String,
    value: Float,
    default: Float,
    onChange: (Float) -> Unit,
    min: Float = 0f,
    max: Float = 2f
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                "%.2f".format(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (kotlin.math.abs(value - default) > 0.01f) SpotifyGreen
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (kotlin.math.abs(value - default) > 0.01f) {
                Spacer(Modifier.width(4.dp))
                Text(
                    "(dom. %.1f)".format(default),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
        Slider(
            value = value,
            onValueChange = { onChange(it) },
            valueRange = min..max,
            steps = 19, // 0.1 step on 0..2 range
            colors = SliderDefaults.colors(
                thumbColor = SpotifyGreen,
                activeTrackColor = SpotifyGreen
            )
        )
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Dialog zapisu
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun SaveDialog(
    currentName: String,
    currentDescription: String,
    trackCount: Int,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = currentDescription,
                    onValueChange = onDescriptionChange,
                    label = { Text("Opis (opcjonalny)") },
                    placeholder = {
                        Text(
                            "Zostaw puste dla auto-opisu",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "ℹ Data generowania zostanie dopisana automatycznie",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun AppendConfirmDialog(
    playlistName: String,
    newTrackCount: Int,
    existingTrackCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dopisz do playlisty") },
        text = {
            Column {
                Text(
                    "Dodamy $newTrackCount utworów na koniec playlisty:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "\uD83D\uDCCC $playlistName",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Po dopisaniu playlista będzie miała ${existingTrackCount + newTrackCount} utworów. Istniejących nie zmienimy.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) { Text("Dopisz") }
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
    isAppend: Boolean,
    onOpenSpotify: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAppend) "Utwory dopisane" else "Playlista utworzona") },
        text = {
            Text(
                if (isAppend) "Dopisano $trackCount utworów do playlisty."
                else "Zapisano $trackCount utworów do Spotify."
            )
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
