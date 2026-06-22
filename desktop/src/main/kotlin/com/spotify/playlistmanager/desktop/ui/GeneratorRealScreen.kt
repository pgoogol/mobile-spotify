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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.collectAsState
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
import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.desktop.data.CsvImport
import com.spotify.playlistmanager.desktop.data.LIKED_ID
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyAmber
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.GenerateResult
import com.spotify.playlistmanager.domain.model.GeneratorTemplate
import com.spotify.playlistmanager.domain.model.SegmentMatchResult
import com.spotify.playlistmanager.domain.model.TargetAction
import com.spotify.playlistmanager.domain.model.TemplateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generator playlist na **prawdziwych** danych Spotify z obsługą **wielu źródeł** —
 * desktopowy odpowiednik mobilnego ekranu „Generuj" (`GenerateScreen`).
 *
 * Każde źródło ([PlaylistSource]) ma własną playlistę, liczbę utworów, strategię
 * doboru ([EnergyCurve], czyli „krzywą energii"), sortowanie (gdy strategia = Brak),
 * przełącznik harmonic mixing (gdy strategia ≠ Brak) oraz przypięte utwory
 * (cross-playlist). Karta źródła znajduje się w [PlaylistSourceCard], a dialog
 * przypinania w [GeneratePinTrackDialog].
 *
 * Generowanie używa `GeneratePlaylistUseCase.generateWithCurves(List<PlaylistSource>)`
 * z :shared (ta sama logika co na Androidzie) i wykonuje się N rund (pole „liczba
 * rund"), akumulując globalnie użyte ID utworów (`allGeneratedTrackIds`) jako
 * `excludeTrackIds` kolejnych rund — dzięki czemu kolejne rundy nie powtarzają
 * utworów. Po wygenerowaniu pokazujemy wykres krzywej energii (cel vs. rzeczywisty)
 * z procentem dopasowania oraz połączoną listę wynikowych utworów.
 *
 * Cele wyjściowe ([TargetAction], multi-select): nowa playlista, istniejąca
 * playlista (dropdown) oraz kolejka odtwarzania. Konfigurację generatora można
 * zapisać jako szablon ([GeneratorTemplate]) i wczytać/zmienić nazwę/usunąć
 * z panelu szablonów (`client.templateRepository`).
 *
 * Uproszczenia względem mobile: pominięto ręczną wymianę pojedynczych utworów
 * (SwapHoriz + Undo) oraz rozwijane statystyki audio per utwór w podglądzie.
 * Jakość krzywych zależy od cech audio — użyj „Importuj CSV".
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

    // ── Repeat count ──────────────────────────────────────────────────────
    var repeatCount by remember { mutableStateOf(1) }
    var repeatProgress by remember { mutableStateOf(0) }

    // ── Cele wyjściowe (multi-select) ─────────────────────────────────────
    var targetActions by remember { mutableStateOf(setOf(TargetAction.NEW_PLAYLIST)) }
    var targetPlaylistId by remember { mutableStateOf<String?>(null) }
    var targetPlaylistName by remember { mutableStateOf<String?>(null) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showQueueDryRun by remember { mutableStateOf(false) }

    // ── Szablony ──────────────────────────────────────────────────────────
    val templates by client.templateRepository.observeAll().collectAsState(initial = emptyList())
    var showTemplatesPanel by remember { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }

    // ── Stan dialogu przypinania utworów (cross-playlist) ─────────────────
    var pinning by remember { mutableStateOf<PinningState?>(null) }

    LaunchedEffect(Unit) {
        runCatching { client.repository.getUserPlaylists() }
            .onSuccess { fetched ->
                // Dodaj syntetyczną „Polubione utwory" na początku — jak na Androidzie.
                availablePlaylists = listOf(likedPlaylist()) + fetched
            }
            .onFailure { status = "Nie udało się pobrać playlist: ${it.message}" }
    }

    // ── Helper: pobiera tracki playlisty/Liked dla pickera przypinania ────
    suspend fun fetchTracksForPicker(playlistId: String): List<Track> =
        if (playlistId == LIKED_ID) client.repository.getLikedTracks()
        else client.repository.getPlaylistTracks(playlistId)

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

    // ── Przypinanie: otwórz dialog dla segmentu ───────────────────────────
    fun openPinning(sourceId: String) {
        val source = sources.find { it.id == sourceId } ?: return
        val initialId = source.playlist?.id ?: LIKED_ID
        pinning = PinningState(
            sourceId = sourceId,
            selectedPlaylistId = initialId,
            tracks = emptyList(),
            draft = source.pinnedTracks,
            loading = true,
        )
        scope.launch {
            val tracks = runCatching { fetchTracksForPicker(initialId) }.getOrElse {
                status = "Nie udało się pobrać utworów: ${it.message}"
                pinning = null
                return@launch
            }
            pinning = pinning?.copy(tracks = tracks, loading = false)
        }
    }

    fun switchPinningPlaylist(playlistId: String) {
        val current = pinning ?: return
        if (current.selectedPlaylistId == playlistId) return
        pinning = current.copy(selectedPlaylistId = playlistId, tracks = emptyList(), loading = true)
        scope.launch {
            val tracks = runCatching { fetchTracksForPicker(playlistId) }.getOrElse {
                status = "Nie udało się pobrać utworów: ${it.message}"
                pinning = pinning?.copy(loading = false)
                return@launch
            }
            // Zachowaj tylko jeśli wciąż patrzymy na tę samą playlistę.
            if (pinning?.selectedPlaylistId == playlistId) {
                pinning = pinning?.copy(tracks = tracks, loading = false)
            }
        }
    }

    fun refreshPinning() {
        val current = pinning ?: return
        pinning = current.copy(loading = true)
        scope.launch {
            val tracks = runCatching { fetchTracksForPicker(current.selectedPlaylistId) }.getOrElse {
                status = "Nie udało się odświeżyć utworów: ${it.message}"
                pinning = pinning?.copy(loading = false)
                return@launch
            }
            pinning = pinning?.copy(tracks = tracks, loading = false)
        }
    }

    fun togglePinnedDraft(track: Track, fromPlaylistId: String) {
        val current = pinning ?: return
        val source = sources.find { it.id == current.sourceId } ?: return
        val trackId = track.id ?: return
        val isAlready = current.draft.any { it.id == trackId }
        val newDraft = if (isAlready) {
            current.draft.filterNot { it.id == trackId }
        } else {
            if (current.draft.size >= source.trackCount) return
            current.draft + PinnedTrackInfo(
                id = trackId,
                title = track.title,
                artist = track.artist,
                albumArtUrl = track.albumArtUrl,
                sourcePlaylistId = fromPlaylistId,
                fullTrack = track,
            )
        }
        pinning = current.copy(draft = newDraft)
    }

    fun confirmPinnedDraft() {
        val current = pinning ?: return
        val idx = sources.indexOfFirst { it.id == current.sourceId }
        if (idx >= 0) {
            val src = sources[idx]
            sources[idx] = src.copy(pinnedTracks = current.draft.take(src.trackCount))
        }
        pinning = null
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
        repeatProgress = 0
        val rounds = repeatCount
        val snapshot = configured.toList()
        scope.launch {
            runCatching {
                // Generuj N rund — każda z akumulowaną listą wykluczeń (dedup w sesji).
                val used = mutableSetOf<String>()
                val mergedSegments = mutableListOf<SegmentMatchResult>()
                val mergedTracks = mutableListOf<Track>()
                var completed = 0
                var exhausted = false
                for (round in 1..rounds) {
                    repeatProgress = round
                    val res = client.generatePlaylistUseCase
                        .generateWithCurves(snapshot, excludeTrackIds = used)
                        .let { it.generateResult to it.allGeneratedTrackIds }
                    val (genResult, generatedIds) = res
                    if (genResult.tracks.isEmpty()) {
                        exhausted = true
                        break
                    }
                    mergedSegments.addAll(genResult.segments)
                    mergedTracks.addAll(genResult.tracks)
                    used.addAll(generatedIds)
                    completed = round
                    if (generatedIds.isEmpty()) {
                        exhausted = true
                        break
                    }
                }
                Triple(mergedSegments.toList(), mergedTracks.toList(), completed to exhausted)
            }.onSuccess { (segs, tracks, info) ->
                val (completed, exhausted) = info
                busy = false
                repeatProgress = 0
                if (tracks.isEmpty()) {
                    result = null
                    status = "Playlisty wyczerpane — brak utworów do wygenerowania."
                } else {
                    val curveSegs = segs.filter { it.targetScores.isNotEmpty() }
                    val overall = if (curveSegs.isEmpty()) 1f
                    else curveSegs.map { it.matchPercentage }.average().toFloat()
                    result = GenerateResult(
                        tracks = tracks,
                        segments = segs,
                        overallMatchPercentage = overall,
                    )
                    // Wyczyść pinned po wygenerowaniu (jednorazowe — jak na Androidzie).
                    for (i in sources.indices) {
                        if (sources[i].pinnedTracks.isNotEmpty()) {
                            sources[i] = sources[i].copy(pinnedTracks = emptyList())
                        }
                    }
                    status = if (exhausted && completed < rounds) {
                        "Playlisty wyczerpane po $completed z $rounds rund · ${tracks.size} utworów."
                    } else {
                        null
                    }
                }
            }.onFailure { busy = false; repeatProgress = 0; status = "Błąd generowania: ${it.message}" }
        }
    }

    fun save() {
        val res = result ?: return
        val name = playlistName.trim().ifBlank { "Nowa Playlista" }
        val uris = res.tracks.mapNotNull { it.uri }
        if (uris.isEmpty()) {
            status = "Brak utworów z URI do zapisania."
            return
        }
        val actions = targetActions
        // Kolejka wymaga osobnego potwierdzenia (dry-run) — pokaż dialog.
        if (TargetAction.QUEUE in actions && actions.size == 1) {
            showQueueDryRun = true
            return
        }
        busy = true
        status = null
        scope.launch {
            runCatching {
                var url: String? = null
                // Nowa playlista.
                if (TargetAction.NEW_PLAYLIST in actions) {
                    val id = client.repository.createPlaylist(
                        name,
                        "Wygenerowano przez Spotify Playlist Manager (desktop)",
                    )
                    client.repository.addTracksToPlaylist(id, uris)
                    url = "https://open.spotify.com/playlist/$id"
                }
                // Istniejąca playlista.
                if (TargetAction.EXISTING_PLAYLIST in actions) {
                    val targetId = targetPlaylistId
                        ?: throw IllegalStateException("Wybierz playlistę docelową")
                    client.repository.addTracksToPlaylist(targetId, uris)
                }
                url to uris.size
            }.onSuccess { (url, count) ->
                busy = false
                savedUrl = url
                status = "Zapisano na Spotify ✓ ($count utworów)"
                // Kolejka łączona z innym celem — pokaż dry-run po zapisie.
                if (TargetAction.QUEUE in actions) showQueueDryRun = true
            }.onFailure { busy = false; status = "Błąd zapisu: ${it.message}" }
        }
    }

    fun addToQueue() {
        val res = result ?: return
        val uris = res.tracks.mapNotNull { it.uri }
        if (uris.isEmpty()) return
        showQueueDryRun = false
        busy = true
        status = null
        scope.launch {
            var added = 0
            runCatching {
                uris.forEach { uri ->
                    client.repository.addToQueue(uri)
                    added++
                }
            }.onSuccess {
                busy = false
                status = "Dodano $added utworów do kolejki ✓"
            }.onFailure { e ->
                busy = false
                val noDevice = e.message?.contains("404") == true ||
                    e.message?.contains("No active device") == true
                status = if (noDevice) {
                    "Brak aktywnego odtwarzacza Spotify. Włącz odtwarzanie i spróbuj ponownie. " +
                        "Dodano $added z ${uris.size} utworów."
                } else {
                    "Błąd dodawania do kolejki: ${e.message}. Dodano $added z ${uris.size} utworów."
                }
            }
        }
    }

    // ── Szablony: zapis / wczytanie / mapowanie ───────────────────────────
    fun saveTemplate(name: String) {
        val configured = sources.filter { it.playlist != null }
        if (configured.isEmpty()) {
            status = "Brak źródeł do zapisania."
            return
        }
        scope.launch {
            runCatching {
                client.templateRepository.save(
                    GeneratorTemplate(
                        name = name,
                        sources = configured.mapIndexed { idx, src ->
                            TemplateSource(
                                position = idx,
                                playlistId = src.playlist!!.id,
                                playlistName = src.playlist!!.name,
                                trackCount = src.trackCount,
                                sortBy = src.sortBy,
                                energyCurve = src.energyCurve,
                                pinnedTracks = src.pinnedTracks,
                                harmonicMixing = src.harmonicMixing,
                            )
                        },
                    ),
                )
            }.onFailure { status = "Błąd zapisu szablonu: ${it.message}" }
        }
    }

    fun loadTemplate(template: GeneratorTemplate) {
        val newSources = template.sources.map { ts ->
            // Rozwiąż playlistId → Playlist z availablePlaylists (fallback: stub z szablonu).
            val playlist = availablePlaylists.find { it.id == ts.playlistId }
                ?: Playlist(ts.playlistId, ts.playlistName, null, null, 0, "")
            PlaylistSource(
                playlist = playlist,
                trackCount = ts.trackCount,
                sortBy = ts.sortBy,
                energyCurve = ts.energyCurve,
                pinnedTracks = ts.pinnedTracks,
                harmonicMixing = ts.harmonicMixing,
            )
        }
        sources.clear()
        sources.addAll(newSources.ifEmpty { listOf(PlaylistSource()) })
        showTemplatesPanel = false
        status = "Wczytano szablon: ${template.name}"
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Nagłówek + akcje szablonów ─────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Generuj playlistę",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { showSaveTemplateDialog = true }, enabled = !busy) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Zapisz szablon")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showTemplatesPanel = !showTemplatesPanel }) {
                    Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Szablony (${templates.size})")
                }
            }

            // ── Panel szablonów (wczytaj/zmień nazwę/usuń) ─────────────────
            if (showTemplatesPanel) {
                TemplatesPanel(
                    templates = templates,
                    onLoad = { loadTemplate(it) },
                    onRename = { id, newName -> scope.launch { client.templateRepository.rename(id, newName) } },
                    onDelete = { id -> scope.launch { client.templateRepository.delete(id) } },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }

            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Nazwa nowej playlisty") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SpotifyGreen),
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Cele wyjściowe (multi-select) ──────────────────────────────
            TargetActionsSection(
                selectedActions = targetActions,
                targetPlaylistName = targetPlaylistName,
                onToggleAction = { action ->
                    targetActions = targetActions.toMutableSet().apply {
                        if (action in this) { if (size > 1) remove(action) } else add(action)
                    }
                },
                onPickPlaylist = { showTargetPicker = true },
            )

            // ── Liczba rund ────────────────────────────────────────────────
            RepeatCountRow(count = repeatCount, onCountChange = { repeatCount = it.coerceIn(1, 100) })

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
                        onPinTracks = { openPinning(source.id) },
                        onRemovePinnedTrack = { trackId ->
                            sources[index] = sources[index].copy(
                                pinnedTracks = sources[index].pinnedTracks.filterNot { it.id == trackId },
                            )
                        },
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
                    Text(if (repeatCount > 1) "Generuj ×$repeatCount" else "Generuj")
                }
            }

            if (busy) {
                if (repeatCount > 1 && repeatProgress > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { repeatProgress.toFloat() / repeatCount },
                            modifier = Modifier.weight(1f),
                            color = SpotifyGreen,
                        )
                        Text(
                            "$repeatProgress/$repeatCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SpotifyGreen)
                }
            }
            status?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (msg.startsWith("Zapisano") || msg.startsWith("Dodano") || msg.startsWith("Wczytano")) {
                        SpotifyGreen
                    } else {
                        MaterialTheme.colorScheme.error
                    },
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
                    savedUrl?.let { url ->
                        OutlinedButton(onClick = { openInBrowser(url) }) {
                            Text("Otwórz w Spotify")
                        }
                        Spacer(Modifier.width(8.dp))
                    }
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

    // ── Dialogi ────────────────────────────────────────────────────────────

    val pin = pinning
    if (pin != null) {
        GeneratePinTrackDialog(
            availablePlaylists = availablePlaylists,
            selectedPlaylistId = pin.selectedPlaylistId,
            tracks = pin.tracks,
            isLoadingTracks = pin.loading,
            selectedTracks = pin.draft,
            maxSelection = sources.find { it.id == pin.sourceId }?.trackCount ?: 0,
            onPickPlaylist = { switchPinningPlaylist(it) },
            onToggleTrack = { track, fromId -> togglePinnedDraft(track, fromId) },
            onConfirm = { confirmPinnedDraft() },
            onRefresh = { refreshPinning() },
            onDismiss = { pinning = null },
        )
    }

    if (showSaveTemplateDialog) {
        SaveTemplateDialog(
            onConfirm = { name -> saveTemplate(name); showSaveTemplateDialog = false },
            onDismiss = { showSaveTemplateDialog = false },
        )
    }

    if (showTargetPicker) {
        TargetPlaylistPickerDialog(
            playlists = availablePlaylists.filter { it.id != LIKED_ID },
            onSelect = { pl ->
                targetPlaylistId = pl.id
                targetPlaylistName = pl.name
                showTargetPicker = false
            },
            onDismiss = { showTargetPicker = false },
        )
    }

    if (showQueueDryRun) {
        val tracks = result?.tracks ?: emptyList()
        QueueDryRunDialog(
            trackCount = tracks.size,
            sampleLabels = tracks.take(10).mapIndexed { i, t -> "${i + 1}. ${t.title} — ${t.artist}" },
            isAdding = busy,
            onConfirm = { addToQueue() },
            onDismiss = { showQueueDryRun = false },
        )
    }
}

/** Syntetyczna playlista „Polubione utwory" — jak na Androidzie. */
private fun likedPlaylist() = Playlist(
    id = LIKED_ID,
    name = "❤ Polubione utwory",
    description = null,
    imageUrl = null,
    trackCount = 0,
    ownerId = "",
)

/** Otwiera URL w domyślnej przeglądarce systemowej (best-effort). */
private fun openInBrowser(url: String) {
    runCatching {
        val desktop = java.awt.Desktop.getDesktop()
        desktop.browse(java.net.URI(url))
    }
}

/**
 * Stan dialogu przypinania utworów (cross-playlist) — desktopowy odpowiednik
 * `PinningState.Picking` z mobilnego ViewModelu. `draft` to wybrane utwory
 * przed zatwierdzeniem (mogą pochodzić z RÓŻNYCH playlist).
 */
private data class PinningState(
    val sourceId: String,
    val selectedPlaylistId: String,
    val tracks: List<Track>,
    val draft: List<PinnedTrackInfo>,
    val loading: Boolean,
)

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
