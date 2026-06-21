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
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.GenerateResult
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Generator na **prawdziwych** playlistach Spotify.
 *
 * Używa [com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase]
 * z :shared (ta sama logika co Android): pobiera utwory wybranej playlisty,
 * dopasowuje je do strategii krzywej energii i pozwala zapisać wynik jako
 * nową playlistę na Spotify.
 *
 * Uwaga: jakość krzywych zależy od cech audio. Dopóki nie zaimportujesz CSV,
 * cechy są nieznane i strategie inne niż „Brak" dają płaski wynik.
 */
@Composable
fun GeneratorRealScreen(client: SpotifyClient) {
    val scope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var selected by remember { mutableStateOf<Playlist?>(null) }
    var curve by remember { mutableStateOf<EnergyCurve>(EnergyCurve.None) }
    var trackCount by remember { mutableStateOf(20) }
    var result by remember { mutableStateOf<GenerateResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var playlistMenu by remember { mutableStateOf(false) }
    var strategyMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { client.repository.getUserPlaylists() }
            .onSuccess { playlists = it; selected = it.firstOrNull() }
            .onFailure { status = "Nie udało się pobrać playlist: ${it.message}" }
    }

    fun generate() {
        val source = selected ?: return
        busy = true
        status = null
        scope.launch {
            runCatching {
                client.generatePlaylistUseCase.generateWithCurves(
                    listOf(PlaylistSource(playlist = source, trackCount = trackCount, energyCurve = curve)),
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
                val name = "Wygenerowano · ${curve.displayName}"
                val id = client.repository.createPlaylist(
                    name,
                    "Spotify Playlist Manager (desktop)",
                )
                client.repository.addTracksToPlaylist(id, uris)
                uris.size
            }.onSuccess { count ->
                busy = false
                status = "Zapisano playlistę na Spotify ✓ ($count utworów)"
            }.onFailure { busy = false; status = "Błąd zapisu: ${it.message}" }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Generator playlist (Spotify)",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Źródłowa playlista
                        Box {
                            OutlinedButton(onClick = { playlistMenu = true }) {
                                Text(selected?.name ?: "Wybierz playlistę")
                            }
                            DropdownMenu(expanded = playlistMenu, onDismissRequest = { playlistMenu = false }) {
                                playlists.forEach { pl ->
                                    DropdownMenuItem(
                                        text = { Text("${pl.name}  (${pl.trackCount})") },
                                        onClick = { selected = pl; playlistMenu = false },
                                    )
                                }
                            }
                        }

                        // Strategia
                        Box {
                            OutlinedButton(onClick = { strategyMenu = true }) {
                                Text(curve.displayName)
                            }
                            DropdownMenu(expanded = strategyMenu, onDismissRequest = { strategyMenu = false }) {
                                EnergyCurve.presets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.displayName) },
                                        onClick = { curve = preset; strategyMenu = false },
                                    )
                                }
                            }
                        }

                        // Liczba utworów
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Liczba utworów: $trackCount", style = MaterialTheme.typography.labelLarge)
                            Slider(
                                value = trackCount.toFloat(),
                                onValueChange = { trackCount = it.roundToInt() },
                                valueRange = 4f..50f,
                                steps = 45,
                            )
                        }

                        Button(onClick = { generate() }, enabled = selected != null && !busy) {
                            Text("Generuj")
                        }
                    }
                    Text(
                        curve.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SpotifyGreen)
            }
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
