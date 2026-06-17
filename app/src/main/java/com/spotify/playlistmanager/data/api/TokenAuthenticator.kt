package com.spotify.playlistmanager.data.api

import com.spotify.playlistmanager.BuildConfig
import com.spotify.playlistmanager.util.TokenManager
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [Authenticator] — reaguje na odpowiedź 401, próbuje cicho odświeżyć
 * access_token przy użyciu refresh_token i ponawia oryginalne żądanie.
 *
 * Dzięki temu wygaśnięcie tokena (domyślnie co ~1h) jest transparentne dla
 * użytkownika — NIE wymusza ponownego logowania. Dopiero gdy odświeżenie się
 * nie powiedzie (brak refresh_token albo został odwołany), emitujemy sygnał
 * [AuthEventBus.emitUnauthorized] → UI przechodzi do ekranu logowania.
 *
 * Zastępuje to poprzednie, agresywne zachowanie: każdy 401 czyścił całą sesję.
 *
 * Authenticator działa synchronicznie na wątku OkHttp, więc blokujące
 * wywołania ([Call.execute], krótki [runBlocking] na zapis DataStore) są tu
 * bezpieczne i zgodne z kontraktem OkHttp.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenManager: TokenManager,
    private val authService: SpotifyAuthService,
    private val authEventBus: AuthEventBus
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Ponowiliśmy już raz z nowym tokenem, a i tak 401 — przerwij pętlę.
        if (responseCount(response) >= 2) {
            authEventBus.emitUnauthorized()
            return null
        }

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        val newToken: String? = synchronized(this) {
            // Inny równoległy request mógł już odświeżyć token — użyj świeżego bez kolejnego refreshu.
            val current = tokenManager.getAccessToken()
            if (current != null && current != failedToken) {
                return@synchronized current
            }
            val refreshToken = tokenManager.getRefreshToken() ?: return@synchronized null
            refresh(refreshToken)
        }

        if (newToken == null) {
            // Brak refresh_token albo został odwołany — wymuś ponowne logowanie.
            authEventBus.emitUnauthorized()
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    /** Synchroniczne odświeżenie. Zwraca nowy access_token lub null przy niepowodzeniu. */
    private fun refresh(refreshToken: String): String? = runCatching {
        val resp = authService.refreshToken(
            refreshToken = refreshToken,
            clientId = BuildConfig.SPOTIFY_CLIENT_ID
        ).execute()

        val body = resp.body()
        if (!resp.isSuccessful || body == null) {
            null
        } else {
            // Spotify nie zawsze rotuje refresh_token — zachowaj poprzedni, gdy brak nowego.
            runBlocking {
                tokenManager.saveTokens(
                    accessToken = body.access_token,
                    refreshToken = body.refresh_token ?: refreshToken,
                    expiresInSec = body.expires_in
                )
            }
            body.access_token
        }
    }.getOrNull()

    private fun responseCount(response: Response): Int {
        var prior = response.priorResponse
        var count = 1
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
