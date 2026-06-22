package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.domain.cache.IImagePreloader
import com.spotify.playlistmanager.domain.repository.CachePolicy
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use-case przygotowania danych na event offline.
 *
 * Pobiera i cache'uje wszystkie playlisty, utwory i obrazy (okładki albumów),
 * żeby aplikacja mogła działać bez internetu na miejscu eventu.
 *
 * Dwa tryby:
 *  - templateId == null → preload WSZYSTKICH playlist użytkownika
 *  - templateId != null → preload tylko playlist używanych w danym szablonie
 *
 * Emituje [OfflineProgress] jako Flow — UI wyświetla postęp w czasie rzeczywistym.
 * Błędy w pojedynczych playlistach nie przerywają procesu — są logowane
 * a aplikacja kontynuuje z następnymi.
 */
@Singleton
class PrepareOfflineUseCase @Inject constructor(
    private val repository: ISpotifyRepository,
    private val playlistCache: IPlaylistCacheRepository,
    private val templateRepository: IGeneratorTemplateRepository,
    private val imagePreloader: IImagePreloader
) {

    data class OfflineProgress(
        val phase: Phase,
        val current: Int = 0,
        val total: Int = 0,
        val currentPlaylistName: String? = null,
        val errors: List<String> = emptyList()
    ) {
        enum class Phase { PLAYLISTS, TRACKS, IMAGES, DONE, ERROR }
    }

    /**
     * Rozpoczyna preload danych.
     *
     * @param templateId ID szablonu — null = wszystkie playlisty
     * @return Flow emitujący postęp; kolektor może cancelować w dowolnym momencie
     */
    operator fun invoke(templateId: Long? = null): Flow<OfflineProgress> = flow {
        val errors = mutableListOf<String>()

        // ── Faza 1: Playlisty ────────────────────────────────────────────
        emit(OfflineProgress(OfflineProgress.Phase.PLAYLISTS))

        val playlists = runCatching {
            repository.getUserPlaylists(CachePolicy.NETWORK_ONLY)
        }.getOrElse { e ->
            errors.add("Playlisty: ${e.message}")
            // Fallback na cache
            playlistCache.getCachedPlaylists()
        }

        // Wyznacz które playlisty preloadować
        val targetPlaylistIds = if (templateId != null) {
            val template = templateRepository.getById(templateId)
            template?.sources?.map { it.playlistId }?.toSet() ?: emptySet()
        } else {
            playlists.map { it.id }.toSet()
        }

        val playlistsToLoad = playlists.filter { it.id in targetPlaylistIds }
        val totalPlaylists = playlistsToLoad.size

        // ── Faza 2: Utwory ───────────────────────────────────────────────
        val allImageUrls = mutableSetOf<String>()

        playlistsToLoad.forEachIndexed { index, playlist ->
            emit(
                OfflineProgress(
                    phase = OfflineProgress.Phase.TRACKS,
                    current = index + 1,
                    total = totalPlaylists,
                    currentPlaylistName = playlist.name,
                    errors = errors
                )
            )

            runCatching {
                val tracks = repository.getPlaylistTracks(playlist.id, CachePolicy.NETWORK_ONLY)
                // Zbierz URL-e okładek do fazy 3
                tracks.mapNotNull { it.albumArtUrl }.forEach { allImageUrls.add(it) }
            }.onFailure { e ->
                errors.add("${playlist.name}: ${e.message}")
            }
        }

        // Liked Songs (jeśli all-playlists mode lub szablon je zawiera)
        if (templateId == null || GeneratePlaylistUseCase.LIKED_SONGS_ID in targetPlaylistIds) {
            emit(
                OfflineProgress(
                    phase = OfflineProgress.Phase.TRACKS,
                    current = totalPlaylists,
                    total = totalPlaylists,
                    currentPlaylistName = "Polubione utwory",
                    errors = errors
                )
            )
            runCatching {
                val liked = repository.getLikedTracks(CachePolicy.NETWORK_ONLY)
                liked.mapNotNull { it.albumArtUrl }.forEach { allImageUrls.add(it) }
            }.onFailure { e ->
                errors.add("Polubione: ${e.message}")
            }
        }

        // ── Faza 3: Obrazy ──────────────────────────────────────────────
        val uniqueUrls = allImageUrls.toList()
        if (uniqueUrls.isNotEmpty()) {
            imagePreloader.preloadBatch(uniqueUrls) { current, total ->
                // Nie emitujemy co obraz (za dużo) — co 10%
                if (current % maxOf(1, total / 10) == 0 || current == total) {
                    // Flow nie pozwala emitować z lambdy, więc batch progress
                    // jest obsługiwany przez preloadBatch wewnętrznie.
                    // UI pokaże fazę IMAGES z jednym emit.
                }
            }
        }
        emit(
            OfflineProgress(
                phase = OfflineProgress.Phase.IMAGES,
                current = uniqueUrls.size,
                total = uniqueUrls.size,
                errors = errors
            )
        )

        // ── Done ────────────────────────────────────────────────────────
        emit(
            OfflineProgress(
                phase = OfflineProgress.Phase.DONE,
                current = totalPlaylists,
                total = totalPlaylists,
                errors = errors
            )
        )
    }
}
