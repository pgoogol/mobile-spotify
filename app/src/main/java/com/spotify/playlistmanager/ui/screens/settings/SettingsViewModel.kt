package com.spotify.playlistmanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.domain.cache.IImageCacheCleaner
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import com.spotify.playlistmanager.domain.usecase.LogoutUseCase
import com.spotify.playlistmanager.domain.usecase.PrepareOfflineUseCase
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
    private val prepareOfflineUseCase: PrepareOfflineUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    // ── Offline prep ─────────────────────────────────────────────────────

    private val _offlineProgress = MutableStateFlow<PrepareOfflineUseCase.OfflineProgress?>(null)
    val offlineProgress: StateFlow<PrepareOfflineUseCase.OfflineProgress?> =
        _offlineProgress.asStateFlow()

    private var offlineJob: Job? = null

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
