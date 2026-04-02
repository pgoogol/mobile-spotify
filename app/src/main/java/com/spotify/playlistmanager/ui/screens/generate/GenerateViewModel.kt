package com.spotify.playlistmanager.ui.screens.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.model.*
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Stan ekranu generowania ──────────────────────────────────────────────────
data class GenerateUiState(
    // ── Obecne pola (backward compatible) ─────────────────────────────────
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

    // ── Nowe pola: tryby i sesja ──────────────────────────────────────────
    /** Aktualny tryb generowania. */
    val generationMode: GenerationMode = GenerationMode.EXHAUST,

    /** Cele wyjściowe (multi-select w trybie SEGMENT). */
    val targetActions: Set<TargetAction> = setOf(TargetAction.NEW_PLAYLIST),

    /** ID istniejącej playlisty do której dodajemy (gdy EXISTING_PLAYLIST). */
    val targetPlaylistId: String? = null,
    val targetPlaylistName: String? = null,

    /** Zbiór ID już użytych utworów — globalna deduplikacja w sesji. */
    val usedTrackIds: Set<String> = emptySet(),

    /** Historia rund generowania w bieżącej sesji. */
    val generationHistory: List<GenerationRound> = emptyList(),

    /** Status wyczerpania per playlista źródłowa. */
    val exhaustionStatuses: List<ExhaustionStatus> = emptyList(),

    /** Czy sesja jest aktywna (przynajmniej jedna runda). */
    val isSessionActive: Boolean = false,

    /** Shuffle przed zapisem. */
    val shuffleBeforeSave: Boolean = false,

    /** Czy wyświetlać dry-run dialog dla queue. */
    val showQueueDryRun: Boolean = false,

    /** Czy dodawanie do kolejki jest w toku. */
    val isAddingToQueue: Boolean = false
)

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val repository: ISpotifyRepository,
    private val generatePlaylist: GeneratePlaylistUseCase,
    private val templateRepository: IGeneratorTemplateRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

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
    //  Tryby i ustawienia sesji
    // ══════════════════════════════════════════════════════════════════════

    fun onGenerationModeChange(mode: GenerationMode) {
        _state.update { it.copy(generationMode = mode) }
    }

    fun toggleTargetAction(action: TargetAction) {
        _state.update { state ->
            val current = state.targetActions.toMutableSet()
            if (action in current) {
                // NEW_PLAYLIST nie może być usunięty jeśli jest jedyny
                if (current.size > 1 || action != TargetAction.NEW_PLAYLIST) {
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

    fun onShuffleBeforeSaveChange(enabled: Boolean) {
        _state.update { it.copy(shuffleBeforeSave = enabled) }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Modyfikacja nazwy
    // ══════════════════════════════════════════════════════════════════════

    fun onPlaylistNameChange(name: String) {
        _state.update { it.copy(newPlaylistName = name) }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Smooth Join toggle
    // ══════════════════════════════════════════════════════════════════════

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
            s.copy(sources = s.sources.map { if (it.id == updated.id) updated else it })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Generowanie — główna logika z deduplikacją
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Generuje podgląd — dodaje nowe utwory do sesji (zachowuje historię).
     * W trybie EXHAUST: generuje pełny szablon.
     * W trybie SEGMENT: generuje dokładną porcję z szablonu.
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
                    it.copy(error = "Zbyt mało utworów dla fali w ${src.playlist?.name}: " +
                            "min. ${curve.tracksPerHalfWave}, ustawiono ${src.trackCount}")
                }
                return
            }
        }

        val currentState = _state.value
        val hasCurves = sources.any { it.energyCurve !is EnergyCurve.None }

        viewModelScope.launch {
            _state.update {
                it.copy(isGenerating = true, error = null)
            }

            if (hasCurves) {
                runCatching {
                    generatePlaylist.generateWithCurves(
                        sources = sources,
                        smoothJoin = currentState.smoothJoin,
                        excludeTrackIds = currentState.usedTrackIds
                    )
                }.onSuccess { result ->
                    handleGenerationSuccess(result, sources)
                }.onFailure { e ->
                    _state.update { it.copy(isGenerating = false, error = e.message) }
                }
            } else {
                // Backward compatible path z excludeTrackIds
                runCatching {
                    generatePlaylist(sources, currentState.usedTrackIds)
                }.onSuccess { tracks ->
                    handleSimpleGenerationSuccess(tracks, sources)
                }.onFailure { e ->
                    _state.update { it.copy(isGenerating = false, error = e.message) }
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
     * Dodaj więcej — zachowuje sesję i generuje kolejną rundę.
     */
    fun generateMore() {
        generatePreview()
    }

    private fun handleGenerationSuccess(
        result: GeneratePlaylistUseCase.GenerateWithExhaustionResult,
        sources: List<PlaylistSource>
    ) {
        val currentState = _state.value
        val newTrackIds = result.allGeneratedTrackIds
        val newTracks = result.generateResult.tracks

        // Sprawdź wyczerpanie w trybie EXHAUST
        if (currentState.generationMode == GenerationMode.EXHAUST &&
            result.exhaustedPlaylists.isNotEmpty() && newTracks.isEmpty()) {
            val names = result.exhaustedPlaylists.joinToString(", ") { it.playlistName }
            _state.update {
                it.copy(
                    isGenerating = false,
                    error = "Playlisty wyczerpane: $names — brak nowych utworów do wygenerowania.",
                    exhaustionStatuses = result.exhaustionStatuses
                )
            }
            return
        }

        // Utwórz rekord historii
        val round = GenerationRound(
            roundNumber = currentState.generationHistory.size + 1,
            templateName = resolveTemplateName(sources),
            trackIds = newTrackIds,
            tracks = newTracks,
            generationMode = currentState.generationMode
        )

        // Kumuluj podgląd: istniejące + nowe
        val cumulativePreview = (currentState.previewTracks ?: emptyList()) + newTracks

        _state.update {
            it.copy(
                isGenerating = false,
                previewTracks = cumulativePreview,
                generateResult = result.generateResult,
                usedTrackIds = it.usedTrackIds + newTrackIds,
                generationHistory = it.generationHistory + round,
                exhaustionStatuses = result.exhaustionStatuses,
                isSessionActive = true
            )
        }

        // Komunikat o wyczerpaniu (jeśli częściowo wyczerpane ale wygenerowało coś)
        if (result.exhaustedPlaylists.isNotEmpty()) {
            val names = result.exhaustedPlaylists.joinToString(", ") { it.playlistName }
            _state.update {
                it.copy(error = "Uwaga: wyczerpane playlisty: $names. " +
                        "Wygenerowano ${newTracks.size} utworów.")
            }
        }
    }

    private fun handleSimpleGenerationSuccess(
        tracks: List<Track>,
        sources: List<PlaylistSource>
    ) {
        val currentState = _state.value
        val newTrackIds = tracks.mapNotNull { it.id }.toSet()

        if (currentState.generationMode == GenerationMode.EXHAUST && tracks.isEmpty()) {
            _state.update {
                it.copy(
                    isGenerating = false,
                    error = "Brak nowych utworów do wygenerowania — playlisty wyczerpane."
                )
            }
            return
        }

        val round = GenerationRound(
            roundNumber = currentState.generationHistory.size + 1,
            templateName = resolveTemplateName(sources),
            trackIds = newTrackIds,
            tracks = tracks,
            generationMode = currentState.generationMode
        )

        val cumulativePreview = (currentState.previewTracks ?: emptyList()) + tracks

        _state.update {
            it.copy(
                isGenerating = false,
                previewTracks = cumulativePreview,
                usedTrackIds = it.usedTrackIds + newTrackIds,
                generationHistory = it.generationHistory + round,
                isSessionActive = true
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Cofanie ostatniego segmentu (Undo)
    // ══════════════════════════════════════════════════════════════════════

    fun undoLastSegment() {
        val currentState = _state.value
        val history = currentState.generationHistory
        if (history.isEmpty()) return

        val lastRound = history.last()
        val remainingHistory = history.dropLast(1)

        // Przelicz usedTrackIds z pozostałych rund
        val recalculatedUsedIds = remainingHistory
            .flatMap { it.trackIds }
            .toSet()

        // Odbuduj podgląd z pozostałych rund
        val recalculatedPreview = remainingHistory.flatMap { it.tracks }

        _state.update {
            it.copy(
                usedTrackIds = recalculatedUsedIds,
                generationHistory = remainingHistory,
                previewTracks = recalculatedPreview.ifEmpty { null },
                generateResult = null, // Wyczyść stary result (wykres będzie nieaktualny)
                isSessionActive = remainingHistory.isNotEmpty()
            )
        }

        // Odśwież statusy wyczerpania
        refreshExhaustionStatuses()
    }

    private fun refreshExhaustionStatuses() {
        val sources = _state.value.sources.filter { it.playlist != null }
        if (sources.isEmpty()) return

        viewModelScope.launch {
            runCatching {
                generatePlaylist.calculateExhaustionStatuses(
                    sources = sources,
                    usedTrackIds = _state.value.usedTrackIds
                )
            }.onSuccess { statuses ->
                _state.update { it.copy(exhaustionStatuses = statuses) }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Zmiana kolejności / usuwanie w podglądzie
    // ══════════════════════════════════════════════════════════════════════

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val tracks = _state.value.previewTracks?.toMutableList() ?: return
        val item = tracks.removeAt(fromIndex)
        tracks.add(toIndex, item)
        _state.update { it.copy(previewTracks = tracks) }
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
                usedTrackIds = updatedUsedIds
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Shuffle before save
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Miesza podgląd przed zapisem.
     * Jeśli shuffleBeforeSave jest włączony, wywoływane automatycznie przed save.
     */
    private fun applyShuffleIfNeeded(): List<Track>? {
        val tracks = _state.value.previewTracks ?: return null
        return if (_state.value.shuffleBeforeSave) tracks.shuffled() else tracks
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Zapis do Spotify — nowa playlista
    // ══════════════════════════════════════════════════════════════════════

    fun saveToSpotify() {
        val tracks = applyShuffleIfNeeded()
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

            // Kolejka — wymaga osobnego flow (dry-run dialog)
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
        val tracks = applyShuffleIfNeeded()
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

    /**
     * Czyści cały stan po zapisie — pełny reset sesji.
     */
    fun clearSavedState() {
        _state.update {
            it.copy(
                savedPlaylistUrl = null,
                previewTracks = null,
                generateResult = null
            )
        }
    }

    /**
     * Pełny reset sesji — czyści historię, usedTrackIds, podgląd.
     */
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
                showQueueDryRun = false
            )
        }
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
}
