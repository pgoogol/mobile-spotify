@file:OptIn(ExperimentalMaterial3Api::class)

package com.spotify.playlistmanager.ui.screens.generate

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.ExhaustionStatus
import com.spotify.playlistmanager.domain.model.MatchedTrack
import com.spotify.playlistmanager.domain.model.TargetAction
import com.spotify.playlistmanager.domain.usecase.FindReplacementsUseCase
import com.spotify.playlistmanager.ui.components.EnergyCurveChart
import com.spotify.playlistmanager.ui.theme.SpotifyGreen
import com.spotify.playlistmanager.util.toHoursMinutesSeconds

@Composable
fun GenerateScreen(
    onBack: () -> Unit, onTemplates: () -> Unit, viewModel: GenerateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val featuresMap by viewModel.featuresMap.collectAsStateWithLifecycle()
    val matchedLookup by viewModel.matchedTrackLookup.collectAsStateWithLifecycle()
    val templateCount by viewModel.templateCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val trackToRound = remember(state.generationHistory) {
        buildMap<String, Int> {
            for (round in state.generationHistory) {
                for (id in round.trackIds) put(id, round.roundNumber)
            }
        }
    }

    // Dialog przypinania utworów
    val pinningState = state.pinningState
    if (pinningState is PinningState.Picking) {
        val pinSource = state.sources.find { it.id == pinningState.sourceId }
        if (pinSource != null) {
            PinTrackDialog(
                tracks = pinningState.tracks,
                currentPinned = pinSource.pinnedTracks,  // ← List<PinnedTrackInfo>
                maxSelection = pinSource.trackCount,
                onConfirm = { selectedIds ->
                    viewModel.setPinnedTracks(pinningState.sourceId, selectedIds)
                    viewModel.closePinningDialog()
                },
                onRefresh = { viewModel.refreshPinningTracks(pinningState.sourceId) },
                onDismiss = viewModel::closePinningDialog
            )
        }
    }
    if (pinningState is PinningState.Loading) {
        AlertDialog(
            onDismissRequest = viewModel::closePinningDialog,
            title = { Text("Ładowanie utworów…") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SpotifyGreen)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::closePinningDialog) {
                    Text("Anuluj")
                }
            }
        )
    }

    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showTargetPlaylistPicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Pokazuj snackbar z Undo po każdej wymianie
    LaunchedEffect(state.lastReplacement) {
        val snapshot = state.lastReplacement ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Wymieniono \"${snapshot.removedTrack.title}\"",
            actionLabel = "Cofnij",
            withDismissAction = true
        )
        when (result) {
            SnackbarResult.ActionPerformed -> viewModel.undoLastReplacement()
            SnackbarResult.Dismissed -> viewModel.clearLastReplacement()
        }
    }

    // Pokazuj błąd wymiany jako snackbar
    LaunchedEffect(state.replacementState) {
        val rs = state.replacementState
        if (rs is ReplacementState.Error) {
            snackbarHostState.showSnackbar(message = rs.message)
            viewModel.cancelReplacement()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.savedPlaylistUrl) {
        state.savedPlaylistUrl?.let {
            snackbarHostState.showSnackbar("✅ Playlista zapisana w Spotify!")
        }
    }

    val hasCurves = state.sources.any { it.energyCurve !is EnergyCurve.None }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Generuj playlistę", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                }
            },
            actions = {
                if (state.isSessionActive) {
                    IconButton(onClick = viewModel::resetSession) {
                        Icon(Icons.Default.Refresh, "Reset sesji")
                    }
                }
                IconButton(onClick = { showSaveTemplateDialog = true }) {
                    Icon(Icons.Default.Save, "Zapisz szablon")
                }
                IconButton(onClick = onTemplates) {
                    BadgedBox(
                        badge = {
                            if (templateCount > 0) {
                                Badge { Text("$templateCount") }
                            }
                        }) {
                        Icon(Icons.Default.Description, "Szablony")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
    }, snackbarHost = { SnackbarHost(snackbarHostState) }, bottomBar = {
        GenerateBottomBar(
            state = state,
            onGenerate = {
                if (state.isSessionActive) viewModel.generateMore()
                else viewModel.generatePreview()
            },
            onGenerateFromScratch = viewModel::generateFromScratch,
            onSave = viewModel::saveToSpotify,
            onOpenSpotify = {
                state.savedPlaylistUrl?.let { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            })
    }) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ── Nazwa nowej playlisty ────────────────────────────────────
            item {
                PlaylistNameField(
                    name = state.newPlaylistName,
                    onChange = viewModel::onPlaylistNameChange,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // ── Cele wyjściowe (zawsze widoczne) ─────────────────────────
            item {
                TargetActionsSection(
                    selectedActions = state.targetActions,
                    targetPlaylistName = state.targetPlaylistName,
                    onToggleAction = viewModel::toggleTargetAction,
                    onPickPlaylist = { showTargetPlaylistPicker = true })
            }

            // ── Repeat count ─────────────────────────────────────────────
            item {
                RepeatCountRow(
                    count = state.repeatCount, onCountChange = viewModel::onRepeatCountChange
                )
            }

            // ── Smooth Join (widoczny gdy są krzywe) ─────────────────────
            if (hasCurves) {
                item {
                    ToggleRow(
                        title = "Smooth Join",
                        subtitle = "Wygładza przejścia między segmentami",
                        checked = state.smoothJoin,
                        onCheckedChange = viewModel::onSmoothJoinChange
                    )
                }
            }

            // ── Sekcja źródeł ────────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Źródła playlist", action = {
                        TextButton(onClick = viewModel::addSource) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dodaj źródło")
                        }
                    })
            }

            if (state.isLoadingPlaylists) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(80.dp), contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = SpotifyGreen, modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(state.sources, key = { _, s -> s.id }) { _, source ->
                    PlaylistSourceCard(
                        source = source,
                        availablePlaylists = state.availablePlaylists,
                        onUpdate = viewModel::updateSource,
                        onRemove = { viewModel.removeSource(source.id) },
                        canRemove = state.sources.size > 1,
                        onPinTracks = { viewModel.openPinningDialog(source.id) },
                        onRemovePinnedTrack = { trackId ->
                            viewModel.removePinnedTrack(source.id, trackId)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Podgląd wyczerpania playlist ─────────────────────────────
            if (state.exhaustionStatuses.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(title = "Wyczerpanie playlist") }
                state.exhaustionStatuses.forEach { status ->
                    item(key = "exhaust_${status.playlistId}") {
                        ExhaustionBar(status = status)
                    }
                }
            }

            // ── Wykres krzywej energii ───────────────────────────────────
            state.generateResult?.let { result ->
                if (result.segments.any { it.targetScores.isNotEmpty() }) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { SectionHeader(title = "Krzywa energii") }
                    item {
                        EnergyCurveChart(
                            segments = result.segments,
                            overallMatchPercentage = result.overallMatchPercentage,
                            isDryRun = false,
                            showOnlyLastRound = state.chartShowOnlyLastRound,
                            onToggleScope = viewModel::toggleChartScope,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // ── Podgląd wygenerowanej playlisty ─────────────────────────
            state.previewTracks?.let { tracks ->
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    val totalDuration = tracks.sumOf { it.durationMs.toLong() }
                    SectionHeader(
                        title = "Podgląd (${tracks.size} utworów · ${totalDuration.toHoursMinutesSeconds()})",
                        action = {
                            TextButton(onClick = viewModel::clearSavedState) {
                                Text("Wyczyść")
                            }
                        })
                }

                itemsIndexed(tracks, key = { i, t -> "${i}_${t.id}" }) { index, track ->
                    val features = track.id?.let { featuresMap[it] }
                    val matched = track.id?.let { matchedLookup[it] }
                    val roundNumber = track.id?.let { trackToRound[it] }
                    val isReplacing =
                        (state.replacementState as? ReplacementState.Loading)?.previewIndex == index
                    PreviewTrackRow(
                        track = track,
                        features = features,
                        matched = matched,
                        roundNumber = roundNumber,
                        index = index + 1,
                        isReplacing = isReplacing,
                        onRemove = { viewModel.removeTrackFromPreview(index) },
                        onReplaceAuto = { viewModel.replaceTrackAuto(index) },
                        onReplacePick = { viewModel.startReplacementPicker(index) }
                    )
                }
            }
        }
    }

    // ── Dialogi ──────────────────────────────────────────────────────────

    if (showSaveTemplateDialog) {
        SaveTemplateDialog(onConfirm = { name ->
            viewModel.saveAsTemplate(name)
            showSaveTemplateDialog = false
        }, onDismiss = { showSaveTemplateDialog = false })
    }

    if (showTargetPlaylistPicker) {
        PlaylistPickerDialog(
            playlists = state.availablePlaylists.filter { it.id != "__liked__" },
            onSelect = { playlist ->
                viewModel.setTargetPlaylist(playlist.id, playlist.name)
                showTargetPlaylistPicker = false
            },
            onDismiss = { showTargetPlaylistPicker = false })
    }

    if (state.showQueueDryRun) {
        QueueDryRunDialog(
            tracks = state.previewTracks ?: emptyList(),
            isAdding = state.isAddingToQueue,
            onConfirm = viewModel::confirmAddToQueue,
            onDismiss = viewModel::dismissQueueDryRun
        )
    }

    (state.replacementState as? ReplacementState.Picking)?.let { picking ->
        ReplacementPickerSheet(
            state = picking,
            featuresMap = featuresMap,
            onPick = { viewModel.confirmReplacement(it) },
            onDismiss = { viewModel.cancelReplacement() }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Repeat Count Row
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RepeatCountRow(
    count: Int, onCountChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Powtórzenia szablonu",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                "Ile razy wykonać szablon (1–100)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { onCountChange(count - 1) },
                enabled = count > 1,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Remove, "Mniej", modifier = Modifier.size(18.dp))
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .width(48.dp)
                    .height(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            IconButton(
                onClick = { onCountChange(count + 1) },
                enabled = count < 100,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Add, "Więcej", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Target Actions Section — multi-select chips (zawsze widoczne)
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TargetActionsSection(
    selectedActions: Set<TargetAction>,
    targetPlaylistName: String?,
    onToggleAction: (TargetAction) -> Unit,
    onPickPlaylist: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Cel wyjściowy",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = TargetAction.NEW_PLAYLIST in selectedActions,
                onClick = { onToggleAction(TargetAction.NEW_PLAYLIST) },
                label = { Text("Nowa playlista") },
                leadingIcon = {
                    if (TargetAction.NEW_PLAYLIST in selectedActions) Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        null,
                        Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen.copy(alpha = 0.15f)
                )
            )

            FilterChip(
                selected = TargetAction.EXISTING_PLAYLIST in selectedActions,
                onClick = { onToggleAction(TargetAction.EXISTING_PLAYLIST) },
                label = { Text("Istniejąca") },
                leadingIcon = {
                    if (TargetAction.EXISTING_PLAYLIST in selectedActions) Icon(
                        Icons.Default.Add,
                        null,
                        Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen.copy(alpha = 0.15f)
                )
            )

            FilterChip(
                selected = TargetAction.QUEUE in selectedActions,
                onClick = { onToggleAction(TargetAction.QUEUE) },
                label = { Text("Kolejka") },
                leadingIcon = {
                    if (TargetAction.QUEUE in selectedActions) Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        null,
                        Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen.copy(alpha = 0.15f)
                )
            )
        }

        AnimatedVisibility(TargetAction.EXISTING_PLAYLIST in selectedActions) {
            OutlinedButton(
                onClick = onPickPlaylist,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(targetPlaylistName ?: "Wybierz playlistę docelową")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Exhaustion Bar
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExhaustionBar(status: ExhaustionStatus) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (status.exhausted) MaterialTheme.colorScheme.errorContainer.copy(
                alpha = 0.5f
            )
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    status.playlistName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${status.usedTracks}/${status.totalTracks}" + if (status.exhausted) " ✓" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status.exhausted) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { status.usagePercent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (status.exhausted) MaterialTheme.colorScheme.error
                else SpotifyGreen,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Toggle Row
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ToggleRow(
    title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = SpotifyGreen)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Playlist Picker Dialog
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlaylistPickerDialog(
    playlists: List<Playlist>, onSelect: (Playlist) -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz playlistę docelową") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(400.dp)
            ) {
                itemsIndexed(playlists) { _, playlist ->
                    Surface(
                        onClick = { onSelect(playlist) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                null,
                                Modifier.size(20.dp),
                                tint = SpotifyGreen
                            )
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
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        })
}

// ══════════════════════════════════════════════════════════════════════════════
//  Queue Dry-Run Dialog
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QueueDryRunDialog(
    tracks: List<Track>, isAdding: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = { if (!isAdding) onDismiss() }, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                null,
                Modifier.size(24.dp),
                tint = SpotifyGreen
            )
            Spacer(Modifier.width(8.dp))
            Text("Dodaj do kolejki")
        }
    }, text = {
        Column {
            Text(
                "Dodać ${tracks.size} utworów do kolejki odtwarzania?",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Wymaga aktywnego odtwarzacza Spotify na dowolnym urządzeniu.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isAdding) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = SpotifyGreen,
                        strokeWidth = 2.dp
                    )
                    Text("Dodawanie...", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(200.dp)) {
                val display = if (tracks.size > 10) tracks.take(10) else tracks
                itemsIndexed(display) { i, track ->
                    Text(
                        "${i + 1}. ${track.title} — ${track.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                if (tracks.size > 10) {
                    item {
                        Text(
                            "… i ${tracks.size - 10} więcej",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }, confirmButton = {
        Button(
            onClick = onConfirm,
            enabled = !isAdding,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
        ) {
            Text("Dodaj do kolejki")
        }
    }, dismissButton = {
        TextButton(onClick = onDismiss, enabled = !isAdding) { Text("Anuluj") }
    })
}

// ══════════════════════════════════════════════════════════════════════════════
//  Save Template Dialog
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SaveTemplateDialog(
    onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Zapisz szablon") }, text = {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nazwa szablonu") },
            placeholder = { Text("np. Salsa Night Set") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
        )
    }, confirmButton = {
        TextButton(
            onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()
        ) { Text("Zapisz") }
    }, dismissButton = {
        TextButton(onClick = onDismiss) { Text("Anuluj") }
    })
}

// ══════════════════════════════════════════════════════════════════════════════
//  Section Header / Playlist Name Field / Preview Track Row
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(
    title: String, action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        action?.invoke()
    }
}

@Composable
private fun PlaylistNameField(
    name: String, onChange: (String) -> Unit, modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = name,
        onValueChange = onChange,
        label = { Text("Nazwa nowej playlisty") },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewTrackRow(
    track: Track,
    features: TrackAudioFeatures?,
    matched: MatchedTrack?,
    roundNumber: Int?,
    index: Int,
    isReplacing: Boolean,
    onRemove: () -> Unit,
    onReplaceAuto: () -> Unit,
    onReplacePick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Numer pozycji
            Text(
                "$index",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(24.dp)
            )

            // Okładka albumu
            if (track.albumArtUrl != null) {
                AsyncImage(
                    model = track.albumArtUrl,
                    contentDescription = track.album,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Tytuł / artysta / meta
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        track.formattedDuration(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Badge BPM (gdy dostępne features)
            if (features != null) {
                MiniBadge(text = "${features.bpm.toInt()} BPM", accent = true)
            }

            // Chevron rozwijania
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Zwiń" else "Rozwiń",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            // Wymiana utworu — tap = auto, long-press = picker
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        enabled = !isReplacing,
                        onClick = onReplaceAuto,
                        onLongClick = onReplacePick
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isReplacing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = SpotifyGreen
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Wymień utwór (długie naciśnięcie = wybierz)",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Usuwanie
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Usuń",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ── Sekcja rozwijana ───────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 34.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
            ) {
                if (features != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniBar(
                            label = "BPM",
                            value = features.bpm.toInt().toString(),
                            progress = (features.bpm / 220f).coerceIn(0f, 1f),
                            modifier = Modifier.weight(1f)
                        )
                        MiniBar(
                            label = "Energy",
                            value = features.energy.toInt().toString(),
                            progress = features.energy / 100f,
                            modifier = Modifier.weight(1f)
                        )
                        MiniBar(
                            label = "Dance",
                            value = features.danceability.toInt().toString(),
                            progress = features.danceability / 100f,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniBadge(text = features.camelot, accent = false)
                        MiniBadge(text = features.musicalKey, accent = false)
                        MiniBadge(text = "Val ${features.valence.toInt()}", accent = false)
                        MiniBadge(text = "Ac ${features.acousticness.toInt()}", accent = false)
                    }

                    // Pasek dopasowania do krzywej (tylko gdy była krzywa ≠ None)
                    if (matched != null && matched.targetScore > 0f) {
                        Spacer(Modifier.height(8.dp))
                        CurveMatchBar(matched = matched)
                    } else if (matched != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Composite score: ${"%.2f".format(matched.compositeScore)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Brak danych audio — zaimportuj CSV w Ustawieniach",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    track.releaseDate?.let {
                        MiniBadge(text = "Wydano $it", accent = false)
                    }
                    roundNumber?.let {
                        MiniBadge(text = "Runda $it", accent = false)
                    }
                    MiniBadge(text = "Pop ${track.popularity}", accent = false)
                }
            }
        }
    }
}

// ── Komponenty pomocnicze PreviewTrackRow ──────────────────────────────

@Composable
private fun MiniBadge(text: String, accent: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (accent) SpotifyGreen.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (accent) SpotifyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MiniBar(
    label: String,
    value: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = SpotifyGreen
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = SpotifyGreen,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
}

/**
 * Pasek dopasowania do krzywej: pokazuje composite score utworu względem
 * zaplanowanego target score. Zielony = bliskie dopasowanie (|delta| < 0.05),
 * żółty = średnie (< 0.15), czerwony = słabe.
 */
@Composable
private fun CurveMatchBar(matched: MatchedTrack) {
    val delta = kotlin.math.abs(matched.compositeScore - matched.targetScore)
    val color = when {
        delta < 0.05f -> SpotifyGreen
        delta < 0.15f -> Color(0xFFFFA726) // amber
        else -> Color(0xFFE57373) // red
    }
    val matchPct = ((1f - delta).coerceIn(0f, 1f) * 100f).toInt()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Dopasowanie do krzywej",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "$matchPct%  (score ${"%.2f".format(matched.compositeScore)} / target ${
                    "%.2f".format(
                        matched.targetScore
                    )
                })",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { (1f - delta).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Bottom bar
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GenerateBottomBar(
    state: GenerateUiState,
    onGenerate: () -> Unit,
    onGenerateFromScratch: () -> Unit,
    onSave: () -> Unit,
    onOpenSpotify: () -> Unit
) {
    val hasPreview = state.previewTracks != null
    val hasSavedUrl = state.savedPlaylistUrl != null

    Surface(
        tonalElevation = 8.dp, color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Postęp repeat
            if (state.isGenerating && state.repeatCount > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { state.repeatProgress.toFloat() / state.repeatCount },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = SpotifyGreen
                    )
                    Text(
                        "${state.repeatProgress}/${state.repeatCount}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Wiersz 1: Generuj / Od zera
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onGenerate,
                    enabled = !state.isGenerating && !state.isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    if (state.isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.isSessionActive) "Dodaj więcej"
                            else if (state.repeatCount > 1) "Generuj ×${state.repeatCount}"
                            else "Generuj"
                        )
                    }
                }

                if (state.isSessionActive) {
                    OutlinedButton(
                        onClick = onGenerateFromScratch,
                        enabled = !state.isGenerating && !state.isSaving
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Od zera")
                    }
                }
            }

            // Wiersz 2: Zapisz / Otwórz w Spotify
            AnimatedVisibility(hasPreview) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!hasSavedUrl) {
                        Button(
                            onClick = onSave,
                            enabled = !state.isGenerating && !state.isSaving,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Zapisz")
                            }
                        }
                    }

                    if (hasSavedUrl) {
                        Button(
                            onClick = onOpenSpotify,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Otwórz w Spotify")
                        }
                    }
                }
            }
        }
    }
}
// ══════════════════════════════════════════════════════════════════════
//  Replacement Picker Bottom Sheet
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplacementPickerSheet(
    state: ReplacementState.Picking,
    featuresMap: Map<String, TrackAudioFeatures>,
    onPick: (FindReplacementsUseCase.ReplacementCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Wymień utwór",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(12.dp))

            // Nagłówek: oryginalny utwór
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Zastępowany",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        state.originalTrack.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        state.originalTrack.artist,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Composite score: ${"%.2f".format(state.originalCompositeScore)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyGreen
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Kandydaci (posortowani wg dopasowania)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(state.candidates, key = { it.track.id ?: it.track.title }) { candidate ->
                    CandidateRow(
                        candidate = candidate,
                        features = candidate.track.id?.let { featuresMap[it] },
                        onClick = { onPick(candidate) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: FindReplacementsUseCase.ReplacementCandidate,
    features: TrackAudioFeatures?,
    onClick: () -> Unit
) {
    val track = candidate.track

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Okładka
            if (track.albumArtUrl != null) {
                AsyncImage(
                    model = track.albumArtUrl,
                    contentDescription = track.album,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tytuł + artysta + mini metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (features != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniBadge(text = "${features.bpm.toInt()} BPM", accent = true)
                        MiniBadge(text = features.camelot, accent = false)
                        MiniBadge(text = "E ${features.energy.toInt()}", accent = false)
                        MiniBadge(text = "D ${features.danceability.toInt()}", accent = false)
                    }
                }
            }

            // Score + delta
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%.2f".format(candidate.compositeScore),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = SpotifyGreen
                )
                if (candidate.scoreDifference > 0f) {
                    Text(
                        "Δ ${"%.2f".format(candidate.scoreDifference)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}