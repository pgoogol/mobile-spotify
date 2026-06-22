package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import com.spotify.playlistmanager.desktop.theme.ErrorRed
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
import com.spotify.playlistmanager.domain.model.CompositeScoreCalculator
import com.spotify.playlistmanager.domain.model.NextTrackTarget
import com.spotify.playlistmanager.domain.model.ScoreAxis
import com.spotify.playlistmanager.domain.usecase.SuggestNextTrackUseCase
import kotlin.math.abs
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
    /** Cel energii użyty przy wyborze — potrzebny do SWAP (Absolute target). */
    val targetScore: Float = score,
    /** Oś, na której utwór był wybrany — potrzebna do SWAP. */
    val axis: ScoreAxis = ScoreAxis.DANCE,
    /**
     * True gdy utwór jest „kotwicą" — załadowany z istniejącej playlisty
     * (tryb APPEND). Kotwice są blokowane przed usunięciem przez Undo i nie
     * są zapisywane ponownie do Spotify (bo już tam są).
     */
    val isAnchor: Boolean = false,
)

/**
 * Struktura tandy: ile utworów z puli A, potem ile z B (auto-switch).
 * Gdy null — ręczne przełączanie (user klika „Aktywuj").
 */
internal data class TandaStructure(val countA: Int, val countB: Int) {
    val totalPerTanda: Int get() = countA + countB
}

/** Licznik postępu w bieżącym bloku tandy. */
internal data class TandaCounter(
    val progressInCurrentBlock: Int = 0,
    /** Numer tandy (1-indexed). */
    val tandaNumber: Int = 1,
)

/** Konfiguracja trybu APPEND — docelowa playlista do dopisania utworów. */
internal data class AppendMode(
    val playlistId: String,
    val playlistName: String,
    val originalTrackCount: Int,
)

/**
 * Snapshot stanu przed auto-fill tandy — do cofnięcia całej grupy.
 * Gdy non-null, UI pokazuje baner potwierdzenia „auto-wypełniono tandę".
 */
internal data class AutoFillSnapshot(
    val preSession: List<SessionTrack>,
    val preCounter: TandaCounter,
    val preActivePool: ActivePool,
    val addedCount: Int,
)

/** Stan dialogu „dodaj utwór z dowolnej playlisty" (duplikaty dozwolone). */
internal data class ManualTrackPicker(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
)

/** Stan podglądu szczegółów utworu (dialog). Features doładowywane async. */
internal data class TrackDetailState(
    val track: Track,
    val features: TrackAudioFeatures? = null,
)

/**
 * Stan wymiany pojedynczego utworu w sesji (SWAP).
 * Idle → (Loading | Picking | Error). Loading/Picking trzymają indeks w `session`.
 */
