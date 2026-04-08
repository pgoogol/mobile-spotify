package com.spotify.playlistmanager.ui.screens.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.CompositeScoreCalculator
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.ExhaustionStatus
import com.spotify.playlistmanager.domain.model.GenerateResult
import com.spotify.playlistmanager.domain.model.GenerationRound
import com.spotify.playlistmanager.domain.model.GeneratorTemplate
import com.spotify.playlistmanager.domain.model.MatchedTrack
import com.spotify.playlistmanager.domain.model.TargetAction
import com.spotify.playlistmanager.domain.model.TemplateSource
import com.spotify.playlistmanager.domain.repository.CachePolicy
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Stan ekranu generowania ──────────────────────────────────────────────────
data class GenerateUiState(
    val availablePlaylists: List<Playlist> = emptyList(),
    val sources: List<PlaylistSource> = listOf(PlaylistSource()),
    val newPlaylistName: String = "Nowa Playlista",
    val previewTracks: List<Track>? = null,
    val generateResult: GenerateResult? = null,
    val smoothJoin: Boolean = true,
    val isLoadingPlaylists: Boolean = true,
    val isGenerating: Boolean = false,
    val isSaving: Boolean = false,
    val savedPlaylistUrl: String? = null,
    val error: String? = null,

    // ── Repeat count ──────────────────────────────────────────────────────
    /** Ile razy powtórzyć cały szablon (1–100). */
    val repeatCount: Int = 1,

    // ── Cele wyjściowe (zawsze widoczne) ──────────────────────────────────
    /** Cele wyjściowe (multi-select). */
    val targetActions: Set<TargetAction> = setOf(TargetAction.NEW_PLAYLIST),

    /** ID istniejącej playlisty do której dodajemy (gdy EXISTING_PLAYLIST). */
    val targetPlaylistId: String? = null,
    val targetPlaylistName: String? = null,

    // ── Sesja generowania ─────────────────────────────────────────────────
    /** Zbiór ID już użytych utworów — globalna deduplikacja w sesji. */
    val usedTrackIds: Set<String> = emptySet(),

    /** Historia rund generowania w bieżącej sesji. */
    val generationHistory: List<GenerationRound> = emptyList(),

    /** Status wyczerpania per playlista źródłowa. */
    val exhaustionStatuses: List<ExhaustionStatus> = emptyList(),

    /** Czy sesja jest aktywna (przynajmniej jedna runda). */
    val isSessionActive: Boolean = false,

    /** Czy wyświetlać dry-run dialog dla queue. */
    val showQueueDryRun: Boolean = false,

    /** Czy dodawanie do kolejki jest w toku. */
    val isAddingToQueue: Boolean = false,

    /** Postęp generowania w pętli repeat (0..repeatCount). */
    val repeatProgress: Int = 0,

    /** Stan dialogu przypinania utworów. */
    val pinningState: PinningState = PinningState.Idle
)

/**
 * Stan dialogu przypinania utworów.
 */
