package com.spotify.playlistmanager.ui.screens.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.domain.model.GeneratorTemplate
import com.spotify.playlistmanager.ui.theme.SpotifyGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekran szablonów generatora.
 * Dostępny z GenerateScreen przez ikonę 📋.
 * Operacje: wczytaj, zmień nazwę, usuń.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    templates: List<GeneratorTemplate>,
    onLoadTemplate: (GeneratorTemplate) -> Unit,
    onRenameTemplate: (Long, String) -> Unit,
    onDeleteTemplate: (Long) -> Unit,
    onBack: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<GeneratorTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<GeneratorTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Szablony", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wróć")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (templates.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Brak zapisanych szablonów",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Zapisz konfigurację generatora jako szablon",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    TemplateCard(
                        template = template,
                        onLoad = { onLoadTemplate(template) },
                        onRename = { renameTarget = template },
                        onDelete = { deleteTarget = template }
                    )
                }
            }
        }
    }

    // Dialog zmień nazwę
    renameTarget?.let { template ->
        RenameDialog(
            currentName = template.name,
            onConfirm = { newName ->
                onRenameTemplate(template.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    // Dialog potwierdź usunięcie
    deleteTarget?.let { template ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Usuń szablon") },
            text = { Text("Czy na pewno chcesz usunąć „${template.name}"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTemplate(template.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Usuń")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Anuluj") }
            }
        )
    }
}

// ── Karta szablonu ───────────────────────────────────────────────────────────

@Composable
private fun TemplateCard(
    template: GeneratorTemplate,
    onLoad: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${template.sources.size} segm. · ${dateFormat.format(Date(template.updatedAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    IconButton(onClick = onLoad, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.FileOpen, "Wczytaj", tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, "Zmień nazwę", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, "Usuń",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Segmenty preview
            if (template.sources.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                template.sources.take(3).forEach { src ->
                    Text(
                        "${src.position + 1}. ${src.playlistName} (${src.trackCount}) ${src.energyCurve.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (template.sources.size > 3) {
                    Text(
                        "… +${template.sources.size - 3} więcej",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Dialog zmiany nazwy ──────────────────────────────────────────────────────

@Composable
private fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
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
