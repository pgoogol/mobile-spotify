@file:OptIn(ExperimentalMaterial3Api::class)
package com.spotify.playlistmanager.ui.screens.generate

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.ui.components.EnergyCurveChart
import com.spotify.playlistmanager.ui.theme.SpotifyGreen
import com.spotify.playlistmanager.ui.theme.SpotifyMidGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    onBack:    () -> Unit,
    viewModel: GenerateViewModel = hiltViewModel()
) {
    val state   by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
        state.savedPlaylistUrl?.let { url ->
            snackbarHostState.showSnackbar("✅ Playlista zapisana w Spotify!")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generuj playlistę", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
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
                hasPreview  = state.previewTracks != null,
                isGenerating = state.isGenerating,
                isSaving    = state.isSaving,
                onGenerate  = viewModel::generatePreview,
                onSave      = viewModel::saveToSpotify,
                onOpenSpotify = {
                    state.savedPlaylistUrl?.let { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
                hasSavedUrl = state.savedPlaylistUrl != null
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Nazwa nowej playlisty ────────────────────────────────────
            item {
                PlaylistNameField(
                    name     = state.newPlaylistName,
                    onChange = viewModel::onPlaylistNameChange,
                    modifier = Modifier.padding(16.dp)
                )
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

                // Wykres energii
                item {
                    val dominantCurve = state.sources
                        .firstOrNull { it.energyCurve != EnergyCurve.NONE }
                        ?.energyCurve ?: EnergyCurve.CONSTANT
                    EnergyCurveChart(
                        tracks   = tracks,
                        curve    = dominantCurve,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth()
                    )
                }

                itemsIndexed(tracks, key = { i, t -> "${i}_${t.id}" }) { index, track ->
                    PreviewTrackRow(
                        track    = track,
                        index    = index + 1,
                        onRemove = { viewModel.removeTrackFromPreview(index) },
                        onMoveUp = { if (index > 0) viewModel.moveTrack(index, index - 1) },
                        onMoveDown = {
                            if (index < tracks.lastIndex) viewModel.moveTrack(index, index + 1)
                        }
                    )
                }
            }
        }
    }
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
        leadingIcon   = { Icon(Icons.Default.PlaylistAdd, null) },
        modifier      = modifier.fillMaxWidth(),
        singleLine    = true,
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
    )
}

// ── Karta źródła ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistSourceCard(
    source:             PlaylistSource,
    availablePlaylists: List<Playlist>,
    onUpdate:           (PlaylistSource) -> Unit,
    onRemove:           () -> Unit,
    canRemove:          Boolean,
    modifier:           Modifier = Modifier
) {
    var playlistExpanded    by remember { mutableStateOf(false) }
    var sortExpanded        by remember { mutableStateOf(false) }
    var energyCurveExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Wiersz 1: Wybór playlisty + usuń ────────────────────────
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dropdown playlisty
                ExposedDropdownMenuBox(
                    expanded        = playlistExpanded,
                    onExpandedChange = { playlistExpanded = it },
                    modifier        = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value            = source.playlist?.name ?: "Wybierz playlistę…",
                        onValueChange    = {},
                        readOnly         = true,
                        trailingIcon     = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = playlistExpanded) },
                        modifier         = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine       = true,
                        shape            = RoundedCornerShape(8.dp),
                        colors           = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
                        textStyle        = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded        = playlistExpanded,
                        onDismissRequest = { playlistExpanded = false }
                    ) {
                        availablePlaylists.forEach { pl ->
                            DropdownMenuItem(
                                text    = { Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    onUpdate(source.copy(playlist = pl))
                                    playlistExpanded = false
                                }
                            )
                        }
                    }
                }

                // Przycisk usuń
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Usuń",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ── Wiersz 2: Liczba + sortowanie + krzywa energii ───────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                // Liczba utworów
                OutlinedTextField(
                    value         = source.trackCount.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.coerceIn(1, 200)
                            ?.let { onUpdate(source.copy(trackCount = it)) }
                    },
                    label         = { Text("Liczba") },
                    modifier      = Modifier.width(80.dp),
                    singleLine    = true,
                    shape         = RoundedCornerShape(8.dp),
                    colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
                )

                // Sortowanie
                ExposedDropdownMenuBox(
                    expanded         = sortExpanded,
                    onExpandedChange = { sortExpanded = it },
                    modifier         = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value         = source.sortBy.label,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Sortuj") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                        modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine    = true,
                        shape         = RoundedCornerShape(8.dp),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
                    )
                    ExposedDropdownMenu(
                        expanded         = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text    = { Text(opt.label) },
                                onClick = {
                                    onUpdate(source.copy(
                                        sortBy = opt,
                                        // Jeśli wybrano sortowanie, wyłącz krzywą energii
                                        energyCurve = if (opt != SortOption.NONE) EnergyCurve.NONE
                                                      else source.energyCurve
                                    ))
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }

                // Krzywa energii (wyłączona gdy jest sortowanie)
                ExposedDropdownMenuBox(
                    expanded         = energyCurveExpanded && source.sortBy == SortOption.NONE,
                    onExpandedChange = {
                        if (source.sortBy == SortOption.NONE) energyCurveExpanded = it
                    },
                    modifier         = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value         = source.energyCurve.let { "${it.emoji} ${it.label}".trim() },
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Energia") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = energyCurveExpanded) },
                        modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine    = true,
                        enabled       = source.sortBy == SortOption.NONE,
                        shape         = RoundedCornerShape(8.dp),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
                    )
                    ExposedDropdownMenu(
                        expanded         = energyCurveExpanded,
                        onDismissRequest = { energyCurveExpanded = false }
                    ) {
                        EnergyCurve.entries.forEach { curve ->
                            DropdownMenuItem(
                                text    = { Text("${curve.emoji} ${curve.label}".trim()) },
                                onClick = {
                                    onUpdate(source.copy(energyCurve = curve))
                                    energyCurveExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Wiersz podglądu ──────────────────────────────────────────────────────────

@Composable
private fun PreviewTrackRow(
    track:      Track,
    index:      Int,
    onRemove:   () -> Unit,
    onMoveUp:   () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Numer porządkowy
        Text(
            "$index",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )

        // Tekst
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artist} · ${track.formattedDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Audio feature badge
        track.energy?.let {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = SpotifyGreen.copy(alpha = 0.2f)
            ) {
                Text(
                    "E%.2f".format(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyGreen,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        // Przyciski kolejności
        Column {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "W górę", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "W dół", modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Usuń
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Usuń", modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        modifier = Modifier.padding(start = 48.dp, end = 16.dp)
    )
}

// ── Bottom bar ───────────────────────────────────────────────────────────────

@Composable
private fun GenerateBottomBar(
    hasPreview:   Boolean,
    isGenerating: Boolean,
    isSaving:     Boolean,
    onGenerate:   () -> Unit,
    onSave:       () -> Unit,
    onOpenSpotify:() -> Unit,
    hasSavedUrl:  Boolean
) {
    Surface(
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Generuj
            Button(
                onClick  = onGenerate,
                enabled  = !isGenerating && !isSaving,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color    = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generuj")
                }
            }

            // Zapisz (aktywny po podglądzie)
            AnimatedVisibility(hasPreview && !hasSavedUrl) {
                Button(
                    onClick  = onSave,
                    enabled  = !isGenerating && !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color    = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Zapisz")
                    }
                }
            }

            // Otwórz w Spotify
            AnimatedVisibility(hasSavedUrl) {
                OutlinedButton(
                    onClick  = onOpenSpotify,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Otwórz Spotify")
                }
            }
        }
    }
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
            text  = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f)
        )
        action()
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}
