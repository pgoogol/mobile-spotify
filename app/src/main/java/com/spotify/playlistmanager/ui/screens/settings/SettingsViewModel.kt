package com.spotify.playlistmanager.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.cache.TrackFeaturesDao
import com.spotify.playlistmanager.data.repository.SpotifyRepository
import com.spotify.playlistmanager.util.LocalCsvImportHelper
import com.spotify.playlistmanager.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val displayName:   String? = null,
    val cacheCount:    Int     = 0,
    val isImporting:   Boolean = false,
    val importMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val csvHelper:    LocalCsvImportHelper,
    private val dao:          TrackFeaturesDao
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

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }
            val result = csvHelper.importFromUri(uri)
            _state.update {
                it.copy(
                    isImporting   = false,
                    cacheCount    = result.totalInCache,
                    importMessage = if (result.errors.isEmpty())
                        "✅ Dodano ${result.newEntries} nowych wpisów (łącznie: ${result.totalInCache})"
                    else
                        "⚠️ ${result.newEntries} nowych, ${result.errors.size} błędów"
                )
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            dao.clear()
            _state.update { it.copy(cacheCount = 0, importMessage = "🗑️ Cache wyczyszczony") }
        }
    }

    fun logout() {
        viewModelScope.launch { tokenManager.clearTokens() }
    }

    fun clearMessage() {
        _state.update { it.copy(importMessage = null) }
    }

    private fun refreshCacheCount() {
        viewModelScope.launch {
            _state.update { it.copy(cacheCount = dao.count()) }
        }
    }
}
