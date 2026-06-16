package com.spotify.playlistmanager.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.spotify.playlistmanager.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("spotify_auth")

/**
 * Singleton zarządzający tokenami Spotify (Authorization Code + PKCE).
 *
 * Przechowuje access_token, refresh_token oraz czas wygaśnięcia. Dzięki
 * refresh_token sesja jest odświeżana w tle (patrz [com.spotify.playlistmanager.data.api.TokenAuthenticator])
 * i użytkownik nie musi logować się ponownie po wygaśnięciu access_token.
 *
 * - Ręczny CoroutineScope zastąpiony @ApplicationScope (zarządzany przez Hilt).
 * - first() przy starcie gwarantuje wypełnienie cache zanim pierwsze żądanie HTTP
 *   przejdzie przez AuthInterceptor (eliminuje race condition zimnego startu).
 * - Dalsze aktualizacje przez collect() reagują na zmiany DataStore w czasie rzeczywistym.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope   private val appScope: CoroutineScope
) {
    companion object {
        private val KEY_ACCESS_TOKEN  = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_EXPIRES_AT    = longPreferencesKey("expires_at")
        private val KEY_USER_ID       = stringPreferencesKey("user_id")
        private val KEY_DISPLAY_NAME  = stringPreferencesKey("display_name")
        // Parametry PKCE — przejściowe, między żądaniem autoryzacji a callbackiem.
        private val KEY_CODE_VERIFIER = stringPreferencesKey("pkce_code_verifier")
        private val KEY_AUTH_STATE    = stringPreferencesKey("oauth_state")
    }

    // In-memory cache – synchroniczny odczyt bezpieczny dla wątku IO w interceptorze/authenticatorze
    private val _cachedToken        = MutableStateFlow<String?>(null)
    private val _cachedRefreshToken = MutableStateFlow<String?>(null)
    private val _cachedExpires      = MutableStateFlow(0L)
    private val _cachedUserId       = MutableStateFlow<String?>(null)

    init {
        appScope.launch {
            // first() blokuje tylko korutynę (nie wątek główny) i czeka na pierwszy
            // emit DataStore — gwarantuje że cache jest wypełniony zanim AuthInterceptor
            // wykona pierwsze żądanie HTTP po zimnym starcie.
            val initial = context.dataStore.data.first()
            applyToCache(initial)

            // Dalsze zmiany (np. po zalogowaniu / odświeżeniu / wylogowaniu) śledzone reaktywnie
            context.dataStore.data.collect { applyToCache(it) }
        }
    }

    private fun applyToCache(prefs: Preferences) {
        _cachedToken.value        = prefs[KEY_ACCESS_TOKEN]
        _cachedRefreshToken.value = prefs[KEY_REFRESH_TOKEN]
        _cachedExpires.value      = prefs[KEY_EXPIRES_AT] ?: 0L
        _cachedUserId.value       = prefs[KEY_USER_ID]
    }

    // ── Odczyt synchroniczny (bezpieczny – brak runBlocking) ─────────────────

    /** Odczytuje token z in-memory cache. Bezpieczne do wywołania z wątku IO. */
    fun getAccessToken(): String? = _cachedToken.value

    /** refresh_token z in-memory cache — używany przez TokenAuthenticator. */
    fun getRefreshToken(): String? = _cachedRefreshToken.value

    fun getUserId(): String? = _cachedUserId.value

    fun isTokenExpired(): Boolean {
        val expiresAt = _cachedExpires.value
        return expiresAt == 0L || System.currentTimeMillis() >= expiresAt - 60_000L
    }

    // ── Odczyt reaktywny (Flow dla UI) ───────────────────────────────────────

    /**
     * Zalogowany = mamy access_token, który jest ważny LUB ma refresh_token
     * (czyli da się go cicho odświeżyć). Wygaśnięcie samego access_token nie
     * wylogowuje użytkownika.
     */
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val token   = prefs[KEY_ACCESS_TOKEN]
        val refresh = prefs[KEY_REFRESH_TOKEN]
        val expires = prefs[KEY_EXPIRES_AT] ?: 0L
        token != null && (System.currentTimeMillis() < expires || refresh != null)
    }

    val displayName: Flow<String?> = context.dataStore.data.map { it[KEY_DISPLAY_NAME] }

    // ── Zapis tokenów ─────────────────────────────────────────────────────────

    /**
     * Zapisuje komplet tokenów po wymianie kodu lub po odświeżeniu.
     * Aktualizuje też in-memory cache natychmiast, aby kolejne żądania (czytające
     * cache synchronicznie) od razu używały świeżego tokena.
     */
    suspend fun saveTokens(accessToken: String, refreshToken: String?, expiresInSec: Int) {
        val expiresAt = System.currentTimeMillis() + expiresInSec * 1000L
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_EXPIRES_AT]   = expiresAt
            if (refreshToken != null) prefs[KEY_REFRESH_TOKEN] = refreshToken
        }
        _cachedToken.value   = accessToken
        _cachedExpires.value = expiresAt
        if (refreshToken != null) _cachedRefreshToken.value = refreshToken
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

    // ── Parametry PKCE (przejściowe) ──────────────────────────────────────────

    /** Zapisuje code_verifier i state przed otwarciem ekranu autoryzacji. */
    suspend fun savePkceParams(codeVerifier: String, state: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CODE_VERIFIER] = codeVerifier
            prefs[KEY_AUTH_STATE]    = state
        }
    }

    /**
     * Odczytuje parametry PKCE z DataStore (autorytatywnie — odporne na śmierć
     * procesu między autoryzacją a callbackiem). Zwraca (code_verifier, state).
     */
    suspend fun readPkceParams(): Pair<String?, String?> {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_CODE_VERIFIER] to prefs[KEY_AUTH_STATE]
    }

    suspend fun clearPkceParams() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CODE_VERIFIER)
            prefs.remove(KEY_AUTH_STATE)
        }
    }
}
