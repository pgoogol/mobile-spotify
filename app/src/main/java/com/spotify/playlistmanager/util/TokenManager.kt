package com.spotify.playlistmanager.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("spotify_auth")

/**
 * Singleton zarządzający tokenem Spotify.
 *
 * Kluczowe zmiany względem poprzedniej wersji:
 * - Eliminacja runBlocking (ryzyko deadlocka na wątkach IO)
 * - Synchroniczny odczyt tokena przez in-memory cache (_cachedToken)
 * - Cache inicjalizowany asynchronicznie przy starcie przez initScope
 * - AuthInterceptor może odczytać token bez blokowania wątku
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
    // Scope do wczytywania DataStore bez blokowania wątku wywołującego
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory cache – aktualizowany przy każdej zmianie DataStore
    private val _cachedToken    = MutableStateFlow<String?>(null)
    private val _cachedExpires  = MutableStateFlow(0L)
    private val _cachedUserId   = MutableStateFlow<String?>(null)

    init {
        initScope.launch {
            context.dataStore.data.collect { prefs ->
                _cachedToken.value   = prefs[KEY_ACCESS_TOKEN]
                _cachedExpires.value = prefs[KEY_EXPIRES_AT] ?: 0L
                _cachedUserId.value  = prefs[KEY_USER_ID]
            }
        }
    }

    // ── Odczyt synchroniczny (bezpieczny – brak runBlocking) ─────────────────

    /** Odczytuje token z in-memory cache. Bezpieczne do wywołania z wątku IO w interceptorze. */
    fun getAccessToken(): String? = _cachedToken.value

    fun getUserId(): String? = _cachedUserId.value

    fun isTokenExpired(): Boolean {
        val expiresAt = _cachedExpires.value
        return expiresAt == 0L || System.currentTimeMillis() >= expiresAt - 60_000L
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
        if (accessToken == null) return
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_EXPIRES_AT]   = System.currentTimeMillis() + expiresInSec * 1000L
        }
    }

    suspend fun saveUserInfo(userId: String, displayName: String?) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            if (displayName != null) prefs[KEY_DISPLAY_NAME] = displayName
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { it.clear() }
    }
}