sealed class PinningState {
    data object Idle : PinningState()
    data class Loading(val sourceId: String) : PinningState()
    data class Picking(
        val sourceId: String,
        val tracks: List<Track>
    ) : PinningState()
}

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val repository: ISpotifyRepository,
    private val generatePlaylist: GeneratePlaylistUseCase,
    private val templateRepository: IGeneratorTemplateRepository,
    private val featuresRepository: ITrackFeaturesRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

    /** Mapa audio features (trackId → features) dla utworów w podglądzie. */
    private val _featuresMap = MutableStateFlow<Map<String, TrackAudioFeatures>>(emptyMap())
    val featuresMap: StateFlow<Map<String, TrackAudioFeatures>> = _featuresMap.asStateFlow()

    /**
     * Szybki lookup MatchedTrack po trackId — agregowany ze wszystkich rund historii.
     * Używany przez UI do wyświetlania targetScore i compositeScore per utwór.
     */
    val matchedTrackLookup: StateFlow<Map<String, MatchedTrack>> =
        _state
            .map { state ->
                buildMap {
                    for (round in state.generationHistory) {
                        for (mt in round.matchedTracks) {
                            val id = mt.track.id ?: continue
                            put(id, mt)
                        }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyMap())

    /** Szablony obserwowane reaktywnie. */
    val templates: StateFlow<List<GeneratorTemplate>> =
        templateRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** Liczba szablonów do badge. */
    val templateCount: StateFlow<Int> =
        templateRepository.observeCount()
            .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    init {
        loadAvailablePlaylists()
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Ładowanie playlist
    // ══════════════════════════════════════════════════════════════════════

    fun loadAvailablePlaylists() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPlaylists = true, error = null) }
            runCatching { repository.getUserPlaylists() }
                .onSuccess { playlists ->
                    val likedPlaylist = Playlist(
                        id = GeneratePlaylistUseCase.LIKED_SONGS_ID,
                        name = "❤ Polubione utwory",
                        description = null,
                        imageUrl = null,
                        trackCount = 0,
                        ownerId = ""
                    )
                    _state.update {
                        it.copy(
                            availablePlaylists = listOf(likedPlaylist) + playlists,
                            isLoadingPlaylists = false
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoadingPlaylists = false, error = e.message) }
                }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Repeat count
    // ══════════════════════════════════════════════════════════════════════

    fun onRepeatCountChange(count: Int) {
        _state.update { it.copy(repeatCount = count.coerceIn(1, 100)) }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Cele wyjściowe
    // ══════════════════════════════════════════════════════════════════════

    fun toggleTargetAction(action: TargetAction) {
        _state.update { state ->
            val current = state.targetActions.toMutableSet()
            if (action in current) {
                // Musi zostać przynajmniej jeden cel
                if (current.size > 1) {
                    current.remove(action)
                }
            } else {
                current.add(action)
            }
            state.copy(targetActions = current)
        }
    }

    fun setTargetPlaylist(playlistId: String, playlistName: String) {
        _state.update {
            it.copy(
                targetPlaylistId = playlistId,
                targetPlaylistName = playlistName
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Modyfikacja nazwy / Smooth Join
    // ══════════════════════════════════════════════════════════════════════

    fun onPlaylistNameChange(name: String) {
        _state.update { it.copy(newPlaylistName = name) }
    }

    fun onSmoothJoinChange(enabled: Boolean) {
        _state.update { it.copy(smoothJoin = enabled) }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Zarządzanie źródłami
    // ══════════════════════════════════════════════════════════════════════

    fun addSource() {
        _state.update { it.copy(sources = it.sources + PlaylistSource()) }
    }

    fun removeSource(id: String) {
        _state.update { it.copy(sources = it.sources.filter { s -> s.id != id }) }
    }

    fun updateSource(updated: PlaylistSource) {
        _state.update { s ->
            s.copy(sources = s.sources.map { src ->
                if (src.id == updated.id) {
                    val clampedPinned = if (updated.pinnedTracks.size > updated.trackCount)
                        updated.pinnedTracks.take(updated.trackCount)
                    else updated.pinnedTracks

                    val finalPinned = if (src.playlist?.id != updated.playlist?.id)
                        emptyList()
                    else clampedPinned

                    updated.copy(pinnedTracks = finalPinned)
                } else src
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Pinned Tracks
    // ══════════════════════════════════════════════════════════════════════

    fun openPinningDialog(sourceId: String) {
        val source = _state.value.sources.find { it.id == sourceId } ?: return
        val playlistId = source.playlist?.id ?: return

        viewModelScope.launch {
            _state.update { it.copy(pinningState = PinningState.Loading(sourceId)) }
            runCatching {
                // CACHE_FIRST: jeśli świeże, repository zwraca z Roomowego cache
                // bez requestu sieciowego. Inaczej fetch + zapis cache w jednym kroku.
                if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID)
                    repository.getLikedTracks(CachePolicy.CACHE_FIRST)
                else
                    repository.getPlaylistTracks(playlistId, CachePolicy.CACHE_FIRST)
            }.onSuccess { tracks ->
                _state.update {
                    it.copy(pinningState = PinningState.Picking(sourceId, tracks))
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        pinningState = PinningState.Idle,
                        error = "Nie udało się pobrać utworów: ${e.message}"
                    )
                }
            }
        }
    }

    fun refreshPinningTracks(sourceId: String) {
        val source = _state.value.sources.find { it.id == sourceId } ?: return
        val playlistId = source.playlist?.id ?: return

        viewModelScope.launch {
            _state.update { it.copy(pinningState = PinningState.Loading(sourceId)) }
            runCatching {
                // NETWORK_ONLY: wymuszony refresh, omija cache.
                // Repository i tak zaktualizuje cache po sukcesie.
                if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID)
                    repository.getLikedTracks(CachePolicy.NETWORK_ONLY)
                else
                    repository.getPlaylistTracks(playlistId, CachePolicy.NETWORK_ONLY)
            }.onSuccess { tracks ->
                _state.update {
                    it.copy(pinningState = PinningState.Picking(sourceId, tracks))
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        pinningState = PinningState.Idle,
                        error = "Nie udało się odświeżyć utworów: ${e.message}"
                    )
                }
            }
        }
    }

    fun closePinningDialog() {
        _state.update { it.copy(pinningState = PinningState.Idle) }
    }

    fun setPinnedTracks(sourceId: String, trackIds: List<String>) {
        val pinningState = _state.value.pinningState
        // Pobierz pełne dane z listy tracków w dialogu
        val tracksList = (pinningState as? PinningState.Picking)?.tracks ?: emptyList()
        val tracksById = tracksList.associateBy { it.id }

        _state.update { s ->
            s.copy(sources = s.sources.map { src ->
                if (src.id == sourceId) {
                    val clamped = trackIds.take(src.trackCount)
                    val pinnedInfos = clamped.mapNotNull { id ->
                        val track = tracksById[id] ?: return@mapNotNull null
                        PinnedTrackInfo(
                            id = id,
                            title = track.title,
                            artist = track.artist
                        )
                    }
                    src.copy(pinnedTracks = pinnedInfos)
                } else src
            })
        }
    }

    fun removePinnedTrack(sourceId: String, trackId: String) {
        _state.update { s ->
            s.copy(sources = s.sources.map { src ->
                if (src.id == sourceId)
                    src.copy(pinnedTracks = src.pinnedTracks.filter { it.id != trackId })
                else src
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Generowanie — główna logika z repeat loop
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Generuje podgląd — wykonuje szablon repeatCount razy.
     *
     * Każda iteracja:
     *  1. Wywołuje use-case z bieżącą exclusion listą
     *  2. Akumuluje wyniki w podglądzie
     *  3. Dodaje rundę do historii
     *  4. Jeśli pula wyczerpana → przerywa wcześniej
     */
    fun generatePreview() {
        val sources = _state.value.sources.filter { it.playlist != null }
        if (sources.isEmpty()) {
            _state.update { it.copy(error = "Wybierz przynajmniej jedną playlistę źródłową") }
            return
        }

        // Walidacja Wave
        for (src in sources) {
            val curve = src.energyCurve
            if (curve is EnergyCurve.Wave && src.trackCount < curve.tracksPerHalfWave) {
                _state.update {
                    it.copy(
                        error = "Zbyt mało utworów dla fali w ${src.playlist?.name}: " +
                                "min. ${curve.tracksPerHalfWave}, ustawiono ${src.trackCount}"
                    )
                }
                return
            }
        }

        val currentState = _state.value
        val repeatCount = currentState.repeatCount
        val hasCurves = sources.any { it.energyCurve !is EnergyCurve.None }
        val templateName = resolveTemplateName(sources)

        viewModelScope.launch {
            _state.update {
                it.copy(isGenerating = true, error = null, repeatProgress = 0)
            }

            var runningUsedIds = currentState.usedTrackIds
            val newRounds = mutableListOf<GenerationRound>()
            val allNewTracks = mutableListOf<Track>()
            var lastGenerateResult: GenerateResult? = null
            var lastExhaustionStatuses = emptyList<ExhaustionStatus>()
            var exhausted = false

            for (iteration in 1..repeatCount) {
                _state.update { it.copy(repeatProgress = iteration) }

                if (hasCurves) {
                    val result = runCatching {
                        generatePlaylist.generateWithCurves(
                            sources = sources,
                            smoothJoin = currentState.smoothJoin,
                            excludeTrackIds = runningUsedIds
                        )
                    }.getOrElse { e ->
                        _state.update {
                            it.copy(isGenerating = false, error = e.message, repeatProgress = 0)
                        }
                        return@launch
                    }

                    val newTracks = result.generateResult.tracks
                    lastGenerateResult = result.generateResult
                    lastExhaustionStatuses = result.exhaustionStatuses

                    if (newTracks.isEmpty()) {
                        // Pula wyczerpana — przerwij pętlę
                        exhausted = true
                        break
                    }

                    val newIds = result.allGeneratedTrackIds
                    runningUsedIds = runningUsedIds + newIds
                    allNewTracks.addAll(newTracks)

                    val roundMatched = result.generateResult.segments.flatMap { it.tracks }
                    val round = GenerationRound(
                        roundNumber = currentState.generationHistory.size + newRounds.size + 1,
                        templateName = templateName,
                        trackIds = newIds,
                        tracks = newTracks,
                        matchedTracks = roundMatched
                    )
                    newRounds.add(round)

                    // Jeśli któraś playlista się wyczerpała, przerwij
                    if (result.exhaustedPlaylists.isNotEmpty()) {
                        exhausted = true
                        break
                    }
                } else {
                    // Ścieżka bez krzywych
                    val tracks = runCatching {
                        generatePlaylist(sources, runningUsedIds)
                    }.getOrElse { e ->
                        _state.update {
                            it.copy(isGenerating = false, error = e.message, repeatProgress = 0)
                        }
                        return@launch
                    }

                    if (tracks.isEmpty()) {
                        exhausted = true
                        break
                    }

                    val newIds = tracks.mapNotNull { it.id }.toSet()
                    runningUsedIds = runningUsedIds + newIds
                    allNewTracks.addAll(tracks)

                    // Zbuduj MatchedTrack dla ścieżki no-curves:
                    // composite score z features gdy dostępne, targetScore = 0f (brak krzywej)
                    val noCurvesFeatures = featuresRepository.getFeaturesMap(newIds.toList())
                    val roundMatched = tracks.map { track ->
                        val score = track.id
                            ?.let { noCurvesFeatures[it] }
                            ?.let { CompositeScoreCalculator.calculate(it) }
                            ?: CompositeScoreCalculator.DEFAULT_SCORE
                        MatchedTrack(track = track, compositeScore = score, targetScore = 0f)
                    }

                    val round = GenerationRound(
                        roundNumber = currentState.generationHistory.size + newRounds.size + 1,
                        templateName = templateName,
                        trackIds = newIds,
                        tracks = tracks,
                        matchedTracks = roundMatched
                    )
                    newRounds.add(round)
                }
            }
            // Wyczyść pinned tracks po wygenerowaniu (jednorazowe)
            _state.update { st ->
                st.copy(sources = st.sources.map { it.copy(pinnedTracks = emptyList()) })
            }
            // Kumuluj podgląd: istniejące + nowe
            val cumulativePreview = (currentState.previewTracks ?: emptyList()) + allNewTracks

            _state.update {
                it.copy(
                    isGenerating = false,
                    repeatProgress = 0,
                    previewTracks = cumulativePreview.ifEmpty { it.previewTracks },
                    generateResult = lastGenerateResult ?: it.generateResult,
                    usedTrackIds = runningUsedIds,
                    generationHistory = it.generationHistory + newRounds,
                    exhaustionStatuses = lastExhaustionStatuses.ifEmpty { it.exhaustionStatuses },
                    isSessionActive = (it.generationHistory + newRounds).isNotEmpty()
                )
            }

            // Załaduj audio features dla podglądu (inkrementalnie)
            loadFeaturesForCurrentPreview()

            // Komunikaty
            val completedRounds = newRounds.size
            if (exhausted && allNewTracks.isEmpty()) {
                _state.update {
                    it.copy(error = "Playlisty wyczerpane — brak nowych utworów do wygenerowania.")
                }
            } else if (exhausted) {
                _state.update {
                    it.copy(
                        error = "Playlisty wyczerpane po $completedRounds z $repeatCount powtórzeń. " +
                                "Wygenerowano ${allNewTracks.size} utworów."
                    )
                }
            }
        }
    }

    /**
     * Generuj ponownie od zera — czyści sesję i generuje.
     */
    fun generateFromScratch() {
        _state.update {
            it.copy(
                usedTrackIds = emptySet(),
                generationHistory = emptyList(),
                exhaustionStatuses = emptyList(),
                previewTracks = null,
                generateResult = null,
                isSessionActive = false,
                savedPlaylistUrl = null
            )
        }
        generatePreview()
    }

    /**
     * Dodaj więcej — zachowuje sesję i generuje kolejną rundę/rundy.
     */
    fun generateMore() {
        generatePreview()
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Zmiana kolejności / usuwanie w podglądzie
    // ══════════════════════════════════════════════════════════════════════

    fun removeTrackFromPreview(index: Int) {
        val tracks = _state.value.previewTracks?.toMutableList() ?: return
        val removedTrack = tracks.removeAt(index)

        // Usuń też z usedTrackIds żeby utwór mógł być ponownie użyty
        val updatedUsedIds = if (removedTrack.id != null) {
            _state.value.usedTrackIds - removedTrack.id!!
        } else {
            _state.value.usedTrackIds
        }

        _state.update {
            it.copy(
                previewTracks = tracks.ifEmpty { null },
                usedTrackIds = updatedUsedIds
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Zapis do Spotify
    // ══════════════════════════════════════════════════════════════════════

    fun saveToSpotify() {
        val tracks = _state.value.previewTracks
        val name = _state.value.newPlaylistName.trim().ifBlank { "Nowa Playlista" }
        if (tracks.isNullOrEmpty()) {
            _state.update { it.copy(error = "Brak utworów do zapisania") }
            return
        }

        val targetActions = _state.value.targetActions

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }

            val uris = tracks.mapNotNull { it.uri }
            var savedUrl: String? = null
            var hasError = false

            // Nowa playlista
            if (TargetAction.NEW_PLAYLIST in targetActions) {
                runCatching {
                    val playlistId = repository.createPlaylist(
                        name = name,
                        description = "Wygenerowano przez Spotify Playlist Manager"
                    )
                    repository.addTracksToPlaylist(playlistId, uris)
                    "https://open.spotify.com/playlist/$playlistId"
                }.onSuccess { url ->
                    savedUrl = url
                }.onFailure { e ->
                    _state.update { it.copy(error = "Błąd tworzenia playlisty: ${e.message}") }
                    hasError = true
                }
            }

            // Istniejąca playlista
            if (!hasError && TargetAction.EXISTING_PLAYLIST in targetActions) {
                val targetId = _state.value.targetPlaylistId
                if (targetId != null) {
                    runCatching {
                        repository.addTracksToPlaylist(targetId, uris)
                    }.onFailure { e ->
                        _state.update {
                            it.copy(error = "Błąd dodawania do playlisty: ${e.message}")
                        }
                        hasError = true
                    }
                } else {
                    _state.update { it.copy(error = "Wybierz playlistę docelową") }
                    hasError = true
                }
            }

            // Kolejka — dry-run dialog
            if (!hasError && TargetAction.QUEUE in targetActions) {
                _state.update { it.copy(showQueueDryRun = true, isSaving = false) }
                return@launch
            }

            _state.update {
                it.copy(
                    isSaving = false,
                    savedPlaylistUrl = savedUrl
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Dodawanie do kolejki odtwarzania
    // ══════════════════════════════════════════════════════════════════════

    fun dismissQueueDryRun() {
        _state.update { it.copy(showQueueDryRun = false) }
    }

    fun confirmAddToQueue() {
        val tracks = _state.value.previewTracks
        if (tracks.isNullOrEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(showQueueDryRun = false, isAddingToQueue = true) }

            val uris = tracks.mapNotNull { it.uri }
            var addedCount = 0

            for (uri in uris) {
                runCatching {
                    repository.addToQueue(uri)
                    addedCount++
                }.onFailure { e ->
                    val errorMsg = when {
                        e.message?.contains("404") == true ||
                                e.message?.contains("No active device") == true ->
                            "Brak aktywnego odtwarzacza Spotify. " +
                                    "Włącz odtwarzanie na dowolnym urządzeniu i spróbuj ponownie. " +
                                    "Dodano $addedCount z ${uris.size} utworów."

                        else ->
                            "Błąd dodawania do kolejki: ${e.message}. " +
                                    "Dodano $addedCount z ${uris.size} utworów."
                    }
                    _state.update { it.copy(isAddingToQueue = false, error = errorMsg) }
                    return@launch
                }
            }

            _state.update {
                it.copy(
                    isAddingToQueue = false,
                    error = null
                )
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Reset i czyszczenie
    // ══════════════════════════════════════════════════════════════════════

    fun clearSavedState() {
        _state.update {
            it.copy(
                savedPlaylistUrl = null,
                previewTracks = null,
                generateResult = null
            )
        }
    }

    fun resetSession() {
        _state.update {
            it.copy(
                usedTrackIds = emptySet(),
                generationHistory = emptyList(),
                exhaustionStatuses = emptyList(),
                previewTracks = null,
                generateResult = null,
                isSessionActive = false,
                savedPlaylistUrl = null,
                showQueueDryRun = false,
                pinningState = PinningState.Idle
            )
        }
        // tracksCache usunięty — Roomowy cache w IPlaylistCacheRepository
        // jest persistent i sam zarządza świeżością przez snapshot_id.
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Szablony
    // ══════════════════════════════════════════════════════════════════════

    fun saveAsTemplate(name: String) {
        val sources = _state.value.sources.filter { it.playlist != null }
        if (sources.isEmpty()) {
            _state.update { it.copy(error = "Brak źródeł do zapisania") }
            return
        }
        viewModelScope.launch {
            runCatching {
                templateRepository.save(
                    GeneratorTemplate(
                        name = name,
                        sources = sources.mapIndexed { idx, src ->
                            TemplateSource(
                                position = idx,
                                playlistId = src.playlist!!.id,
                                playlistName = src.playlist!!.name,
                                trackCount = src.trackCount,
                                sortBy = src.sortBy,
                                energyCurve = src.energyCurve
                            )
                        }
                    ))
            }.onFailure { e ->
                _state.update { it.copy(error = "Błąd zapisu szablonu: ${e.message}") }
            }
        }
    }

    fun loadTemplate(template: GeneratorTemplate) {
        val available = _state.value.availablePlaylists
        val newSources = template.sources.map { src ->
            val playlist = available.find { it.id == src.playlistId }
                ?: Playlist(src.playlistId, src.playlistName, null, null, 0, "")
            PlaylistSource(
                playlist = playlist,
                trackCount = src.trackCount,
                sortBy = src.sortBy,
                energyCurve = src.energyCurve
            )
        }
        _state.update {
            it.copy(
                sources = newSources.ifEmpty { listOf(PlaylistSource()) },
                // NIE czyścimy sesji — użytkownik może chcieć "Zmień szablon i dodaj"
                previewTracks = if (it.isSessionActive) it.previewTracks else null,
                generateResult = if (it.isSessionActive) it.generateResult else null
            )
        }
    }

    fun renameTemplate(id: Long, newName: String) {
        viewModelScope.launch { templateRepository.rename(id, newName) }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { templateRepository.delete(id) }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpery prywatne
    // ══════════════════════════════════════════════════════════════════════

    private fun resolveTemplateName(sources: List<PlaylistSource>): String {
        return if (sources.size == 1) {
            sources.first().playlist?.name ?: "Szablon"
        } else {
            "${sources.size} źródeł"
        }
    }

    /**
     * Ładuje audio features dla wszystkich utworów w bieżącym podglądzie.
     * Wywoływane po każdej operacji modyfikującej previewTracks.
     * Różnica od TracksViewModel: robimy merge zamiast zastępowania, żeby
     * nie tracić features między wywołaniami (np. po usunięciu jednego utworu
     * nie trzeba ponownie fetchować pozostałych).
     */
    private fun loadFeaturesForCurrentPreview() {
        val ids = _state.value.previewTracks
            ?.mapNotNull { it.id }
            ?.distinct()
            ?: return
        if (ids.isEmpty()) {
            _featuresMap.value = emptyMap()
            return
        }
        // Pobierz tylko brakujące
        val missing = ids.filterNot { _featuresMap.value.containsKey(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            runCatching { featuresRepository.getFeaturesMap(missing) }
                .onSuccess { fresh ->
                    _featuresMap.update { current -> current + fresh }
                }
        }
    }
}
