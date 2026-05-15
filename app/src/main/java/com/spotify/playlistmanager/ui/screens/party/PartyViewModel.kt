package com.spotify.playlistmanager.ui.screens.party

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.dj.BlockGenerator
import com.spotify.playlistmanager.domain.dj.IPartyStateStore
import com.spotify.playlistmanager.domain.dj.LiveAssistant
import com.spotify.playlistmanager.domain.dj.PartyPlanner
import com.spotify.playlistmanager.domain.dj.TrackAnalyzer
import com.spotify.playlistmanager.domain.dj.model.AnalyzedTrack
import com.spotify.playlistmanager.domain.dj.model.Block
import com.spotify.playlistmanager.domain.dj.model.EnergyArc
import com.spotify.playlistmanager.domain.dj.model.EnergyShape
import com.spotify.playlistmanager.domain.dj.model.PartyMode
import com.spotify.playlistmanager.domain.dj.model.PartyState
import com.spotify.playlistmanager.domain.dj.model.Preset
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.StyleRatio
import com.spotify.playlistmanager.domain.dj.model.SubstyleStrategy
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel ekranu "Impreza DJ" — orkiestracja wszystkich modułów algorytmu.
 *
 * Cykl pracy:
 *  1. `loadAvailablePlaylists` + `restorePartyState` (przy init).
 *  2. `onLoadCorpus` — agreguje utwory ze wszystkich playlist usera,
 *     `TrackAnalyzer.analyzePool` → `analyzedByStyle`.
 *  3. **Planning**: `onGeneratePlan` → `PartyPlanner.plan` → `blocks`.
 *  4. **Live**: `onPresetClick` → `LiveAssistant.nextBlock` → kolejny blok.
 *     `onCommitPlayed` → przeniesienie utworu do `playedTrackIds`.
 *  5. `onSaveAsNewPlaylist` → `spotifyRepository.createPlaylist` + `addTracksToPlaylist`.
 *
 * Po każdej mutacji `PartyState` zapisujemy go do trwałego store'u (auto-save).
 */
