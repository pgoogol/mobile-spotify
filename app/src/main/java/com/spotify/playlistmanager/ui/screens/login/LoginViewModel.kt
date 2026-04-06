package com.spotify.playlistmanager.ui.screens.login

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.BuildConfig
import com.spotify.playlistmanager.data.repository.SpotifyRepository
import com.spotify.playlistmanager.domain.usecase.LogoutUseCase
import com.spotify.playlistmanager.util.TokenManager
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val repository: SpotifyRepository,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    val isLoggedIn = tokenManager.isLoggedIn

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    companion object {
        const val AUTH_REQUEST_CODE = 1337

        // Zakresy zgodne z oryginałem + playlist-modify-public dla tworzenia
        // + user-modify-playback-state dla kolejki odtwarzania
        private val SCOPES = arrayOf(
            "playlist-read-private",
            "playlist-read-collaborative",
            "user-library-read",
            "playlist-modify-public",
            "playlist-modify-private",
            "user-read-private",
            "user-read-email",
            "user-top-read",
            "user-modify-playback-state"
        )
    }

    /**
     * Otwiera Spotify Login Activity (SSO lub przeglądarka).
     * Wywoływane z Compose przez rememberLauncherForActivityResult.
     */
    fun buildAuthRequest(): AuthorizationRequest =
        AuthorizationRequest.Builder(
            BuildConfig.SPOTIFY_CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            BuildConfig.SPOTIFY_REDIRECT_URI
        )
            .setScopes(SCOPES)
            .setShowDialog(false)
            .build()

    /**
     * Obsługuje wynik z AuthorizationClient.openLoginActivity (onActivityResult).
     */
    fun handleActivityResult(resultCode: Int, intent: android.content.Intent?) {
        val response = AuthorizationClient.getResponse(resultCode, intent)
        processAuthResponse(response)
    }

    /**
     * Obsługuje callback z przeglądarki (Intent z redirect URI).
     */
    fun handleAuthCallback(uri: Uri) {
        val response = AuthorizationResponse.fromUri(uri)
        processAuthResponse(response)
    }

    private fun processAuthResponse(response: AuthorizationResponse) {
        when (response.type) {
            AuthorizationResponse.Type.TOKEN -> {
                _uiState.value = LoginUiState.Loading
                viewModelScope.launch {
                    runCatching {
                        tokenManager.saveToken(
                            accessToken = response.accessToken,
                            expiresInSec = response.expiresIn
                        )
                        // Pobierz i zapisz profil użytkownika
                        repository.fetchAndCacheCurrentUser()
                    }.onSuccess {
                        _uiState.value = LoginUiState.Success
                    }.onFailure { e ->
                        _uiState.value = LoginUiState.Error(e.message ?: "Nieznany błąd")
                    }
                }
            }

            AuthorizationResponse.Type.ERROR ->
                _uiState.value = LoginUiState.Error(response.error ?: "Błąd autoryzacji")

            else ->
                _uiState.value = LoginUiState.Idle
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.value = LoginUiState.Idle
        }
    }
}
