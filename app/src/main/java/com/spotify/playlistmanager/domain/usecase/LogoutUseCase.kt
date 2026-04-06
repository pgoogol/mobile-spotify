package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.domain.cache.IImageCacheCleaner
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import com.spotify.playlistmanager.util.SpotifyAppRemoteManager
import com.spotify.playlistmanager.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pełne wylogowanie użytkownika.
 *
 * Kolejność jest istotna ze względów prywatności:
 *   1. Disconnect App Remote — żeby nie próbował używać starych tokenów
 *   2. Wyczyść cache playlist i utworów — dane jednego użytkownika
 *      nie mogą wyciec do następnego zalogowanego konta
 *   3. Wyczyść cache obrazów Coila (okładki playlist, awatary)
 *   4. Wyczyść tokeny — finalizuje wylogowanie
 *
 * Cache TrackFeatures (CSV) NIE jest czyszczony — to dane importowane
 * przez użytkownika z lokalnego pliku, nie powiązane z kontem Spotify.
 */
@Singleton
class LogoutUseCase @Inject constructor(
    private val appRemoteManager: SpotifyAppRemoteManager,
    private val playlistCache: IPlaylistCacheRepository,
    private val imageCache: IImageCacheCleaner,
    private val tokenManager: TokenManager
) {
    suspend operator fun invoke() {
        appRemoteManager.disconnect()
        playlistCache.clearAll()
        // Cache dyskowy Coila wymaga IO; memory cache jest błyskawiczny.
        imageCache.clearMemoryCache()
        withContext(Dispatchers.IO) {
            imageCache.clearDiskCache()
        }
        tokenManager.clearTokens()
    }
}