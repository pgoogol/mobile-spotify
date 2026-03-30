package com.spotify.playlistmanager.ui.screens.csv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onBack: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.parseFile(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import cech audio (CSV)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        AnimatedContent(targetState = state.phase, label = "csv_phase") { phase ->
            when (phase) {
                CsvImportUiState.Phase.IDLE,
                CsvImportUiState.Phase.ERROR -> IdleView(
                    cachedCount = state.cachedCount,
                    errorMessage = state.errorMessage,
                    onPick = { filePicker.launch("text/*") },
                    onClearCache = viewModel::clearCache,
                    modifier = Modifier.padding(padding)
                )

                CsvImportUiState.Phase.PARSING,
                CsvImportUiState.Phase.IMPORTING -> LoadingView(
                    label = if (phase == CsvImportUiState.Phase.PARSING)
                        "Parsowanie pliku…" else "Zapisywanie do bazy…",
                    modifier = Modifier.padding(padding)
                )

                CsvImportUiState.Phase.PREVIEW -> PreviewView(
                    features = state.preview,
                    parseErrors = state.parseErrors,
                    skippedCount = state.skippedCount,
                    onConfirm = viewModel::confirmImport,
                    onCancel = viewModel::reset,
                    modifier = Modifier.padding(padding)
                )

                CsvImportUiState.Phase.DONE -> DoneView(
                    imported = state.importedCount,
                    cachedTotal = state.cachedCount,
                    onBack = onBack,
                    onImportMore = { viewModel.reset(); filePicker.launch("text/*") },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

// ── Idle / Error ──────────────────────────────────────────────────────────────

@Composable
private fun IdleView(
    cachedCount: Int,
    errorMessage: String?,
    onPick: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        Icon(Icons.Default.UploadFile, null, tint = SpotifyGreen, modifier = Modifier.size(72.dp))
        Text(
            "Import cech audio z CSV",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Wgraj plik CSV z kolumnami: Spotify Track Id, BPM, Energy,\n" +
                    "Dance, Valence, Acoustic, Instrumental, Loud (Db), Camelot itd.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Status cache
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Storage, null, tint = SpotifyGreen)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Rekordy w cache", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "$cachedCount utworów",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = SpotifyGreen
                    )
                }
                if (cachedCount > 0) {
                    TextButton(onClick = onClearCache) {
                        Text("Wyczyść", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        errorMessage?.let { msg ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.ErrorOutline, null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        msg, color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Button(
            onClick = onPick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
        ) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Wybierz plik CSV", fontWeight = FontWeight.Bold)
        }
    }
}

// ── Loading ───────────────────────────────────────────────────────────────────

@Composable
private fun LoadingView(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = SpotifyGreen)
            Spacer(Modifier.height(16.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Composable
private fun PreviewView(
    features: List<TrackAudioFeatures>,
    parseErrors: List<String>,
    skippedCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Znaleziono ${features.size} rekordów",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (skippedCount > 0)
                        Text(
                            "Pominięto: $skippedCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("Anuluj") }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                    ) { Text("Importuj") }
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp)) {
            if (parseErrors.isNotEmpty()) {
                item {
                    var expanded by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${parseErrors.size} błędów",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(
                                        if (expanded) "Zwiń" else "Rozwiń",
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            if (expanded) parseErrors.forEach { err ->
                                Text(
                                    "• $err", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(top = 2.dp),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            items(features.take(200)) { f ->
                FeaturePreviewRow(f); HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
            }
            if (features.size > 200) item {
                Text(
                    "… i ${features.size - 200} więcej",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun FeaturePreviewRow(f: TrackAudioFeatures) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                f.spotifyTrackId,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                f.genres.take(40),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        FeaturePill("${f.bpm.toInt()} BPM")
        FeaturePill("E${f.energy.toInt()}")
        FeaturePill("D${f.danceability.toInt()}")
    }
}

@Composable
private fun FeaturePill(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = SpotifyGreen.copy(alpha = 0.12f)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, color = SpotifyGreen,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ── Done ──────────────────────────────────────────────────────────────────────

@Composable
private fun DoneView(
    imported: Int,
    cachedTotal: Int,
    onBack: () -> Unit,
    onImportMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
    ) {
        Icon(Icons.Default.CheckCircle, null, tint = SpotifyGreen, modifier = Modifier.size(80.dp))
        Text(
            "Import zakończony!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatLine("Zaimportowano:", "$imported rekordów")
                StatLine("Łącznie w cache:", "$cachedTotal rekordów")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onImportMore, modifier = Modifier.weight(1f)) {
                Text("Importuj kolejny")
            }
            Button(
                onClick = onBack, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Text("Gotowe")
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = SpotifyGreen
        )
    }
}