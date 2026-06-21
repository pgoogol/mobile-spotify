package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.playlistmanager.data.csv.CsvParser
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.desktop.data.CsvImport
import com.spotify.playlistmanager.desktop.data.LIKED_ID
import com.spotify.playlistmanager.desktop.data.SpotifyClient
import com.spotify.playlistmanager.desktop.theme.SpotifyAmber
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.dj.LiveAssistant
import com.spotify.playlistmanager.domain.dj.PartyPlanner
import com.spotify.playlistmanager.domain.dj.TrackAnalyzer
import com.spotify.playlistmanager.domain.dj.model.AnalyzedTrack
import com.spotify.playlistmanager.domain.dj.model.EnergyArc
import com.spotify.playlistmanager.domain.dj.model.PartyMode
import com.spotify.playlistmanager.domain.dj.model.PartyState
import com.spotify.playlistmanager.domain.dj.model.Preset
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.StyleRatio
import com.spotify.playlistmanager.domain.model.NextTrackTarget
import com.spotify.playlistmanager.domain.model.ScoreAxis
import com.spotify.playlistmanager.domain.usecase.SuggestNextTrackUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ══════════════════════════════════════════════════════════════════════
//  Modele stanu (desktopowy odpowiednik StepwiseUiState z :app — bez ViewModel)
// ══════════════════════════════════════════════════════════════════════

/** Slot puli — playlista źródłowa + jej utwory (ładowane raz, cache w slocie). */
internal data class PoolSlot(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
)

/** Która pula jest aktywna (z której algorytm sugeruje). */
internal enum class ActivePool { A, B }

/** Jeden utwór w sesji wraz z metadanymi wyboru. */
internal data class SessionTrack(
    val track: Track,
    val pool: ActivePool,
    val score: Float,
    val targetLabel: String,
    val bpm: Float?,
    val camelot: String?,
)

/** Trzy tryby pracy ekranu „Krok" — zakładki w UI. */
internal enum class GeneratorMode(val displayName: String) {
    STEPWISE("Krok po kroku"),
    PLAN("Plan imprezy"),
    LIVE("Live"),
}

private fun poolColor(pool: ActivePool): Color = when (pool) {
    ActivePool.A -> SpotifyGreen
    ActivePool.B -> SpotifyAmber
}

private fun styleLabel(style: Style): String = when (style) {
    Style.SALSA -> "Salsa"
    Style.BACHATA -> "Bachata"
}