internal sealed interface SwapState {
    data object Idle : SwapState
    data class Loading(val sessionIndex: Int) : SwapState
    data class Picking(
        val sessionIndex: Int,
        val original: SessionTrack,
        val candidates: List<SuggestNextTrackUseCase.Candidate>,
    ) : SwapState
    data class Error(val message: String) : SwapState
}

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

    // ── Stan zaawansowany (parytet z mobile StepwiseScreen) ─────────────
    var appendMode by remember { mutableStateOf<AppendMode?>(null) }
    var loadingAnchors by remember { mutableStateOf(false) }
    var anchorsDirty by remember { mutableStateOf(false) } // podmieniono kotwicę → nadpisz playlistę
    var tandaStructure by remember { mutableStateOf<TandaStructure?>(null) }
    var tandaCounter by remember { mutableStateOf(TandaCounter()) }
    var autoFillSnapshot by remember { mutableStateOf<AutoFillSnapshot?>(null) }
    var autoFilling by remember { mutableStateOf(false) }
    var weights by remember { mutableStateOf(SuggestNextTrackUseCase.Weights.DEFAULT) }
    var manualPicker by remember { mutableStateOf<ManualTrackPicker?>(null) }
    var trackDetail by remember { mutableStateOf<TrackDetailState?>(null) }
    var swapState by remember { mutableStateOf<SwapState>(SwapState.Idle) }

    // Cache analizy puli (poolA + poolB) — invalidowany przy zmianie pul.
    var analyzedByStyle by remember { mutableStateOf<Map<Style, List<AnalyzedTrack>>>(emptyMap()) }
    // Cache cech audio dla metadanych w trybie Krok.
    var featuresMap by remember { mutableStateOf<Map<String, TrackAudioFeatures>>(emptyMap()) }

    fun pickedIds(): Set<String> = session.mapNotNull { it.track.id }.toSet()
    fun activeSlot(): PoolSlot = if (activePool == ActivePool.A) poolA else (poolB ?: PoolSlot())
    fun hasPoolB(): Boolean = poolB != null
    fun newSession(): List<SessionTrack> = session.filter { !it.isAnchor }
    fun canSave(): Boolean = (newSession().isNotEmpty() || (appendMode != null && anchorsDirty)) && !busy
    fun tandaLimit(pool: ActivePool): Int =
        tandaStructure?.let { if (pool == ActivePool.A) it.countA else it.countB } ?: 0
    fun remainingInBlock(): Int =
        tandaStructure?.let { (tandaLimit(activePool) - tandaCounter.progressInCurrentBlock).coerceAtLeast(0) } ?: 0
    fun canAutoFill(): Boolean =
        tandaStructure != null && remainingInBlock() > 0 && candidates.isNotEmpty() &&
            autoFillSnapshot == null && !autoFilling

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
                    weights = weights,
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
            targetScore = resolvedScore,
            axis = c.scoreAxis,
        )
        currentAxis = c.scoreAxis
        currentTarget = NextTrackTarget.Hold

        // TANDA auto-switch — gdy zapełniono blok aktywnej puli, przełącz pulę.
        val structure = tandaStructure
        if (structure != null && poolB != null) {
            val advanced = tandaCounter.copy(progressInCurrentBlock = tandaCounter.progressInCurrentBlock + 1)
            val limit = if (activePool == ActivePool.A) structure.countA else structure.countB
            if (advanced.progressInCurrentBlock >= limit) {
                val next = if (activePool == ActivePool.A) ActivePool.B else ActivePool.A
                activePool = next
                tandaCounter = TandaCounter(
                    progressInCurrentBlock = 0,
                    tandaNumber = if (next == ActivePool.A) advanced.tandaNumber + 1 else advanced.tandaNumber,
                )
            } else {
                tandaCounter = advanced
            }
        } else if (structure != null) {
            tandaCounter = tandaCounter.copy(progressInCurrentBlock = tandaCounter.progressInCurrentBlock + 1)
        }
        recomputeCandidates()
    }

    fun undoLast() {
        // W trybie APPEND nie cofamy kotwic — usuwamy ostatni nie-kotwicowy.
        val lastIdx = session.indexOfLast { !it.isAnchor }
        if (lastIdx < 0) return
        session = session.toMutableList().apply { removeAt(lastIdx) }
        currentAxis = session.lastOrNull()?.axis ?: ScoreAxis.DANCE
        // Uproszczenie wobec :app: licznik resetujemy do 0 w bieżącym bloku.
        tandaCounter = TandaCounter(progressInCurrentBlock = 0, tandaNumber = tandaCounter.tandaNumber)
        recomputeCandidates()
    }

    fun clearSession() {
        // W trybie APPEND zachowujemy kotwice.
        session = session.filter { it.isAnchor }
        currentTarget = NextTrackTarget.Hold
        currentAxis = session.lastOrNull()?.axis ?: ScoreAxis.DANCE
        activePool = ActivePool.A
        tandaCounter = TandaCounter()
        autoFillSnapshot = null
        status = null
        recomputeCandidates()
    }

    // ── TANDA: auto-fill reszty bloku (baner „auto-wypełniono tandę") ────

    fun autoFillBlock() {
        if (!canAutoFill()) return
        val snapshot = AutoFillSnapshot(
            preSession = session,
            preCounter = tandaCounter,
            preActivePool = activePool,
            addedCount = 0,
        )
        autoFilling = true
        scope.launch {
            val remaining = remainingInBlock()
            var added = 0
            repeat(remaining) {
                val slot = activeSlot()
                if (slot.playlist == null || slot.tracks.isEmpty()) return@repeat
                val lastPicked = session.lastOrNull { it.pool == activePool }?.track
                val suggestion = runCatching {
                    suggestUseCase.suggest(
                        pool = slot.tracks,
                        alreadyPickedIds = pickedIds(),
                        lastPickedTrack = lastPicked,
                        target = currentTarget,
                        currentAxis = currentAxis,
                        k = 1,
                        weights = weights,
                    )
                }.getOrNull() ?: return@repeat
                val top = suggestion.candidates.firstOrNull() ?: return@repeat
                val f = top.track.id?.let { featuresMap[it] }
                session = session + SessionTrack(
                    track = top.track,
                    pool = activePool,
                    score = top.score,
                    targetLabel = "${labelFor(currentTarget)} (auto)",
                    bpm = f?.bpm,
                    camelot = f?.camelot,
                    targetScore = suggestion.resolvedTargetScore,
                    axis = top.scoreAxis,
                )
                currentAxis = top.scoreAxis
                tandaCounter = tandaCounter.copy(progressInCurrentBlock = tandaCounter.progressInCurrentBlock + 1)
                added++
            }
            if (added == 0) { autoFilling = false; return@launch }

            // Po zapełnieniu — przełącz pulę gdy osiągnięto limit.
            val limit = tandaLimit(activePool)
            val shouldSwitch = tandaCounter.progressInCurrentBlock >= limit && poolB != null
            if (shouldSwitch) {
                val next = if (activePool == ActivePool.A) ActivePool.B else ActivePool.A
                tandaCounter = TandaCounter(
                    progressInCurrentBlock = 0,
                    tandaNumber = if (next == ActivePool.A) tandaCounter.tandaNumber + 1 else tandaCounter.tandaNumber,
                )
                activePool = next
            }
            currentTarget = NextTrackTarget.Hold
            autoFillSnapshot = snapshot.copy(addedCount = added)
            autoFilling = false
            recomputeCandidates()
        }
    }

    fun acceptAutoFill() { autoFillSnapshot = null }

    fun undoAutoFill() {
        val snap = autoFillSnapshot ?: return
        session = snap.preSession
        tandaCounter = snap.preCounter
        activePool = snap.preActivePool
        currentAxis = session.lastOrNull()?.axis ?: ScoreAxis.DANCE
        autoFillSnapshot = null
        recomputeCandidates()
    }

    fun setTandaStructure(structure: TandaStructure?) {
        val oldWasNull = tandaStructure == null
        val newIsNull = structure == null
        tandaStructure = structure
        tandaCounter = if (oldWasNull != newIsNull) {
            TandaCounter()
        } else if (structure != null) {
            val limit = if (activePool == ActivePool.A) structure.countA else structure.countB
            tandaCounter.copy(progressInCurrentBlock = tandaCounter.progressInCurrentBlock.coerceAtMost(limit))
        } else {
            tandaCounter
        }
    }

    // ── APPEND: kontynuacja istniejącej playlisty (kotwice) ─────────────

    fun enableAppendMode(playlist: Playlist) {
        if (playlist.id == LIKED_ID) {
            status = "Nie można dopisywać do Polubionych — wybierz zwykłą playlistę."
            return
        }
        loadingAnchors = true
        anchorsDirty = false
        autoFillSnapshot = null
        session = emptyList()
        tandaCounter = TandaCounter()
        scope.launch {
            val tracks = runCatching { client.repository.getPlaylistTracks(playlist.id) }
                .onFailure { loadingAnchors = false; status = "Nie udało się pobrać utworów playlisty: ${it.message}" }
                .getOrNull() ?: return@launch
            val ids = tracks.mapNotNull { it.id }
            val features = runCatching { client.featuresRepository.getFeaturesMap(ids) }.getOrDefault(emptyMap())
            session = tracks.map { track ->
                val f = track.id?.let { features[it] }
                val score = f?.let { CompositeScoreCalculator.calculate(it, ScoreAxis.DANCE) } ?: 0f
                SessionTrack(
                    track = track,
                    pool = ActivePool.A,
                    score = score,
                    targetLabel = "Kotwica",
                    bpm = f?.bpm,
                    camelot = f?.camelot,
                    targetScore = score,
                    axis = ScoreAxis.DANCE,
                    isAnchor = true,
                )
            }
            appendMode = AppendMode(playlist.id, playlist.name, tracks.size)
            currentAxis = session.lastOrNull()?.axis ?: ScoreAxis.DANCE
            loadingAnchors = false
            recomputeCandidates()
        }
    }

    fun disableAppendMode() {
        appendMode = null
        anchorsDirty = false
        autoFillSnapshot = null
        session = session.filter { !it.isAnchor }
        tandaCounter = TandaCounter()
        recomputeCandidates()
    }

    // ── MANUAL PICKER: dodaj utwór z DOWOLNEJ playlisty (duplikaty OK) ──

    fun openManualPicker() { manualPicker = ManualTrackPicker() }
    fun closeManualPicker() { manualPicker = null }

    fun selectManualPickerPlaylist(playlist: Playlist) {
        manualPicker = ManualTrackPicker(playlist = playlist, isLoading = true)
        scope.launch {
            val tracks = runCatching { loadTracks(playlist) }
                .onFailure { manualPicker = null; status = "Nie udało się pobrać utworów: ${it.message}" }
                .getOrNull() ?: return@launch
            manualPicker = ManualTrackPicker(playlist = playlist, tracks = tracks, isLoading = false)
        }
    }

    fun pickTrackFromAnyPlaylist(track: Track) {
        scope.launch {
            val f = track.id?.let { id ->
                featuresMap[id] ?: runCatching { client.featuresRepository.getFeaturesMap(listOf(id)) }
                    .getOrNull()?.get(id)
            }
            val score = f?.let { CompositeScoreCalculator.calculate(it, currentAxis) } ?: 0f
            session = session + SessionTrack(
                track = track,
                pool = activePool,
                score = score,
                targetLabel = "Z innej playlisty",
                bpm = f?.bpm,
                camelot = f?.camelot,
                targetScore = score,
                axis = currentAxis,
            )
            manualPicker = null
        }
    }

    // ── SWAP: podmiana wybranego utworu na innego kandydata z puli ──────

    suspend fun findSwapCandidates(sessionIndex: Int, k: Int): Result<List<SuggestNextTrackUseCase.Candidate>> {
        val original = session.getOrNull(sessionIndex)
            ?: return Result.failure(IllegalStateException("Utwór poza zakresem"))
        val poolSlot = if (original.pool == ActivePool.A) poolA else poolB
        val pool = poolSlot?.tracks.orEmpty()
        if (pool.isEmpty()) {
            return Result.failure(IllegalStateException("Pula ${original.pool.name} jest pusta — wybierz playlistę źródłową"))
        }
        val previous = session.getOrNull(sessionIndex - 1)?.track
        val excludeIds = session.mapNotNull { it.track.id }.toSet()
        return runCatching {
            suggestUseCase.suggest(
                pool = pool,
                alreadyPickedIds = excludeIds,
                lastPickedTrack = previous,
                target = NextTrackTarget.Absolute(original.targetScore, original.axis),
                currentAxis = original.axis,
                k = k,
                weights = weights,
            ).candidates
        }
    }

    suspend fun applySwap(sessionIndex: Int, candidate: SuggestNextTrackUseCase.Candidate) {
        val f = candidate.track.id?.let { id ->
            featuresMap[id] ?: runCatching { client.featuresRepository.getFeaturesMap(listOf(id)) }
                .getOrNull()?.get(id)
        }
        val list = session.toMutableList()
        val original = list.getOrNull(sessionIndex) ?: return
        list[sessionIndex] = original.copy(
            track = candidate.track,
            score = candidate.score,
            axis = candidate.scoreAxis,
            bpm = f?.bpm,
            camelot = f?.camelot,
            targetLabel = if (original.isAnchor) "Kotwica (wymieniona)" else "${original.targetLabel} → wymieniony",
        )
        session = list
        if (original.isAnchor) anchorsDirty = true
    }

    fun swapAuto(sessionIndex: Int) {
        if (session.getOrNull(sessionIndex) == null) return
        swapState = SwapState.Loading(sessionIndex)
        scope.launch {
            val candidates = findSwapCandidates(sessionIndex, k = 1).getOrElse {
                swapState = SwapState.Error(it.message ?: "Błąd wyszukiwania zamiennika"); return@launch
            }
            val best = candidates.firstOrNull()
            if (best == null) {
                swapState = SwapState.Error("Brak innych utworów w puli do wymiany"); return@launch
            }
            applySwap(sessionIndex, best)
            swapState = SwapState.Idle
            status = "Wymieniono na „${best.track.title}”"
        }
    }

    fun swapPick(sessionIndex: Int) {
        val original = session.getOrNull(sessionIndex) ?: return
        swapState = SwapState.Loading(sessionIndex)
        scope.launch {
            val candidates = findSwapCandidates(sessionIndex, k = 12).getOrElse {
                swapState = SwapState.Error(it.message ?: "Błąd wyszukiwania zamiennika"); return@launch
            }
            if (candidates.isEmpty()) {
                swapState = SwapState.Error("Brak innych utworów w puli do wymiany"); return@launch
            }
            swapState = SwapState.Picking(sessionIndex, original, candidates)
        }
    }

    fun confirmSwap(candidate: SuggestNextTrackUseCase.Candidate) {
        val picking = swapState as? SwapState.Picking ?: return
        scope.launch {
            applySwap(picking.sessionIndex, candidate)
            swapState = SwapState.Idle
            status = "Wymieniono na „${candidate.track.title}”"
        }
    }

    fun cancelSwap() { swapState = SwapState.Idle }

    // ── TRACK DETAIL: podgląd szczegółów (z doładowaniem cech) ──────────

    fun showTrackDetail(track: Track) {
        val cached = track.id?.let { featuresMap[it] }
        trackDetail = TrackDetailState(track, cached)
        if (cached != null || track.id == null) return
        val id = track.id
        scope.launch {
            val fetched = runCatching { client.featuresRepository.getFeaturesMap(listOf(id)) }
                .getOrNull()?.get(id)
            val current = trackDetail
            if (current != null && current.track.id == id) trackDetail = current.copy(features = fetched)
        }
    }

    fun closeTrackDetail() { trackDetail = null }

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
        if (!canSave()) return
        busy = true
        scope.launch {
            val append = appendMode
            // Podmieniono kotwicę → nadpisz całą playlistę (kotwice + nowe).
            val rewriteWhole = append != null && anchorsDirty
            val newUris = newSession().mapNotNull { it.track.uri }
            val urisToSend = if (rewriteWhole) session.mapNotNull { it.track.uri } else newUris
            runCatching {
                require(urisToSend.isNotEmpty()) { "Brak utworów z URI do zapisania." }
                when {
                    append != null && rewriteWhole -> {
                        client.repository.replacePlaylistTracks(append.playlistId, urisToSend)
                        "Zaktualizowano playlistę „${append.playlistName}” ✓ (${urisToSend.size} utworów)"
                    }
                    append != null -> {
                        client.repository.addTracksToPlaylist(append.playlistId, urisToSend)
                        "Dopisano do „${append.playlistName}” ✓ (${urisToSend.size} utworów)"
                    }
                    else -> {
                        val name = when (mode) {
                            GeneratorMode.PLAN -> "Impreza · ${arc.displayName} · salsa $salsaPercent%"
                            GeneratorMode.LIVE -> "Sesja Live DJ"
                            GeneratorMode.STEPWISE -> "Sesja DJ — krok po kroku"
                        }
                        val id = client.repository.createPlaylist(name, "Spotify Playlist Manager (desktop) — tryb Krok")
                        client.repository.addTracksToPlaylist(id, urisToSend)
                        "Zapisano playlistę na Spotify ✓ (${urisToSend.size} utworów)"
                    }
                }
            }.onSuccess { msg ->
                busy = false
                status = msg
                // Po zapisie append: nowe tracki stają się kotwicami, dirty czyścimy.
                if (append != null) {
                    val newTotal = if (rewriteWhole) urisToSend.size else append.originalTrackCount + urisToSend.size
                    session = session.map { if (it.isAnchor) it else it.copy(isAnchor = true) }
                    appendMode = append.copy(originalTrackCount = newTotal)
                    anchorsDirty = false
                }
            }.onFailure { busy = false; status = "Błąd zapisu: ${it.message}" }
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
                        val subtitle = appendMode?.let { "${newSession().size} nowych → ${it.playlistName}" }
                            ?: "${session.size} utworów w sesji"
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { undoLast() }, enabled = session.any { !it.isAnchor } && !busy) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Cofnij")
                }
                IconButton(onClick = { save() }, enabled = canSave()) {
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
                // Tryb: nowa playlista / dokończ istniejącą (APPEND).
                item {
                    StepwiseAppendModeSection(
                        playlists = playlists,
                        appendMode = appendMode,
                        loadingAnchors = loadingAnchors,
                        onEnable = { enableAppendMode(it) },
                        onDisable = { disableAppendMode() },
                    )
                }

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
                            poolB = null; activePool = ActivePool.A; tandaStructure = null
                            tandaCounter = TandaCounter(); analyzedByStyle = emptyMap(); recomputeCandidates()
                        },
                        onSetActive = { activePool = it; recomputeCandidates() },
                    )
                }

                // Struktura tandy (auto-switch) — tylko gdy są dwie pule.
                if (hasPoolB()) {
                    item {
                        StepwiseTandaStructureSection(
                            current = tandaStructure,
                            counter = tandaCounter,
                            activePool = activePool,
                            onSetStructure = { setTandaStructure(it) },
                        )
                    }
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
                            axisOfLast = currentAxis,
                            candidates = candidates,
                            computing = computing,
                            poolSelected = activeSlot().playlist != null,
                            hasContext = session.any { it.pool == activePool },
                            sessionHasContext = session.isNotEmpty(),
                            canAutoFill = canAutoFill(),
                            remainingInBlock = remainingInBlock(),
                            autoFilling = autoFilling,
                            weights = weights,
                            onTarget = { currentTarget = it; recomputeCandidates() },
                            onPick = { pickCandidate(it) },
                            onAutoFill = { autoFillBlock() },
                            onShowDetail = { showTrackDetail(it) },
                            onUpdateWeight = { upd -> weights = upd(weights); recomputeCandidates() },
                            onResetWeights = { weights = SuggestNextTrackUseCase.Weights.DEFAULT; recomputeCandidates() },
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
                        val ok = listOf("Zapisano", "Plan", "Dodano", "Dopisano", "Zaktualizowano", "Wymieniono")
                            .any { msg.startsWith(it) }
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (ok) SpotifyGreen else ErrorRed,
                        )
                    }
                }

                // Baner „auto-wypełniono tandę" (po auto-fill).
                autoFillSnapshot?.let { snap ->
                    item {
                        StepwiseAutoFillBanner(
                            snapshot = snap,
                            onAccept = { acceptAutoFill() },
                            onUndo = { undoAutoFill() },
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
                        swapEnabled = mode == GeneratorMode.LIVE || mode == GeneratorMode.STEPWISE,
                        swapState = swapState,
                        onUndo = { undoLast() },
                        onClear = { clearSession() },
                        onSave = { save() },
                        onAddFromAnyPlaylist = { openManualPicker() },
                        onShowDetail = { showTrackDetail(it) },
                        onSwapAuto = { swapAuto(it) },
                        onSwapPick = { swapPick(it) },
                        saveEnabled = canSave(),
                    )
                }
            }
        }
    }

    // ── Dialog: szczegóły utworu ────────────────────────────────────────
    trackDetail?.let { detail ->
        StepwiseTrackDetailDialog(
            track = detail.track,
            features = detail.features,
            onDismiss = { closeTrackDetail() },
        )
    }

    // ── Dialog: ręczny picker zamiennika (SWAP z listy) ─────────────────
    (swapState as? SwapState.Picking)?.let { picking ->
        StepwiseSwapPickerDialog(
            picking = picking,
            onPick = { confirmSwap(it) },
            onShowDetail = { showTrackDetail(it) },
            onDismiss = { cancelSwap() },
        )
    }

    // ── Dialog: ręczny picker z dowolnej playlisty (duplikaty OK) ───────
    manualPicker?.let { picker ->
        if (picker.playlist == null) {
            StepwiseManualPickPlaylistDialog(
                playlists = playlists,
                onSelect = { selectManualPickerPlaylist(it) },
                onDismiss = { closeManualPicker() },
            )
        } else {
            StepwiseManualPickTrackDialog(
                playlistName = picker.playlist.name,
                tracks = picker.tracks,
                isLoading = picker.isLoading,
                onPick = { pickTrackFromAnyPlaylist(it) },
                onBack = { openManualPicker() },
                onDismiss = { closeManualPicker() },
            )
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
    axisOfLast: ScoreAxis,
    candidates: List<SuggestNextTrackUseCase.Candidate>,
    computing: Boolean,
    poolSelected: Boolean,
    hasContext: Boolean,
    sessionHasContext: Boolean,
    canAutoFill: Boolean,
    remainingInBlock: Int,
    autoFilling: Boolean,
    weights: SuggestNextTrackUseCase.Weights,
    onTarget: (NextTrackTarget) -> Unit,
    onPick: (SuggestNextTrackUseCase.Candidate) -> Unit,
    onAutoFill: () -> Unit,
    onShowDetail: (Track) -> Unit,
    onUpdateWeight: ((SuggestNextTrackUseCase.Weights) -> SuggestNextTrackUseCase.Weights) -> Unit,
    onResetWeights: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Resolved target badge (z informacją o zmianie osi vs ostatni).
        StepwiseSectionCard(title = "Dokąd dalej?") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text("Cel: ${resolvedAxis.name} ${"%.2f".format(resolvedScore)}", style = MaterialTheme.typography.labelSmall)
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = SpotifyGreen.copy(alpha = 0.12f),
                        disabledLabelColor = SpotifyGreen,
                    ),
                )
                if (sessionHasContext && resolvedAxis != axisOfLast) {
                    Text("(zmiana osi vs ostatni)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                        StepwiseCandidateRow(
                            rank = idx + 1,
                            candidate = c,
                            onPick = { onPick(c) },
                            onShowDetail = { onShowDetail(c.track) },
                        )
                    }
                }
            }

            // Auto-wypełnij tandę (gdy struktura ustawiona i jest miejsce w bloku).
            if (canAutoFill || autoFilling) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAutoFill,
                    enabled = canAutoFill,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen.copy(alpha = 0.85f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (autoFilling) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Uzupełniam…")
                    } else {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Uzupełnij tandę ($remainingInBlock)")
                    }
                }
            }
        }

        // Zaawansowane — dostrojenie wag algorytmu.
        StepwiseAdvancedWeightsSection(weights = weights, onUpdateWeight = onUpdateWeight, onReset = onResetWeights)
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
    onShowDetail: () -> Unit,
) {
    val compatChip = when {
        candidate.harmonicCompat >= 0.85f -> "✅"
        candidate.harmonicCompat >= 0.5f -> "⚠"
        else -> "❌"
    }
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onPick).padding(10.dp),
            ) {
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
                IconButton(onClick = onShowDetail, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = "Szczegóły utworu", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Zwiń szczegóły" else "Dlaczego ten utwór?",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.MusicNote, contentDescription = "Dodaj", tint = SpotifyGreen, modifier = Modifier.size(20.dp))
            }
            if (expanded) StepwiseCandidateExplainRow(candidate)
        }
    }
}

