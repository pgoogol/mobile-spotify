package com.spotify.playlistmanager.ui.screens.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.PlaylistSource
import com.spotify.playlistmanager.data.model.SortOption
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.CompositeScoreCalculator
import com.spotify.playlistmanager.domain.model.EnergyCurve
import com.spotify.playlistmanager.domain.model.ExhaustionStatus
import com.spotify.playlistmanager.domain.model.GenerateResult
import com.spotify.playlistmanager.domain.model.GenerationRound
import com.spotify.playlistmanager.domain.model.GeneratorTemplate
import com.spotify.playlistmanager.domain.model.MatchedTrack
import com.spotify.playlistmanager.domain.model.SegmentMatchResult
import com.spotify.playlistmanager.domain.model.TargetAction
import com.spotify.playlistmanager.domain.model.TemplateSource
import com.spotify.playlistmanager.domain.repository.CachePolicy
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import com.spotify.playlistmanager.domain.usecase.FindReplacementsUseCase
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
    val pinningState: PinningState = PinningState.Idle,

    /** Stan procesu wymiany utworu. */
    val replacementState: ReplacementState = ReplacementState.Idle,

    /** Snapshot ostatniej wymiany do obsługi Undo. Null = brak dostępnego Undo. */
    val lastReplacement: ReplacementSnapshot? = null,

    /** Czy wykres pokazuje tylko ostatnią rundę (true) czy całą sesję (false). */
    val chartShowOnlyLastRound: Boolean = false
)

/**
 * Stan dialogu przypinania utworów.
 *
 * Picking trzyma:
 *  - liste dostepnych playlist do wyboru w pickerze (Liked + user playlists)
 *  - aktualnie wyswietlana playliste (selectedPlaylistId)
 *  - tracki tej playlisty (currentTracks)
 *  - draftSelected: aktualnie wybrane pinned (CROSS-PLAYLIST!) — to "pre-commit"
 *    stan, zatwierdzany dopiero przy onConfirm
 *
 * switchingPlaylist = true gdy ladujemy nowa playliste po przelaczeniu w pickerze
 * (pokazuje spinner inline w dialogu zamiast zamykac i otwierac go ponownie)
 */
sealed class PinningState {
    data object Idle : PinningState()
    data class Loading(val sourceId: String) : PinningState()
    data class Picking(
        val sourceId: String,
        val availablePlaylists: List<Playlist>,
        val selectedPlaylistId: String,
        val currentTracks: List<Track>,
        val draftSelected: List<PinnedTrackInfo>,
        val switchingPlaylist: Boolean = false
    ) : PinningState()

}

/**
 * Stan procesu wymiany pojedynczego utworu w podglądzie.
 */
sealed class ReplacementState {
    data object Idle : ReplacementState()
    data class Loading(val previewIndex: Int) : ReplacementState()
    data class Picking(
        val previewIndex: Int,
        val originalTrack: Track,
        val originalCompositeScore: Float,
        val candidates: List<FindReplacementsUseCase.ReplacementCandidate>
    ) : ReplacementState()

    data class Error(val message: String) : ReplacementState()
}

/**
 * Snapshot pojedynczej wymiany — do obsługi Undo przez snackbar.
 * Po wykonaniu kolejnej wymiany lub innej operacji na podglądzie snapshot jest czyszczony.
 */
