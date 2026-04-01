@file:OptIn(ExperimentalMaterial3Api::class)
package com.spotify.playlistmanager.ui.screens.generate

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.ui.components.EnergyCurveChart
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    onBack:        () -> Unit,
    onTemplates:   () -> Unit,
    viewModel:     GenerateViewModel = hiltViewModel()
) {
    val state         by viewModel.state.collectAsStateWithLifecycle()
    val templateCount by viewModel.templateCount.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showSaveTemplateDialog by remember { mutableStateOf(false) }

    // Obsługa błędów jako Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Sukces – otwórz Spotify
    LaunchedEffect(state.savedPlaylistUrl) {
        state.savedPlaylistUrl?.let {
            snackbarHostState.showSnackbar("✅ Playlista zapisana w Spotify!")
        }
    }

    // Czy jakiekolwiek źródło ma krzywą energii
    val hasCurves = state.sources.any { it.energyCurve !is EnergyCurve.None }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generuj playlistę", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                    }
                },
                actions = {
                    // Zapisz jako szablon
                    IconButton(onClick = { showSaveTemplateDialog = true }) {
                        Icon(Icons.Default.Save, "Zapisz szablon")
                    }
                    // Szablony z badge
                    IconButton(onClick = onTemplates) {
                        BadgedBox(
                            badge = {
                                if (templateCount > 0) {
                                    Badge { Text("$templateCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Description, "Szablony")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            GenerateBottomBar(
                hasPreview    = state.previewTracks != null,
                isGenerating  = state.isGenerating,
                isSaving      = state.isSaving,
                onGenerate    = viewModel::generatePreview,
                onSave        = viewModel::saveToSpotify,
                onOpenSpotify = {
                    state.savedPlaylistUrl?.let { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    }
                },
                hasSavedUrl = state.savedPlaylistUrl != null
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ── Nazwa nowej playlisty ────────────────────────────────────
            item {
                PlaylistNameField(
                    name     = state.newPlaylistName,
                    onChange = viewModel::onPlaylistNameChange,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // ── Smooth Join switch (widoczny gdy są krzywe) ──────────────
            if (hasCurves) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Smooth Join", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text("Wygładza przejścia między segmentami",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.smoothJoin,
                            onCheckedChange = viewModel::onSmoothJoinChange,
                            colors = SwitchDefaults.colors(checkedTrackColor = SpotifyGreen)
                        )
                    }
                }
            }

            // ── Sekcja źródeł ────────────────────────────────────────────
            item {
                SectionHeader(
                    title  = "Źródła playlist",
                    action = {
                        TextButton(onClick = viewModel::addSource) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dodaj źródło")
                        }
                    }
                )
            }

            if (state.isLoadingPlaylists) {
                item {
                    Box(
                        Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                itemsIndexed(state.sources, key = { _, s -> s.id }) { _, source ->
                    PlaylistSourceCard(
                        source             = source,
                        availablePlaylists = state.availablePlaylists,
                        onUpdate           = viewModel::updateSource,
                        onRemove           = { viewModel.removeSource(source.id) },
                        canRemove          = state.sources.size > 1,
                        modifier           = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // ── Wykres krzywej energii (po wygenerowaniu) ────────────────
            state.generateResult?.let { result ->
                if (result.segments.any { it.targetScores.isNotEmpty() }) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item {
                        SectionHeader(title = "Krzywa energii")
                    }
                    item {
                        EnergyCurveChart(
                            segments = result.segments,
                            overallMatchPercentage = result.overallMatchPercentage,
                            isDryRun = false,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            // ── Podgląd wygenerowanej playlisty ─────────────────────────
            state.previewTracks?.let { tracks ->
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    SectionHeader(
                        title = "Podgląd (${tracks.size} utworów)",
                        action = {
                            TextButton(onClick = viewModel::clearSavedState) {
                                Text("Wyczyść")
                            }
                        }
                    )
                }

                itemsIndexed(tracks, key = { i, t -> "${i}_${t.id}" }) { index, track ->
                    PreviewTrackRow(
                        track      = track,
                        index      = index + 1,
                        onRemove   = { viewModel.removeTrackFromPreview(index) },
                        onMoveUp   = { if (index > 0) viewModel.moveTrack(index, index - 1) },
                        onMoveDown = {
                            if (index < tracks.lastIndex) viewModel.moveTrack(index, index + 1)
                        }
                    )
                }
            }
        }
    }

    // Dialog zapisz szablon
    if (showSaveTemplateDialog) {
        SaveTemplateDialog(
            onConfirm = { name ->
                viewModel.saveAsTemplate(name)
                showSaveTemplateDialog = false
            },
            onDismiss = { showSaveTemplateDialog = false }
        )
    }
}

// ── Dialog zapisu szablonu ────────────────────────────────────────────────────

@Composable
private fun SaveTemplateDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zapisz szablon") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa szablonu") },
                placeholder = { Text("np. Salsa Night Set") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Zapisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

// ── Pole nazwy playlisty ─────────────────────────────────────────────────────

@Composable
private fun PlaylistNameField(
    name: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = name,
        onValueChange = onChange,
        label         = { Text("Nazwa nowej playlisty") },
        leadingIcon   = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
        modifier      = modifier.fillMaxWidth(),
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
    )
}

// ── Sekcja nagłówka ──────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title:  String,
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = title,
            style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        action()
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}
