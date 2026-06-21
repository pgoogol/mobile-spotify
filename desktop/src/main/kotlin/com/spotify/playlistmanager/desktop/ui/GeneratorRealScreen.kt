package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.data.csv.CsvParser
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.desktop.data.CsvImport
import com.spotify.playlistmanager.desktop.data.LIKED_ID
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyAmber
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.GenerateResult
import com.spotify.playlistmanager.domain.model.SegmentMatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generator playlist na **prawdziwych** danych Spotify z obsługą **wielu źródeł** —
 * desktopowy odpowiednik mobilnego ekranu „Generuj" (`GenerateScreen`).
 *
 * Każde źródło ([PlaylistSource]) ma własną playlistę, liczbę utworów, strategię
 * doboru ([EnergyCurve], czyli „krzywą energii"), sortowanie (gdy strategia = Brak)
 * oraz przełącznik harmonic mixing (gdy strategia ≠ Brak). Karta źródła znajduje się
 * w [PlaylistSourceCard].
 *
 * Generowanie używa `GeneratePlaylistUseCase.generateWithCurves(List<PlaylistSource>)`
 * z :shared (ta sama logika co na Androidzie). Po wygenerowaniu pokazujemy wykres
 * krzywej energii (cel vs. rzeczywisty) z procentem dopasowania, listę wynikowych
 * utworów i pozwalamy zapisać je jako nową playlistę na Spotify.
 *
 * Uproszczenia względem mobile: pominięto przypinanie utworów (pinned tracks),
 * sesje „Dodaj więcej / Od zera", repeat count, kolejkę odtwarzania, ręczną wymianę
 * pojedynczych utworów oraz szablony. Skupiamy się na kartach źródeł + strategiach +
 * wykresie + zapisie. Jakość krzywych zależy od cech audio — użyj „Importuj CSV".
 */
@Composable
fun GeneratorRealScreen(client: SpotifyClient) {
    val scope = rememberCoroutineScope()

    var availablePlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    val sources = remember { mutableStateListOf(PlaylistSource()) }
    var result by remember { mutableStateOf<GenerateResult?>(null) }
    var playlistName by remember { mutableStateOf("Nowa Playlista") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var savedUrl by remember { mutableStateOf<String?>(null) }
    var featuresInfo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { client.repository.getUserPlaylists() }
            .onSuccess { fetched ->
                // Dodaj syntetyczną „Polubione utwory" na początku — jak na Androidzie.
                val liked = Playlist(
                    id = LIKED_ID,
                    name = "❤ Polubione utwory",
                    description = null,
                    imageUrl = null,
                    trackCount = 0,
                    ownerId = "",
                )
                availablePlaylists = listOf(liked) + fetched
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
            status = "Wybierz przynajmniej jedną playlistę źródłową."
            return
        }
        // Walidacja Wave — jak na Androidzie.
        for (src in configured) {
            val curve = src.energyCurve
            if (curve is EnergyCurve.Wave && src.trackCount < curve.tracksPerHalfWave) {
                status = "Zbyt mało utworów dla fali w ${src.playlist?.name}: " +
                    "min. ${curve.tracksPerHalfWave}, ustawiono ${src.trackCount}"
                return
            }
        }
        busy = true
        status = null
        savedUrl = null
        scope.launch {
            runCatching {
                client.generatePlaylistUseCase.generateWithCurves(configured.toList()).generateResult
            }.onSuccess { result = it; busy = false }
                .onFailure { status = "Błąd generowania: ${it.message}"; busy = false }
        }
    }

    fun save() {
        val res = result ?: return
        val name = playlistName.trim().ifBlank { "Nowa Playlista" }
        busy = true
        status = null
        scope.launch {
            runCatching {
                val uris = res.tracks.mapNotNull { it.uri }
                require(uris.isNotEmpty()) { "Brak utworów z URI do zapisania." }
                val id = client.repository.createPlaylist(
                    name,
                    "Wygenerowano przez Spotify Playlist Manager (desktop)",
                )
                client.repository.addTracksToPlaylist(id, uris)
                "https://open.spotify.com/playlist/$id" to uris.size
            }.onSuccess { (url, count) ->
                busy = false
                savedUrl = url
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
                "Generuj playlistę",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Nazwa nowej playlisty") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Sekcja źródeł ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Źródła playlist",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = { sources.add(PlaylistSource(playlist = availablePlaylists.firstOrNull())) },
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Dodaj źródło")
                }
            }

            sources.forEachIndexed { index, source ->
                key(source.id) {
                    val prevAxis = if (index > 0) sources[index - 1].energyCurve.scoreAxis else null
                    PlaylistSourceCard(
                        index = index,
                        source = source,
                        availablePlaylists = availablePlaylists,
                        onUpdate = { sources[index] = it },
                        onRemove = { sources.removeAt(index) },
                        canRemove = sources.size > 1,
                        prevScoreAxis = prevAxis,
                    )
                }
            }

            // ── Pasek akcji: Importuj CSV + Generuj ────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { importCsv() }, enabled = !busy) {
                    Text("Importuj CSV")
                }
                featuresInfo?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { generate() },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                ) {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generuj")
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
                // ── Wykres krzywej energii (cel vs. rzeczywisty) ──────────
                if (res.segments.any { it.targetScores.isNotEmpty() }) {
                    Text(
                        "Krzywa energii",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    MultiSegmentEnergyChart(
                        segments = res.segments,
                        overallMatchPercentage = res.overallMatchPercentage,
                    )
                }

                // ── Podsumowanie + zapis ──────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${res.tracks.size} utworów · dopasowanie ${(res.overallMatchPercentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { save() }, enabled = !busy && res.tracks.isNotEmpty()) {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Zapisz na Spotify")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // ── Lista wynikowych utworów ──────────────────────────────
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(res.tracks) { index, track ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                "${index + 1}",
                                modifier = Modifier.width(28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            NetworkImage(
                                url = track.albumArtUrl,
                                contentDescription = track.album,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop,
                                fallback = {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                track.formattedDuration(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  Wykres wielu segmentów (cel vs. rzeczywisty) — własny, NIE modyfikuje
//  istniejącego EnergyCurveChart.kt (który obsługuje pojedynczy segment).
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Rysuje krzywą energii dla **wielu segmentów** połączonych w jedną oś X
 * (jak na Androidzie). Zielona ciągła = cel (target), bursztynowa przerywana =
 * rzeczywisty composite score. Pionowe linie oddzielają segmenty. Chip nad
 * wykresem pokazuje globalny procent dopasowania.
 *
 * Oś Y jest auto-skalowana do zakresu wartości (score'y są już przeskalowane do
 * rozkładu puli przez `EnergyCurveCalculator`).
 */
@Composable
private fun MultiSegmentEnergyChart(
    segments: List<SegmentMatchResult>,
    overallMatchPercentage: Float,
    modifier: Modifier = Modifier,
) {
    val allTargets = segments.flatMap { it.targetScores }
    val allActual = segments.flatMap { seg -> seg.tracks.map { it.compositeScore } }
    val segmentSizes = segments.map { it.tracks.size }

    val all = allTargets + allActual
    val rawMin = all.minOrNull() ?: 0f
    val rawMax = all.maxOrNull() ?: 1f
    val yMin = rawMin - 0.05f
    val yMax = rawMax + 0.05f
    val yRange = (yMax - yMin).coerceAtLeast(0.01f)

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MatchPercentageChip(overallMatchPercentage)
            Spacer(Modifier.weight(1f))
            LegendItem("Cel", SpotifyGreen, dashed = false)
            LegendItem("Rzeczywisty", SpotifyAmber, dashed = true)
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(8.dp),
        ) {
            val pad = 18f
            // Granice segmentów (pionowe, subtelne).
            drawSegmentBoundaries(segmentSizes, allActual.size, pad)
            // Cel — ciągła zielona.
            if (allTargets.size >= 2) {
                drawCurve(allTargets, SpotifyGreen, dashed = false, yMin, yRange, pad)
            }
            // Rzeczywisty — przerywana bursztynowa.
            if (allActual.size >= 2) {
                drawCurve(allActual, SpotifyAmber, dashed = true, yMin, yRange, pad)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                "${allActual.size} utworów",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MatchPercentageChip(percentage: Float) {
    val pct = (percentage * 100).toInt()
    val (emoji, chipColor) = when {
        pct >= 80 -> "🟢" to SpotifyGreen
        pct >= 60 -> "🟡" to SpotifyAmber
        else -> "🔴" to MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(12.dp), color = chipColor.copy(alpha = 0.15f)) {
        Text(
            "$emoji Dopasowanie: $pct%",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = chipColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun DrawScope.drawCurve(
    scores: List<Float>,
    color: Color,
    dashed: Boolean,
    yMin: Float,
    yRange: Float,
    pad: Float,
) {
    val w = size.width
    val h = size.height
    fun px(i: Int) = pad + (w - 2 * pad) * i / (scores.size - 1).coerceAtLeast(1)
    fun py(score: Float) = h - pad - (h - 2 * pad) * ((score - yMin) / yRange).coerceIn(0f, 1f)

    val path = Path()
    scores.forEachIndexed { i, s ->
        val x = px(i)
        val y = py(s)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val stroke = if (dashed) {
        Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)))
    } else {
        Stroke(width = 3f)
    }
    drawPath(path, color, style = stroke)

    scores.forEachIndexed { i, s ->
        drawCircle(color, radius = 4f, center = Offset(px(i), py(s)))
    }
}

private fun DrawScope.drawSegmentBoundaries(
    segmentSizes: List<Int>,
    totalPoints: Int,
    pad: Float,
) {
    if (totalPoints < 2 || segmentSizes.size < 2) return
    val w = size.width
    val h = size.height
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
    var offset = 0
    // Granice między segmentami (pomijamy ostatnią — koniec wykresu).
    segmentSizes.dropLast(1).forEach { sz ->
        offset += sz
        val x = pad + (w - 2 * pad) * offset / (totalPoints - 1).coerceAtLeast(1)
        drawLine(
            color = Color.White.copy(alpha = 0.20f),
            start = Offset(x, pad),
            end = Offset(x, h - pad),
            strokeWidth = 1.5f,
            pathEffect = dash,
        )
    }
}

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.size(18.dp, 3.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 3f,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