@HiltViewModel
class PartyViewModel @Inject constructor(
    private val spotifyRepository: ISpotifyRepository,
    private val featuresRepository: ITrackFeaturesRepository,
    private val trackAnalyzer: TrackAnalyzer,
    private val blockGenerator: BlockGenerator,
    private val partyPlanner: PartyPlanner,
    private val liveAssistant: LiveAssistant,
    private val partyStateStore: IPartyStateStore
) : ViewModel() {

    private val _state = MutableStateFlow(PartyUiState())
    val state: StateFlow<PartyUiState> = _state.asStateFlow()

    /** Mapuje track id → AnalyzedTrack — szybki lookup dla `commitPlayed` itp. */
    private var trackIndex: Map<String, AnalyzedTrack> = emptyMap()

    init {
        loadAvailablePlaylists()
        restorePartyState()
    }

    // ── Bootstrap ──────────────────────────────────────────────────────────

    private fun loadAvailablePlaylists() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPlaylists = true, error = null) }
            runCatching { spotifyRepository.getUserPlaylists() }
                .onSuccess { playlists ->
                    _state.update {
                        it.copy(availablePlaylists = playlists, isLoadingPlaylists = false)
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

    private fun restorePartyState() {
        viewModelScope.launch {
            val saved = runCatching { partyStateStore.load() }.getOrNull() ?: return@launch
            _state.update { it.copy(partyState = saved) }
        }
    }

    /**
     * Agreguje wszystkie utwory z playlist usera (Liked + zwykłe) i przeanalizowuje.
     * Wynik trafia do `analyzedByStyle` + `partyState.poolIdsByStyle`.
     */
    fun onLoadCorpus() {
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzingCorpus = true, error = null) }
            val allTracks = collectAllTracks()
            val ids = allTracks.mapNotNull { it.id }.distinct()
            val features = runCatching {
                featuresRepository.getFeaturesMap(ids)
            }.getOrDefault(emptyMap())

            val analyzed = trackAnalyzer.analyzePool(allTracks, features)
            trackIndex = analyzed.values.flatten().mapNotNull { t -> t.id?.let { it to t } }.toMap()

            val poolIds = analyzed.mapValues { (_, list) ->
                list.filter { it.passesGate }.mapNotNull { it.id }
            }
            _state.update { current ->
                current.copy(
                    isAnalyzingCorpus = false,
                    corpusLoaded = true,
                    analyzedByStyle = analyzed,
                    partyState = current.partyState.copy(poolIdsByStyle = poolIds)
                )
            }
            persistState()
        }
    }

    private suspend fun collectAllTracks(): List<Track> {
        val state = _state.value
        val playlists = if (state.availablePlaylists.isNotEmpty()) {
            state.availablePlaylists
        } else {
            runCatching { spotifyRepository.getUserPlaylists() }.getOrDefault(emptyList())
        }
        val all = mutableListOf<Track>()
        // Polubione — zawsze dorzucamy jeśli token na to pozwala
        runCatching { spotifyRepository.getLikedTracks() }.onSuccess { all += it }
        for (p in playlists) {
            // Pomijamy "wirtualną" pozycję Liked Songs jeśli jest na liście (z innego ekranu)
            if (p.id == GeneratePlaylistUseCase.LIKED_SONGS_ID) continue
            runCatching { spotifyRepository.getPlaylistTracks(p.id) }
                .onSuccess { all += it }
        }
        return all
    }

    // ── Tryb pracy ─────────────────────────────────────────────────────────

    fun onModeChange(mode: PartyMode) {
        _state.update { it.copy(partyState = it.partyState.copy(mode = mode)) }
        persistState()
    }

    // ── Planning ───────────────────────────────────────────────────────────

    fun onPlanDurationChange(ms: Long) {
        _state.update { it.copy(planDurationMs = ms.coerceAtLeast(MIN_PLAN_MS)) }
    }

    fun onRatioChange(salsaPercent: Int) {
        _state.update {
            it.copy(styleRatio = StyleRatio(salsaPercent = salsaPercent.coerceIn(0, 100)))
        }
    }

    fun onArcChange(arc: EnergyArc) {
        _state.update { it.copy(energyArc = arc) }
    }

    fun onStrategyChange(strategy: SubstyleStrategy) {
        _state.update { it.copy(substyleStrategy = strategy) }
    }

    fun onPlanBlockSizeChange(n: Int) {
        _state.update { it.copy(planBlockSize = n.coerceIn(3, 10)) }
    }

    fun onGeneratePlan() {
        val current = _state.value
        if (current.analyzedByStyle.isEmpty()) {
            _state.update { it.copy(error = "Najpierw wczytaj bibliotekę utworów") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingPlan = true, error = null) }
            val result = partyPlanner.plan(
                state = current.partyState.copy(mode = PartyMode.PLANNING),
                analyzedByStyle = current.analyzedByStyle,
                durationMs = current.planDurationMs,
                ratio = current.styleRatio,
                arc = current.energyArc,
                strategy = current.substyleStrategy,
                blockSize = current.planBlockSize
            )
            val ids = result.blocks.flatMap { b -> b.tracks.mapNotNull { it.id } }
            _state.update { s ->
                s.copy(
                    isGeneratingPlan = false,
                    blocks = result.blocks,
                    phaseByBlock = result.phaseByBlock,
                    partyState = s.partyState.copy(
                        currentQueueTail = ids,
                        plannedMs = current.planDurationMs
                    ),
                    error = result.errors.firstOrNull()?.let { (idx, e) ->
                        "Blok #$idx pominięty: ${e.message}"
                    }
                )
            }
            persistState()
        }
    }

    // ── Live ──────────────────────────────────────────────────────────────

    fun onSelectLiveStyle(style: Style) {
        _state.update { it.copy(liveSelectedStyle = style) }
    }

    fun onLiveBlockSizeChange(n: Int) {
        _state.update { it.copy(liveBlockSize = n.coerceIn(3, 10)) }
    }

    fun onLiveShapeChange(shape: EnergyShape) {
        _state.update { it.copy(liveShape = shape) }
    }

    fun onPresetClick(preset: Preset) {
        val current = _state.value
        val style = current.liveSelectedStyle
        val analyzedPool = current.analyzedByStyle[style].orEmpty()
        if (analyzedPool.isEmpty()) {
            _state.update {
                it.copy(error = "Pula dla stylu ${style.name} jest pusta — wczytaj bibliotekę")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGeneratingLiveBlock = true, livePreset = preset, error = null) }
            val result = liveAssistant.nextBlock(
                state = current.partyState.copy(mode = PartyMode.LIVE),
                style = style,
                analyzedPool = analyzedPool,
                preset = preset,
                n = current.liveBlockSize
            )
            result.onSuccess { block ->
                val newIds = block.tracks.mapNotNull { it.id }
                _state.update { s ->
                    s.copy(
                        isGeneratingLiveBlock = false,
                        blocks = s.blocks + block,
                        partyState = s.partyState.copy(
                            mode = PartyMode.LIVE,
                            currentQueueTail = s.partyState.currentQueueTail + newIds
                        )
                    )
                }
                persistState()
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isGeneratingLiveBlock = false,
                        error = e.message ?: "Nie udało się zbudować bloku"
                    )
                }
            }
        }
    }

    fun onRerollSlot(blockIdx: Int, slotIdx: Int) {
        val current = _state.value
        val block = current.blocks.getOrNull(blockIdx) ?: return
        val pool = current.analyzedByStyle[block.style].orEmpty()
        viewModelScope.launch {
            val result = liveAssistant.rerollSlot(
                block = block,
                slotIndex = slotIdx,
                state = current.partyState,
                analyzedPool = pool
            )
            result.onSuccess { newBlock ->
                val newBlocks = current.blocks.toMutableList().also { it[blockIdx] = newBlock }
                // Aktualizujemy queueTail — wymieniamy ID slotu
                val oldId = block.tracks[slotIdx].id
                val newId = newBlock.tracks[slotIdx].id
                val newTail = current.partyState.currentQueueTail.map {
                    if (it == oldId && newId != null) newId else it
                }
                _state.update { s ->
                    s.copy(
                        blocks = newBlocks,
                        partyState = s.partyState.copy(currentQueueTail = newTail)
                    )
                }
                persistState()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Reroll nieudany") }
            }
        }
    }

    fun onCommitPlayed(blockIdx: Int, slotIdx: Int) {
        val current = _state.value
        val block = current.blocks.getOrNull(blockIdx) ?: return
        val track = block.tracks.getOrNull(slotIdx) ?: return
        val newState = liveAssistant.commitPlayed(current.partyState, track)
        _state.update { it.copy(partyState = newState) }
        persistState()
    }

    fun onSoftLock(blockIdx: Int, slotIdx: Int) {
        val current = _state.value
        val block = current.blocks.getOrNull(blockIdx) ?: return
        val newBlock = if (slotIdx in block.lockedSlots) {
            liveAssistant.softUnlockSlot(block, slotIdx)
        } else {
            liveAssistant.softLockSlot(block, slotIdx)
        }
        val newBlocks = current.blocks.toMutableList().also { it[blockIdx] = newBlock }
        _state.update { it.copy(blocks = newBlocks) }
    }

    fun onReshapeCurrent(newShape: EnergyShape) {
        val current = _state.value
        val style = current.liveSelectedStyle
        val pool = current.analyzedByStyle[style].orEmpty()
        viewModelScope.launch {
            val result = liveAssistant.reshape(
                state = current.partyState,
                style = style,
                analyzedPool = pool,
                newShape = newShape,
                n = current.liveBlockSize
            )
            result.onSuccess { (block, newState) ->
                // Niezagrany ogon znika z UI — usuwamy bloki, których wszystkie utwory
                // są w queueTail (czyli niezagrane)
                val playedIds = newState.playedTrackIds
                val keptBlocks = current.blocks.filter { b ->
                    b.tracks.any { it.id in playedIds }
                }
                _state.update { s ->
                    s.copy(
                        blocks = keptBlocks + block,
                        partyState = newState.copy(
                            currentQueueTail = block.tracks.mapNotNull { it.id }
                        )
                    )
                }
                persistState()
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Nie udało się zmienić kształtu") }
            }
        }
    }

    fun onResetSession() {
        viewModelScope.launch {
            runCatching { partyStateStore.clear() }
            _state.update {
                it.copy(
                    partyState = PartyState(mode = it.partyState.mode),
                    blocks = emptyList(),
                    phaseByBlock = emptyList(),
                    appendMode = null,
                    error = null
                )
            }
        }
    }

    // ── Zapis ──────────────────────────────────────────────────────────────

    fun onPlaylistNameChange(name: String) {
        _state.update { it.copy(newPlaylistName = name) }
    }

    fun onPlaylistDescriptionChange(description: String) {
        _state.update { it.copy(newPlaylistDescription = description) }
    }

    fun onEnableAppendMode(playlist: Playlist) {
        if (playlist.id == GeneratePlaylistUseCase.LIKED_SONGS_ID) {
            _state.update {
                it.copy(error = "Nie można dopisywać do Polubionych — wybierz zwykłą playlistę")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoadingAppendAnchors = true, error = null) }
            val anchorCount = runCatching {
                spotifyRepository.getPlaylistTracks(playlist.id).size
            }.getOrDefault(0)
            _state.update {
                it.copy(
                    isLoadingAppendAnchors = false,
                    appendMode = AppendMode(
                        playlistId = playlist.id,
                        playlistName = playlist.name,
                        originalTrackCount = anchorCount
                    )
                )
            }
        }
    }

    fun onDisableAppendMode() {
        _state.update { it.copy(appendMode = null) }
    }

    fun onSaveAsNewPlaylist() {
        val current = _state.value
        if (!current.canSave) return
        val uris = current.allTracks.mapNotNull { it.track.uri }
        if (uris.isEmpty()) {
            _state.update { it.copy(saveState = SaveState.Error("Żaden utwór nie ma URI Spotify")) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saveState = SaveState.Saving) }
            runCatching {
                val append = current.appendMode
                if (append != null) {
                    spotifyRepository.addTracksToPlaylist(append.playlistId, uris)
                    append.playlistId
                } else {
                    val description = buildFinalDescription(current)
                    val playlistId = spotifyRepository.createPlaylist(
                        name = current.newPlaylistName.ifBlank { "Impreza DJ" },
                        description = description
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
     * Buduje opis playlisty — kopia logiki z [StepwiseViewModel.buildFinalDescription].
     * Gwarantuje obecność członu "Wygenerowane: YYYY-MM-DD" niezależnie od opisu usera.
     * Spotify API odrzuca opisy z nowymi liniami → strippujemy `\r\n`.
     * Limit 300 znaków — twardo tniemy.
     */
    private fun buildFinalDescription(state: PartyUiState): String {
        val today = java.time.LocalDate.now().toString()
        val userDesc = state.newPlaylistDescription
            .trim()
            .replace(Regex("[\\r\\n]+"), " ")
        val dateFragment = "Wygenerowane: $today"
        val hasDateMarker = userDesc.contains("Wygenerowane:")

        val full = if (userDesc.isEmpty()) {
            val parts = mutableListOf(dateFragment)
            parts += "Tryb: Impreza DJ"
            parts += "Łuk: ${state.energyArc.displayName}"
            parts += "Bloki: ${state.blocks.size}"
            parts += "S/B: ${state.styleRatio.salsaPercent}:${state.styleRatio.bachataPercent}"
            parts += "Utwory: ${state.allTracks.size}"
            parts.joinToString(" · ")
        } else if (!hasDateMarker) {
            "$dateFragment · $userDesc"
        } else {
            userDesc
        }
        return full.take(SPOTIFY_DESCRIPTION_LIMIT)
    }

    fun onSaveStateConsumed() {
        _state.update { it.copy(saveState = SaveState.Idle) }
    }

    fun onClearError() {
        _state.update { it.copy(error = null) }
    }

    // ── Persistence ───────────────────────────────────────────────────────

    private fun persistState() {
        viewModelScope.launch {
            runCatching { partyStateStore.save(_state.value.partyState) }
        }
    }

    private companion object {
        const val MIN_PLAN_MS = 30 * 60_000L
        const val SPOTIFY_DESCRIPTION_LIMIT = 300
    }
}
