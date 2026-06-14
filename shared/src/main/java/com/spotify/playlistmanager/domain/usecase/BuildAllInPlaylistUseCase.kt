package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.repository.CachePolicy
import com.spotify.playlistmanager.domain.repository.IPlaylistCacheRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Buduje (lub aktualizuje) playlistę "All-in" na koncie Spotify użytkownika.
 *
 * Wchodzące dane:
 *  - WSZYSTKIE playlisty których właścicielem jest aktualny użytkownik
 *    (filtrowane po `ownerId == myUserId`); playlisty obserwowane innych
 *    użytkowników są pomijane.
 *  - Polubione utwory (Liked Songs) — traktowane jako "moje".
 *
 * Wynik:
 *  - Jedna playlista o nazwie "All-in" zawierająca zdeduplikowany zbiór
 *    URI utworów ze wszystkich powyższych źródeł.
 *  - Pozostałe playlisty NIE są modyfikowane ani usuwane — algorytm
 *    tylko czyta (NETWORK_ONLY) i zapisuje do "All-in".
 *
 * Strategia idempotencji:
 *  - Jeśli "All-in" już istnieje (po name + owner), używamy istniejącego id
 *    i `replacePlaylistTracks` zastępuje całą zawartość.
 *  - Jeśli nie istnieje, tworzony jest nowy przez `createPlaylist`.
 *
 * Cache lokalny:
 *  - Wszystkie czytane playlisty trafiają do Room (efekt uboczny
 *    NETWORK_ONLY w SpotifyRepository).
 *  - "All-in" zapisywany jest bezpośrednio do Room (zebrane Tracki + nagłówek),
 *    żeby tryb offline miał do niego natychmiastowy dostęp.
 */
@Singleton
class BuildAllInPlaylistUseCase @Inject constructor(
    private val repository: ISpotifyRepository,
    private val playlistCache: IPlaylistCacheRepository
) {

    data class Progress(
        val phase: Phase,
        val current: Int = 0,
        val total: Int = 0,
        val currentPlaylistName: String? = null,
        val tracksCount: Int = 0,
        val errors: List<String> = emptyList(),
        val allInPlaylistId: String? = null
    ) {
        enum class Phase { PLAYLISTS, TRACKS, BUILDING, UPDATING, DONE, ERROR }
    }

    operator fun invoke(): Flow<Progress> = flow {
        val errors = mutableListOf<String>()

        // ── Faza 1: ustal kim jestem ─────────────────────────────────────
        emit(Progress(Progress.Phase.PLAYLISTS))
        val me = runCatching { repository.fetchAndCacheCurrentUser() }.getOrElse {
            emit(Progress(Progress.Phase.ERROR, errors = listOf("Nie udało się pobrać profilu: ${it.message}")))
            return@flow
        }
        val myUserId = me.id

        // ── Faza 2: pobierz wszystkie playlisty i odfiltruj moje ─────────
        val all = runCatching {
            repository.getUserPlaylists(CachePolicy.NETWORK_ONLY)
        }.getOrElse {
            emit(Progress(Progress.Phase.ERROR, errors = listOf("Nie udało się pobrać playlist: ${it.message}")))
            return@flow
        }
        val myPlaylists = all.filter { it.ownerId == myUserId && it.name != ALL_IN_NAME }

        // ── Faza 3: pobierz utwory z każdej mojej playlisty + Liked ──────
        // Dedupujemy po URI — ten sam utwór z kilku playlist trafia raz.
        val unique = linkedMapOf<String, Track>()
        val totalSteps = myPlaylists.size + 1 // +1 dla Liked Songs

        myPlaylists.forEachIndexed { idx, pl ->
            emit(
                Progress(
                    phase = Progress.Phase.TRACKS,
                    current = idx + 1,
                    total = totalSteps,
                    currentPlaylistName = pl.name,
                    errors = errors
                )
            )
            runCatching {
                repository.getPlaylistTracks(pl.id, CachePolicy.NETWORK_ONLY)
            }.onSuccess { tracks ->
                tracks.forEach { t ->
                    val uri = t.uri ?: return@forEach
                    unique.putIfAbsent(uri, t)
                }
            }.onFailure { e ->
                errors.add("${pl.name}: ${e.message}")
            }
        }

        // Liked Songs
        emit(
            Progress(
                phase = Progress.Phase.TRACKS,
                current = totalSteps,
                total = totalSteps,
                currentPlaylistName = "❤ Polubione utwory",
                errors = errors
            )
        )
        runCatching {
            repository.getLikedTracks(CachePolicy.NETWORK_ONLY)
        }.onSuccess { tracks ->
            tracks.forEach { t ->
                val uri = t.uri ?: return@forEach
                unique.putIfAbsent(uri, t)
            }
        }.onFailure { e ->
            errors.add("Polubione: ${e.message}")
        }

        if (unique.isEmpty()) {
            emit(
                Progress(
                    phase = Progress.Phase.ERROR,
                    errors = errors + "Brak utworów do dodania (Twoje playlisty są puste)."
                )
            )
            return@flow
        }

        // ── Faza 4: znajdź lub utwórz All-in ─────────────────────────────
        emit(Progress(Progress.Phase.BUILDING, tracksCount = unique.size, errors = errors))
        val existing = all.find { it.name == ALL_IN_NAME && it.ownerId == myUserId }
        val allInId = existing?.id ?: runCatching {
            repository.createPlaylist(ALL_IN_NAME, ALL_IN_DESCRIPTION)
        }.getOrElse {
            emit(
                Progress(
                    phase = Progress.Phase.ERROR,
                    errors = errors + "Nie udało się utworzyć playlisty All-in: ${it.message}"
                )
            )
            return@flow
        }

        // ── Faza 5: zastąp zawartość All-in zdeduplikowanym zbiorem ──────
        emit(
            Progress(
                phase = Progress.Phase.UPDATING,
                tracksCount = unique.size,
                errors = errors,
                allInPlaylistId = allInId
            )
        )
        val uris = unique.values.mapNotNull { it.uri }
        runCatching {
            repository.replacePlaylistTracks(allInId, uris)
        }.onFailure { e ->
            emit(
                Progress(
                    phase = Progress.Phase.ERROR,
                    errors = errors + "Nie udało się zaktualizować All-in: ${e.message}",
                    allInPlaylistId = allInId
                )
            )
            return@flow
        }

        // ── Faza 6: zapisz All-in lokalnie żeby tryb offline ją widział ──
        val header = Playlist(
            id = allInId,
            name = ALL_IN_NAME,
            description = ALL_IN_DESCRIPTION,
            imageUrl = existing?.imageUrl,
            trackCount = unique.size,
            ownerId = myUserId,
            snapshotId = null
        )
        runCatching {
            playlistCache.cacheTracks(
                playlist = header,
                tracks = unique.values.toList(),
                snapshotId = null,
                now = System.currentTimeMillis()
            )
        }
        // Inwaliduj listę playlist — następne otwarcie ekranu Playlist pobierze
        // świeży nagłówek "All-in" z Spotify (lub zobaczy go w cache jeśli offline).
        runCatching { playlistCache.invalidatePlaylistsList() }

        emit(
            Progress(
                phase = Progress.Phase.DONE,
                tracksCount = unique.size,
                total = totalSteps,
                errors = errors,
                allInPlaylistId = allInId
            )
        )
    }

    companion object {
        const val ALL_IN_NAME = "All-in"
        const val ALL_IN_DESCRIPTION =
            "Automatycznie zarządzana playlista trybu offline — wszystkie utwory z Twoich playlist."
    }
}
