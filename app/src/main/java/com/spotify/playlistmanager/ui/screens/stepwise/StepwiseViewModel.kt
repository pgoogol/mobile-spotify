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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val camelot: String?
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
    val saveState: SaveState = SaveState.Idle,

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

    val canSave: Boolean
        get() = sessionTracks.isNotEmpty() && saveState !is SaveState.Saving

    val hasPoolB: Boolean
        get() = poolB != null
}

// ══════════════════════════════════════════════════════════════════════
//  ViewModel
// ══════════════════════════════════════════════════════════════════════

@HiltViewModel
class StepwiseViewModel @Inject constructor(
    private val spotifyRepository: ISpotifyRepository,
    private val featuresRepository: ITrackFeaturesRepository,
    private val suggestUseCase: SuggestNextTrackUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(StepwiseUiState())
    val state: StateFlow<StepwiseUiState> = _state.asStateFlow()

    init {
        loadAvailablePlaylists()
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
        _state.update {
            it.copy(
                tandaStructure = structure,
                tandaCounter = TandaCounter()
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
            val without = list.dropLast(1)
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
        _state.update {
            it.copy(
                sessionTracks = emptyList(),
                currentAxis = ScoreAxis.DANCE,
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
                    k = SuggestNextTrackUseCase.DEFAULT_K
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

    fun onSaveAsNewPlaylist() {
        val state = _state.value
        if (!state.canSave) return
        val uris = state.sessionTracks.mapNotNull { it.track.uri }
        if (uris.isEmpty()) {
            _state.update {
                it.copy(saveState = SaveState.Error("Żaden utwór nie ma URI Spotify"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saveState = SaveState.Saving) }
            runCatching {
                val playlistId = spotifyRepository.createPlaylist(
                    name = state.newPlaylistName.ifBlank { "Sesja DJ" },
                    description = "Utworzono w trybie krok po kroku"
                )
                spotifyRepository.addTracksToPlaylist(playlistId, uris)
                playlistId
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
