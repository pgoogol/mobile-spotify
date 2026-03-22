package com.spotify.playlistmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.util.SpotifyAppRemoteManager
import com.spotify.playlistmanager.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Główny ViewModel – zarządza App Remote i globalnym stanem nawigacji.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    val tokenManager:     TokenManager,
    val appRemoteManager: SpotifyAppRemoteManager
) : ViewModel() {

    val isLoggedIn = tokenManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val displayName = tokenManager.displayName
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Podłącz App Remote gdy użytkownik jest zalogowany. */
    fun connectAppRemote() {
        if (!appRemoteManager.isConnected) {
            appRemoteManager.connect()
        }
    }

    /** Odegraj URI przez App Remote (utwór lub playlista). */
    fun playUri(uri: String) {
        if (appRemoteManager.isConnected) {
            appRemoteManager.play(uri)
        } else {
            appRemoteManager.connect {
                appRemoteManager.play(uri)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        appRemoteManager.disconnect()
    }
}
