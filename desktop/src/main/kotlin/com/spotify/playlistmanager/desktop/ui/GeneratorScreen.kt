package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.desktop.data.SampleData
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.EnergyCurveCalculator
import com.spotify.playlistmanager.domain.model.MatchedTrack
import com.spotify.playlistmanager.domain.model.SegmentMatchResult
import kotlin.math.roundToInt

/**
 * Ekran generatora dla wersji desktopowej.
 *
 * Wiąże kontrolki UI z [EnergyCurveCalculator] z modułu :shared — tym samym
 * algorytmem, którego używa aplikacja Android. Działa na wbudowanej puli
 * [SampleData] (bez połączenia ze Spotify).
 */
@Composable
fun GeneratorScreen() {
    var selectedCurve by remember { mutableStateOf<EnergyCurve>(EnergyCurve.Rising) }
    var trackCount by remember { mutableStateOf(12) }
    var result by remember { mutableStateOf<SegmentMatchResult?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    fun generate() {
        result = EnergyCurveCalculator.matchTracks(
            tracks = SampleData.tracks,
            featuresMap = SampleData.featuresMap,
            curve = selectedCurve,
            trackCount = trackCount,
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Nagłówek ──────────────────────────────────────────────
            Column {
                Text(
                    text = "Generator playlist — krzywe energii",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Wersja desktopowa · algorytmy reużyte z modułu :shared",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SpotifyGreen,
                )
            }

            // ── Panel kontrolny ───────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Wybór strategii
                        Box {
                            OutlinedButton(onClick = { menuOpen = true }) {
                                Text(selectedCurve.displayName)
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                EnergyCurve.presets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.displayName) },
                                        onClick = {
                                            selectedCurve = preset
                                            menuOpen = false
                                        },
                                    )
                                }
                            }
                        }

                        // Liczba utworów
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Liczba utworów: $trackCount",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Slider(
                                value = trackCount.toFloat(),
                                onValueChange = { trackCount = it.roundToInt() },
                                valueRange = 4f..28f,
                                steps = 23,
                            )
                        }

                        Button(onClick = { generate() }) {
                            Text("Generuj")
                        }
                    }

                    Text(
                        text = selectedCurve.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Wynik ─────────────────────────────────────────────────
            val segment = result
            if (segment == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Wybierz strategię i kliknij „Generuj\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                EnergyCurveChart(segment)

                TrackListHeader()
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(segment.tracks) { index, matched ->
                        TrackRow(index + 1, matched)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#", modifier = Modifier.width(32.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Utwór", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Score", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text("Cel", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrackRow(position: Int, matched: MatchedTrack) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$position",
            modifier = Modifier.width(32.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(matched.track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${matched.track.artist} · ${matched.track.formattedDuration()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "%.2f".format(matched.compositeScore),
            modifier = Modifier.width(80.dp),
            color = SpotifyGreen,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "%.2f".format(matched.targetScore),
            modifier = Modifier.width(80.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
