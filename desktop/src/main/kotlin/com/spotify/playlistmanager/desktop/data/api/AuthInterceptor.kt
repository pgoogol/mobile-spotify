package com.spotify.playlistmanager.desktop.data.api

import com.spotify.playlistmanager.desktop.data.auth.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Dokleja nagłówek `Authorization: Bearer <token>` do żądań Web API.
 *
 * Przed żądaniem (i ponownie przy 401) próbuje odświeżyć token przez
 * [refresh], jeśli wygasł i mamy refresh_token. [refresh] korzysta z osobnego
 * klienta Accounts (bez tego interceptora), więc nie ma rekurencji.
 */
class AuthInterceptor(
    private val tokenStore: TokenStore,
    private val refresh: () -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (tokenStore.isExpired() && tokenStore.refreshToken != null) {
            runCatching { refresh() }
        }

        var response = chain.proceed(withBearer(chain))

        if (response.code == 401 && tokenStore.refreshToken != null) {
            response.close()
            runCatching { refresh() }
            response = chain.proceed(withBearer(chain))
        }
        return response
    }

    private fun withBearer(chain: Interceptor.Chain): okhttp3.Request {
        val token = tokenStore.accessToken ?: return chain.request()
        return chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
    }
}
