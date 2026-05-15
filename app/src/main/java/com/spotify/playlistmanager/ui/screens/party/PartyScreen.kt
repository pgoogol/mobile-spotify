@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.spotify.playlistmanager.ui.screens.party

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.domain.dj.model.Block
import com.spotify.playlistmanager.domain.dj.model.EnergyArc
import com.spotify.playlistmanager.domain.dj.model.EnergyShape
import com.spotify.playlistmanager.domain.dj.model.PartyMode
import com.spotify.playlistmanager.domain.dj.model.Preset
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.SubstyleStrategy
import com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

/**
 * Ekran "Impreza DJ" — generator playlist wg algorytmu bloków.
 *
 * Sekcje (od góry):
 *  1. TopAppBar z tytułem + ikoną zapisu.
 *  2. Loading banner (gdy ładujemy korpus).
 *  3. Tryb pracy: Plan / Live (segmented chips).
 *  4. Biblioteka utworów: status + CTA "Wczytaj bibliotekę".
 *  5. Save mode: nowa playlista / dopisać do istniejącej.
 *  6. Wejścia trybu (Plan: czas/proporcja/łuk/strategia; Live: presety/styl/N).
 *  7. Lista bloków (BlockCard) z akcjami per slot.
 *  8. Dialogi zapisu (SaveDialog, AppendConfirmDialog, SaveSuccessDialog).
 */
@Composable
fun PartyScreen(
    onBack: () -> Unit,
    viewModel: PartyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }

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
                        Text("Impreza DJ", fontWeight = FontWeight.Bold)
                        val subtitle = when {
                            state.allTracks.isNotEmpty() ->
                                "${state.allTracks.size} utworów · ${state.blocks.size} bloków"
                            state.corpusLoaded -> {
                                val s = state.poolSizes[Style.SALSA] ?: 0
                                val b = state.poolSizes[Style.BACHATA] ?: 0
                                "Salsa: $s · Bachata: $b"
                            }
                            else -> null
                        }
                        if (subtitle != null) {
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
                    IconButton(onClick = viewModel::onResetSession) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset sesji")
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
                PartyLoadingRow("Ładowanie playlist…")
            }
            if (state.isAnalyzingCorpus) {
                PartyLoadingRow("Analizuję bibliotekę (może chwilę potrwać)…")
            }

            ModeToggleSection(
                mode = state.mode,
                onModeChange = viewModel::onModeChange
            )

            CorpusSection(
                corpusLoaded = state.corpusLoaded,
                isAnalyzing = state.isAnalyzingCorpus,
                poolSizes = state.poolSizes,
                onLoadCorpus = viewModel::onLoadCorpus
            )

            SaveModeSection(
                availablePlaylists = state.availablePlaylists,
                appendMode = state.appendMode,
                isLoadingAnchors = state.isLoadingAppendAnchors,
                onEnable = viewModel::onEnableAppendMode,
                onDisable = viewModel::onDisableAppendMode
            )

            when (state.mode) {
                PartyMode.PLANNING -> PlanInputsSection(
                    state = state,
                    onDurationChange = viewModel::onPlanDurationChange,
                    onRatioChange = viewModel::onRatioChange,
                    onArcChange = viewModel::onArcChange,
                    onStrategyChange = viewModel::onStrategyChange,
                    onBlockSizeChange = viewModel::onPlanBlockSizeChange,
                    onGenerate = viewModel::onGeneratePlan
                )

                PartyMode.LIVE -> LiveInputsSection(
                    state = state,
                    onSelectStyle = viewModel::onSelectLiveStyle,
                    onBlockSizeChange = viewModel::onLiveBlockSizeChange,
                    onShapeChange = viewModel::onLiveShapeChange,
                    onPreset = viewModel::onPresetClick,
                    onReshape = viewModel::onReshapeCurrent
                )
            }

            BlockListSection(
                blocks = state.blocks,
                playedIds = state.partyState.playedTrackIds,
                showCommit = state.mode == PartyMode.LIVE,
                onCommitPlayed = viewModel::onCommitPlayed,
                onRerollSlot = viewModel::onRerollSlot,
                onSoftLock = viewModel::onSoftLock
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
                newTrackCount = state.allTracks.size,
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
                trackCount = state.allTracks.size,
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

// ════════════════════════════════════════════════════════════════════════
//  Sekcje UI
// ════════════════════════════════════════════════════════════════════════

@Composable
private fun PartySectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
            content()
        }
    }
}