/**
 * Ekran „Krok" (Impreza DJ) na desktopie — port trybu krokowego z aplikacji
 * mobilnej (StepwiseScreen / StepwiseViewModel). Trzy tryby przez zakładki:
 *
 *  • Krok po kroku — [SuggestNextTrackUseCase]: mood buttons → top-K → wybór.
 *  • Plan imprezy  — [PartyPlanner] + [TrackAnalyzer]: czas + łuk → wszystkie bloki.
 *  • Live          — [LiveAssistant]: preset → kolejny blok do sesji.
 *
 * Wszystkie tryby pracują na wspólnej puli (A + opcjonalnie B) i dopisują
 * wynik do `session` — wspólny zapis na Spotify na końcu.
 *
 * Analiza DJ (Plan/Live) wymaga cech audio (styl wykrywany z `genres`), więc
 * bez importu CSV pula jest pusta — pokazujemy komunikat i przycisk importu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyPlannerScreen(client: SpotifyClient) {
    val scope = rememberCoroutineScope()

    // Logika z :shared — bezpośrednie instancje (jak ręczne DI w SpotifyClient).
    val analyzer = remember { TrackAnalyzer() }
    val planner = remember { PartyPlanner() }
    val liveAssistant = remember { LiveAssistant() }
    val suggestUseCase = remember { SuggestNextTrackUseCase(client.featuresRepository) }

    // ── Wspólny stan ────────────────────────────────────────────────────
    var mode by remember { mutableStateOf(GeneratorMode.STEPWISE) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }

    var poolA by remember { mutableStateOf(PoolSlot()) }
    var poolB by remember { mutableStateOf<PoolSlot?>(null) }
    var activePool by remember { mutableStateOf(ActivePool.A) }

    var session by remember { mutableStateOf<List<SessionTrack>>(emptyList()) }

    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var featuresInfo by remember { mutableStateOf<String?>(null) }
    var featuresCount by remember { mutableStateOf(0) }

    // ── Stan trybu Krok po kroku ────────────────────────────────────────
    var currentTarget by remember { mutableStateOf<NextTrackTarget>(NextTrackTarget.Hold) }
    var currentAxis by remember { mutableStateOf(ScoreAxis.DANCE) }
    var candidates by remember { mutableStateOf<List<SuggestNextTrackUseCase.Candidate>>(emptyList()) }
    var resolvedScore by remember { mutableStateOf(0.5f) }
    var resolvedAxis by remember { mutableStateOf(ScoreAxis.DANCE) }
    var computing by remember { mutableStateOf(false) }

    // ── Stan trybu Plan ─────────────────────────────────────────────────
    var durationMin by remember { mutableStateOf(120) }
    var salsaPercent by remember { mutableStateOf(50) }
    var arc by remember { mutableStateOf(EnergyArc.CLASSIC) }
    var blockSize by remember { mutableStateOf(5) }

    // ── Stan trybu Live ─────────────────────────────────────────────────
    var livePreset by remember { mutableStateOf<Preset?>(null) }

    // Cache analizy puli (poolA + poolB) — invalidowany przy zmianie pul.
    var analyzedByStyle by remember { mutableStateOf<Map<Style, List<AnalyzedTrack>>>(emptyMap()) }
    // Cache cech audio dla metadanych w trybie Krok.
    var featuresMap by remember { mutableStateOf<Map<String, TrackAudioFeatures>>(emptyMap()) }

    fun pickedIds(): Set<String> = session.mapNotNull { it.track.id }.toSet()
    fun activeSlot(): PoolSlot = if (activePool == ActivePool.A) poolA else (poolB ?: PoolSlot())

    // ── Ładowanie playlist (z syntetycznym „Polubione") ─────────────────
    LaunchedEffect(Unit) {
        runCatching { client.repository.getUserPlaylists() }
            .onSuccess { fetched ->
                val liked = Playlist(
                    id = LIKED_ID,
                    name = "❤ Polubione utwory",
                    description = null,
                    imageUrl = null,
                    trackCount = 0,
                    ownerId = "",
                )
                playlists = listOf(liked) + fetched
            }
            .onFailure { status = "Nie udało się pobrać playlist: ${it.message}" }
    }

    // ── Operacje pomocnicze ─────────────────────────────────────────────

    suspend fun loadTracks(playlist: Playlist): List<Track> =
        if (playlist.id == LIKED_ID) client.repository.getLikedTracks()
        else client.repository.getPlaylistTracks(playlist.id)

    fun recomputeCandidates() {
        val slot = activeSlot()
        if (slot.playlist == null || slot.tracks.isEmpty()) {
            candidates = emptyList(); computing = false
            return
        }
        computing = true
        scope.launch {
            val lastPicked = session.lastOrNull { it.pool == activePool }?.track
            runCatching {
                suggestUseCase.suggest(
                    pool = slot.tracks,
                    alreadyPickedIds = pickedIds(),
                    lastPickedTrack = lastPicked,
                    target = currentTarget,
                    currentAxis = currentAxis,
                    k = SuggestNextTrackUseCase.DEFAULT_K,
                )
            }.onSuccess { s ->
                candidates = s.candidates
                resolvedScore = s.resolvedTargetScore
                resolvedAxis = s.resolvedAxis
                computing = false
                // Odśwież cache cech dla metadanych UI.
                val ids = slot.tracks.mapNotNull { it.id } + listOfNotNull(lastPicked?.id)
                runCatching { client.featuresRepository.getFeaturesMap(ids.distinct()) }
                    .onSuccess { featuresMap = it }
            }.onFailure {
                computing = false
                status = "Błąd obliczania sugestii: ${it.message}"
            }
        }
    }

    fun selectPool(which: ActivePool, playlist: Playlist) {
        if (which == ActivePool.A) poolA = PoolSlot(playlist = playlist)
        else poolB = PoolSlot(playlist = playlist)
        analyzedByStyle = emptyMap() // invalidate
        scope.launch {
            val tracks = runCatching { loadTracks(playlist) }
                .onFailure { status = "Nie udało się pobrać utworów: ${it.message}" }
                .getOrDefault(emptyList())
            if (which == ActivePool.A) poolA = poolA.copy(tracks = tracks)
            else poolB = (poolB ?: PoolSlot()).copy(tracks = tracks)
            recomputeCandidates()
        }
    }

    /** Lazy analiza puli — wynik cache'owany w [analyzedByStyle]. */
    suspend fun ensureAnalyzedPool(): Map<Style, List<AnalyzedTrack>> {
        if (analyzedByStyle.isNotEmpty()) return analyzedByStyle
        val combined = (poolA.tracks + (poolB?.tracks ?: emptyList())).distinctBy { it.id }
        val ids = combined.mapNotNull { it.id }
        val features = runCatching { client.featuresRepository.getFeaturesMap(ids) }.getOrDefault(emptyMap())
        val result = analyzer.analyzePool(combined, features)
        analyzedByStyle = result
        return result
    }

    fun importCsv() {
        val file = CsvImport.pickFile() ?: return
        busy = true
        scope.launch {
            runCatching {
                val parsed = withContext(Dispatchers.IO) { file.inputStream().use { CsvParser.parse(it) } }
                client.featuresRepository.upsert(parsed.features)
                parsed.features.size
            }.onSuccess {
                busy = false
                featuresCount = it
                featuresInfo = "Zaimportowano $it cech audio"
                analyzedByStyle = emptyMap() // świeże cechy → ponów analizę
                recomputeCandidates()
            }.onFailure { busy = false; featuresInfo = "Błąd importu CSV: ${it.message}" }
        }
    }

    // ── Krok po kroku: pick / undo / clear ──────────────────────────────

    fun pickCandidate(c: SuggestNextTrackUseCase.Candidate) {
        val f = c.track.id?.let { featuresMap[it] }
        session = session + SessionTrack(
            track = c.track,
            pool = activePool,
            score = c.score,
            targetLabel = labelFor(currentTarget),
            bpm = f?.bpm,
            camelot = f?.camelot,
        )
        currentAxis = c.scoreAxis
        currentTarget = NextTrackTarget.Hold
        recomputeCandidates()
    }

    fun undoLast() {
        if (session.isEmpty()) return
        session = session.dropLast(1)
        // Uproszczenie wobec :app — oś resetujemy do domyślnej (SessionTrack
        // na desktopie nie przechowuje osi wyboru).
        currentAxis = ScoreAxis.DANCE
        recomputeCandidates()
    }

    fun clearSession() {
        session = emptyList()
        currentTarget = NextTrackTarget.Hold
        currentAxis = ScoreAxis.DANCE
        activePool = ActivePool.A
        status = null
        recomputeCandidates()
    }

    // ── Plan: generuj wszystkie bloki ───────────────────────────────────

    fun generatePlan() {
        if (poolA.playlist == null) return
        busy = true; status = null
        scope.launch {
            runCatching {
                val analyzed = ensureAnalyzedPool()
                require(analyzed.values.any { it.isNotEmpty() }) {
                    if (featuresCount == 0)
                        "Brak cech audio — zaimportuj CSV (przycisk niżej)."
                    else
                        "Nie wykryto utworów salsa/bachata w tej puli (sprawdź kolumnę Genres w CSV)."
                }
                val partyState = PartyState(
                    mode = PartyMode.PLANNING,
                    playedTrackIds = session.mapNotNull { it.track.id }.toSet(),
                )
                val size = blockSize.coerceIn(3, 10)
                planner.plan(
                    state = partyState,
                    analyzedByStyle = analyzed,
                    durationMs = durationMin * 60_000L,
                    ratio = StyleRatio(salsaPercent),
                    arc = arc,
                    blockSizeByStyle = mapOf(Style.SALSA to size, Style.BACHATA to size),
                )
            }.onSuccess { plan ->
                busy = false
                val added = plan.blocks.flatMapIndexed { blockIdx, block ->
                    block.tracks.map { at ->
                        SessionTrack(
                            track = at.track,
                            pool = if (block.style == Style.SALSA) ActivePool.A else ActivePool.B,
                            score = at.energyScore,
                            targetLabel = "Plan #${blockIdx + 1}: ${block.shape.displayName}",
                            bpm = at.audio.bpm,
                            camelot = at.audio.camelot,
                        )
                    }
                }
                session = session + added
                val totalMin = plan.blocks.sumOf { it.totalDurationMs } / 60_000
                status = "Plan: ${plan.blocks.size} bloków · ${added.size} utworów · $totalMin min" +
                    (plan.errors.firstOrNull()?.let { (idx, e) -> " · blok #$idx pominięty: ${e.message}" } ?: "")
            }.onFailure { busy = false; status = it.message }
        }
    }

    // ── Live: kolejny blok presetem ─────────────────────────────────────

    fun generateLiveBlock(preset: Preset) {
        if (poolA.playlist == null) return
        busy = true; livePreset = preset; status = null
        scope.launch {
            runCatching {
                val analyzed = ensureAnalyzedPool()
                val style = if (activePool == ActivePool.A) Style.SALSA else Style.BACHATA
                val pool = analyzed[style].orEmpty()
                require(pool.isNotEmpty()) {
                    "Pula dla ${styleLabel(style)} pusta (sprawdź wybór playlisty / Genres w CSV)."
                }
                val n = blockSize.coerceIn(1, 10)
                val anchorId = session.lastOrNull { it.pool == activePool }?.track?.id
                val partyState = PartyState(
                    mode = PartyMode.LIVE,
                    playedTrackIds = session.mapNotNull { it.track.id }.toSet(),
                    lastPlayedIdByStyle = if (anchorId != null) mapOf(style to anchorId) else emptyMap(),
                )
                liveAssistant.nextBlock(
                    state = partyState,
                    style = style,
                    analyzedPool = pool,
                    preset = preset,
                    n = n,
                ).getOrThrow() to style
            }.onSuccess { (block, _) ->
                busy = false
                val added = block.tracks.map { at ->
                    SessionTrack(
                        track = at.track,
                        pool = activePool,
                        score = at.energyScore,
                        targetLabel = "Live: ${preset.label} (${block.shape.displayName})",
                        bpm = at.audio.bpm,
                        camelot = at.audio.camelot,
                    )
                }
                session = session + added
                // Auto-switch puli po zbudowaniu bloku (jak w mood-button flow).
                if (poolB != null) activePool = if (activePool == ActivePool.A) ActivePool.B else ActivePool.A
                status = "Dodano blok Live: ${added.size} utworów · ${block.totalDurationMs / 60_000} min"
            }.onFailure { busy = false; status = "Nie udało się zbudować bloku: ${it.message}" }
        }
    }

    // ── Zapis na Spotify ────────────────────────────────────────────────

    fun save() {
        busy = true
        scope.launch {
            runCatching {
                val uris = session.mapNotNull { it.track.uri }
                require(uris.isNotEmpty()) { "Brak utworów z URI do zapisania." }
                val name = when (mode) {
                    GeneratorMode.PLAN -> "Impreza · ${arc.displayName} · salsa $salsaPercent%"
                    GeneratorMode.LIVE -> "Sesja Live DJ"
                    GeneratorMode.STEPWISE -> "Sesja DJ — krok po kroku"
                }
                val id = client.repository.createPlaylist(name, "Spotify Playlist Manager (desktop) — tryb Krok")
                client.repository.addTracksToPlaylist(id, uris)
                uris.size
            }.onSuccess { busy = false; status = "Zapisano playlistę na Spotify ✓ ($it utworów)" }
                .onFailure { busy = false; status = "Błąd zapisu: ${it.message}" }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  UI
    // ════════════════════════════════════════════════════════════════════

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // ── Nagłówek ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Krok — Impreza DJ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    if (session.isNotEmpty()) {
                        Text(
                            "${session.size} utworów w sesji",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { undoLast() }, enabled = session.isNotEmpty() && !busy) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Cofnij")
                }
                IconButton(onClick = { save() }, enabled = session.isNotEmpty() && !busy) {
                    Icon(Icons.Filled.Save, contentDescription = "Zapisz")
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Zakładki trybów ─────────────────────────────────────────
            TabRow(selectedTabIndex = mode.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                GeneratorMode.entries.forEach { m ->
                    Tab(
                        selected = mode == m,
                        onClick = { mode = m },
                        text = { Text(m.displayName) },
                        selectedContentColor = SpotifyGreen,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Treść przewijalna ───────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Wspólny selektor pul (A + opcjonalnie B).
                item {
                    StepwisePoolSelectors(
                        playlists = playlists,
                        poolA = poolA,
                        poolB = poolB,
                        activePool = activePool,
                        onSelectA = { selectPool(ActivePool.A, it) },
                        onSelectB = { selectPool(ActivePool.B, it) },
                        onAddSecond = { if (poolB == null) poolB = PoolSlot() },
                        onRemoveSecond = {
                            poolB = null; activePool = ActivePool.A; analyzedByStyle = emptyMap(); recomputeCandidates()
                        },
                        onSetActive = { activePool = it; recomputeCandidates() },
                    )
                }

                // Import cech audio (wymagane do analizy DJ).
                item {
                    StepwiseSectionCard(title = "Cechy audio (CSV)") {
                        Text(
                            "Analiza DJ (Plan/Live) wymaga cech audio — styl (salsa/bachata) " +
                                "wykrywany jest z kolumny Genres. Bez importu pula jest pusta.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { importCsv() }, enabled = !busy) {
                                Text("Importuj CSV")
                            }
                            featuresInfo?.let {
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Panel zależny od trybu.
                item {
                    when (mode) {
                        GeneratorMode.STEPWISE -> StepwiseStepwisePanel(
                            currentTarget = currentTarget,
                            resolvedScore = resolvedScore,
                            resolvedAxis = resolvedAxis,
                            candidates = candidates,
                            computing = computing,
                            poolSelected = activeSlot().playlist != null,
                            hasContext = session.any { it.pool == activePool },
                            onTarget = { currentTarget = it; recomputeCandidates() },
                            onPick = { pickCandidate(it) },
                        )
                        GeneratorMode.PLAN -> StepwisePlanPanel(
                            durationMin = durationMin,
                            salsaPercent = salsaPercent,
                            arc = arc,
                            blockSize = blockSize,
                            poolReady = poolA.playlist != null,
                            busy = busy,
                            onDuration = { durationMin = it },
                            onSalsa = { salsaPercent = it },
                            onArc = { arc = it },
                            onBlockSize = { blockSize = it },
                            onGenerate = { generatePlan() },
                        )
                        GeneratorMode.LIVE -> StepwiseLivePanel(
                            activePool = activePool,
                            hasPoolB = poolB != null,
                            poolReady = poolA.playlist != null,
                            busy = busy,
                            lastPreset = livePreset,
                            onPreset = { generateLiveBlock(it) },
                        )
                    }
                }

                // Pasek postępu + status.
                if (busy) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SpotifyGreen) }
                status?.let { msg ->
                    item {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (msg.startsWith("Zapisano") || msg.startsWith("Plan") || msg.startsWith("Dodano"))
                                SpotifyGreen else MaterialTheme.colorScheme.error,
                        )
                    }
                }

                // Krzywa energii sesji.
                if (session.size >= 2) {
                    item {
                        StepwiseSectionCard(title = "Krzywa energii sesji") {
                            StepwiseSessionEnergyChart(
                                scores = session.map { it.score },
                                pools = session.map { it.pool == ActivePool.A },
                                modifier = Modifier.fillMaxWidth().height(80.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StepwiseLegendDot(SpotifyGreen, "Pula A (salsa)")
                                StepwiseLegendDot(SpotifyAmber, "Pula B (bachata)")
                            }
                        }
                    }
                }

                // Sesja (lista) + akcje.
                item {
                    StepwiseSessionSection(
                        tracks = session,
                        onUndo = { undoLast() },
                        onClear = { clearSession() },
                        onSave = { save() },
                        saveEnabled = session.isNotEmpty() && !busy,
                    )
                }
            }
        }
    }
}

private fun labelFor(target: NextTrackTarget): String = when (target) {
    NextTrackTarget.Peak -> "Peak"
    NextTrackTarget.Warmup -> "Podgrzej"
    NextTrackTarget.Hold -> "Trzymaj"
    NextTrackTarget.Chill -> "Schłódź"
    NextTrackTarget.Cooldown -> "Cooldown"
    NextTrackTarget.SwitchAxis -> "Zmiana klimatu"
    is NextTrackTarget.Absolute -> "Ręczny cel"
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: selektory pul (wspólne dla wszystkich trybów)
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepwisePoolSelectors(
    playlists: List<Playlist>,
    poolA: PoolSlot,
    poolB: PoolSlot?,
    activePool: ActivePool,
    onSelectA: (Playlist) -> Unit,
    onSelectB: (Playlist) -> Unit,
    onAddSecond: () -> Unit,
    onRemoveSecond: () -> Unit,
    onSetActive: (ActivePool) -> Unit,
) {
    StepwiseSectionCard(title = "Pule") {
        StepwisePoolRow(
            label = "Pula A (salsa)",
            slot = poolA,
            playlists = playlists,
            isActive = activePool == ActivePool.A,
            canActivate = poolB != null && poolA.playlist != null,
            onSelect = onSelectA,
            onActivate = { onSetActive(ActivePool.A) },
            onRemove = null,
        )
        if (poolB != null) {
            StepwisePoolRow(
                label = "Pula B (bachata)",
                slot = poolB,
                playlists = playlists,
                isActive = activePool == ActivePool.B,
                canActivate = poolA.playlist != null && poolB.playlist != null,
                onSelect = onSelectB,
                onActivate = { onSetActive(ActivePool.B) },
                onRemove = onRemoveSecond,
            )
        } else {
            OutlinedButton(onClick = onAddSecond, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Dodaj drugą pulę (tandy salsa+bachata)")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepwisePoolRow(
    label: String,
    slot: PoolSlot,
    playlists: List<Playlist>,
    isActive: Boolean,
    canActivate: Boolean,
    onSelect: (Playlist) -> Unit,
    onActivate: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepwisePlaylistDropdown(
            label = label,
            selected = slot.playlist,
            available = playlists,
            onSelect = onSelect,
            modifier = Modifier.weight(1f),
        )
        if (canActivate) {
            FilterChip(
                selected = isActive,
                onClick = onActivate,
                label = { Text(if (isActive) "Aktywna" else "Aktywuj") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpotifyGreen.copy(alpha = 0.25f),
                    selectedLabelColor = SpotifyGreen,
                ),
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Usuń pulę")
            }
        }
    }
    if (slot.playlist != null) {
        Text(
            "${slot.tracks.size} utworów",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepwisePlaylistDropdown(
    label: String,
    selected: Playlist?,
    available: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.name ?: "Wybierz…",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { playlist ->
                DropdownMenuItem(
                    text = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(playlist); expanded = false },
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Panel: Krok po kroku (mood buttons + kandydaci)
// ══════════════════════════════════════════════════════════════════════

private data class MoodButtonDef(
    val target: NextTrackTarget,
    val emoji: String,
    val label: String,
    val requiresContext: Boolean,
)

private val MOOD_BUTTONS = listOf(
    MoodButtonDef(NextTrackTarget.Peak, "🔥", "Peak", false),
    MoodButtonDef(NextTrackTarget.Warmup, "⬆", "Podgrzej", true),
    MoodButtonDef(NextTrackTarget.Hold, "➡", "Trzymaj", true),
    MoodButtonDef(NextTrackTarget.Chill, "⬇", "Schłódź", true),
    MoodButtonDef(NextTrackTarget.Cooldown, "🌙", "Cooldown", false),
    MoodButtonDef(NextTrackTarget.SwitchAxis, "🎭", "Zmień klimat", true),
)

@Composable
private fun StepwiseStepwisePanel(
    currentTarget: NextTrackTarget,
    resolvedScore: Float,
    resolvedAxis: ScoreAxis,
    candidates: List<SuggestNextTrackUseCase.Candidate>,
    computing: Boolean,
    poolSelected: Boolean,
    hasContext: Boolean,
    onTarget: (NextTrackTarget) -> Unit,
    onPick: (SuggestNextTrackUseCase.Candidate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Resolved target badge.
        StepwiseSectionCard(title = "Dokąd dalej?") {
            Surface(
                color = SpotifyGreen.copy(alpha = 0.12f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    "Cel: ${resolvedAxis.name} ${"%.2f".format(resolvedScore)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SpotifyGreen,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            // Siatka mood buttonów 3x2.
            MOOD_BUTTONS.chunked(3).forEach { rowDefs ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowDefs.forEach { def ->
                        val enabled = !def.requiresContext || hasContext
                        StepwiseMoodButton(
                            emoji = def.emoji,
                            label = def.label,
                            selected = def.target == currentTarget,
                            enabled = enabled,
                            onClick = { onTarget(def.target) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - rowDefs.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        // Kandydaci.
        StepwiseSectionCard(title = "Sugestie (top ${SuggestNextTrackUseCase.DEFAULT_K})") {
            when {
                !poolSelected -> Text(
                    "Wybierz pulę powyżej, żeby zobaczyć sugestie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                computing -> StepwiseLoadingRow("Liczenie kandydatów…")
                candidates.isEmpty() -> Text(
                    "Brak kandydatów — pula wyczerpana albo bez cech audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    candidates.forEachIndexed { idx, c ->
                        StepwiseCandidateRow(rank = idx + 1, candidate = c, onPick = { onPick(c) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StepwiseMoodButton(
    emoji: String,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        selected -> SpotifyGreen.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(1.5.dp, SpotifyGreen) else null,
        modifier = modifier.height(60.dp).let { if (enabled) it.clickable(onClick = onClick) else it },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(emoji, fontSize = 18.sp)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun StepwiseCandidateRow(
    rank: Int,
    candidate: SuggestNextTrackUseCase.Candidate,
    onPick: () -> Unit,
) {
    val compatChip = when {
        candidate.harmonicCompat >= 0.85f -> "✅"
        candidate.harmonicCompat >= 0.5f -> "⚠"
        else -> "❌"
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Surface(color = SpotifyGreen.copy(alpha = 0.15f), shape = CircleShape, modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$rank", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SpotifyGreen)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(candidate.track.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("score %.2f".format(candidate.score), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!candidate.bpmDelta.isNaN()) {
                        Spacer(Modifier.width(6.dp))
                        val delta = candidate.bpmDelta.toInt()
                        Text("${if (delta > 0) "+" else ""}$delta BPM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(compatChip, fontSize = 12.sp)
                }
            }
            Icon(Icons.Filled.MusicNote, contentDescription = "Dodaj", tint = SpotifyGreen, modifier = Modifier.size(20.dp))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Panel: Plan imprezy
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepwisePlanPanel(
    durationMin: Int,
    salsaPercent: Int,
    arc: EnergyArc,
    blockSize: Int,
    poolReady: Boolean,
    busy: Boolean,
    onDuration: (Int) -> Unit,
    onSalsa: (Int) -> Unit,
    onArc: (EnergyArc) -> Unit,
    onBlockSize: (Int) -> Unit,
    onGenerate: () -> Unit,
) {
    StepwiseSectionCard(title = "Plan imprezy") {
        Text("Czas trwania: ${durationMin / 60}h ${durationMin % 60}min", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = durationMin.toFloat(),
            onValueChange = { onDuration(it.toInt()) },
            valueRange = 30f..360f,
            steps = 32,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
        )

        Text("Salsa $salsaPercent% · Bachata ${100 - salsaPercent}%", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = salsaPercent.toFloat(),
            onValueChange = { onSalsa(it.toInt()) },
            valueRange = 0f..100f,
            steps = 19,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
        )

        Text("Rozmiar bloku (tandy): $blockSize utw.", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = blockSize.toFloat(),
            onValueChange = { onBlockSize(it.toInt()) },
            valueRange = 3f..10f,
            steps = 6,
            colors = SliderDefaults.colors(thumbColor = SpotifyGreen, activeTrackColor = SpotifyGreen),
        )

        var arcExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = arcExpanded, onExpandedChange = { arcExpanded = it }) {
            OutlinedTextField(
                value = arc.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Łuk energii") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = arcExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            )
            ExposedDropdownMenu(expanded = arcExpanded, onDismissRequest = { arcExpanded = false }) {
                EnergyArc.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.displayName) }, onClick = { onArc(option); arcExpanded = false })
                }
            }
        }

        Button(
            onClick = onGenerate,
            enabled = poolReady && !busy,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Generuję plan…")
            } else {
                Text("Wygeneruj plan")
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Panel: Live
// ══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StepwiseLivePanel(
    activePool: ActivePool,
    hasPoolB: Boolean,
    poolReady: Boolean,
    busy: Boolean,
    lastPreset: Preset?,
    onPreset: (Preset) -> Unit,
) {
    StepwiseSectionCard(title = "Live — kolejny blok") {
        val hint = if (hasPoolB) {
            "Aktywna pula: ${if (activePool == ActivePool.A) "A (salsa)" else "B (bachata)"} — kliknij preset, " +
                "blok pójdzie do sesji, pula się przełączy."
        } else {
            "Tylko pula A — wszystkie bloki z tego stylu (dodaj pulę B, by przeplatać)."
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Preset.entries.forEach { preset ->
                FilterChip(
                    selected = lastPreset == preset,
                    onClick = { onPreset(preset) },
                    label = { Text(preset.label, fontSize = 12.sp) },
                    enabled = poolReady && !busy,
                )
            }
        }
        if (busy) StepwiseLoadingRow("Buduję blok…")
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Sekcja: sesja (lista zbudowanych utworów)
// ══════════════════════════════════════════════════════════════════════

@Composable
private fun StepwiseSessionSection(
    tracks: List<SessionTrack>,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
) {
    StepwiseSectionCard(title = if (tracks.isEmpty()) "Sesja (pusta)" else "Sesja · ${tracks.size} utworów") {
        if (tracks.isEmpty()) {
            Text(
                "Wybierz pulę i zacznij budować — przyciskiem nastrojowym, planem albo presetem Live.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@StepwiseSectionCard
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(tracks) { index, st ->
                StepwiseSessionRow(number = index + 1, sessionTrack = st)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cofnij ostatni")
            }
            TextButton(onClick = onClear) {
                Icon(Icons.Filled.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Wyczyść sesję")
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Zapisz na Spotify")
            }
        }
    }
}

@Composable
private fun StepwiseSessionRow(number: Int, sessionTrack: SessionTrack) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "$number.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(sessionTrack.track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sessionTrack.track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                sessionTrack.bpm?.let {
                    Spacer(Modifier.width(6.dp))
                    StepwiseMiniBadge("${it.toInt()} BPM")
                }
                sessionTrack.camelot?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.width(4.dp))
                    StepwiseMiniBadge(it)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Surface(color = poolColor(sessionTrack.pool).copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
            Text(
                sessionTrack.pool.name,
                style = MaterialTheme.typography.labelSmall,
                color = poolColor(sessionTrack.pool),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
//  Wspólne helpery UI
// ══════════════════════════════════════════════════════════════════════

@Composable
internal fun StepwiseSectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            content()
        }
    }
}

@Composable
internal fun StepwiseLoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = SpotifyGreen)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun StepwiseMiniBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 10.sp,
        )
    }
}

@Composable
internal fun StepwiseLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}
