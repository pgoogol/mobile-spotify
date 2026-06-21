package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.spotify.playlistmanager.desktop.data.CsvImport
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.dj.PartyPlanner
import com.spotify.playlistmanager.domain.dj.TrackAnalyzer
import com.spotify.playlistmanager.domain.dj.model.Block
import com.spotify.playlistmanager.domain.dj.model.EnergyArc
import com.spotify.playlistmanager.domain.dj.model.PartyState
import com.spotify.playlistmanager.domain.dj.model.Phase
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.StyleRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Tryb „Impreza DJ" (Plan) na desktopie — używa [PartyPlanner] i [TrackAnalyzer]
 * z :shared (ta sama logika bloków co Android).
 *
 * Przepływ: wybierz pulę (playlistę) → zaimportuj cechy audio (CSV) → ustaw czas,
 * proporcję salsa/bachata i łuk energii → „Zaplanuj" → plan bloków → zapis na Spotify.
 *
 * Uwaga: analiza wymaga cech audio (styl wykrywany z `genres`), więc bez importu
 * CSV pula jest pusta.
 */
@Composable
fun PartyPlannerScreen(client: SpotifyClient) {
    val scope = rememberCoroutineScope()
    val analyzer = remember { TrackAnalyzer() }
    val planner = remember { PartyPlanner() }

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var pool by remember { mutableStateOf<Playlist?>(null) }
    var durationMin by remember { mutableStateOf(120) }
    var salsaPercent by remember { mutableStateOf(50) }
    var arc by remember { mutableStateOf(EnergyArc.CLASSIC) }
    var plan by remember { mutableStateOf<PartyPlanner.PlanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var featuresInfo by remember { mutableStateOf<String?>(null) }

    var poolMenu by remember { mutableStateOf(false) }
    var arcMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { client.repository.getUserPlaylists() }
            .onSuccess { playlists = it; pool = it.firstOrNull() }
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

    fun generatePlan() {
        val source = pool ?: return
        busy = true
        status = null
        scope.launch {
            runCatching {
                val tracks = client.repository.getPlaylistTracks(source.id)
                val features = client.featuresRepository.getFeaturesMap(tracks.mapNotNull { it.id })
                require(features.isNotEmpty()) { "Brak cech audio — zaimportuj CSV (przycisk niżej)." }
                val analyzed = analyzer.analyzePool(tracks, features)
                require(analyzed.values.any { it.isNotEmpty() }) {
                    "Nie wykryto utworów salsa/bachata w tej puli (sprawdź kolumnę Genres w CSV)."
                }
                planner.plan(
                    state = PartyState(),
                    analyzedByStyle = analyzed,
                    durationMs = durationMin * 60_000L,
                    ratio = StyleRatio(salsaPercent),
                    arc = arc,
                )
            }.onSuccess {
                plan = it
                busy = false
                val tracks = it.blocks.sumOf { b -> b.size }
                status = "Plan: ${it.blocks.size} bloków · $tracks utworów"
            }.onFailure { busy = false; status = it.message }
        }
    }

    fun save() {
        val current = plan ?: return
        busy = true
        scope.launch {
            runCatching {
                val uris = current.blocks.flatMap { it.tracks }.mapNotNull { it.track.uri }
                require(uris.isNotEmpty()) { "Brak utworów z URI do zapisania." }
                val id = client.repository.createPlaylist(
                    "Impreza · ${arc.displayName} · salsa $salsaPercent%",
                    "Spotify Playlist Manager (desktop) — Impreza DJ",
                )
                client.repository.addTracksToPlaylist(id, uris)
                uris.size
            }.onSuccess { busy = false; status = "Zapisano playlistę imprezy na Spotify ✓ ($it utworów)" }
                .onFailure { busy = false; status = "Błąd zapisu: ${it.message}" }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Impreza DJ — plan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box {
                            OutlinedButton(onClick = { poolMenu = true }) {
                                Text(pool?.name ?: "Wybierz pulę (playlistę)")
                            }
                            DropdownMenu(expanded = poolMenu, onDismissRequest = { poolMenu = false }) {
                                playlists.forEach { pl ->
                                    DropdownMenuItem(
                                        text = { Text("${pl.name}  (${pl.trackCount})") },
                                        onClick = { pool = pl; poolMenu = false },
                                    )
                                }
                            }
                        }
                        Box {
                            OutlinedButton(onClick = { arcMenu = true }) { Text("Łuk: ${arc.displayName}") }
                            DropdownMenu(expanded = arcMenu, onDismissRequest = { arcMenu = false }) {
                                EnergyArc.entries.forEach { a ->
                                    DropdownMenuItem(text = { Text(a.displayName) }, onClick = { arc = a; arcMenu = false })
                                }
                            }
                        }
                        Button(onClick = { generatePlan() }, enabled = pool != null && !busy) {
                            Text("Zaplanuj")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Czas imprezy: ${durationMin} min", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = durationMin.toFloat(),
                                onValueChange = { durationMin = it.roundToInt() },
                                valueRange = 30f..300f,
                                steps = 26,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Salsa $salsaPercent% · Bachata ${100 - salsaPercent}%", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = salsaPercent.toFloat(),
                                onValueChange = { salsaPercent = it.roundToInt() },
                                valueRange = 0f..100f,
                                steps = 19,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { importCsv() }, enabled = !busy) {
                            Text("Importuj CSV (cechy audio)")
                        }
                        featuresInfo?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SpotifyGreen)
            status?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.startsWith("Zapisano") || msg.startsWith("Plan")) SpotifyGreen else MaterialTheme.colorScheme.error,
                )
            }

            val current = plan
            if (current != null && current.blocks.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val totalMs = current.blocks.sumOf { it.totalDurationMs }
                    Text(
                        "Łączny czas planu: ${totalMs / 60_000} min",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { save() }, enabled = !busy) { Text("Zapisz na Spotify") }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(current.blocks) { index, block ->
                        val phase = current.phaseByBlock.getOrNull(index)
                        BlockCard(index + 1, block, phase)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockCard(number: Int, block: Block, phase: Phase?) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Blok $number · ${phase?.displayName ?: "—"} · ${styleLabel(block.style)} · " +
                    "${block.size} utw. · ${block.totalDurationMs / 60_000} min",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SpotifyGreen,
            )
            block.tracks.forEach { at ->
                Text(
                    "• ${at.track.title} — ${at.track.artist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private fun styleLabel(style: Style): String = when (style) {
    Style.SALSA -> "Salsa"
    Style.BACHATA -> "Bachata"
}
