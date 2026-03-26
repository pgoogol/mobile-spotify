package com.spotify.playlistmanager.ui.screens.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.*
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.usecase.GeneratePlaylistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── Stan ekranu generowania ──────────────────────────────────────────────────
data class GenerateUiState(
    val availablePlaylists: List<Playlist>  = emptyList(),
    val sources:            List<PlaylistSource> = listOf(PlaylistSource()),
    val newPlaylistName:    String          = "Nowa Playlista",
    val previewTracks:      List<Track>?    = null,
    val isLoadingPlaylists: Boolean         = true,
    val isGenerating:       Boolean         = false,
    val isSaving:           Boolean         = false,
    val savedPlaylistUrl:   String?         = null,
    val error:              String?         = null
)

@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val repository:       ISpotifyRepository,
    private val generatePlaylist: GeneratePlaylistUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

    init { loadAvailablePlaylists() }

    // ── Ładowanie dostępnych playlist ────────────────────────────────────────
    fun loadAvailablePlaylists() {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPlaylists = true, error = null) }
            runCatching { repository.getUserPlaylists() }
                .onSuccess { playlists ->
                    // Dodaj wirtualną "Polubione" na początku
                    val likedPlaylist = Playlist(
                        id         = GeneratePlaylistUseCase.LIKED_SONGS_ID,
                        name       = "❤ Polubione utwory",
                        description = null,
                        imageUrl   = null,
                        trackCount = 0,
                        ownerId    = ""
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

    // ── Modyfikacja nazwy nowej playlisty ────────────────────────────────────
    fun onPlaylistNameChange(name: String) {
        _state.update { it.copy(newPlaylistName = name) }
    }

    // ── Zarządzanie źródłami ─────────────────────────────────────────────────
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

    // ── Generowanie podglądu ─────────────────────────────────────────────────
    fun generatePreview() {
        val sources = _state.value.sources.filter { it.playlist != null }
        if (sources.isEmpty()) {
            _state.update { it.copy(error = "Wybierz przynajmniej jedną playlistę źródłową") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null, previewTracks = null) }
            runCatching { generatePlaylist(sources) }
                .onSuccess { tracks ->
                    _state.update { it.copy(isGenerating = false, previewTracks = tracks) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isGenerating = false, error = e.message) }
                }
        }
    }

    // ── Zmiana kolejności w podglądzie (drag & drop) ─────────────────────────
    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val tracks = _state.value.previewTracks?.toMutableList() ?: return
        val item = tracks.removeAt(fromIndex)
        tracks.add(toIndex, item)
        _state.update { it.copy(previewTracks = tracks) }
    }

    fun removeTrackFromPreview(index: Int) {
        val tracks = _state.value.previewTracks?.toMutableList() ?: return
        tracks.removeAt(index)
        _state.update { it.copy(previewTracks = tracks) }
    }

    // ── Zapis do Spotify ─────────────────────────────────────────────────────
    fun saveToSpotify() {
        val tracks = _state.value.previewTracks
        val name   = _state.value.newPlaylistName.trim().ifBlank { "Nowa Playlista" }
        if (tracks.isNullOrEmpty()) {
            _state.update { it.copy(error = "Brak utworów do zapisania") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val playlistId = repository.createPlaylist(
                    name        = name,
                    description = "Wygenerowano przez Spotify Playlist Manager"
                )
                val uris = tracks.mapNotNull { it.uri }
                repository.addTracksToPlaylist(playlistId, uris)
                "https://open.spotify.com/playlist/$playlistId"
            }.onSuccess { url ->
                _state.update { it.copy(isSaving = false, savedPlaylistUrl = url) }
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun clearSavedState() {
        _state.update { it.copy(savedPlaylistUrl = null, previewTracks = null) }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
}
