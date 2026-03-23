package com.spotify.playlistmanager.data.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Szyna zdarzeń autoryzacji.
 *
 * Pozwala warstwie danych (AuthInterceptor) poinformować UI (MainActivity)
 * o wygasłym tokenie bez bezpośredniej zależności między warstwami.
 *
 * MainActivity subskrybuje [unauthorized] i przekierowuje do LoginScreen.
 */
@Singleton
class AuthEventBus @Inject constructor() {

    private val _unauthorized = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unauthorized: SharedFlow<Unit> = _unauthorized.asSharedFlow()

    /** Wywoływane z AuthInterceptor przy odpowiedzi HTTP 401. */
    fun emitUnauthorized() {
        _unauthorized.tryEmit(Unit)
    }
}
