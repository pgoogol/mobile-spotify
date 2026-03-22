package com.spotify.playlistmanager.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("spotify_auth")

/**
 * Singleton zarządzający tokenem Spotify.
 * Przechowuje access token w DataStore (szyfrowany przez system Android).
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_EXPIRES_AT   = longPreferencesKey("expires_at")
        private val KEY_USER_ID      = stringPreferencesKey("user_id")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    }

    // ── Odczyt synchroniczny (używany w AuthInterceptor) ────────────────────

    fun getAccessToken(): String? = runBlocking {
        context.dataStore.data.first()[KEY_ACCESS_TOKEN]
    }

    fun getUserId(): String? = runBlocking {
        context.dataStore.data.first()[KEY_USER_ID]
    }

    fun isTokenExpired(): Boolean = runBlocking {
        val expiresAt = context.dataStore.data.first()[KEY_EXPIRES_AT] ?: return@runBlocking true
        System.currentTimeMillis() >= expiresAt - 60_000L  // 1 min margines
    }

    // ── Odczyt reaktywny (Flow dla UI) ───────────────────────────────────────

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val token = prefs[KEY_ACCESS_TOKEN]
        val expires = prefs[KEY_EXPIRES_AT] ?: 0L
        token != null && System.currentTimeMillis() < expires
    }

    val displayName: Flow<String?> = context.dataStore.data.map { it[KEY_DISPLAY_NAME] }

    // ── Zapis ────────────────────────────────────────────────────────────────

    /**
     * Zapisuje token otrzymany ze Spotify Auth SDK.
     * @param accessToken  Bearer token
     * @param expiresInSec czas ważności w sekundach (Spotify daje 3600)
     */
    suspend fun saveToken(accessToken: String?, expiresInSec: Int = 3600) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken as String
            prefs[KEY_EXPIRES_AT]   = System.currentTimeMillis() + expiresInSec * 1000L
        }
    }

    suspend fun saveUserInfo(userId: String, displayName: String?) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID]      = userId
            if (displayName != null) prefs[KEY_DISPLAY_NAME] = displayName
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { it.clear() }
    }
}
