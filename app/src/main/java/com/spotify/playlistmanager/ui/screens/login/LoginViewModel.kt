package com.spotify.playlistmanager.ui.screens.login

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotify.playlistmanager.BuildConfig
import com.spotify.playlistmanager.data.api.SpotifyAuthService
import com.spotify.playlistmanager.data.repository.SpotifyRepository
import com.spotify.playlistmanager.domain.usecase.LogoutUseCase
import com.spotify.playlistmanager.util.PkceGenerator
import com.spotify.playlistmanager.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

/**
 * Logowanie przez Authorization Code Flow + PKCE.
 *
 * Krok 1: [onLoginClicked] generuje parę PKCE, zapisuje code_verifier/state
 *         i emituje URL `/authorize`, który ekran otwiera w Custom Tab.
 * Krok 2: Spotify przekierowuje na redirect URI → [handleAuthCallback] wymienia
 *         authorization code (z code_verifier) na access_token + refresh_token.
 *
 * Dzięki refresh_token sesja jest podtrzymywana w tle (TokenAuthenticator),
 * więc użytkownik nie musi logować się ponownie po wygaśnięciu access_token.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val repository: SpotifyRepository,
    private val authService: SpotifyAuthService,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    val isLoggedIn = tokenManager.isLoggedIn

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** URL `/authorize` do otwarcia w przeglądarce/Custom Tab. */
    private val _authUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val authUrl: SharedFlow<String> = _authUrl.asSharedFlow()

    companion object {
        private const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"

        // Zakresy zgodne z oryginałem.
        private val SCOPES = listOf(
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
     * Przygotowuje PKCE i emituje URL autoryzacji do otwarcia przez UI.
     * Nie ustawia stanu Loading — „ładowaniem" jest tu Custom Tab przeglądarki;
     * gdy użytkownik ją zamknie bez logowania, przycisk pozostaje aktywny.
     * Loading pojawia się dopiero przy realnej wymianie kodu w [handleAuthCallback].
     */
    fun onLoginClicked() {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Idle  // wyczyść ewentualny poprzedni błąd
            val verifier  = PkceGenerator.generateCodeVerifier()
            val state     = PkceGenerator.generateState()
            tokenManager.savePkceParams(verifier, state)
            val challenge = PkceGenerator.deriveCodeChallenge(verifier)
            _authUrl.emit(buildAuthorizeUrl(challenge, state))
        }
    }

    private fun buildAuthorizeUrl(codeChallenge: String, state: String): String =
        Uri.parse(AUTHORIZE_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.SPOTIFY_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.SPOTIFY_REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .appendQueryParameter("state", state)
            .build()
            .toString()

    /**
     * Obsługuje powrót z autoryzacji (deep-link redirect URI).
     * Wymienia authorization code na tokeny i pobiera profil użytkownika.
     */
    fun handleAuthCallback(uri: Uri) {
        // Ignoruj wywołania niezwiązane z OAuth (np. zwykłe uruchomienie z linku).
        val code  = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        if (code == null && error == null) return

        if (error != null) {
            _uiState.value = LoginUiState.Error(mapAuthError(error))
            return
        }

        val returnedState = uri.getQueryParameter("state")
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            val (verifier, savedState) = tokenManager.readPkceParams()

            if (verifier == null || savedState == null || savedState != returnedState) {
                _uiState.value = LoginUiState.Error(
                    "Niepoprawny stan autoryzacji — spróbuj zalogować się ponownie."
                )
                return@launch
            }

            runCatching {
                val resp = withContext(Dispatchers.IO) {
                    authService.exchangeCode(
                        code = code!!,
                        redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI,
                        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
                        codeVerifier = verifier
                    ).execute()
                }
                val body = resp.body()
                check(resp.isSuccessful && body != null) {
                    "Wymiana kodu na token nie powiodła się (HTTP ${resp.code()})"
                }
                tokenManager.saveTokens(
                    accessToken = body.access_token,
                    refreshToken = body.refresh_token,
                    expiresInSec = body.expires_in
                )
                tokenManager.clearPkceParams()
                // Pobierz i zapisz profil użytkownika (nowy token jest już w cache).
                repository.fetchAndCacheCurrentUser()
            }.onSuccess {
                _uiState.value = LoginUiState.Success
            }.onFailure { e ->
                _uiState.value = LoginUiState.Error(e.message ?: "Nieznany błąd logowania")
            }
        }
    }

    private fun mapAuthError(error: String): String = when (error) {
        "access_denied" -> "Logowanie anulowane."
        else -> "Błąd autoryzacji: $error"
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.value = LoginUiState.Idle
        }
    }
}
