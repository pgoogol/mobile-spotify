package com.spotify.playlistmanager.desktop.data

import com.spotify.playlistmanager.desktop.config.AppConfig
import com.spotify.playlistmanager.desktop.data.api.AuthInterceptor
import com.spotify.playlistmanager.desktop.data.api.SpotifyApiService
import com.spotify.playlistmanager.desktop.data.auth.SpotifyAuthApi
import com.spotify.playlistmanager.desktop.data.auth.SpotifyAuthenticator
import com.spotify.playlistmanager.desktop.data.auth.TokenStore
import com.spotify.playlistmanager.desktop.data.repository.DesktopSpotifyRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Ręczne „DI" dla desktopu (bez Hilt) — spina sieć, auth i repozytorium.
 *
 * Dwa klienty HTTP, tak jak na Androidzie:
 *  • Accounts (token endpoint) — bez [AuthInterceptor], żeby uniknąć rekurencji.
 *  • Web API — z [AuthInterceptor] (Bearer + odświeżanie tokenu).
 */
class SpotifyClient(private val clientId: String) {

    val tokenStore = TokenStore()

    private fun logging() = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    // ── Klient Accounts (accounts.spotify.com) ──────────────────────────────
    private val accountsClient = OkHttpClient.Builder()
        .addInterceptor(logging())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authApi: SpotifyAuthApi = Retrofit.Builder()
        .baseUrl(AppConfig.ACCOUNTS_BASE_URL)
        .client(accountsClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyAuthApi::class.java)

    /** Synchroniczne odświeżenie tokenu — wołane z [AuthInterceptor]. */
    private fun refresh() {
        val refreshToken = tokenStore.refreshToken ?: return
        val resp = authApi.refreshToken(refreshToken = refreshToken, clientId = clientId).execute()
        resp.body()?.let {
            tokenStore.saveTokens(it.access_token, it.refresh_token, it.expires_in)
        }
    }

    // ── Klient Web API (api.spotify.com) ────────────────────────────────────
    private val apiClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenStore) { refresh() })
        .addInterceptor(logging())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api: SpotifyApiService = Retrofit.Builder()
        .baseUrl(AppConfig.API_BASE_URL)
        .client(apiClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(SpotifyApiService::class.java)

    val repository: ISpotifyRepository = DesktopSpotifyRepository(api, tokenStore)
    val authenticator = SpotifyAuthenticator(authApi, tokenStore, clientId)
}
