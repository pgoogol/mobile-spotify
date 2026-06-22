package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.data.csv.CsvParser
import com.spotify.playlistmanager.desktop.data.CsvImport
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Import cech audio z CSV — odwzorowanie mobilnego `CsvImportScreen`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(client: SpotifyClient, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CsvParser.ParseResult?>(null) }
    var featureCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { featureCount = client.featuresRepository.count() }

    fun importCsv() {
        val file = CsvImport.pickFile() ?: return
        busy = true
        scope.launch {
            runCatching {
                val parsed = withContext(Dispatchers.IO) { file.inputStream().use { CsvParser.parse(it) } }
                client.featuresRepository.upsert(parsed.features)
                parsed
            }.onSuccess {
                result = it
                featureCount = client.featuresRepository.count()
                busy = false
            }.onFailure {
                result = CsvParser.ParseResult(emptyList(), 0, listOf(it.message ?: "Błąd importu"))
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import cech audio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Wczytaj plik CSV z cechami audio (BPM, energia, taneczność, valence, " +
                    "klucz Camelot, gatunki…). Zasilają one krzywe energii generatora i " +
                    "analizę trybu Impreza DJ.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = { importCsv() }, enabled = !busy) {
                Icon(Icons.Default.FileUpload, null, modifier = Modifier.padding(end = 8.dp))
                Text("Wybierz plik CSV")
            }

            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SpotifyGreen)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cech w pamięci: $featureCount", fontWeight = FontWeight.SemiBold)
                    result?.let { r ->
                        Text(
                            "Zaimportowano ${r.features.size} · pominięto ${r.skipped}",
                            color = SpotifyGreen,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (r.errors.isNotEmpty()) {
                            Text(
                                "Problemy (${r.errors.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            r.errors.take(5).forEach {
                                Text("• $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
