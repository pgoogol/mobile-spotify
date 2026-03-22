package com.spotify.playlistmanager.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.repository.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PlaylistsUiState {
    data object Loading : PlaylistsUiState()
    data class  Success(val playlists: List<Playlist>) : PlaylistsUiState()
    data class  Error(val message: String) : PlaylistsUiState()
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repository: SpotifyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PlaylistsUiState>(PlaylistsUiState.Loading)
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var allPlaylists: List<Playlist> = emptyList()

    /** Przefiltrowane playlisty wg searchQuery */
    @OptIn(FlowPreview::class)
    val filteredPlaylists: StateFlow<List<Playlist>> = _searchQuery
        .debounce(200)
        .combine(_uiState) { query, state ->
            if (state is PlaylistsUiState.Success) {
                if (query.isBlank()) state.playlists
                else state.playlists.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    (it.description?.contains(query, ignoreCase = true) == true)
                }
            } else emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadPlaylists()
    }

    fun loadPlaylists() {
        _uiState.value = PlaylistsUiState.Loading
        viewModelScope.launch {
            runCatching { repository.getUserPlaylists() }
                .onSuccess { playlists ->
                    allPlaylists = playlists
                    _uiState.value = PlaylistsUiState.Success(playlists)
                }
                .onFailure { e ->
                    _uiState.value = PlaylistsUiState.Error(
                        e.message ?: "Błąd pobierania playlist"
                    )
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
}
