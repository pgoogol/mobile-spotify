package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.cache.GeneratorTemplateDao
import com.spotify.playlistmanager.data.cache.GeneratorTemplateEntity
import com.spotify.playlistmanager.data.cache.TemplateSourceEntity
import com.spotify.playlistmanager.data.cache.TemplateWithSources
import com.spotify.playlistmanager.data.model.SortOption
import com.spotify.playlistmanager.domain.model.*
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeneratorTemplateRepository @Inject constructor(
    private val dao: GeneratorTemplateDao
) : IGeneratorTemplateRepository {

    override fun observeAll(): Flow<List<GeneratorTemplate>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCount(): Flow<Int> =
        dao.observeCount()

    override suspend fun getById(id: Long): GeneratorTemplate? =
        withContext(Dispatchers.IO) { dao.getById(id)?.toDomain() }

    override suspend fun save(template: GeneratorTemplate): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val templateId = dao.insertTemplate(
                GeneratorTemplateEntity(
                    name = template.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
            dao.insertSources(template.sources.map { it.toEntity(templateId) })
            templateId
        }

    override suspend fun overwrite(template: GeneratorTemplate) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.updateTemplate(
                GeneratorTemplateEntity(
                    id = template.id,
                    name = template.name,
                    createdAt = template.createdAt,
                    updatedAt = now
                )
            )
            dao.deleteSourcesByTemplateId(template.id)
            dao.insertSources(template.sources.map { it.toEntity(template.id) })
        }

    override suspend fun rename(id: Long, newName: String) =
        withContext(Dispatchers.IO) {
            val existing = dao.getById(id)?.template ?: return@withContext
            dao.updateTemplate(
                existing.copy(name = newName, updatedAt = System.currentTimeMillis())
            )
        }

    override suspend fun delete(id: Long) =
        withContext(Dispatchers.IO) { dao.deleteById(id) }

    // ── Mapery ──────────────────────────────────────────────────────────

    private fun TemplateWithSources.toDomain() = GeneratorTemplate(
        id = template.id,
        name = template.name,
        createdAt = template.createdAt,
        updatedAt = template.updatedAt,
        sources = sources.sortedBy { it.position }.map { it.toDomain() }
    )

    private fun TemplateSourceEntity.toDomain() = TemplateSource(
        position = position,
        playlistId = playlistId,
        playlistName = playlistName,
        trackCount = trackCount,
        sortBy = runCatching { SortOption.valueOf(sortBy) }.getOrDefault(SortOption.NONE),
        energyCurve = deserializeCurve(curveType, curveParams)
    )

    private fun TemplateSource.toEntity(templateId: Long): TemplateSourceEntity {
        val (type, params) = serializeCurve(energyCurve)
        return TemplateSourceEntity(
            templateId = templateId,
            position = position,
            playlistId = playlistId,
            playlistName = playlistName,
            trackCount = trackCount,
            sortBy = sortBy.name,
            curveType = type,
            curveParams = params
        )
    }

    // ── Serializacja EnergyCurve → curveType + curveParams ──────────────

    companion object {
        fun serializeCurve(curve: EnergyCurve): Pair<String, String?> = when (curve) {
            is EnergyCurve.None           -> "none" to null
            is EnergyCurve.SalsaRomantica -> "salsa_romantica" to null
            is EnergyCurve.SalsaClasica   -> "salsa_clasica" to null
            is EnergyCurve.SalsaRapida    -> "salsa_rapida" to null
            is EnergyCurve.Timba          -> "timba" to null
            is EnergyCurve.Wave           -> "wave" to JSONObject().apply {
                put("direction", curve.direction.name.lowercase())
                put("tracksPerHalfWave", curve.tracksPerHalfWave)
            }.toString()
        }

        fun deserializeCurve(type: String, params: String?): EnergyCurve = when (type) {
            "none"            -> EnergyCurve.None
            "salsa_romantica" -> EnergyCurve.SalsaRomantica
            "salsa_clasica"   -> EnergyCurve.SalsaClasica
            "salsa_rapida"    -> EnergyCurve.SalsaRapida
            "timba"           -> EnergyCurve.Timba
            "wave"            -> {
                if (params == null) EnergyCurve.Wave()
                else runCatching {
                    val json = JSONObject(params)
                    val direction = when (json.optString("direction", "rising")) {
                        "falling" -> WaveDirection.FALLING
                        else      -> WaveDirection.RISING
                    }
                    val tracksPerHalfWave = json.optInt("tracksPerHalfWave", 3)
                    EnergyCurve.Wave(direction, tracksPerHalfWave)
                }.getOrDefault(EnergyCurve.Wave())
            }
            else -> EnergyCurve.None
        }
    }
}