@Composable
private fun StepwiseCandidateExplainRow(candidate: SuggestNextTrackUseCase.Candidate) {
    val bpmJumpNorm = if (!candidate.bpmDelta.isNaN()) {
        (abs(candidate.bpmDelta) / SuggestNextTrackUseCase.BPM_JUMP_NORMALIZER).coerceIn(0f, 1f)
    } else 0f
    val fitCost = SuggestNextTrackUseCase.W_FIT * candidate.fitDistance
    val harmonicCost = SuggestNextTrackUseCase.W_HARMONIC * (1f - candidate.harmonicCompat)
    val bpmCost = SuggestNextTrackUseCase.W_BPM_JUMP * bpmJumpNorm
    Column(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Dlaczego ten utwór?", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StepwiseExplainLine("Dopasowanie do celu", "odległość %.2f od targetu".format(candidate.fitDistance), fitCost, SuggestNextTrackUseCase.W_FIT)
        StepwiseExplainLine("Harmonia (Camelot)", "kompatybilność %.2f".format(candidate.harmonicCompat), harmonicCost, SuggestNextTrackUseCase.W_HARMONIC)
        StepwiseExplainLine(
            "Skok BPM",
            if (candidate.bpmDelta.isNaN()) "brak kontekstu" else "Δ %+d BPM".format(candidate.bpmDelta.toInt()),
            bpmCost,
            SuggestNextTrackUseCase.W_BPM_JUMP,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 2.dp))
        Row {
            Text("Koszt łączny", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("%.3f".format(candidate.totalCost), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SpotifyGreen)
        }
    }
}

