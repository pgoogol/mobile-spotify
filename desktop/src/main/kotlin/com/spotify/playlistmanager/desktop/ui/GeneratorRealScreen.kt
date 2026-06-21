package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.data.csv.CsvParser
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.desktop.data.CsvImport
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.GenerateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

/** Konfiguracja pojedynczego źródła generatora (UI). */
private data class SourceConfig(
    val id: String = UUID.randomUUID().toString(),
    val playlist: Playlist? = null,
    val curve: EnergyCurve = EnergyCurve.None,
    val trackCount: Int = 10,
)

/**
 * Generator na **prawdziwych** playlistach Spotify, z obsługą **wielu źródeł**
 * (każde z własną playlistą, strategią i liczbą utworów) — jak na Androidzie.
 *
 * Używa `GeneratePlaylistUseCase.generateWithCurves(List<PlaylistSource>)` z
 * :shared, pokazuje wynik i pozwala zapisać go jako nową playlistę na Spotify.
 *
 * Jakość krzywych zależy od cech audio — użyj „Importuj CSV".
 */
@Composable
fun GeneratorRealScreen(client: SpotifyClient) {
    val scope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    val sources = remember { mutableStateListOf(SourceConfig()) }
    var result by remember { mutableStateOf<GenerateResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var featuresInfo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { client.repository.getUserPlaylists() }
            .onSuccess {
                playlists = it
                if (sources.size == 1 && sources[0].playlist == null) {
                    sources[0] = sources[0].copy(playlist = it.firstOrNull())
                }
            }
            .onFailure { status = "Nie udało się pobrać playlist: ${it.message}" }
    }

    fun importCsv() {
        val file = CsvImport.pickFile() ?: return
        busy = true
        scope.launch {
            runCatching {
                val parsed = withContext(Dispatchers.IO) { file.inputStream().use { CsvParser.parse(it) } }
                client.featuresRepository.upsert(parsed.features)
                parsed.features.size
            }.onSuccess { busy = false; featuresInfo = "Zaimportowano $it cech audio" }
                .onFailure { busy = false; featuresInfo = "Błąd importu CSV: ${it.message}" }
        }
    }

    fun generate() {
        val configured = sources.filter { it.playlist != null }
        if (configured.isEmpty()) {
            status = "Dodaj przynajmniej jedną playlistę."
            return
        }
        busy = true
        status = null
        scope.launch {
            runCatching {
                client.generatePlaylistUseCase.generateWithCurves(
                    configured.map {
                        PlaylistSource(playlist = it.playlist, trackCount = it.trackCount, energyCurve = it.curve)
                    },
                ).generateResult
            }.onSuccess { result = it; busy = false }
                .onFailure { status = "Błąd generowania: ${it.message}"; busy = false }
        }
    }

    fun save() {
        val res = result ?: return
        busy = true
        status = null
        scope.launch {
            runCatching {
                val uris = res.tracks.mapNotNull { it.uri }
                require(uris.isNotEmpty()) { "Brak utworów z URI do zapisania." }
                val id = client.repository.createPlaylist(
                    "Wygenerowano (${sources.count { it.playlist != null }} źródeł)",
                    "Spotify Playlist Manager (desktop)",
                )
                client.repository.addTracksToPlaylist(id, uris)
                uris.size
            }.onSuccess { busy = false; status = "Zapisano playlistę na Spotify ✓ ($it utworów)" }
                .onFailure { busy = false; status = "Błąd zapisu: ${it.message}" }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Generator playlist (Spotify)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sources.forEachIndexed { index, config ->
                        key(config.id) {
                            SourceRow(
                                index = index,
                                config = config,
                                playlists = playlists,
                                canRemove = sources.size > 1,
                                onChange = { sources[index] = it },
                                onRemove = { sources.removeAt(index) },
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = { sources.add(SourceConfig(playlist = playlists.firstOrNull())) }) {
                            Text("+ Dodaj źródło")
                        }
                        OutlinedButton(onClick = { importCsv() }, enabled = !busy) {
                            Text("Importuj CSV")
                        }
                        featuresInfo?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { generate() }, enabled = !busy) { Text("Generuj") }
                    }
                }
            }

            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SpotifyGreen)
            status?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.startsWith("Zapisano")) SpotifyGreen else MaterialTheme.colorScheme.error,
                )
            }

            val res = result
            if (res != null) {
                res.segments.firstOrNull()?.let { EnergyCurveChart(it) }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${res.tracks.size} utworów · dopasowanie ${(res.overallMatchPercentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { save() }, enabled = !busy && res.tracks.isNotEmpty()) {
                        Text("Zapisz na Spotify")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(res.tracks) { index, track ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${index + 1}", modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(track.formattedDuration(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    index: Int,
    config: SourceConfig,
    playlists: List<Playlist>,
    canRemove: Boolean,
    onChange: (SourceConfig) -> Unit,
    onRemove: () -> Unit,
) {
    var playlistMenu by remember { mutableStateOf(false) }
    var strategyMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("${index + 1}.", modifier = Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Box {
            OutlinedButton(onClick = { playlistMenu = true }) {
                Text(config.playlist?.name ?: "Wybierz playlistę")
            }
            DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
                playlists.forEach { pl ->
                    DropdownMenuItem(
                        text = { Text("${pl.name}  (${pl.trackCount})") },
                        onClick = { onChange(config.copy(playlist = pl)); playlistMenu = false },
                    )
                }
            }
        }

        Box {
            OutlinedButton(onClick = { strategyMenu = true }) { Text(config.curve.displayName) }
            DropdownMenu(expanded = strategyMenu, onDismissRequest = { strategyMenu = false }) {
                EnergyCurve.presets.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text(preset.displayName) },
                        onClick = { onChange(config.copy(curve = preset)); strategyMenu = false },
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text("Utworów: ${config.trackCount}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = config.trackCount.toFloat(),
                onValueChange = { onChange(config.copy(trackCount = it.roundToInt())) },
                valueRange = 1f..50f,
                steps = 48,
            )
        }

        if (canRemove) {
            TextButton(onClick = onRemove) { Text("Usuń") }
        }
    }
}
