@file:OptIn(ExperimentalLayoutApi::class)

package com.spotify.playlistmanager.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.model.GeneratorTemplate
import com.spotify.playlistmanager.domain.model.TargetAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ══════════════════════════════════════════════════════════════════════════════
//  Helpery UI „Generuj" — odwzorowane z mobilnego GenerateScreen / TemplatesScreen.
//  Wszystkie są bezstanowymi liśćmi (callbacki + parametry); stan i orkiestracja
//  żyją w GeneratorRealScreen.
// ══════════════════════════════════════════════════════════════════════════════

// ── Repeat Count Row ─────────────────────────────────────────────────────────

/**
 * Wiersz „liczba rund N" — stepper 1–100 (odpowiednik mobilnego `RepeatCountRow`).
 * Steruje, ile razy cały szablon zostanie wykonany podczas generowania.
 */
@Composable
fun RepeatCountRow(
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Powtórzenia szablonu",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                "Ile razy wykonać szablon (1–100)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { onCountChange(count - 1) },
                enabled = count > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Default.Remove, "Mniej", modifier = Modifier.size(18.dp))
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(48.dp).height(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "$count",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }

            IconButton(
                onClick = { onCountChange(count + 1) },
                enabled = count < 100,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Default.Add, "Więcej", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Target Actions Section ───────────────────────────────────────────────────

/**
 * Sekcja celów wyjściowych — multi-select chipy (odpowiednik mobilnego
 * `TargetActionsSection`). Gdy wybrana „Istniejąca", pokazuje przycisk wyboru
 * playlisty docelowej.
 */
@Composable
fun TargetActionsSection(
    selectedActions: Set<TargetAction>,
    targetPlaylistName: String?,
    onToggleAction: (TargetAction) -> Unit,
    onPickPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Cel wyjściowy",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = TargetAction.NEW_PLAYLIST in selectedActions,
                onClick = { onToggleAction(TargetAction.NEW_PLAYLIST) },
                label = { Text("Nowa playlista") },
                leadingIcon = {
                    if (TargetAction.NEW_PLAYLIST in selectedActions) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(16.dp))
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen.copy(alpha = 0.15f),
                ),
            )

            FilterChip(
                selected = TargetAction.EXISTING_PLAYLIST in selectedActions,
                onClick = { onToggleAction(TargetAction.EXISTING_PLAYLIST) },
                label = { Text("Istniejąca") },
                leadingIcon = {
                    if (TargetAction.EXISTING_PLAYLIST in selectedActions) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen.copy(alpha = 0.15f),
                ),
            )

            FilterChip(
                selected = TargetAction.QUEUE in selectedActions,
                onClick = { onToggleAction(TargetAction.QUEUE) },
                label = { Text("Kolejka") },
                leadingIcon = {
                    if (TargetAction.QUEUE in selectedActions) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(16.dp))
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen.copy(alpha = 0.15f),
                ),
            )
        }

        AnimatedVisibility(TargetAction.EXISTING_PLAYLIST in selectedActions) {
            OutlinedButton(
                onClick = onPickPlaylist,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(targetPlaylistName ?: "Wybierz playlistę docelową")
            }
        }
    }
}

// ── Playlist Picker Dialog (cel docelowy) ────────────────────────────────────

/**
 * Dialog wyboru playlisty docelowej dla `EXISTING_PLAYLIST`
 * (odpowiednik mobilnego `PlaylistPickerDialog`).
 */
@Composable
fun TargetPlaylistPickerDialog(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz playlistę docelową") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(400.dp),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Surface(
                        onClick = { onSelect(playlist) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                null,
                                Modifier.size(20.dp),
                                tint = SpotifyGreen,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${playlist.trackCount} utworów",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        },
    )
}

// ── Save Template Dialog ─────────────────────────────────────────────────────

/**
 * Dialog zapisu bieżącej konfiguracji generatora jako szablon
 * (odpowiednik mobilnego `SaveTemplateDialog`).
 */
@Composable
fun SaveTemplateDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
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
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}

// ── Rename Template Dialog ───────────────────────────────────────────────────

@Composable
fun RenameTemplateDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zmień nazwę") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa szablonu") },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        },
    )
}

// ── Templates Panel (lista szablonów: wczytaj/zmień nazwę/usuń) ───────────────

/**
 * Rozwijany panel szablonów — odpowiednik mobilnego `TemplatesScreen`, ale jako
 * inline panel w desktopowym ekranie (bez Navigation-Compose). Operacje:
 * wczytaj, zmień nazwę, usuń. Usuwanie/zmiana nazwy obsługiwane przez dialogi.
 */
@Composable
fun TemplatesPanel(
    templates: List<GeneratorTemplate>,
    onLoad: (GeneratorTemplate) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<GeneratorTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<GeneratorTemplate?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (templates.isEmpty()) {
            Text(
                "Brak zapisanych szablonów — zapisz konfigurację przyciskiem \"Zapisz szablon\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            templates.forEach { template ->
                TemplateCard(
                    template = template,
                    onLoad = { onLoad(template) },
                    onRename = { renameTarget = template },
                    onDelete = { deleteTarget = template },
                )
            }
        }
    }

    renameTarget?.let { template ->
        RenameTemplateDialog(
            currentName = template.name,
            onConfirm = { newName ->
                onRename(template.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Usuń szablon") },
            text = { Text("Czy na pewno chcesz usunąć '${template.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(template.id)
                        deleteTarget = null
                    },
                ) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun TemplateCard(
    template: GeneratorTemplate,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${template.sources.size} segm. · ${dateFormat.format(Date(template.updatedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row {
                    IconButton(onClick = onLoad, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.FileOpen,
                            "Wczytaj",
                            tint = SpotifyGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, "Zmień nazwę", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            "Usuń",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (template.sources.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                template.sources.take(3).forEach { src ->
                    Text(
                        "${src.position + 1}. ${src.playlistName} (${src.trackCount}) ${src.energyCurve.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (template.sources.size > 3) {
                    Text(
                        "… +${template.sources.size - 3} więcej",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Queue Dry-Run Dialog ─────────────────────────────────────────────────────

/**
 * Dialog potwierdzenia dodania utworów do kolejki odtwarzania
 * (odpowiednik mobilnego `QueueDryRunDialog`).
 */
@Composable
fun QueueDryRunDialog(
    trackCount: Int,
    sampleLabels: List<String>,
    isAdding: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    null,
                    Modifier.size(24.dp),
                    tint = SpotifyGreen,
                )
                Spacer(Modifier.width(8.dp))
                Text("Dodaj do kolejki")
            }
        },
        text = {
            Column {
                Text(
                    "Dodać $trackCount utworów do kolejki odtwarzania?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wymaga aktywnego odtwarzacza Spotify na dowolnym urządzeniu.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(sampleLabels) { label ->
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    if (trackCount > sampleLabels.size) {
                        item {
                            Text(
                                "… i ${trackCount - sampleLabels.size} więcej",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isAdding) { Text("Dodaj do kolejki") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAdding) { Text("Anuluj") }
        },
    )
}