@Composable
private fun StepwiseExplainLine(label: String, detail: String, cost: Float, weight: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
        Text("waga %.1f".format(weight), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Spacer(Modifier.width(8.dp))
        Text("+%.3f".format(cost), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = if (cost > 0.3f) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant)
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun StepwiseSessionSection(
    tracks: List<SessionTrack>,
    swapEnabled: Boolean,
    swapState: SwapState,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onAddFromAnyPlaylist: () -> Unit,
    onShowDetail: (Track) -> Unit,
    onSwapAuto: (Int) -> Unit,
    onSwapPick: (Int) -> Unit,
    saveEnabled: Boolean,
) {
    val anchorCount = tracks.count { it.isAnchor }
    val newCount = tracks.size - anchorCount
    val title = when {
        tracks.isEmpty() -> "Sesja (pusta)"
        anchorCount > 0 -> "Sesja · $newCount nowych + $anchorCount kotwic"
        else -> "Sesja · $newCount utworów"
    }
    StepwiseSectionCard(title = title) {
        if (tracks.isEmpty()) {
            Text(
                "Wybierz pulę i zacznij budować — przyciskiem nastrojowym, planem albo presetem Live.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onAddFromAnyPlaylist) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Dodaj utwór z playlisty")
            }
            return@StepwiseSectionCard
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(tracks) { index, st ->
                val newNumber = if (st.isAnchor) 0 else tracks.take(index + 1).count { !it.isAnchor }
                StepwiseSessionRow(
                    number = newNumber,
                    sessionTrack = st,
                    swapEnabled = swapEnabled && !st.isAnchor,
                    isSwapping = (swapState as? SwapState.Loading)?.sessionIndex == index,
                    onClick = { onShowDetail(st.track) },
                    onSwapAuto = { onSwapAuto(index) },
                    onSwapPick = { onSwapPick(index) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
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
            TextButton(onClick = onAddFromAnyPlaylist) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Z playlisty")
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepwiseSessionRow(
    number: Int,
    sessionTrack: SessionTrack,
    swapEnabled: Boolean,
    isSwapping: Boolean,
    onClick: () -> Unit,
    onSwapAuto: () -> Unit,
    onSwapPick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .let {
                if (sessionTrack.isAnchor) it.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                else it
            }
            .clickable(onClick = onClick),
    ) {
        if (sessionTrack.isAnchor) {
            Text("📌", fontSize = 12.sp, modifier = Modifier.width(28.dp))
        } else {
            Text("$number.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                sessionTrack.track.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (sessionTrack.isAnchor) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
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
        if (!sessionTrack.isAnchor) {
            Surface(color = poolColor(sessionTrack.pool).copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                Text(
                    sessionTrack.pool.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = poolColor(sessionTrack.pool),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (swapEnabled) {
            Spacer(Modifier.width(2.dp))
            if (isSwapping) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = SpotifyGreen, modifier = Modifier.padding(9.dp).size(18.dp))
            } else {
                Box(
                    modifier = Modifier.size(36.dp).combinedClickable(onClick = onSwapAuto, onLongClick = onSwapPick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = "Wymień utwór (przytrzymaj, by wybrać z listy)",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
