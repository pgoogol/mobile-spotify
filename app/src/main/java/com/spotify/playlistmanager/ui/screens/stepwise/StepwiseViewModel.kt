package com.spotify.playlistmanager.ui.screens.stepwise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.NextTrackTarget
import com.spotify.playlistmanager.domain.model.ScoreAxis
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase
import com.spotify.playlistmanager.domain.usecase.SuggestNextTrackUseCase
import com.spotify.playlistmanager.util.StepwisePreferencesStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ══════════════════════════════════════════════════════════════════════
//  Modele stanu
// ══════════════════════════════════════════════════════════════════════

/**
 * Slot puli — playlista źródłowa + jej utwory.
 * Utwory są ładowane raz po wyborze i cache'owane w slocie.
 */
data class PoolSlot(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val featuresLoaded: Boolean = false
)

/** Która pula jest aktywna (z której algorytm sugeruje). */
enum class ActivePool { A, B }

/**
 * Struktura tandy: ile utworów z puli A, potem ile z B.
 * Gdy null, aplikacja nie przełącza sama (user klika manual toggle).
 */
data class TandaStructure(val countA: Int, val countB: Int) {
    val totalPerTanda: Int get() = countA + countB
    companion object {
        val TWO_THREE = TandaStructure(2, 3)
        val THREE_THREE = TandaStructure(3, 3)
        val THREE_FOUR = TandaStructure(3, 4)
    }
}

/** Licznik postępu w bieżącym bloku tandy. */
data class TandaCounter(
    val progressInCurrentBlock: Int = 0,
    /** Numer tandy (1-indexed). */
    val tandaNumber: Int = 1
)

/** Jeden utwór w sesji — wraz z metadanymi wyboru. */
data class SessionTrack(
    val track: Track,
    val pool: ActivePool,
    val score: Float,
    val targetScore: Float,
    val axis: ScoreAxis,
    val targetLabel: String,
    val bpm: Float?,
    val camelot: String?,
    /**
     * True gdy utwór jest „kotwicą" — załadowany z istniejącej playlisty
     * (append mode). Kotwice są blokowane przed usunięciem przez Undo i nie
     * są zapisywane ponownie do Spotify (bo już tam są).
     */
    val isAnchor: Boolean = false
)

/**
 * Konfiguracja trybu append — docelowa playlista do której dopisujemy utwory.
 * null = tryb „nowa playlista".
 */
data class AppendMode(
    val playlistId: String,
    val playlistName: String,
    val originalTrackCount: Int
)

/**
 * Snapshot stanu przed auto-fill — używany do undo całej grupy.
 * Gdy non-null, UI pokazuje modal potwierdzenia auto-fill.
 */
data class AutoFillSnapshot(
    val preSessionTracks: List<SessionTrack>,
    val preCounter: TandaCounter,
    val preActivePool: ActivePool,
    val preAxis: ScoreAxis,
    val preTarget: NextTrackTarget,
    val addedCount: Int
)

/** Stan zapisu playlisty do Spotify. */
sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data class Success(val playlistUrl: String, val trackCount: Int) : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * Stan pełnego ekranu „Krok po kroku".
 */
