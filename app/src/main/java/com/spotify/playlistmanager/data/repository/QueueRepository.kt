package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.api.SpotifyApiService
import com.spotify.playlistmanager.data.cache.QueueDao
import com.spotify.playlistmanager.data.cache.QueueEntity
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.repository.AddToQueueResult
import com.spotify.playlistmanager.domain.repository.IQueueRepository
import com.spotify.playlistmanager.domain.repository.QueueEntry
import com.spotify.playlistmanager.util.OfflineModeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja IQueueRepository.
 *
 * Lokalna tabela queue_entries jest źródłem prawdy dla UI. Spotify Web API
 * traktujemy jako "best effort" — przy włączonym trybie online próbujemy
 * wysłać utwór na kolejkę Spotify, ale błąd (brak aktywnego odtwarzacza,
 * sieć itp.) nie wycofuje wpisu z bazy lokalnej.
 */
@Singleton
class QueueRepository @Inject constructor(
    private val dao: QueueDao,
    private val api: SpotifyApiService,
    private val offlineMode: OfflineModeManager
) : IQueueRepository {

    override val queueFlow: Flow<List<QueueEntry>> =
        dao.observeAll().map { entities ->
            entities.map { e ->
                QueueEntry(
                    entryId = e.id,
                    track = e.toDomain(),
                    addedAt = e.addedAt
                )
            }
        }

    override suspend fun addToQueue(track: Track): AddToQueueResult =
        withContext(Dispatchers.IO) {
            // 1) Lokalna kolejka jest źródłem prawdy — zapisujemy zawsze.
            dao.insert(QueueEntity.fromDomain(track, System.currentTimeMillis()))

            // 2) Offline → kończymy. Brak prób kontaktu z siecią.
            if (offlineMode.isEnabledNow()) {
                return@withContext AddToQueueResult.SavedOffline
            }

            // 3) Online → wyślij URI do Spotify (jeśli mamy URI).
            val uri = track.uri
                ?: return@withContext AddToQueueResult.SavedRemoteFailed(
                    "Brak URI utworu — utwór dodany tylko lokalnie."
                )

            runCatching { api.addToQueue(uri) }.fold(
                onSuccess = { AddToQueueResult.Success },
                onFailure = { e ->
                    val msg = when {
                        e.message?.contains("404") == true ||
                            e.message?.contains("No active device") == true ->
                            "Brak aktywnego odtwarzacza Spotify."
                        else -> e.message ?: "Błąd Spotify API"
                    }
                    AddToQueueResult.SavedRemoteFailed(msg)
                }
            )
        }

    override suspend fun removeFromQueue(entryId: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(entryId)
    }

    override suspend fun clearQueue() = withContext(Dispatchers.IO) {
        dao.clear()
    }

    override suspend fun queueSize(): Int = withContext(Dispatchers.IO) {
        dao.count()
    }
}
