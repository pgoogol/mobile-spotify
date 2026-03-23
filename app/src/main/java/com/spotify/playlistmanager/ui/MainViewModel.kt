package com.spotify.playlistmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.api.AuthEventBus
import com.spotify.playlistmanager.util.SpotifyAppRemoteManager
import com.spotify.playlistmanager.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Główny ViewModel – zarządza App Remote, globalnym stanem nawigacji
 * i obsługą wygasłych tokenów (401 z AuthEventBus).
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    val tokenManager:     TokenManager,
    val appRemoteManager: SpotifyAppRemoteManager,
    val authEventBus:     AuthEventBus
) : ViewModel() {

    val isLoggedIn = tokenManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val displayName = tokenManager.displayName
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /** Podłącz App Remote gdy użytkownik jest zalogowany. */
    fun connectAppRemote() {
        if (!appRemoteManager.isConnected) appRemoteManager.connect()
    }

    /** Odegraj URI przez App Remote (utwór lub playlista). */
    fun playUri(uri: String) {
        if (appRemoteManager.isConnected) {
            appRemoteManager.play(uri)
        } else {
            appRemoteManager.connect { appRemoteManager.play(uri) }
        }
    }

    /** Wylogowuje i czyści tokeny – wywoływane przy zdarzeniu 401. */
    fun forceLogout() {
        viewModelScope.launch {
            appRemoteManager.disconnect()
            tokenManager.clearTokens()
        }
    }

    override fun onCleared() {
        super.onCleared()
        appRemoteManager.disconnect()
    }
}
