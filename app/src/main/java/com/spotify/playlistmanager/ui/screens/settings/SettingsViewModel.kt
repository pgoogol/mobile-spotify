package com.spotify.playlistmanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.domain.cache.IImageCacheCleaner
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import com.spotify.playlistmanager.domain.usecase.LogoutUseCase
import com.spotify.playlistmanager.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

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

    fun logout() {
        viewModelScope.launch { logoutUseCase() }
    }

    fun clearMessage() {
        _state.update { it.copy(actionMessage = null) }
    }
}