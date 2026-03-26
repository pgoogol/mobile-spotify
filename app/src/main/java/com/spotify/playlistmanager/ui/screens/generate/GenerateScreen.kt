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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.data.model.SortOption
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

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
        state.savedPlaylistUrl?.let {
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
    var playlistExpanded by remember { mutableStateOf(false) }
    var sortExpanded     by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Wiersz 1: Wybór playlisty + usuń ────────────────────────
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded         = playlistExpanded,
                    onExpandedChange = { playlistExpanded = it },
                    modifier         = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value         = source.playlist?.name ?: "Wybierz playlistę…",
                        onValueChange = {},
                        readOnly      = true,
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = playlistExpanded) },
                        modifier      = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        singleLine    = true,
                        shape         = RoundedCornerShape(8.dp),
                        colors        = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
                        textStyle     = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded         = playlistExpanded,
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

            // ── Wiersz 2: Liczba + sortowanie ────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                OutlinedTextField(
                    value         = source.trackCount.toString(),
                    onValueChange = { v ->
                        v.toIntOrNull()?.coerceIn(1, 200)
                            ?.let { onUpdate(source.copy(trackCount = it)) }
                    },
                    label      = { Text("Liczba") },
                    modifier   = Modifier.width(80.dp),
                    singleLine = true,
                    shape      = RoundedCornerShape(8.dp),
                    colors     = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen)
                )

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
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$index",
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artist} · ${track.formattedDuration()}",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column {
            IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "W górę",
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "W dół",
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, "Usuń",
                modifier = Modifier.size(16.dp),
                tint     = MaterialTheme.colorScheme.error)
        }
    }
    HorizontalDivider(
        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        modifier = Modifier.padding(start = 48.dp, end = 16.dp)
    )
}

// ── Bottom bar ───────────────────────────────────────────────────────────────

@Composable
private fun GenerateBottomBar(
    hasPreview:    Boolean,
    isGenerating:  Boolean,
    isSaving:      Boolean,
    onGenerate:    () -> Unit,
    onSave:        () -> Unit,
    onOpenSpotify: () -> Unit,
    hasSavedUrl:   Boolean
) {
    Surface(
        tonalElevation = 8.dp,
        color          = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick  = onGenerate,
                enabled  = !isGenerating && !isSaving,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(18.dp),
                        color       = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generuj")
                }
            }

            AnimatedVisibility(hasPreview && !hasSavedUrl) {
                Button(
                    onClick  = onSave,
                    enabled  = !isGenerating && !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Zapisz")
                    }
                }
            }

            AnimatedVisibility(hasSavedUrl) {
                OutlinedButton(
                    onClick  = onOpenSpotify,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
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
