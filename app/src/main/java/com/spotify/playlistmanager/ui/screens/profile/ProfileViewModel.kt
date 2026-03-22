package com.spotify.playlistmanager.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.model.TopArtist
import com.spotify.playlistmanager.data.model.UserProfile
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading:     Boolean         = true,
    val profile:       UserProfile?    = null,
    val topArtists:    List<TopArtist> = emptyList(),
    val playlistCount: Int             = 0,
    val likedCount:    Int             = 0,
    val error:         String?         = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ISpotifyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init { loadProfile() }

    fun loadProfile() {
        viewModelScope.launch {
            _state.value = ProfileUiState(isLoading = true)
            runCatching {
                val profileDeferred      = async { repository.getUserProfile() }
                val topArtistsDeferred   = async { repository.getTopArtists() }
                val playlistCountDeferred = async { repository.getUserPlaylists().size }
                val likedCountDeferred   = async { repository.getLikedTracksCount() }

                val profile      = profileDeferred.await()
                val topArtists   = topArtistsDeferred.await()
                val playlistCount = playlistCountDeferred.await()
                val likedCount   = likedCountDeferred.await()

                _state.value = ProfileUiState(
                    isLoading     = false,
                    profile       = profile,
                    topArtists    = topArtists,
                    playlistCount = playlistCount,
                    likedCount    = likedCount
                )
            }.onFailure { e ->
                _state.value = ProfileUiState(isLoading = false, error = e.message)
            }
        }
    }
}
