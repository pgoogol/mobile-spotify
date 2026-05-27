package com.spotify.playlistmanager.domain.repository

import com.spotify.playlistmanager.data.model.Track
import kotlinx.coroutines.flow.Flow

/**
 * Kontrakt lokalnej kolejki odtwarzania.
 *
 * Hybryda online/offline:
 *  - online (OfflineModeManager.isEnabled == false): utwór trafia do Spotify
 *    Web API (/v1/me/player/queue) ORAZ do lokalnej tabeli queue_entries.
 *  - offline: utwór trafia wyłącznie do lokalnej tabeli queue_entries.
 *
 * Lokalna kolejka jest źródłem prawdy dla UI — zawsze wyświetlamy
 * jej zawartość, niezależnie od tego, czy Spotify zaakceptowało request.
 */
interface IQueueRepository {

    /** Reaktywny strumień zawartości kolejki, posortowanej chronologicznie. */
    val queueFlow: Flow<List<QueueEntry>>

    /**
     * Dodaje utwór na koniec kolejki.
     * Zwraca rezultat informujący czy Spotify API zaakceptowało wpis.
     */
    suspend fun addToQueue(track: Track): AddToQueueResult

    /** Usuwa pojedynczy wpis z kolejki (po identyfikatorze wpisu, nie utworu). */
    suspend fun removeFromQueue(entryId: Long)

    /** Czyści całą kolejkę. */
    suspend fun clearQueue()

    /** Aktualna liczba utworów w kolejce. */
    suspend fun queueSize(): Int
}

/** Pojedynczy wpis kolejki — utwór + metadane wpisu (id, czas dodania). */
data class QueueEntry(
    val entryId: Long,
    val track: Track,
    val addedAt: Long
)

/** Rezultat dodawania do kolejki. */
sealed class AddToQueueResult {
    /** Online — Spotify zaakceptowało URI, lokalna kolejka również zaktualizowana. */
    data object Success : AddToQueueResult()

    /** Offline — zapisano tylko w lokalnej kolejce, bez wywołania API. */
    data object SavedOffline : AddToQueueResult()

    /**
     * Online — zapisano lokalnie, ale wywołanie API się nie powiodło
     * (np. brak aktywnego odtwarzacza, błąd sieci, brak URI utworu).
     */
    data class SavedRemoteFailed(val reason: String) : AddToQueueResult()
}
