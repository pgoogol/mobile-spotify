package com.spotify.playlistmanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.domain.cache.IImageCacheCleaner
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import com.spotify.playlistmanager.domain.usecase.BuildAllInPlaylistUseCase
import com.spotify.playlistmanager.domain.usecase.LogoutUseCase
import com.spotify.playlistmanager.domain.usecase.PrepareOfflineUseCase
import com.spotify.playlistmanager.util.OfflineModeManager
import com.spotify.playlistmanager.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName: String? = null,
    val cachedPlaylists: Int = 0,
    val cachedTracks: Int = 0,
    val actionMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val playlistCache: IPlaylistCacheRepository,
    private val imageCache: IImageCacheCleaner,
    private val logoutUseCase: LogoutUseCase,
    private val prepareOfflineUseCase: PrepareOfflineUseCase,
    private val buildAllInUseCase: BuildAllInPlaylistUseCase,
    private val offlineModeManager: OfflineModeManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    // ── Offline prep ─────────────────────────────────────────────────────

    private val _offlineProgress = MutableStateFlow<PrepareOfflineUseCase.OfflineProgress?>(null)
    val offlineProgress: StateFlow<PrepareOfflineUseCase.OfflineProgress?> =
        _offlineProgress.asStateFlow()

    private var offlineJob: Job? = null

    // ── Globalny tryb offline ────────────────────────────────────────────

    val isOfflineMode: StateFlow<Boolean> = offlineModeManager.isEnabled

    /**
     * Postęp budowania playlisty "All-in" podczas włączania trybu offline.
     * null → nie uruchomione lub zakończone i wyczyszczone.
     */
    private val _allInProgress =
        MutableStateFlow<BuildAllInPlaylistUseCase.Progress?>(null)
    val allInProgress: StateFlow<BuildAllInPlaylistUseCase.Progress?> =
        _allInProgress.asStateFlow()

    private var allInJob: Job? = null

    /**
     * Włącza/wyłącza globalny tryb offline.
     *
     * Włączanie (enabled = true) jest wieloetapowe:
     *  1. Buduje playlistę "All-in" na koncie Spotify (zawiera wszystkie
     *     utwory z playlist użytkownika + Liked Songs, zdeduplikowane).
     *  2. Zapisuje All-in do lokalnego cache.
     *  3. Dopiero gdy krok 1-2 się powiódł — flipuje flagę offline,
     *     odcinając aplikację od sieci.
     *
     * Pozostałe playlisty nie są modyfikowane — algorytm tylko czyta i
     * pisze wyłącznie do playlisty All-in.
     *
     * Wyłączanie (enabled = false) — natychmiastowa zmiana flagi.
     */
    fun setOfflineMode(enabled: Boolean) {
        if (!enabled) {
            // Wyłączenie w trakcie budowy All-in anuluje też zadanie
            allInJob?.cancel()
            allInJob = null
            _allInProgress.value = null
            viewModelScope.launch {
                offlineModeManager.setEnabled(false)
                _state.update { it.copy(actionMessage = "Tryb offline wyłączony") }
            }
            return
        }

        // Włączanie — najpierw budowa All-in, potem flaga
        allInJob?.cancel()
        allInJob = viewModelScope.launch {
            buildAllInUseCase().collect { progress ->
                _allInProgress.value = progress
                when (progress.phase) {
                    BuildAllInPlaylistUseCase.Progress.Phase.DONE -> {
                        offlineModeManager.setEnabled(true)
                        _state.update {
                            it.copy(
                                actionMessage = "Tryb offline włączony — All-in: " +
                                    "${progress.tracksCount} utworów"
                            )
                        }
                        refreshCacheStats()
                    }
                    BuildAllInPlaylistUseCase.Progress.Phase.ERROR -> {
                        val msg = progress.errors.lastOrNull()
                            ?: "Nie udało się przygotować trybu offline"
                        _state.update { it.copy(actionMessage = msg) }
                    }
                    else -> { /* in-progress phases — UI pokazuje pasek */ }
                }
            }
        }
    }

    /** Czyści ostatni postęp budowania All-in (np. po zamknięciu komunikatu). */
    fun clearAllInProgress() {
        _allInProgress.value = null
    }

    /** Anuluje trwającą budowę All-in (jeśli użytkownik zmienił zdanie). */
    fun cancelAllInBuild() {
        allInJob?.cancel()
        allInJob = null
        _allInProgress.value = null
    }

    init {
        viewModelScope.launch {
            tokenManager.displayName.collect { name ->
                _state.update { it.copy(displayName = name) }
            }
        }
        refreshCacheStats()
    }

    fun refreshCacheStats() {
        viewModelScope.launch {
            val playlists = playlistCache.playlistsCount()
            val tracks = playlistCache.tracksCount()
            _state.update { it.copy(cachedPlaylists = playlists, cachedTracks = tracks) }
        }
    }

    fun clearPlaylistCache() {
        viewModelScope.launch {
            playlistCache.clearAll()
            imageCache.clearMemoryCache()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                imageCache.clearDiskCache()
            }
            refreshCacheStats()
            _state.update { it.copy(actionMessage = "Cache playlist i obrazów wyczyszczony") }
        }
    }

    /**
     * Rozpoczyna preload danych na event offline.
     * @param templateId null = wszystkie playlisty, non-null = tylko z szablonu
     */
    fun prepareOffline(templateId: Long? = null) {
        // Anuluj poprzedni job jeśli był w toku
        offlineJob?.cancel()
        offlineJob = viewModelScope.launch {
            prepareOfflineUseCase(templateId).collect { progress ->
                _offlineProgress.value = progress

                // Po zakończeniu odśwież statystyki cache
                if (progress.phase == PrepareOfflineUseCase.OfflineProgress.Phase.DONE) {
                    refreshCacheStats()
                }
            }
        }
    }

    /**
     * Anuluje trwający preload offline.
     */
    fun cancelOfflinePrep() {
        offlineJob?.cancel()
        offlineJob = null
        _offlineProgress.value = null
    }

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }

    fun clearMessage() {
        _state.update { it.copy(actionMessage = null) }
    }
}
