package com.spotify.playlistmanager.data.api

import com.spotify.playlistmanager.util.TokenManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * OkHttp Interceptor – wstrzykuje nagłówek Authorization: Bearer <token>
 * i obsługuje odpowiedź 401 (wygasły token).
 *
 * Zmiany względem poprzedniej wersji:
 * - Korzysta z in-memory cache w TokenManager (brak runBlocking)
 * - Przy odpowiedzi 401 emituje sygnał do AuthEventBus, co wymusza
 *   ponowne logowanie w MainActivity bez restartu aplikacji
 */
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val authEventBus: AuthEventBus
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getAccessToken()

        val request = chain.request().newBuilder().apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()

        val response = chain.proceed(request)

        if (response.code == 401) {
            authEventBus.emitUnauthorized()
        }

        return response
    }
}