data class StepwiseUiState(
    // ── Listy / source'y ────────────────────────────────────
    val availablePlaylists: List<Playlist> = emptyList(),
    val isLoadingPlaylists: Boolean = true,

    val poolA: PoolSlot = PoolSlot(),
    val poolB: PoolSlot? = null,

    // ── Sesja ───────────────────────────────────────────────
    val sessionTracks: List<SessionTrack> = emptyList(),
    val activePool: ActivePool = ActivePool.A,
    val tandaStructure: TandaStructure? = null,
    val tandaCounter: TandaCounter = TandaCounter(),

    // ── Kandydaci / target ──────────────────────────────────
    val currentTarget: NextTrackTarget = NextTrackTarget.Hold,
    val currentAxis: ScoreAxis = ScoreAxis.DANCE,
    val resolvedTargetScore: Float = 0.5f,
    val resolvedAxis: ScoreAxis = ScoreAxis.DANCE,
    val candidates: List<SuggestNextTrackUseCase.Candidate> = emptyList(),
    val isComputingCandidates: Boolean = false,

    // ── Zapis ───────────────────────────────────────────────
    val newPlaylistName: String = "Sesja DJ",
    /**
     * Opis playlisty — user może wpisać własny, ale data generowania
     * zostanie zawsze dopisana przy save (linia "Wygenerowane: YYYY-MM-DD").
     * Pusty = zostanie użyty pełny szablon auto.
     */
    val newPlaylistDescription: String = "",
    val saveState: SaveState = SaveState.Idle,

    // ── Wagi algorytmu (advanced) ──────────────────────────
    val weights: SuggestNextTrackUseCase.Weights = SuggestNextTrackUseCase.Weights.DEFAULT,

    // ── Auto-fill tandy ───────────────────────────────────
    val autoFillSnapshot: AutoFillSnapshot? = null,
    val isAutoFilling: Boolean = false,

    // ── Append mode (kontynuacja istniejącej playlisty) ───
    /** null = tryb „nowa playlista"; non-null = dopisujemy do istniejącej. */
    val appendMode: AppendMode? = null,
    val isLoadingAppendAnchors: Boolean = false,

    // ── Błędy ───────────────────────────────────────────────
    val error: String? = null
) {
    val activePoolSlot: PoolSlot
        get() = if (activePool == ActivePool.A) poolA else (poolB ?: PoolSlot())

    /** Zestaw ID już wybranych — do dedup. */
    val pickedTrackIds: Set<String>
        get() = sessionTracks.mapNotNull { it.track.id }.toSet()

    /** Ile utworów z danej puli jest w aktualnym bloku tandy. */
    fun countInCurrentBlock(pool: ActivePool): Int =
        tandaCounter.progressInCurrentBlock.takeIf { activePool == pool } ?: 0

    /** Utwory dodane w tej sesji (bez kotwic z istniejącej playlisty). */
    val newSessionTracks: List<SessionTrack>
        get() = sessionTracks.filter { !it.isAnchor }

    val canSave: Boolean
        get() = newSessionTracks.isNotEmpty() && saveState !is SaveState.Saving

    val hasPoolB: Boolean
        get() = poolB != null

    /** Ile jeszcze brakuje do końca bieżącego bloku tandy (0 = brak struktury lub pełny). */
    val remainingInBlock: Int
        get() {
            val structure = tandaStructure ?: return 0
            val limit = if (activePool == ActivePool.A) structure.countA else structure.countB
            return (limit - tandaCounter.progressInCurrentBlock).coerceAtLeast(0)
        }

    val canAutoFill: Boolean
        get() = tandaStructure != null &&
                remainingInBlock > 0 &&
                candidates.isNotEmpty() &&
                autoFillSnapshot == null &&
                !isAutoFilling
}

// ══════════════════════════════════════════════════════════════════════
//  ViewModel
// ══════════════════════════════════════════════════════════════════════

