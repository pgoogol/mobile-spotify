package com.spotify.playlistmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.data.api.AuthEventBus
import com.spotify.playlistmanager.util.SpotifyAppRemoteManager
import com.spotify.playlistmanager.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Główny ViewModel — zarządza App Remote, globalnym stanem nawigacji
 * i obsługą wygasłych tokenów (401 z AuthEventBus).
 *
 * Zmiany enkapsulacji:
 * - tokenManager, appRemoteManager, authEventBus są teraz private.
 * - Activity nie operuje bezpośrednio na menedżerach — używa metod VM.
 * - sessionExpired: SharedFlow<Unit> zastępuje bezpośrednie mainViewModel.authEventBus.unauthorized.
 * - onAppForeground() / onAppBackground() zastępują mainViewModel.appRemoteManager.connect/disconnect.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val tokenManager:     TokenManager,
    private val appRemoteManager: SpotifyAppRemoteManager,
    private val authEventBus:     AuthEventBus
) : ViewModel() {

    // ── Stany obserwowane przez UI ────────────────────────────────────────────

    val isLoggedIn: StateFlow<Boolean> = tokenManager.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val displayName: StateFlow<String?> = tokenManager.displayName
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Emituje Unit gdy token wygasł (HTTP 401).
     * MainActivity obserwuje ten flow i przekierowuje do LoginScreen.
     */
    val sessionExpired: SharedFlow<Unit> = authEventBus.unauthorized

    // ── Cykl życia App Remote ─────────────────────────────────────────────────

    /** Wywołaj z onStart() Activity. */
    fun onAppForeground() {
        if (isLoggedIn.value && !appRemoteManager.isConnected) {
            appRemoteManager.connect()
        }
    }

    /** Wywołaj z onStop() Activity. */
    fun onAppBackground() {
        appRemoteManager.disconnect()
    }

    // ── Operacje ──────────────────────────────────────────────────────────────

    fun playUri(uri: String) {
        if (appRemoteManager.isConnected) {
            appRemoteManager.play(uri)
        } else {
            appRemoteManager.connect { appRemoteManager.play(uri) }
        }
    }

    /** Wylogowuje i czyści tokeny — wywoływane przy zdarzeniu 401 lub przez użytkownika. */
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