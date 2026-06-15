package com.spotify.playlistmanager.ui.screens.stepwise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.dj.LiveAssistant
import com.spotify.playlistmanager.domain.dj.PartyPlanner
import com.spotify.playlistmanager.domain.dj.TrackAnalyzer
import com.spotify.playlistmanager.domain.dj.model.AnalyzedTrack
import com.spotify.playlistmanager.domain.dj.model.EnergyArc
import com.spotify.playlistmanager.domain.dj.model.EnergyShape
import com.spotify.playlistmanager.domain.dj.model.PartyMode
import com.spotify.playlistmanager.domain.dj.model.PartyState
import com.spotify.playlistmanager.domain.dj.model.Preset
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.StyleRatio
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
 * Stan dialogu „dodaj utwór z dowolnej playlisty".
 * null = zamknięty. Gdy [playlist] non-null — wczytane utwory tej playlisty
 * (bypass dedup, duplikaty dozwolone).
 */
data class ManualTrackPicker(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false
)

/**
 * Stan podglądu informacji o utworze (bottom sheet).
 * Features ładowane asynchronicznie po otwarciu — nullable dopóki nie przyjdą.
 */
data class TrackDetailSheet(
    val track: Track,
    val features: TrackAudioFeatures? = null
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

/**
 * Tryby pracy ekranu Krok — który flow user-a jest aktywny.
 *
 * Trzy tryby:
 *  - [STEPWISE] — klasyczny krok-po-kroku: mood buttons → top-K → pick.
 *  - [PLAN] — generator imprezy: ustaw czas i łuk → wygeneruj wszystkie bloki naraz.
 *  - [LIVE] — generator bloków na żądanie: preset → kolejny blok dorzucany do sesji.
 *
 * Wszystkie tryby pracują na tej samej puli (poolA + opcjonalnie poolB)
 * i tej samej [TandaStructure] (countA/countB = rozmiar bloku salsy/bachaty).
 * Wynikowe utwory zawsze trafiają do `sessionTracks` — wspólny zapis na koniec.
 */
enum class GeneratorMode(val displayName: String) {
    STEPWISE("Krok po kroku"),
    PLAN("Plan imprezy"),
    LIVE("Live bloki")
}

/** Stan zapisu playlisty do Spotify. */
sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data class Success(val playlistUrl: String, val trackCount: Int) : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * Stan wymiany pojedynczego utworu w sesji (tryb Live bloki).
 * Idle → (Loading | Picking | Error). [Loading] i [Picking] trzymają indeks
 * utworu w `sessionTracks`, którego dotyczy operacja.
 */
sealed class SwapState {
    data object Idle : SwapState()

    /** Auto-wymiana w toku dla danego indeksu. */
    data class Loading(val sessionIndex: Int) : SwapState()

    /** Picker — lista kandydatów z puli do ręcznego wyboru. */
    data class Picking(
        val sessionIndex: Int,
        val original: SessionTrack,
        val candidates: List<SuggestNextTrackUseCase.Candidate>
    ) : SwapState()

    data class Error(val message: String) : SwapState()
}

/** Snapshot do cofnięcia ostatniej wymiany (akcja „Cofnij" w snackbarze). */
data class SwapSnapshot(
    val sessionIndex: Int,
    val replaced: SessionTrack,
    val insertedTitle: String,
    /** Wartość `appendAnchorsDirty` sprzed wymiany — przywracana przy undo. */
    val anchorsDirtyBefore: Boolean
)

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
    /**
     * True, gdy w trybie append podmieniono co najmniej jedną „kotwicę"
     * (utwór już zapisany na playliście). Wymusza nadpisanie całej playlisty
     * (replacePlaylistTracks) przy zapisie zamiast samego dopisania nowych.
     */
    val appendAnchorsDirty: Boolean = false,

    // ── Ręczny pick z dowolnej playlisty (duplikaty dozwolone) ─
    val manualTrackPicker: ManualTrackPicker? = null,

    // ── Wymiana pojedynczego utworu w sesji (auto + ręczny picker) ─
    val swapState: SwapState = SwapState.Idle,
    val lastSwap: SwapSnapshot? = null,

    // ── Podgląd informacji o utworze (bottom sheet) ────────
    val trackDetailSheet: TrackDetailSheet? = null,

    // ── Generator bloków (Plan imprezy / Live) ─────────────
    val generatorMode: GeneratorMode = GeneratorMode.STEPWISE,
    /** Przeanalizowana pula utworów per styl (cache po zmianie poolA/poolB). */
    val analyzedByStyle: Map<Style, List<AnalyzedTrack>> = emptyMap(),
    val isAnalyzingPool: Boolean = false,
    /** Plan imprezy — czas trwania w ms (slider 30 min – 6 h). */
    val planDurationMs: Long = 90 * 60_000L,
    /** Plan imprezy — łuk energii. */
    val energyArc: EnergyArc = EnergyArc.CLASSIC,
    /** Live — wybrany kształt do generowania kolejnego bloku. */
    val liveShape: EnergyShape = EnergyShape.Wave(),
    /** Live — ostatnio użyty preset (do podświetlenia w UI). */
    val livePreset: Preset? = null,
    val isGeneratingPlan: Boolean = false,
    val isGeneratingLiveBlock: Boolean = false,

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
        get() = (newSessionTracks.isNotEmpty() || (appendMode != null && appendAnchorsDirty)) &&
                saveState !is SaveState.Saving

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
    private val preferencesStore: StepwisePreferencesStore,
    private val trackAnalyzer: TrackAnalyzer,
    private val partyPlanner: PartyPlanner,
    private val liveAssistant: LiveAssistant
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
                    autoFillSnapshot = null,
                    appendAnchorsDirty = false
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
                    isLoadingAppendAnchors = false,
                    currentAxis = anchors.lastOrNull()?.axis ?: ScoreAxis.DANCE
                )
            }

            // Pule zostaja nietkniete — uzytkownik wybiera samodzielnie skad
            // czerpac nowe utwory (typowo INNA playlista niz ta dopisywana).
            recomputeCandidates()
        }
    }

    fun onDisableAppendMode() {
        _state.update { current ->
            current.copy(
                appendMode = null,
                sessionTracks = current.sessionTracks.filter { !it.isAnchor },
                tandaCounter = TandaCounter(),
                autoFillSnapshot = null,
                appendAnchorsDirty = false
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
                        poolA = current.poolA.copy(tracks = tracks, featuresLoaded = true),
                        analyzedByStyle = emptyMap()  // invalidate — pula zmienila sie
                    )
                    ActivePool.B -> current.copy(
                        poolB = (current.poolB ?: PoolSlot()).copy(
                            tracks = tracks, featuresLoaded = true
                        ),
                        analyzedByStyle = emptyMap()
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

    // ── Manual pick z dowolnej playlisty (duplikaty dozwolone) ─────────

    fun onOpenManualTrackPicker() {
        _state.update { it.copy(manualTrackPicker = ManualTrackPicker()) }
    }

    fun onCloseManualTrackPicker() {
        _state.update { it.copy(manualTrackPicker = null) }
    }

    fun onSelectManualPickerPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    manualTrackPicker = ManualTrackPicker(
                        playlist = playlist,
                        isLoading = true
                    )
                )
            }
            val tracks = runCatching {
                if (playlist.id == GeneratePlaylistUseCase.LIKED_SONGS_ID) {
                    spotifyRepository.getLikedTracks()
                } else {
                    spotifyRepository.getPlaylistTracks(playlist.id)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        manualTrackPicker = null,
                        error = "Nie udało się pobrać utworów: ${e.message}"
                    )
                }
            }.getOrNull() ?: return@launch

            _state.update {
                it.copy(
                    manualTrackPicker = ManualTrackPicker(
                        playlist = playlist,
                        tracks = tracks,
                        isLoading = false
                    )
                )
            }
        }
    }

    // ── Podgląd informacji o utworze (bottom sheet) ────────────────────

    fun onShowTrackDetail(track: Track) {
        // Pokaż natychmiast z cache, jeśli mamy; doładuj features w tle.
        val trackId = track.id
        val cached = trackId?.let { lastKnownFeaturesMap[it] }
        _state.update {
            it.copy(trackDetailSheet = TrackDetailSheet(track = track, features = cached))
        }
        if (cached != null || trackId == null) return
        viewModelScope.launch {
            val fetched = runCatching {
                featuresRepository.getFeaturesMap(listOf(trackId))
            }.getOrNull()?.get(trackId)
            _state.update { current ->
                val sheet = current.trackDetailSheet ?: return@update current
                if (sheet.track.id != trackId) return@update current
                current.copy(trackDetailSheet = sheet.copy(features = fetched))
            }
        }
    }

    fun onCloseTrackDetail() {
        _state.update { it.copy(trackDetailSheet = null) }
    }

    /**
     * Dodaje utwór z dowolnej playlisty do sesji — bez sprawdzania dedup,
     * więc duplikat jest dozwolony. Pobiera features (BPM/Camelot) jeśli
     * dostępne, by badge w sesji wyglądał spójnie z resztą.
     */
    fun onPickTrackFromAnyPlaylist(track: Track) {
        viewModelScope.launch {
            val features = track.id?.let { id ->
                lastKnownFeaturesMap[id] ?: runCatching {
                    featuresRepository.getFeaturesMap(listOf(id))
                }.getOrNull()?.get(id)
            }
            _state.update { current ->
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
                        targetLabel = "Z innej playlisty",
                        bpm = features?.bpm,
                        camelot = features?.camelot
                    ),
                    manualTrackPicker = null
                )
            }
        }
    }

    // ── Wymiana pojedynczego utworu w sesji (auto + ręczny) ─────────────
    //
    // Działa dla KAŻDEGO utworu sesji — także „kotwic" (utworów już zapisanych
    // na docelowej playliście w trybie append). Podmiana kotwicy ustawia
    // [StepwiseUiState.appendAnchorsDirty], przez co zapis nadpisuje całą
    // playlistę (replacePlaylistTracks) zamiast tylko dopisać nowe utwory.
    //
    // Kandydaci pochodzą z puli zgodnej z `pool` wymienianego utworu (A→poolA,
    // B→poolB) i są dobierani tym samym silnikiem co tryb krok-po-kroku:
    // cel = poziom energii slotu (targetScore/axis), z premią za harmonię i
    // płynność BPM względem poprzedniego utworu w sesji.

    /** Auto-wymiana: podmienia utwór na najlepszego kandydata z puli. */
    fun onSwapAuto(sessionIndex: Int) {
        val s = _state.value
        s.sessionTracks.getOrNull(sessionIndex) ?: return
        _state.update { it.copy(swapState = SwapState.Loading(sessionIndex), lastSwap = null) }
        viewModelScope.launch {
            val candidates = findSwapCandidates(s, sessionIndex, k = 1).getOrElse { e ->
                _state.update {
                    it.copy(swapState = SwapState.Error(e.message ?: "Błąd wyszukiwania zamiennika"))
                }
                return@launch
            }
            val best = candidates.firstOrNull()
            if (best == null) {
                _state.update {
                    it.copy(swapState = SwapState.Error("Brak innych utworów w puli do wymiany"))
                }
                return@launch
            }
            applySwap(sessionIndex, best)
            _state.update { it.copy(swapState = SwapState.Idle) }
        }
    }

    /** Ręczny wybór: otwiera picker z listą kandydatów z puli. */
    fun onSwapPick(sessionIndex: Int) {
        val s = _state.value
        val original = s.sessionTracks.getOrNull(sessionIndex) ?: return
        _state.update { it.copy(swapState = SwapState.Loading(sessionIndex), lastSwap = null) }
        viewModelScope.launch {
            val candidates = findSwapCandidates(s, sessionIndex, k = SWAP_PICKER_K).getOrElse { e ->
                _state.update {
                    it.copy(swapState = SwapState.Error(e.message ?: "Błąd wyszukiwania zamiennika"))
                }
                return@launch
            }
            if (candidates.isEmpty()) {
                _state.update {
                    it.copy(swapState = SwapState.Error("Brak innych utworów w puli do wymiany"))
                }
                return@launch
            }
            _state.update {
                it.copy(
                    swapState = SwapState.Picking(
                        sessionIndex = sessionIndex,
                        original = original,
                        candidates = candidates
                    )
                )
            }
        }
    }

    /** Potwierdzenie wyboru kandydata z pickera. */
    fun onConfirmSwap(candidate: SuggestNextTrackUseCase.Candidate) {
        val picking = _state.value.swapState as? SwapState.Picking ?: return
        viewModelScope.launch {
            applySwap(picking.sessionIndex, candidate)
            _state.update { it.copy(swapState = SwapState.Idle) }
        }
    }

    /** Zamknięcie pickera / wyczyszczenie błędu bez zmian. */
    fun onCancelSwap() {
        _state.update { it.copy(swapState = SwapState.Idle) }
    }

    /** Cofa ostatnią wymianę (akcja „Cofnij" w snackbarze). */
    fun onUndoSwap() {
        _state.update { current ->
            val snap = current.lastSwap ?: return@update current
            val list = current.sessionTracks.toMutableList()
            if (snap.sessionIndex !in list.indices) return@update current.copy(lastSwap = null)
            list[snap.sessionIndex] = snap.replaced
            current.copy(
                sessionTracks = list,
                appendAnchorsDirty = snap.anchorsDirtyBefore,
                lastSwap = null
            )
        }
    }

    fun onClearLastSwap() {
        if (_state.value.lastSwap != null) {
            _state.update { it.copy(lastSwap = null) }
        }
    }

    /**
     * Dobiera kandydatów na zamiennik dla utworu sesji o indeksie [sessionIndex].
     * Pula = źródło zgodne z `pool` wymienianego utworu. Wyklucza wszystkie
     * utwory już obecne w sesji (w tym wymieniany — brak self-swap i duplikatów).
     */
    private suspend fun findSwapCandidates(
        state: StepwiseUiState,
        sessionIndex: Int,
        k: Int
    ): Result<List<SuggestNextTrackUseCase.Candidate>> {
        val original = state.sessionTracks.getOrNull(sessionIndex)
            ?: return Result.failure(IllegalStateException("Utwór poza zakresem"))
        val poolSlot = if (original.pool == ActivePool.A) state.poolA else state.poolB
        val pool = poolSlot?.tracks.orEmpty()
        if (pool.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    "Pula ${original.pool.name} jest pusta — wybierz playlistę źródłową"
                )
            )
        }
        val previous = state.sessionTracks.getOrNull(sessionIndex - 1)?.track
        val excludeIds = state.sessionTracks.mapNotNull { it.track.id }.toSet()
        val suggestion = suggestUseCase.suggest(
            pool = pool,
            alreadyPickedIds = excludeIds,
            lastPickedTrack = previous,
            target = NextTrackTarget.Absolute(original.targetScore, original.axis),
            currentAxis = original.axis,
            k = k,
            weights = state.weights
        )
        return Result.success(suggestion.candidates)
    }

    /**
     * Podmienia utwór sesji [sessionIndex] na [candidate], zachowując parametry
     * slotu (pool, isAnchor, targetScore). Zapisuje snapshot do undo i — gdy
     * wymieniono kotwicę — ustawia `appendAnchorsDirty`.
     */
    private suspend fun applySwap(
        sessionIndex: Int,
        candidate: SuggestNextTrackUseCase.Candidate
    ) {
        val features = candidate.track.id?.let { featuresFor(it) }
        _state.update { current ->
            val list = current.sessionTracks.toMutableList()
            val original = list.getOrNull(sessionIndex) ?: return@update current
            list[sessionIndex] = original.copy(
                track = candidate.track,
                score = candidate.score,
                axis = candidate.scoreAxis,
                bpm = features?.bpm,
                camelot = features?.camelot,
                targetLabel = if (original.isAnchor) "Kotwica (wymieniona)"
                else "${original.targetLabel} → wymieniony"
            )
            current.copy(
                sessionTracks = list,
                appendAnchorsDirty = current.appendAnchorsDirty || original.isAnchor,
                lastSwap = SwapSnapshot(
                    sessionIndex = sessionIndex,
                    replaced = original,
                    insertedTitle = candidate.track.title,
                    anchorsDirtyBefore = current.appendAnchorsDirty
                )
            )
        }
    }

    /** Features dla pojedynczego utworu — z cache UI lub doładowane z repo. */
    private suspend fun featuresFor(trackId: String): TrackAudioFeatures? =
        lastKnownFeaturesMap[trackId] ?: runCatching {
            featuresRepository.getFeaturesMap(listOf(trackId))
        }.getOrNull()?.get(trackId)

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

        val append = state.appendMode
        // Gdy podmieniono kotwicę — nadpisujemy całą playlistę pełną sesją
        // (kotwice z podmianami + nowe utwory, w kolejności sesji). W innym
        // wypadku zachowujemy dotychczasowe zachowanie (dopisanie nowych).
        val rewriteWholePlaylist = append != null && state.appendAnchorsDirty
        val newUris = state.newSessionTracks.mapNotNull { it.track.uri }
        val urisToSend = if (rewriteWholePlaylist) {
            state.sessionTracks.mapNotNull { it.track.uri }
        } else {
            newUris
        }
        if (urisToSend.isEmpty()) {
            _state.update {
                it.copy(saveState = SaveState.Error("Żaden utwór nie ma URI Spotify"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saveState = SaveState.Saving) }
            runCatching {
                when {
                    append != null && rewriteWholePlaylist -> {
                        // Pełne nadpisanie istniejącej playlisty (PUT + POST chunki)
                        spotifyRepository.replacePlaylistTracks(append.playlistId, urisToSend)
                        append.playlistId to urisToSend.size
                    }
                    append != null -> {
                        // Dopisz nowe utwory na koniec istniejącej playlisty
                        spotifyRepository.addTracksToPlaylist(append.playlistId, urisToSend)
                        append.playlistId to (append.originalTrackCount + urisToSend.size)
                    }
                    else -> {
                        // Utwórz nową playlistę
                        val finalDescription = buildFinalDescription(state)
                        val playlistId = spotifyRepository.createPlaylist(
                            name = state.newPlaylistName.ifBlank { "Sesja DJ" },
                            description = finalDescription
                        )
                        spotifyRepository.addTracksToPlaylist(playlistId, urisToSend)
                        playlistId to urisToSend.size
                    }
                }
            }.onSuccess { (playlistId, newTotalCount) ->
                val url = "https://open.spotify.com/playlist/$playlistId"
                _state.update { current ->
                    val ap = current.appendMode
                    if (ap != null) {
                        // Po zapisie: nowo zapisane tracki staja sie kotwicami, zeby
                        // kolejny save nie wyslal ich ponownie. Kotwice (w tym
                        // podmienione) sa juz na playliscie — czyscimy dirty.
                        current.copy(
                            saveState = SaveState.Success(url, newUris.size),
                            sessionTracks = current.sessionTracks.map {
                                if (it.isAnchor) it else it.copy(isAnchor = true)
                            },
                            appendMode = ap.copy(originalTrackCount = newTotalCount),
                            appendAnchorsDirty = false
                        )
                    } else {
                        current.copy(saveState = SaveState.Success(url, newUris.size))
                    }
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

        /** Ilu kandydatów pokazać w pickerze ręcznej wymiany. */
        const val SWAP_PICKER_K = 12
    }

    fun onSaveStateConsumed() {
        _state.update { it.copy(saveState = SaveState.Idle) }
    }

    // ── Błędy ───────────────────────────────────────────────────────────

    fun onClearError() {
        _state.update { it.copy(error = null) }
    }

    // ── Generator bloków (Plan imprezy / Live) ──────────────────────────

    fun onGeneratorModeChange(mode: GeneratorMode) {
        _state.update { it.copy(generatorMode = mode) }
        // Lazy prefetch — gdy user wchodzi w PLAN/LIVE i nie mamy jeszcze analizy puli
        if (mode != GeneratorMode.STEPWISE && _state.value.analyzedByStyle.isEmpty()) {
            viewModelScope.launch { ensureAnalyzedPool() }
        }
    }

    fun onPlanDurationChange(ms: Long) {
        _state.update { it.copy(planDurationMs = ms.coerceAtLeast(30 * 60_000L)) }
    }

    fun onEnergyArcChange(arc: EnergyArc) {
        _state.update { it.copy(energyArc = arc) }
    }

    fun onLiveShapeChange(shape: EnergyShape) {
        _state.update { it.copy(liveShape = shape) }
    }

    /**
     * Plan imprezy — wywołuje [PartyPlanner.plan] i konwertuje wszystkie wynikowe
     * bloki na [SessionTrack]'i, dorzucając je do sesji.
     *
     * Mapowanie: TandaStructure(countA, countB) → blockSize per styl, gdzie
     * A = SALSA, B = BACHATA. Proporcja S:B liczona z tej samej struktury.
     * Bez ustawionej tandy: defaultowy rozmiar bloku 5 i proporcja 50:50.
     */
    fun onGeneratePlanClick() {
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingPlan = true, error = null) }
            val analyzed = ensureAnalyzedPool()
            if (analyzed.values.all { it.isEmpty() }) {
                _state.update { it.copy(isGeneratingPlan = false, error = "Pula pusta — wybierz playliste") }
                return@launch
            }

            val s = _state.value
            val tanda = s.tandaStructure
            val salsaSize = (tanda?.countA ?: PartyPlanner.DEFAULT_BLOCK_SIZE).coerceIn(3, 10)
            val bachataSize = (tanda?.countB ?: PartyPlanner.DEFAULT_BLOCK_SIZE).coerceIn(3, 10)
            val ratio = if (tanda != null) {
                StyleRatio(
                    salsaPercent = (tanda.countA.toFloat() / tanda.totalPerTanda * 100).toInt()
                        .coerceIn(0, 100)
                )
            } else StyleRatio(salsaPercent = 50)

            val partyState = PartyState(
                mode = PartyMode.PLANNING,
                playedTrackIds = s.sessionTracks.mapNotNull { it.track.id }.toSet()
            )
            val plan = partyPlanner.plan(
                state = partyState,
                analyzedByStyle = analyzed,
                durationMs = s.planDurationMs,
                ratio = ratio,
                arc = s.energyArc,
                blockSizeByStyle = mapOf(
                    Style.SALSA to salsaSize,
                    Style.BACHATA to bachataSize
                )
            )

            val newSessionTracks = plan.blocks.flatMapIndexed { blockIdx, block ->
                block.tracks.mapIndexed { slotIdx, at ->
                    SessionTrack(
                        track = at.track,
                        pool = if (block.style == Style.SALSA) ActivePool.A else ActivePool.B,
                        score = at.energyScore,
                        targetScore = block.targetScores.getOrNull(slotIdx) ?: at.energyScore,
                        axis = ScoreAxis.DANCE,
                        targetLabel = "Plan #${blockIdx + 1}: ${block.shape.displayName}",
                        bpm = at.audio.bpm,
                        camelot = at.audio.camelot
                    )
                }
            }

            _state.update { current ->
                current.copy(
                    sessionTracks = current.sessionTracks + newSessionTracks,
                    isGeneratingPlan = false,
                    error = plan.errors.firstOrNull()?.let { (idx, e) ->
                        "Blok #$idx pominiety: ${e.message}"
                    }
                )
            }
        }
    }

    /**
     * Live — wywołuje [LiveAssistant.nextBlock] dla aktywnej puli i dorzuca utwory
     * do sesji. Pula → styl: A=SALSA, B=BACHATA. Rozmiar bloku z [TandaStructure].
     * Anchor energii = ostatni SessionTrack tej samej puli (jeśli istnieje).
     * Auto-switch puli po zbudowaniu bloku (jak w mood-button flow).
     */
    fun onLivePresetClick(preset: Preset) {
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingLiveBlock = true, livePreset = preset, error = null) }
            val analyzed = ensureAnalyzedPool()
            val s = _state.value
            val activeStyle = if (s.activePool == ActivePool.A) Style.SALSA else Style.BACHATA
            val pool = analyzed[activeStyle].orEmpty()
            if (pool.isEmpty()) {
                _state.update {
                    it.copy(
                        isGeneratingLiveBlock = false,
                        error = "Pula dla ${activeStyle.name} pusta (sprawdz wybor playlisty / genres w CSV)"
                    )
                }
                return@launch
            }

            val tanda = s.tandaStructure
            // Respektuj rozmiar tandy nawet gdy < 3 (np. 2:3). Tylko brak
            // struktury → domyslny rozmiar bloku. Gorny limit 10 zostaje.
            val n = (
                if (tanda != null) (if (activeStyle == Style.SALSA) tanda.countA else tanda.countB)
                else PartyPlanner.DEFAULT_BLOCK_SIZE
            ).coerceIn(1, 10)

            val lastForStyle = s.sessionTracks.lastOrNull {
                it.pool == s.activePool
            }
            val anchorId = lastForStyle?.track?.id
            val partyState = PartyState(
                mode = PartyMode.LIVE,
                playedTrackIds = s.sessionTracks.mapNotNull { it.track.id }.toSet(),
                lastPlayedIdByStyle = if (anchorId != null) mapOf(activeStyle to anchorId) else emptyMap()
            )

            val result = liveAssistant.nextBlock(
                state = partyState,
                style = activeStyle,
                analyzedPool = pool,
                preset = preset,
                n = n
            )

            result.onSuccess { block ->
                val newSessionTracks = block.tracks.mapIndexed { slotIdx, at ->
                    SessionTrack(
                        track = at.track,
                        pool = s.activePool,
                        score = at.energyScore,
                        targetScore = block.targetScores.getOrNull(slotIdx) ?: at.energyScore,
                        axis = ScoreAxis.DANCE,
                        targetLabel = "Live: ${preset.label} (${block.shape.displayName})",
                        bpm = at.audio.bpm,
                        camelot = at.audio.camelot
                    )
                }
                val newActive = if (s.poolB != null) {
                    if (s.activePool == ActivePool.A) ActivePool.B else ActivePool.A
                } else s.activePool

                _state.update { current ->
                    current.copy(
                        sessionTracks = current.sessionTracks + newSessionTracks,
                        activePool = newActive,
                        isGeneratingLiveBlock = false
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isGeneratingLiveBlock = false,
                        error = e.message ?: "Nie udalo sie zbudowac bloku"
                    )
                }
            }
        }
    }

    /**
     * Cache'owana analiza puli (poolA + poolB) — uruchamiana lazy.
     * Wynik trafia do `state.analyzedByStyle` i jest invalidowany przy zmianie pul.
     */
    private suspend fun ensureAnalyzedPool(): Map<Style, List<AnalyzedTrack>> {
        val cached = _state.value.analyzedByStyle
        if (cached.isNotEmpty()) return cached

        _state.update { it.copy(isAnalyzingPool = true) }
        val s = _state.value
        val combined = (s.poolA.tracks + (s.poolB?.tracks ?: emptyList())).distinctBy { it.id }
        val ids = combined.mapNotNull { it.id }
        val features = runCatching {
            featuresRepository.getFeaturesMap(ids)
        }.getOrDefault(emptyMap())
        val result = trackAnalyzer.analyzePool(combined, features)
        _state.update { it.copy(analyzedByStyle = result, isAnalyzingPool = false) }
        return result
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