@HiltViewModel
class StepwiseViewModel @Inject constructor(
    private val spotifyRepository: ISpotifyRepository,
    private val featuresRepository: ITrackFeaturesRepository,
    private val suggestUseCase: SuggestNextTrackUseCase,
    private val preferencesStore: StepwisePreferencesStore
) : ViewModel() {

    private val _state = MutableStateFlow(StepwiseUiState())
    val state: StateFlow<StepwiseUiState> = _state.asStateFlow()

    init {
        loadAvailablePlaylists()
        observeWeights()
    }

    private fun observeWeights() {
        viewModelScope.launch {
            preferencesStore.weightsFlow.collect { weights ->
                _state.update { it.copy(weights = weights) }
                recomputeCandidates()
            }
        }
    }

    fun onUpdateWeight(update: (SuggestNextTrackUseCase.Weights) -> SuggestNextTrackUseCase.Weights) {
        viewModelScope.launch {
            val current = _state.value.weights
            preferencesStore.setWeights(update(current))
            // weightsFlow zaemituje i wywoła recomputeCandidates
        }
    }

    fun onResetWeights() {
        viewModelScope.launch { preferencesStore.resetToDefaults() }
    }

    // ── Ładowanie playlist ──────────────────────────────────────────────

    private fun loadAvailablePlaylists() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPlaylists = true, error = null) }
            runCatching { spotifyRepository.getUserPlaylists() }
                .onSuccess { playlists ->
                    val liked = Playlist(
                        id = GeneratePlaylistUseCase.LIKED_SONGS_ID,
                        name = "\u2764 Polubione utwory",
                        description = null,
                        imageUrl = null,
                        trackCount = 0,
                        ownerId = ""
                    )
                    _state.update {
                        it.copy(
                            availablePlaylists = listOf(liked) + playlists,
                            isLoadingPlaylists = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoadingPlaylists = false,
                            error = e.message ?: "Nie udało się pobrać playlist"
                        )
                    }
                }
        }
    }

    // ── Append mode — dopisywanie do istniejącej playlisty ─────────────

    /**
     * Włącza tryb append: ładuje utwory docelowej playlisty jako „kotwice"
     * i ustawia pula A na tę samą playlistę (żeby algorytm nie duplikował).
     * User wciąż może zmienić pule ręcznie — kotwice zostają.
     */
    fun onEnableAppendMode(playlist: Playlist) {
        if (playlist.id == GeneratePlaylistUseCase.LIKED_SONGS_ID) {
            _state.update {
                it.copy(error = "Nie można dopisywać do Polubionych utworów — wybierz zwykłą playlistę")
            }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoadingAppendAnchors = true,
                    error = null,
                    sessionTracks = emptyList(),
                    tandaCounter = TandaCounter(),
                    autoFillSnapshot = null
                )
            }
            val tracks = runCatching {
                spotifyRepository.getPlaylistTracks(playlist.id)
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isLoadingAppendAnchors = false,
                        error = "Nie udało się pobrać utworów playlisty: ${e.message}"
                    )
                }
            }.getOrNull() ?: return@launch

            val ids = tracks.mapNotNull { it.id }
            val features = runCatching {
                featuresRepository.getFeaturesMap(ids)
            }.getOrDefault(emptyMap())

            val anchors = tracks.map { track ->
                val f = track.id?.let { features[it] }
                val score = f?.let {
                    com.spotify.playlistmanager.domain.model.CompositeScoreCalculator
                        .calculate(it, ScoreAxis.DANCE)
                } ?: 0f
                SessionTrack(
                    track = track,
                    pool = ActivePool.A,
                    score = score,
                    targetScore = score,
                    axis = ScoreAxis.DANCE,
                    targetLabel = "Kotwica",
                    bpm = f?.bpm,
                    camelot = f?.camelot,
                    isAnchor = true
                )
            }

            _state.update { current ->
                current.copy(
                    appendMode = AppendMode(
                        playlistId = playlist.id,
                        playlistName = playlist.name,
                        originalTrackCount = tracks.size
                    ),
                    sessionTracks = anchors,
                    poolA = PoolSlot(playlist = playlist, tracks = tracks, featuresLoaded = true),
                    isLoadingAppendAnchors = false,
                    currentAxis = anchors.lastOrNull()?.axis ?: ScoreAxis.DANCE
                )
            }

            // Features dla poolA już pobrane
            recomputeCandidates()
        }
    }

    fun onDisableAppendMode() {
        _state.update { current ->
            current.copy(
                appendMode = null,
                sessionTracks = current.sessionTracks.filter { !it.isAnchor },
                tandaCounter = TandaCounter(),
                autoFillSnapshot = null
            )
        }
        recomputeCandidates()
    }

    // ── Wybór pul ───────────────────────────────────────────────────────

    fun onSelectPoolA(playlist: Playlist) {
        _state.update { it.copy(poolA = PoolSlot(playlist = playlist)) }
        loadPoolTracks(ActivePool.A, playlist.id)
    }

    fun onSelectPoolB(playlist: Playlist) {
        _state.update { it.copy(poolB = PoolSlot(playlist = playlist)) }
        loadPoolTracks(ActivePool.B, playlist.id)
    }

    fun onAddSecondPool() {
        if (_state.value.poolB == null) {
            _state.update { it.copy(poolB = PoolSlot()) }
        }
    }

    fun onRemoveSecondPool() {
        _state.update {
            it.copy(
                poolB = null,
                activePool = ActivePool.A,
                tandaStructure = null,
                tandaCounter = TandaCounter()
            )
        }
        recomputeCandidates()
    }

    private fun loadPoolTracks(which: ActivePool, playlistId: String) {
        viewModelScope.launch {
            val tracks = runCatching {
                if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID) {
                    spotifyRepository.getLikedTracks()
                } else {
                    spotifyRepository.getPlaylistTracks(playlistId)
                }
            }.onFailure { e ->
                _state.update { it.copy(error = "Nie udało się pobrać utworów: ${e.message}") }
            }.getOrDefault(emptyList())

            _state.update { current ->
                when (which) {
                    ActivePool.A -> current.copy(
                        poolA = current.poolA.copy(tracks = tracks, featuresLoaded = true)
                    )
                    ActivePool.B -> current.copy(
                        poolB = (current.poolB ?: PoolSlot()).copy(
                            tracks = tracks, featuresLoaded = true
                        )
                    )
                }
            }

            // Prefetch features do cache
            val ids = tracks.mapNotNull { it.id }
            if (ids.isNotEmpty()) {
                runCatching { featuresRepository.getFeaturesMap(ids) }
            }

            recomputeCandidates()
        }
    }

    // ── Pula aktywna / Tanda struktura ──────────────────────────────────

    fun onSetActivePool(pool: ActivePool) {
        val s = _state.value
        if (pool == ActivePool.B && s.poolB == null) return
        if (pool == s.activePool) return
        _state.update { it.copy(activePool = pool) }
        recomputeCandidates()
    }

    fun onSetTandaStructure(structure: TandaStructure?) {
        _state.update { current ->
            val oldWasNull = current.tandaStructure == null
            val newIsNull = structure == null
            val shouldReset = oldWasNull != newIsNull
            val newCounter = if (shouldReset) {
                TandaCounter()
            } else if (structure != null) {
                val limit = if (current.activePool == ActivePool.A) structure.countA
                else structure.countB
                current.tandaCounter.copy(
                    progressInCurrentBlock = current.tandaCounter.progressInCurrentBlock
                        .coerceAtMost(limit)
                )
            } else current.tandaCounter
            current.copy(
                tandaStructure = structure,
                tandaCounter = newCounter
            )
        }
    }

    // ── Target nastrojowy ───────────────────────────────────────────────

    fun onTargetClick(target: NextTrackTarget) {
        _state.update { it.copy(currentTarget = target) }
        recomputeCandidates()
    }

    // ── Wybór kandydata → append do sesji ───────────────────────────────

    fun onPickCandidate(candidate: SuggestNextTrackUseCase.Candidate) {
        val featuresMap = lastKnownFeaturesMap
        val pickedFeatures: TrackAudioFeatures? = candidate.track.id?.let { featuresMap[it] }

        _state.update { current ->
            val label = labelFor(current.currentTarget)
            val session = current.sessionTracks + SessionTrack(
                track = candidate.track,
                pool = current.activePool,
                score = candidate.score,
                targetScore = current.resolvedTargetScore,
                axis = candidate.scoreAxis,
                targetLabel = label,
                bpm = pickedFeatures?.bpm,
                camelot = pickedFeatures?.camelot
            )

            val newCounter = advanceCounter(current)
            val newActive = autoSwitchPool(current, newCounter)

            current.copy(
                sessionTracks = session,
                currentAxis = candidate.scoreAxis,
                activePool = newActive,
                tandaCounter = if (newActive != current.activePool) {
                    TandaCounter(
                        progressInCurrentBlock = 0,
                        tandaNumber = if (newActive == ActivePool.A && newCounter.progressInCurrentBlock == 0)
                            newCounter.tandaNumber + 1
                        else newCounter.tandaNumber
                    )
                } else newCounter,
                currentTarget = NextTrackTarget.Hold  // reset do bezpiecznego domyślnego
            )
        }

        recomputeCandidates()
    }

    // ── Auto-fill reszty bloku tandy ────────────────────────────────────

    /**
     * Dobiera automatycznie pozostałe utwory w bieżącym bloku tandy.
     * Używa bieżącego [StepwiseUiState.currentTarget] dla każdego kroku.
     * Po zakończeniu state wystawia [AutoFillSnapshot] — UI pokazuje
     * modal potwierdzenia, user akceptuje albo cofa całą grupę.
     */
    fun onAutoFillBlock() {
        val start = _state.value
        if (!start.canAutoFill) return

        val snapshot = AutoFillSnapshot(
            preSessionTracks = start.sessionTracks,
            preCounter = start.tandaCounter,
            preActivePool = start.activePool,
            preAxis = start.currentAxis,
            preTarget = start.currentTarget,
            addedCount = 0
        )

        viewModelScope.launch {
            _state.update { it.copy(isAutoFilling = true) }

            val remaining = start.remainingInBlock
            var added = 0

            // Loop: dobierz top-1 dla bieżącego targetu, ograniczając do bieżącej puli
            repeat(remaining) {
                val s = _state.value
                val slot = s.activePoolSlot
                if (slot.playlist == null || slot.tracks.isEmpty()) return@repeat

                val lastPicked = s.sessionTracks.lastOrNull { it.pool == s.activePool }?.track

                val suggestion = runCatching {
                    suggestUseCase.suggest(
                        pool = slot.tracks,
                        alreadyPickedIds = s.pickedTrackIds,
                        lastPickedTrack = lastPicked,
                        target = s.currentTarget,
                        currentAxis = s.currentAxis,
                        k = 1,
                        weights = s.weights
                    )
                }.getOrNull() ?: return@repeat

                val top = suggestion.candidates.firstOrNull() ?: return@repeat

                // Append — tylko w obrębie bieżącej puli, nie przełączamy auto w tej pętli,
                // żeby uzupełnić WYŁĄCZNIE bieżący blok.
                val pickedFeatures = top.track.id?.let { lastKnownFeaturesMap[it] }
                _state.update { current ->
                    val label = labelFor(current.currentTarget)
                    val session = current.sessionTracks + SessionTrack(
                        track = top.track,
                        pool = current.activePool,
                        score = top.score,
                        targetScore = suggestion.resolvedTargetScore,
                        axis = top.scoreAxis,
                        targetLabel = "$label (auto)",
                        bpm = pickedFeatures?.bpm,
                        camelot = pickedFeatures?.camelot
                    )
                    val newCounter = current.tandaCounter.copy(
                        progressInCurrentBlock = current.tandaCounter.progressInCurrentBlock + 1
                    )
                    current.copy(
                        sessionTracks = session,
                        currentAxis = top.scoreAxis,
                        tandaCounter = newCounter
                    )
                }
                added++
            }

            if (added == 0) {
                _state.update { it.copy(isAutoFilling = false) }
                return@launch
            }

            // Po zakończeniu bloku — sprawdź czy osiągnęliśmy limit i przełącz pulę
            val afterFill = _state.value
            val limit = afterFill.tandaStructure?.let {
                if (afterFill.activePool == ActivePool.A) it.countA else it.countB
            } ?: 0
            val shouldSwitch = afterFill.tandaCounter.progressInCurrentBlock >= limit &&
                    afterFill.poolB != null
            val newActive = if (shouldSwitch) {
                if (afterFill.activePool == ActivePool.A) ActivePool.B else ActivePool.A
            } else afterFill.activePool
            val newCounter = if (shouldSwitch) {
                TandaCounter(
                    progressInCurrentBlock = 0,
                    tandaNumber = if (newActive == ActivePool.A)
                        afterFill.tandaCounter.tandaNumber + 1
                    else afterFill.tandaCounter.tandaNumber
                )
            } else afterFill.tandaCounter

            _state.update { current ->
                current.copy(
                    activePool = newActive,
                    tandaCounter = newCounter,
                    autoFillSnapshot = snapshot.copy(addedCount = added),
                    isAutoFilling = false,
                    currentTarget = NextTrackTarget.Hold
                )
            }

            recomputeCandidates()
        }
    }

    fun onAcceptAutoFill() {
        _state.update { it.copy(autoFillSnapshot = null) }
    }

    fun onUndoAutoFill() {
        val snapshot = _state.value.autoFillSnapshot ?: return
        _state.update { current ->
            current.copy(
                sessionTracks = snapshot.preSessionTracks,
                tandaCounter = snapshot.preCounter,
                activePool = snapshot.preActivePool,
                currentAxis = snapshot.preAxis,
                currentTarget = snapshot.preTarget,
                autoFillSnapshot = null
            )
        }
        recomputeCandidates()
    }

    /** Pomocnicza mapa features — odświeżana przy recompute, trzymana dla UI metadanych. */
    private var lastKnownFeaturesMap: Map<String, TrackAudioFeatures> = emptyMap()

    // ── Advance counter + auto switch ───────────────────────────────────

    private fun advanceCounter(state: StepwiseUiState): TandaCounter {
        val structure = state.tandaStructure ?: return state.tandaCounter
        return state.tandaCounter.copy(
            progressInCurrentBlock = state.tandaCounter.progressInCurrentBlock + 1
        )
    }

    /**
     * Po dodaniu utworu — czy przełączyć pulę?
     * Tylko gdy jest ustawiona struktura tandy, pula B istnieje,
     * i licznik osiągnął limit bieżącego bloku.
     */
    private fun autoSwitchPool(state: StepwiseUiState, newCounter: TandaCounter): ActivePool {
        val structure = state.tandaStructure ?: return state.activePool
        if (state.poolB == null) return state.activePool

        val limit = when (state.activePool) {
            ActivePool.A -> structure.countA
            ActivePool.B -> structure.countB
        }

        return if (newCounter.progressInCurrentBlock >= limit) {
            if (state.activePool == ActivePool.A) ActivePool.B else ActivePool.A
        } else {
            state.activePool
        }
    }

    // ── Undo / Remove ──────────────────────────────────────────────────

    fun onUndoLast() {
        _state.update { current ->
            val list = current.sessionTracks
            if (list.isEmpty()) return@update current
            // W trybie append — kotwic nie cofamy
            val lastIdx = list.indexOfLast { !it.isAnchor }
            if (lastIdx < 0) return@update current
            val without = list.toMutableList().apply { removeAt(lastIdx) }
            val lastAxis = without.lastOrNull()?.axis ?: ScoreAxis.DANCE

            // Rekonstrukcja licznika — uproszczenie: przy undo resetujemy licznik do 0
            // w bieżącym bloku, zostawiając numer tandy. Nie jest idealnie dokładne
            // dla zagnieżdżonych undo, ale działa dla codziennego użycia.
            current.copy(
                sessionTracks = without,
                currentAxis = lastAxis,
                tandaCounter = TandaCounter(
                    progressInCurrentBlock = 0,
                    tandaNumber = current.tandaCounter.tandaNumber
                )
            )
        }
        recomputeCandidates()
    }

    fun onClearSession() {
        _state.update { current ->
            // W trybie append zachowujemy kotwice
            val preserved = current.sessionTracks.filter { it.isAnchor }
            current.copy(
                sessionTracks = preserved,
                currentAxis = preserved.lastOrNull()?.axis ?: ScoreAxis.DANCE,
                currentTarget = NextTrackTarget.Hold,
                tandaCounter = TandaCounter(),
                activePool = ActivePool.A,
                saveState = SaveState.Idle
            )
        }
        recomputeCandidates()
    }

    // ── Ręczne dodanie utworu (pin — bypasses dedup warning) ────────────

    fun onPickManually(track: Track) {
        _state.update { current ->
            val features = lastKnownFeaturesMap[track.id]
            val score = features?.let {
                com.spotify.playlistmanager.domain.model.CompositeScoreCalculator
                    .calculate(it, current.currentAxis)
            } ?: 0f

            current.copy(
                sessionTracks = current.sessionTracks + SessionTrack(
                    track = track,
                    pool = current.activePool,
                    score = score,
                    targetScore = score,
                    axis = current.currentAxis,
                    targetLabel = "Ręcznie",
                    bpm = features?.bpm,
                    camelot = features?.camelot
                )
            )
        }
        recomputeCandidates()
    }

    // ── Obliczanie kandydatów ───────────────────────────────────────────

    private fun recomputeCandidates() {
        viewModelScope.launch {
            val s = _state.value
            val slot = s.activePoolSlot
            if (slot.playlist == null || slot.tracks.isEmpty()) {
                _state.update {
                    it.copy(
                        candidates = emptyList(),
                        isComputingCandidates = false
                    )
                }
                return@launch
            }

            _state.update { it.copy(isComputingCandidates = true) }

            // Ostatni wybór TEJ SAMEJ puli — dla kontekstu harmonii/BPM w obrębie tandy
            val lastPicked = s.sessionTracks.lastOrNull { it.pool == s.activePool }?.track

            val suggestion = runCatching {
                suggestUseCase.suggest(
                    pool = slot.tracks,
                    alreadyPickedIds = s.pickedTrackIds,
                    lastPickedTrack = lastPicked,
                    target = s.currentTarget,
                    currentAxis = s.currentAxis,
                    k = SuggestNextTrackUseCase.DEFAULT_K,
                    weights = s.weights
                )
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        error = "Błąd obliczania sugestii: ${e.message}",
                        isComputingCandidates = false
                    )
                }
            }.getOrNull() ?: return@launch

            // Zaktualizuj cache features dla UI
            val allIds = slot.tracks.mapNotNull { it.id } +
                    listOfNotNull(lastPicked?.id)
            runCatching {
                featuresRepository.getFeaturesMap(allIds.distinct())
            }.onSuccess { lastKnownFeaturesMap = it }

            _state.update {
                it.copy(
                    candidates = suggestion.candidates,
                    resolvedTargetScore = suggestion.resolvedTargetScore,
                    resolvedAxis = suggestion.resolvedAxis,
                    isComputingCandidates = false
                )
            }
        }
    }

    // ── Zapis do Spotify ────────────────────────────────────────────────

    fun onPlaylistNameChange(name: String) {
        _state.update { it.copy(newPlaylistName = name) }
    }

    fun onPlaylistDescriptionChange(description: String) {
        _state.update { it.copy(newPlaylistDescription = description) }
    }

    fun onSaveAsNewPlaylist() {
        val state = _state.value
        if (!state.canSave) return
        // Append mode — tylko nowe (kotwice już są na playliście)
        val tracksToSave = state.newSessionTracks
        val uris = tracksToSave.mapNotNull { it.track.uri }
        if (uris.isEmpty()) {
            _state.update {
                it.copy(saveState = SaveState.Error("Żaden utwór nie ma URI Spotify"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saveState = SaveState.Saving) }
            runCatching {
                val append = state.appendMode
                if (append != null) {
                    // Dopisz do istniejącej playlisty
                    spotifyRepository.addTracksToPlaylist(append.playlistId, uris)
                    append.playlistId
                } else {
                    // Utwórz nową playlistę
                    val finalDescription = buildFinalDescription(state)
                    val playlistId = spotifyRepository.createPlaylist(
                        name = state.newPlaylistName.ifBlank { "Sesja DJ" },
                        description = finalDescription
                    )
                    spotifyRepository.addTracksToPlaylist(playlistId, uris)
                    playlistId
                }
            }.onSuccess { playlistId ->
                val url = "https://open.spotify.com/playlist/$playlistId"
                _state.update {
                    it.copy(saveState = SaveState.Success(url, uris.size))
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(saveState = SaveState.Error(e.message ?: "Błąd zapisu"))
                }
            }
        }
    }

    /**
     * Buduje finalny opis playlisty. Gwarantuje obecność członu
     * "Wygenerowane: YYYY-MM-DD" — nawet gdy user nadpisał opis.
     *
     * UWAGA: Spotify API zwraca 400 Bad Request gdy opis zawiera znaki
     * nowej linii (`\n`, `\r`). Dlatego łączymy wszystkie fragmenty
     * separatorem ` · ` i strippujemy newlines z opisu usera.
     *
     * Dodatkowo: Spotify limituje opis do 300 znaków — tniemy na zapas.
     */
    private fun buildFinalDescription(state: StepwiseUiState): String {
        val today = java.time.LocalDate.now().toString() // ISO 2026-04-21
        val userDesc = state.newPlaylistDescription
            .trim()
            .replace(Regex("[\\r\\n]+"), " ")
        val dateFragment = "Wygenerowane: $today"
        val hasDateMarker = userDesc.contains("Wygenerowane:")

        val full = if (userDesc.isEmpty()) {
            val parts = mutableListOf(dateFragment)
            state.tandaStructure?.let {
                parts += "Struktura: ${it.countA}×A / ${it.countB}×B"
            }
            parts += "Utwory: ${state.sessionTracks.size}"
            parts += "Tryb: Krok po kroku"
            parts.joinToString(" · ")
        } else if (!hasDateMarker) {
            "$dateFragment · $userDesc"
        } else {
            userDesc
        }

        return full.take(SPOTIFY_DESCRIPTION_LIMIT)
    }

    private companion object {
        const val SPOTIFY_DESCRIPTION_LIMIT = 300
    }

    fun onSaveStateConsumed() {
        _state.update { it.copy(saveState = SaveState.Idle) }
    }

    // ── Błędy ───────────────────────────────────────────────────────────

    fun onClearError() {
        _state.update { it.copy(error = null) }
    }

    // ── Pomocnicze ──────────────────────────────────────────────────────

    private fun labelFor(target: NextTrackTarget): String = when (target) {
        NextTrackTarget.Peak -> "Peak"
        NextTrackTarget.Warmup -> "Podgrzej"
        NextTrackTarget.Hold -> "Trzymaj"
        NextTrackTarget.Chill -> "Schłódź"
        NextTrackTarget.Cooldown -> "Cooldown"
        NextTrackTarget.SwitchAxis -> "Zmiana klimatu"
        is NextTrackTarget.Absolute -> "Ręczny cel"
    }
}
