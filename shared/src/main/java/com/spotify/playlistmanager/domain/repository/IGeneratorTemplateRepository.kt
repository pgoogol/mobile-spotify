package com.spotify.playlistmanager.domain.repository

import com.spotify.playlistmanager.domain.model.GeneratorTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Kontrakt repozytorium szablonów generatora.
 * Implementacja w :app zna Room; ViewModel zna tylko ten interfejs.
 */
interface IGeneratorTemplateRepository {

    /** Obserwuj wszystkie szablony (posortowane po updated_at DESC). */
    fun observeAll(): Flow<List<GeneratorTemplate>>

    /** Obserwuj liczbę szablonów (do badge). */
    fun observeCount(): Flow<Int>

    /** Pobierz szablon po ID (z segmentami). */
    suspend fun getById(id: Long): GeneratorTemplate?

    /** Zapisz nowy szablon, zwraca ID. */
    suspend fun save(template: GeneratorTemplate): Long

    /** Nadpisz istniejący szablon (aktualizuj nagłówek + podmień segmenty). */
    suspend fun overwrite(template: GeneratorTemplate)

    /** Zmień nazwę szablonu. */
    suspend fun rename(id: Long, newName: String)

    /** Usuń szablon. */
    suspend fun delete(id: Long)
}
