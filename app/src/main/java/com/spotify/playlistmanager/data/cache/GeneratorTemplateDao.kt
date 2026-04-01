package com.spotify.playlistmanager.data.cache

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeneratorTemplateDao {

    // ── Odczyt ──────────────────────────────────────────────────────────

    /** Wszystkie szablony posortowane po dacie aktualizacji (najnowsze pierwsze). */
    @Transaction
    @Query("SELECT * FROM generator_templates ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<TemplateWithSources>>

    /** Jeden szablon po ID z relacją. */
    @Transaction
    @Query("SELECT * FROM generator_templates WHERE id = :id")
    suspend fun getById(id: Long): TemplateWithSources?

    /** Liczba szablonów (do badge). */
    @Query("SELECT COUNT(*) FROM generator_templates")
    fun observeCount(): Flow<Int>

    // ── Zapis ───────────────────────────────────────────────────────────

    /** Wstaw nagłówek szablonu, zwraca ID. */
    @Insert
    suspend fun insertTemplate(template: GeneratorTemplateEntity): Long

    /** Wstaw segmenty szablonu. */
    @Insert
    suspend fun insertSources(sources: List<TemplateSourceEntity>)

    // ── Aktualizacja ────────────────────────────────────────────────────

    /** Aktualizuj nagłówek (nazwa, updatedAt). */
    @Update
    suspend fun updateTemplate(template: GeneratorTemplateEntity)

    /** Usuń segmenty szablonu (przed nadpisaniem nowymi). */
    @Query("DELETE FROM template_sources WHERE template_id = :templateId")
    suspend fun deleteSourcesByTemplateId(templateId: Long)

    // ── Usuwanie ────────────────────────────────────────────────────────

    /** Usuń szablon (CASCADE usunie segmenty). */
    @Query("DELETE FROM generator_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