data class ReplacementSnapshot(
    val previewIndex: Int,
    val removedTrackId: String,
    val removedTrack: Track,
    val removedMatched: MatchedTrack?,
    val removedSourcePlaylistId: String?,
    val insertedTrackId: String,
    val roundNumber: Int
)

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val repository: ISpotifyRepository,
    private val generatePlaylist: GeneratePlaylistUseCase,
    private val templateRepository: IGeneratorTemplateRepository,
    private val featuresRepository: ITrackFeaturesRepository,
    private val findReplacements: FindReplacementsUseCase
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

                    val finalPinned = if (src.playlist?.id != updated.playlist?.id) {
                        // Zmieniono playliste zrodla segmentu — kasujemy pinned ze
                        // STAREJ playlisty zrodla, ale pinned z innych playlist
                        // przetrwuja (bo ich zrodlo to inna, niezmieniona playlista).
                        val oldSourceId = src.playlist?.id
                        clampedPinned.filter { pinned ->
                            pinned.sourcePlaylistId != null &&
                                    pinned.sourcePlaylistId != oldSourceId
                        }
                    } else clampedPinned

                    updated.copy(pinnedTracks = finalPinned)
                } else src
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Pinned Tracks (cross-playlist)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Otwiera dialog przypinania dla danego segmentu.
     * Domyslnie wyswietla playliste zrodla segmentu (lub Liked jesli zrodlo jest puste).
     * Zachowuje aktualnie pinned tracks segmentu jako draftSelected.
     */
    fun openPinningDialog(sourceId: String) {
        val source = _state.value.sources.find { it.id == sourceId } ?: return
        val initialPlaylistId = source.playlist?.id
            ?: GeneratePlaylistUseCase.LIKED_SONGS_ID

        viewModelScope.launch {
            _state.update { it.copy(pinningState = PinningState.Loading(sourceId)) }

            // Zaladuj liste playlist (CACHE_FIRST)
            val playlists = runCatching {
                repository.getUserPlaylists(CachePolicy.CACHE_FIRST)
            }.getOrDefault(emptyList())

            val likedPlaylist = Playlist(
                id = GeneratePlaylistUseCase.LIKED_SONGS_ID,
                name = "\u2764 Polubione utwory",
                description = null,
                imageUrl = null,
                trackCount = 0,
                ownerId = ""
            )
            val available = listOf(likedPlaylist) + playlists

            // Zaladuj tracki dla domyslnej playlisty
            val tracks = runCatching {
                fetchTracksForPicker(initialPlaylistId, CachePolicy.CACHE_FIRST)
            }.getOrElse { e ->
                _state.update {
                    it.copy(
                        pinningState = PinningState.Idle,
                        error = "Nie udalo sie pobrac utworow: ${e.message}"
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    pinningState = PinningState.Picking(
                        sourceId = sourceId,
                        availablePlaylists = available,
                        selectedPlaylistId = initialPlaylistId,
                        currentTracks = tracks,
                        draftSelected = source.pinnedTracks
                    )
                )
            }
        }
    }

    /**
     * Wywolywane gdy user wybiera inna playliste w dropdownie pickera.
     * Laduje tracki nowej playlisty bez resetowania draftSelected.
     */
    fun switchPinningPlaylist(playlistId: String) {
        val current = _state.value.pinningState as? PinningState.Picking ?: return
        if (current.selectedPlaylistId == playlistId) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    pinningState = current.copy(
                        selectedPlaylistId = playlistId,
                        currentTracks = emptyList(),
                        switchingPlaylist = true
                    )
                )
            }
            runCatching {
                fetchTracksForPicker(playlistId, CachePolicy.CACHE_FIRST)
            }.onSuccess { tracks ->
                val latest = _state.value.pinningState as? PinningState.Picking
                if (latest != null && latest.sourceId == current.sourceId) {
                    _state.update {
                        it.copy(
                            pinningState = latest.copy(
                                currentTracks = tracks,
                                switchingPlaylist = false
                            )
                        )
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        pinningState = current.copy(switchingPlaylist = false),
                        error = "Nie udalo sie pobrac utworow: ${e.message}"
                    )
                }
            }
        }
    }

    /** Wymusza fetch z sieci dla aktualnie wybranej playlisty w pickerze. */
    fun refreshPinningTracks() {
        val current = _state.value.pinningState as? PinningState.Picking ?: return

        viewModelScope.launch {
            _state.update {
                it.copy(pinningState = current.copy(switchingPlaylist = true))
            }
            runCatching {
                fetchTracksForPicker(current.selectedPlaylistId, CachePolicy.NETWORK_ONLY)
            }.onSuccess { tracks ->
                val latest = _state.value.pinningState as? PinningState.Picking
                if (latest != null && latest.sourceId == current.sourceId) {
                    _state.update {
                        it.copy(
                            pinningState = latest.copy(
                                currentTracks = tracks,
                                switchingPlaylist = false
                            )
                        )
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        pinningState = current.copy(switchingPlaylist = false),
                        error = "Nie udalo sie odswiezyc utworow: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Toggluje wybor utworu w biezacym dialogu.
     * Trzyma sourcePlaylistId i fullTrack — zeby use case pozniej mogl uzyc
     * pinned z obcej playlisty bez dodatkowego fetcha.
     *
     * Limit maxSelection (= source.trackCount) jest egzekwowany tutaj —
     * jesli draft jest pelny, klik na nowy utwor jest no-op.
     */
    fun togglePinnedDraft(track: Track, fromPlaylistId: String) {
        val current = _state.value.pinningState as? PinningState.Picking ?: return
        val source = _state.value.sources.find { it.id == current.sourceId } ?: return
        val trackId = track.id ?: return

        val isAlready = current.draftSelected.any { it.id == trackId }
        val newDraft = if (isAlready) {
            current.draftSelected.filterNot { it.id == trackId }
        } else {
            if (current.draftSelected.size >= source.trackCount) return
            current.draftSelected + PinnedTrackInfo(
                id = trackId,
                title = track.title,
                artist = track.artist,
                albumArtUrl = track.albumArtUrl,
                sourcePlaylistId = fromPlaylistId,
                fullTrack = track
            )
        }
        _state.update {
            it.copy(pinningState = current.copy(draftSelected = newDraft))
        }
    }

    /** Zatwierdza draftSelected jako finalne pinned tracks dla segmentu. */
    fun confirmPinnedDraft() {
        val current = _state.value.pinningState as? PinningState.Picking ?: return
        val sourceId = current.sourceId
        val draft = current.draftSelected

        _state.update { s ->
            s.copy(
                sources = s.sources.map { src ->
                    if (src.id == sourceId) {
                        val clamped = draft.take(src.trackCount)
                        src.copy(pinnedTracks = clamped)
                    } else src
                },
                pinningState = PinningState.Idle
            )
        }
    }

    fun closePinningDialog() {
        _state.update { it.copy(pinningState = PinningState.Idle) }
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

    /**
     * Helper: pobiera tracki dla pickera. Honoruje LIKED_SONGS_ID i CachePolicy.
     */
    private suspend fun fetchTracksForPicker(
        playlistId: String,
        policy: CachePolicy
    ): List<Track> = if (playlistId == GeneratePlaylistUseCase.LIKED_SONGS_ID) {
        repository.getLikedTracks(policy)
    } else {
        repository.getPlaylistTracks(playlistId, policy)
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
            val accumulatedSegments = mutableListOf<SegmentMatchResult>()
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
                    // Numer rundy = ile rund już było w sesji + ile w bieżącej pętli + 1
                    val thisRoundNumber =
                        currentState.generationHistory.size + newRounds.size + 1
                    accumulatedSegments.addAll(
                        result.generateResult.segments.map { it.copy(roundNumber = thisRoundNumber) }
                    )
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

                    // Zbuduj mapę trackId → sourcePlaylistId.
                    // Segments są w tej samej kolejności co sources, więc iterujemy parami.
                    val sourceMap = buildMap<String, String> {
                        result.generateResult.segments.forEachIndexed { segIdx, segment ->
                            val srcPlaylistId =
                                sources.getOrNull(segIdx)?.playlist?.id ?: return@forEachIndexed
                            for (mt in segment.tracks) {
                                val id = mt.track.id ?: continue
                                put(id, srcPlaylistId)
                            }
                        }
                    }

                    val round = GenerationRound(
                        roundNumber = currentState.generationHistory.size + newRounds.size + 1,
                        templateName = templateName,
                        trackIds = newIds,
                        tracks = newTracks,
                        matchedTracks = roundMatched,
                        trackToSourceMap = sourceMap
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

                    // Mapowanie trackId → sourcePlaylistId dla ścieżki bez krzywych.
                    // Bez krzywych nie mamy segments, więc przypisujemy utwory do sources
                    // sekwencyjnie (w kolejności generowania: source[0] dostaje pierwsze N, itd.)
                    // UWAGA: ta ścieżka używa GeneratePlaylistUseCase.invoke() które samo
                    // iteruje po sources i bierze po trackCount z każdego — więc kolejność
                    // utworów w 'tracks' odpowiada kolejności źródeł.
                    val sourceMap = buildMap<String, String> {
                        var cursor = 0
                        for (src in sources) {
                            val srcId = src.playlist?.id ?: continue
                            val take = minOf(src.trackCount, tracks.size - cursor)
                            if (take <= 0) break
                            for (i in 0 until take) {
                                val id = tracks[cursor + i].id ?: continue
                                put(id, srcId)
                            }
                            cursor += take
                        }
                    }

                    val round = GenerationRound(
                        roundNumber = currentState.generationHistory.size + newRounds.size + 1,
                        templateName = templateName,
                        trackIds = newIds,
                        tracks = tracks,
                        matchedTracks = roundMatched,
                        trackToSourceMap = sourceMap
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

            // Kumuluj segmenty wykresu: stare segmenty sesji + nowo wygenerowane.
            // Dzięki temu wykres pokazuje cała historię, nie tylko ostatnią rundę.
            val mergedSegments = if (currentState.isSessionActive) {
                (currentState.generateResult?.segments.orEmpty()) + accumulatedSegments
            } else {
                accumulatedSegments.toList()
            }

            val mergedGenerateResult: GenerateResult? = if (mergedSegments.isEmpty()) {
                null
            } else {
                val curveSegs = mergedSegments.filter { it.targetScores.isNotEmpty() }
                val overall = if (curveSegs.isEmpty()) 1f
                else curveSegs.map { it.matchPercentage }.average().toFloat()
                GenerateResult(
                    tracks = mergedSegments.flatMap { seg -> seg.tracks.map { it.track } },
                    segments = mergedSegments,
                    overallMatchPercentage = overall
                )
            }

            _state.update {
                it.copy(
                    isGenerating = false,
                    repeatProgress = 0,
                    previewTracks = cumulativePreview.ifEmpty { it.previewTracks },
                    generateResult = mergedGenerateResult ?: it.generateResult,
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

    /**
     * Przełącza widok wykresu między "cała sesja" a "tylko ostatnia runda".
     */
    fun toggleChartScope() {
        _state.update { it.copy(chartShowOnlyLastRound = !it.chartShowOnlyLastRound) }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Wymiana pojedynczego utworu
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Automatyczna wymiana utworu na pierwszego kandydata (najlepsze dopasowanie).
     * Zapisuje snapshot do ReplacementSnapshot dla obsługi Undo przez snackbar.
     */
    fun replaceTrackAuto(previewIndex: Int) {
        val context = prepareReplacement(previewIndex) ?: return

        _state.update { it.copy(replacementState = ReplacementState.Loading(previewIndex)) }

        viewModelScope.launch {
            val candidates = runCatching {
                findReplacements(
                    sourcePlaylistId = context.sourcePlaylistId,
                    currentCompositeScore = context.originalCompositeScore,
                    excludeTrackIds = context.excludeIds,
                    energyCurve = context.energyCurve,
                    sortBy = context.sortBy,
                    maxResults = 1
                )
            }.getOrElse { e ->
                _state.update {
                    it.copy(
                        replacementState = ReplacementState.Error(
                            e.message ?: "Błąd wyszukiwania"
                        )
                    )
                }
                return@launch
            }

            val best = candidates.firstOrNull()
            if (best == null) {
                _state.update {
                    it.copy(
                        replacementState = ReplacementState.Error(
                            "Brak więcej utworów w playliście \"${context.sourcePlaylistName}\" do wymiany"
                        )
                    )
                }
                return@launch
            }

            commitReplacement(context, best.track, best.compositeScore)
            _state.update { it.copy(replacementState = ReplacementState.Idle) }
        }
    }

    /**
     * Otwiera picker z listą kandydatów do ręcznego wyboru.
     */
    fun startReplacementPicker(previewIndex: Int) {
        val context = prepareReplacement(previewIndex) ?: return

        _state.update { it.copy(replacementState = ReplacementState.Loading(previewIndex)) }

        viewModelScope.launch {
            val candidates = runCatching {
                findReplacements(
                    sourcePlaylistId = context.sourcePlaylistId,
                    currentCompositeScore = context.originalCompositeScore,
                    excludeTrackIds = context.excludeIds,
                    energyCurve = context.energyCurve,
                    sortBy = context.sortBy,
                    maxResults = 10
                )
            }.getOrElse { e ->
                _state.update {
                    it.copy(
                        replacementState = ReplacementState.Error(
                            e.message ?: "Błąd wyszukiwania"
                        )
                    )
                }
                return@launch
            }

            if (candidates.isEmpty()) {
                _state.update {
                    it.copy(
                        replacementState = ReplacementState.Error(
                            "Brak więcej utworów w playliście \"${context.sourcePlaylistName}\" do wymiany"
                        )
                    )
                }
                return@launch
            }

            _state.update {
                it.copy(
                    replacementState = ReplacementState.Picking(
                        previewIndex = previewIndex,
                        originalTrack = context.originalTrack,
                        originalCompositeScore = context.originalCompositeScore,
                        candidates = candidates
                    )
                )
            }
        }
    }

    /**
     * Potwierdzenie wyboru kandydata z pickera.
     */
    fun confirmReplacement(candidate: FindReplacementsUseCase.ReplacementCandidate) {
        val picking = _state.value.replacementState as? ReplacementState.Picking ?: return
        val context = prepareReplacement(picking.previewIndex) ?: return
        commitReplacement(context, candidate.track, candidate.compositeScore)
        _state.update { it.copy(replacementState = ReplacementState.Idle) }
    }

    /**
     * Zamknięcie pickera bez zmian.
     */
    fun cancelReplacement() {
        _state.update { it.copy(replacementState = ReplacementState.Idle) }
    }

    /**
     * Cofa ostatnią wymianę (dostępne po tapnięciu "Cofnij" w snackbarze).
     */
    fun undoLastReplacement() {
        val snapshot = _state.value.lastReplacement ?: return
        val preview = _state.value.previewTracks?.toMutableList() ?: return
        if (snapshot.previewIndex !in preview.indices) return

        val insertedId = snapshot.insertedTrackId
        val removedOriginal = snapshot.removedTrack
        val removedId = snapshot.removedTrackId

        // Przywróć utwór w podglądzie
        preview[snapshot.previewIndex] = removedOriginal

        // Cofnij zmiany w usedTrackIds: dodaj removedId, usuń insertedId
        val updatedUsed = (_state.value.usedTrackIds - insertedId) + removedId

        // Zrewertuj GenerationRound
        val updatedHistory = _state.value.generationHistory.map { round ->
            if (round.roundNumber != snapshot.roundNumber) return@map round

            val newTrackIds = (round.trackIds - insertedId) + removedId
            val newTracks = round.tracks.map { t ->
                if (t.id == insertedId) removedOriginal else t
            }
            val newMatched = round.matchedTracks.map { mt ->
                if (mt.track.id == insertedId) {
                    snapshot.removedMatched ?: mt.copy(track = removedOriginal)
                } else mt
            }
            val newSourceMap = buildMap<String, String> {
                putAll(round.trackToSourceMap)
                remove(insertedId)
                snapshot.removedSourcePlaylistId?.let { put(removedId, it) }
            }

            round.copy(
                trackIds = newTrackIds,
                tracks = newTracks,
                matchedTracks = newMatched,
                trackToSourceMap = newSourceMap
            )
        }

        _state.update {
            it.copy(
                previewTracks = preview,
                usedTrackIds = updatedUsed,
                generationHistory = updatedHistory,
                lastReplacement = null
            )
        }

        // Odśwież features dla przywróconego ID
        loadFeaturesForCurrentPreview()
    }

    /**
     * Czyści snapshot (wywoływane gdy snackbar znika lub pojawia się nowa operacja).
     */
    fun clearLastReplacement() {
        if (_state.value.lastReplacement != null) {
            _state.update { it.copy(lastReplacement = null) }
        }
    }

    // ── Helpery wymiany ────────────────────────────────────────────────

    /**
     * Kontekst wymagany do przeprowadzenia wymiany: wszystkie informacje o oryginale
     * oraz konfiguracja źródła potrzebna do wyszukiwania kandydatów.
     */
    private data class ReplacementContext(
        val previewIndex: Int,
        val originalTrack: Track,
        val originalTrackId: String,
        val originalMatched: MatchedTrack?,
        val originalCompositeScore: Float,
        val sourcePlaylistId: String,
        val sourcePlaylistName: String,
        val energyCurve: EnergyCurve,
        val sortBy: SortOption,
        val roundNumber: Int,
        val excludeIds: Set<String>
    )

    private fun prepareReplacement(previewIndex: Int): ReplacementContext? {
        val state = _state.value
        val preview = state.previewTracks ?: return null
        if (previewIndex !in preview.indices) return null

        val original = preview[previewIndex]
        val originalId = original.id ?: run {
            _state.update {
                it.copy(replacementState = ReplacementState.Error("Utwór bez ID — nie można wymienić"))
            }
            return null
        }

        // Znajdź rundę z której pochodzi ten utwór (pierwszą, która go zawiera)
        val round = state.generationHistory.firstOrNull { originalId in it.trackIds }
        if (round == null) {
            _state.update {
                it.copy(replacementState = ReplacementState.Error("Utwór spoza historii generowania"))
            }
            return null
        }

        val sourcePlaylistId = round.trackToSourceMap[originalId]
        if (sourcePlaylistId == null) {
            _state.update {
                it.copy(
                    replacementState = ReplacementState.Error(
                        "Brak informacji o źródle utworu — wymień po ponownym wygenerowaniu"
                    )
                )
            }
            return null
        }

        // Znajdź aktualny PlaylistSource z tą playlistą (dla curve i sortBy)
        val source = state.sources.firstOrNull { it.playlist?.id == sourcePlaylistId }
        val energyCurve = source?.energyCurve ?: EnergyCurve.None
        val sortBy = source?.sortBy ?: SortOption.NONE
        val sourceName = source?.playlist?.name
            ?: state.availablePlaylists.firstOrNull { it.id == sourcePlaylistId }?.name
            ?: "nieznane źródło"

        val matched = round.matchedTracks.firstOrNull { it.track.id == originalId }
        val score = matched?.compositeScore ?: 0f

        // Exclude: wszystkie utwory aktualnie w podglądzie (poza wymienianym, żeby go można było wymienić na nowy)
        val excludeIds = preview.mapNotNull { it.id }.toSet()

        return ReplacementContext(
            previewIndex = previewIndex,
            originalTrack = original,
            originalTrackId = originalId,
            originalMatched = matched,
            originalCompositeScore = score,
            sourcePlaylistId = sourcePlaylistId,
            sourcePlaylistName = sourceName,
            energyCurve = energyCurve,
            sortBy = sortBy,
            roundNumber = round.roundNumber,
            excludeIds = excludeIds
        )
    }

    private fun commitReplacement(
        context: ReplacementContext,
        newTrack: Track,
        newCompositeScore: Float
    ) {
        val newId = newTrack.id ?: return
        val state = _state.value
        val preview = state.previewTracks?.toMutableList() ?: return
        if (context.previewIndex !in preview.indices) return

        preview[context.previewIndex] = newTrack

        val updatedUsed = (state.usedTrackIds - context.originalTrackId) + newId

        // Zaktualizuj rundę — podmień ID, track i matchedTrack
        val updatedHistory = state.generationHistory.map { round ->
            if (round.roundNumber != context.roundNumber) return@map round

            val newTrackIds = (round.trackIds - context.originalTrackId) + newId
            val newTracks = round.tracks.map { t ->
                if (t.id == context.originalTrackId) newTrack else t
            }
            val newMatched = round.matchedTracks.map { mt ->
                if (mt.track.id == context.originalTrackId) {
                    // Zachowaj targetScore z oryginału — reprezentuje pozycję w krzywej
                    MatchedTrack(
                        track = newTrack,
                        compositeScore = newCompositeScore,
                        targetScore = mt.targetScore
                    )
                } else mt
            }
            val newSourceMap = buildMap<String, String> {
                putAll(round.trackToSourceMap)
                remove(context.originalTrackId)
                put(newId, context.sourcePlaylistId)
            }

            round.copy(
                trackIds = newTrackIds,
                tracks = newTracks,
                matchedTracks = newMatched,
                trackToSourceMap = newSourceMap
            )
        }

        val snapshot = ReplacementSnapshot(
            previewIndex = context.previewIndex,
            removedTrackId = context.originalTrackId,
            removedTrack = context.originalTrack,
            removedMatched = context.originalMatched,
            removedSourcePlaylistId = context.sourcePlaylistId,
            insertedTrackId = newId,
            roundNumber = context.roundNumber
        )

        _state.update {
            it.copy(
                previewTracks = preview,
                usedTrackIds = updatedUsed,
                generationHistory = updatedHistory,
                lastReplacement = snapshot
            )
        }

        // Załaduj features dla nowego ID
        loadFeaturesForCurrentPreview()
    }

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
                usedTrackIds = updatedUsedIds,
                lastReplacement = null // usunięcie unieważnia snapshot Undo
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
