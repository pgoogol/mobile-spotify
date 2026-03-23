package com.spotify.playlistmanager.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesCache
import com.spotify.playlistmanager.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName:   String? = null,
    val cacheCount:    Int     = 0,
    val actionMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val cache:        ITrackFeaturesCache
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            tokenManager.displayName.collect { name ->
                _state.update { it.copy(displayName = name) }
            }
        }
        refreshCacheCount()
    }

    fun clearCache() {
        viewModelScope.launch {
            cache.clear()
            _state.update {
                it.copy(cacheCount = 0, actionMessage = "🗑️ Cache wyczyszczony")
            }
        }
    }

    fun logout() {
        viewModelScope.launch { tokenManager.clearTokens() }
    }

    fun clearMessage() {
        _state.update { it.copy(actionMessage = null) }
    }

    private fun refreshCacheCount() {
        viewModelScope.launch {
            _state.update { it.copy(cacheCount = cache.count()) }
        }
    }
}