@Composable
private fun PartyLoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModeToggleSection(
    mode: PartyMode,
    onModeChange: (PartyMode) -> Unit
) {
    PartySectionCard(title = "Tryb pracy") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == PartyMode.PLANNING,
                onClick = { onModeChange(PartyMode.PLANNING) },
                label = { Text("Plan imprezy") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = mode == PartyMode.LIVE,
                onClick = { onModeChange(PartyMode.LIVE) },
                label = { Text("Live DJ") },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            if (mode == PartyMode.PLANNING)
                "Rozpisuję cały plan z góry — bloki, fazy, łuk energii."
            else
                "Buduję bloki na żądanie z anchorem ostatniego zagranego utworu.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CorpusSection(
    corpusLoaded: Boolean,
    isAnalyzing: Boolean,
    poolSizes: Map<Style, Int>,
    onLoadCorpus: () -> Unit
) {
    PartySectionCard(title = "Biblioteka") {
        if (corpusLoaded) {
            Text(
                "Salsa: ${poolSizes[Style.SALSA] ?: 0} · Bachata: ${poolSizes[Style.BACHATA] ?: 0}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Tylko utwory z biblioteki CSV (z `genres`) i `danceFloorGate ≥ 0.55`.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Wczytaj wszystkie utwory ze swoich playlist + Polubione. Wymagane przed generowaniem.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onLoadCorpus,
            enabled = !isAnalyzing,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (corpusLoaded) "Odśwież bibliotekę" else "Wczytaj bibliotekę")
        }
    }
}

@Composable
private fun SaveModeSection(
    availablePlaylists: List<Playlist>,
    appendMode: AppendMode?,
    isLoadingAnchors: Boolean,
    onEnable: (Playlist) -> Unit,
    onDisable: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    PartySectionCard(title = "Zapis") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            PartyLoadingRow("Ładowanie utworów playlisty…")
        }
        if (appendMode != null) {
            Surface(
                color = SpotifyGreen.copy(alpha = 0.10f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text("📌", fontSize = 14.sp)
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
                            "${appendMode.originalTrackCount} utworów — zostaną nietknięte",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { expanded = true }) { Text("Zmień") }
                    IconButton(onClick = onDisable) {
                        Icon(Icons.Filled.Close, contentDescription = "Wyłącz append", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (expanded) {
        AppendTargetPickerDialog(
            playlists = availablePlaylists,
            onSelect = { onEnable(it); expanded = false },
            onDismiss = { expanded = false }
        )
    }
}

@Composable
private fun PlanInputsSection(
    state: PartyUiState,
    onDurationChange: (Long) -> Unit,
    onRatioChange: (Int) -> Unit,
    onArcChange: (EnergyArc) -> Unit,
    onStrategyChange: (SubstyleStrategy) -> Unit,
    onBlockSizeChange: (Int) -> Unit,
    onGenerate: () -> Unit
) {
    PartySectionCard(title = "Parametry planu") {
        // Czas trwania
        val minutes = (state.planDurationMs / 60_000L).toInt()
        Text("Czas trwania: ${minutes / 60}h ${minutes % 60}min", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = minutes.toFloat(),
            onValueChange = { onDurationChange((it.toInt() * 60_000L)) },
            valueRange = 30f..360f,
            steps = 32,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
        )

        // Proporcja S:B
        Text(
            "Proporcja: salsa ${state.styleRatio.salsaPercent}% · bachata ${state.styleRatio.bachataPercent}%",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = state.styleRatio.salsaPercent.toFloat(),
            onValueChange = { onRatioChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 19,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
        )

        // Łuk energii — dropdown
        EnumDropdown(
            label = "Łuk energii",
            current = state.energyArc,
            options = EnergyArc.values().toList(),
            displayOf = { it.displayName },
            onSelect = onArcChange
        )

        // Strategia podstylów
        EnumDropdown(
            label = "Strategia podstylów",
            current = state.substyleStrategy,
            options = SubstyleStrategy.values().toList(),
            displayOf = { it.displayName },
            onSelect = onStrategyChange
        )

        // Rozmiar bloku
        Text("Rozmiar bloku: ${state.planBlockSize} utworów", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = state.planBlockSize.toFloat(),
            onValueChange = { onBlockSizeChange(it.toInt()) },
            valueRange = 3f..10f,
            steps = 6,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
        )

        Button(
            onClick = onGenerate,
            enabled = state.corpusLoaded && !state.isGeneratingPlan,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isGeneratingPlan) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Generuję plan…")
            } else {
                Text("Wygeneruj plan")
            }
        }
    }
}

@Composable
private fun LiveInputsSection(
    state: PartyUiState,
    onSelectStyle: (Style) -> Unit,
    onBlockSizeChange: (Int) -> Unit,
    onShapeChange: (EnergyShape) -> Unit,
    onPreset: (Preset) -> Unit,
    onReshape: (EnergyShape) -> Unit
) {
    PartySectionCard(title = "Live — kolejny blok") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.liveSelectedStyle == Style.SALSA,
                onClick = { onSelectStyle(Style.SALSA) },
                label = { Text("Salsa") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = state.liveSelectedStyle == Style.BACHATA,
                onClick = { onSelectStyle(Style.BACHATA) },
                label = { Text("Bachata") },
                modifier = Modifier.weight(1f)
            )
        }

        Text("Rozmiar bloku: ${state.liveBlockSize}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = state.liveBlockSize.toFloat(),
            onValueChange = { onBlockSizeChange(it.toInt()) },
            valueRange = 3f..10f,
            steps = 6,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen)
        )

        Text("Presety:", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Preset.values().forEach { preset ->
                AssistChip(
                    onClick = { onPreset(preset) },
                    label = { Text(preset.label, fontSize = 12.sp) },
                    enabled = state.corpusLoaded && !state.isGeneratingLiveBlock
                )
            }
        }
        if (state.isGeneratingLiveBlock) {
            PartyLoadingRow("Buduję blok…")
        }

        Text("Zmień kształt bieżącego bloku:", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            EnergyShape.presets.forEach { shape ->
                OutlinedButton(
                    onClick = {
                        onShapeChange(shape)
                        onReshape(shape)
                    },
                    enabled = state.corpusLoaded && state.blocks.isNotEmpty()
                ) {
                    Text(shape.displayName, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BlockListSection(
    blocks: List<Block>,
    playedIds: Set<String>,
    showCommit: Boolean,
    onCommitPlayed: (Int, Int) -> Unit,
    onRerollSlot: (Int, Int) -> Unit,
    onSoftLock: (Int, Int) -> Unit
) {
    if (blocks.isEmpty()) return
    PartySectionCard(title = "Bloki (${blocks.size})") {
        blocks.forEachIndexed { blockIdx, block ->
            BlockCard(
                block = block,
                playedIds = playedIds,
                showCommit = showCommit,
                onCommit = { slotIdx -> onCommitPlayed(blockIdx, slotIdx) },
                onReroll = { slotIdx -> onRerollSlot(blockIdx, slotIdx) },
                onLock = { slotIdx -> onSoftLock(blockIdx, slotIdx) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BlockCard(
    block: Block,
    playedIds: Set<String>,
    showCommit: Boolean,
    onCommit: (Int) -> Unit,
    onReroll: (Int) -> Unit,
    onLock: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${block.style.name} · ${block.shape.displayName}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${block.size} utw.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            block.tracks.forEachIndexed { slotIdx, t ->
                val played = t.id in playedIds
                val locked = slotIdx in block.lockedSlots
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${slotIdx + 1}.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t.track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (played) FontWeight.Bold else FontWeight.Normal,
                            color = if (played) SpotifyGreen else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${t.track.artist} · E=${"%.2f".format(t.energyScore)} · ${"%.0f".format(t.bpmFolded)} BPM",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (showCommit && !played) {
                        IconButton(onClick = { onCommit(slotIdx) }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Zagrano", modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(onClick = { onLock(slotIdx) }) {
                        Icon(
                            if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            contentDescription = if (locked) "Odblokuj" else "Zablokuj",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { onReroll(slotIdx) },
                        enabled = !locked && !played
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reroll", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> EnumDropdown(
    label: String,
    current: T,
    options: List<T>,
    displayOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = displayOf(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(displayOf(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Dialogi zapisu — kopia ze StepwiseScreen.kt (zgodnie z decyzją: zero refactor)
// ════════════════════════════════════════════════════════════════════════

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
                    "📌 $playlistName",
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

@Composable
private fun AppendTargetPickerDialog(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onDismiss: () -> Unit
) {
    val targets = playlists.filter { it.id != GeneratePlaylistUseCase.LIKED_SONGS_ID }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz playlistę") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (targets.isEmpty()) {
                    Text(
                        "Brak playlist do dopisywania.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    targets.take(50).forEach { p ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            TextButton(
                                onClick = { onSelect(p) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    p.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
