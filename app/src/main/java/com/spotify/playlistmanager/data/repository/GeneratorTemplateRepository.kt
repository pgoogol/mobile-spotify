package com.spotify.playlistmanager.data.repository

import com.spotify.playlistmanager.data.cache.GeneratorTemplateDao
import com.spotify.playlistmanager.data.cache.GeneratorTemplateEntity
import com.spotify.playlistmanager.data.cache.TemplateSourceEntity
import com.spotify.playlistmanager.data.cache.TemplateWithSources
import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.SortOption
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.domain.model.*
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
        energyCurve = deserializeCurve(curveType, curveParams),
        pinnedTracks = deserializePinnedTracks(pinnedTracksJson)
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
            curveParams = params,
            pinnedTracksJson = serializePinnedTracks(pinnedTracks)
        )
    }

    // ── Serializacja EnergyCurve i Pinned Tracks ────────────────────────

    companion object {
        fun serializeCurve(curve: EnergyCurve): Pair<String, String?> = when (curve) {
            is EnergyCurve.None           -> "none" to null
            is EnergyCurve.SalsaRomantica -> "salsa_romantica" to null
            is EnergyCurve.SalsaClasica   -> "salsa_clasica" to null
            is EnergyCurve.SalsaRapida    -> "salsa_rapida" to null
            is EnergyCurve.Timba          -> "timba" to null
            is EnergyCurve.BachataRise    -> "bachata_rise" to null
            is EnergyCurve.BachataArc     -> "bachata_arc" to null
            is EnergyCurve.Crescendo      -> "crescendo" to null
            is EnergyCurve.Peak           -> "peak" to null
            is EnergyCurve.Wave           -> "wave" to JSONObject().apply {
                put("direction", curve.direction.name.lowercase())
                put("tracksPerHalfWave", curve.tracksPerHalfWave)
                put("center", curve.center.toDouble())
            }.toString()
        }

        fun deserializeCurve(type: String, params: String?): EnergyCurve = when (type) {
            "none" -> EnergyCurve.None
            "salsa_romantica" -> EnergyCurve.SalsaRomantica
            "salsa_clasica" -> EnergyCurve.SalsaClasica
            "salsa_rapida" -> EnergyCurve.SalsaRapida
            "timba" -> EnergyCurve.Timba
            "bachata_rise" -> EnergyCurve.BachataRise
            "bachata_arc" -> EnergyCurve.BachataArc
            "crescendo" -> EnergyCurve.Crescendo
            "peak" -> EnergyCurve.Peak
            "wave" -> {
                if (params == null) EnergyCurve.None
                else runCatching {
                    val obj = JSONObject(params)
                    val dir = obj.optString("direction", "rising")
                    val tphw = obj.optInt("tracksPerHalfWave", 3)
                    // Wsteczna kompatybilność: stare zapisy nie mają "center" —
                    // optDouble z defaultem daje 0.50f (obecne zachowanie)
                    val center = obj.optDouble("center", EnergyCurve.Wave.CENTER_UNIVERSAL.toDouble()).toFloat()
                    EnergyCurve.Wave(
                        direction = WaveDirection.valueOf(dir.uppercase()),
                        tracksPerHalfWave = tphw,
                        center = center
                    )
                }.getOrDefault(EnergyCurve.None)
            }
            else -> EnergyCurve.None
        }

        // ── Pinned Tracks JSON ──────────────────────────────────────────

        /**
         * Serializuje liste pinned do JSON Array. Zwraca null gdy lista pusta
         * (zeby kolumna byla NULL — czytelniejsze niz pusty "[]").
         */
        fun serializePinnedTracks(pinned: List<PinnedTrackInfo>): String? {
            if (pinned.isEmpty()) return null
            val arr = JSONArray()
            for (p in pinned) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("artist", p.artist)
                    if (p.albumArtUrl != null) put("albumArtUrl", p.albumArtUrl)
                    if (p.sourcePlaylistId != null) put("sourcePlaylistId", p.sourcePlaylistId)
                    if (p.fullTrack != null) put("fullTrack", trackToJson(p.fullTrack!!))
                }
                arr.put(obj)
            }
            return arr.toString()
        }

        /**
         * Deserializuje JSON Array do listy PinnedTrackInfo.
         * Bledy parsowania (ucharowane wartosci, brakujace pola) skutkuja
         * pominieciem danego wpisu, nie crashem.
         */
        fun deserializePinnedTracks(json: String?): List<PinnedTrackInfo> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val title = obj.optString("title")
                        val artist = obj.optString("artist")
                        val albumArtUrl = obj.optString("albumArtUrl").takeIf { it.isNotBlank() }
                        val sourcePlaylistId = obj.optString("sourcePlaylistId").takeIf { it.isNotBlank() }
                        val fullTrack = obj.optJSONObject("fullTrack")?.let { jsonToTrack(it) }
                        add(
                            PinnedTrackInfo(
                                id = id,
                                title = title,
                                artist = artist,
                                albumArtUrl = albumArtUrl,
                                sourcePlaylistId = sourcePlaylistId,
                                fullTrack = fullTrack
                            )
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

        // ── Helpery Track <-> JSON ──────────────────────────────────────

        private fun trackToJson(track: Track): JSONObject = JSONObject().apply {
            if (track.id != null) put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            if (track.albumArtUrl != null) put("albumArtUrl", track.albumArtUrl)
            put("durationMs", track.durationMs)
            put("popularity", track.popularity)
            if (track.uri != null) put("uri", track.uri)
            if (track.releaseDate != null) put("releaseDate", track.releaseDate)
            if (track.previewUrl != null) put("previewUrl", track.previewUrl)
        }

        private fun jsonToTrack(obj: JSONObject): Track? = runCatching {
            Track(
                id = obj.optString("id").takeIf { it.isNotBlank() },
                title = obj.optString("title"),
                artist = obj.optString("artist"),
                album = obj.optString("album"),
                albumArtUrl = obj.optString("albumArtUrl").takeIf { it.isNotBlank() },
                durationMs = obj.optInt("durationMs"),
                popularity = obj.optInt("popularity"),
                uri = obj.optString("uri").takeIf { it.isNotBlank() },
                releaseDate = obj.optString("releaseDate").takeIf { it.isNotBlank() },
                previewUrl = obj.optString("previewUrl").takeIf { it.isNotBlank() }
            )
        }.getOrNull()
    }
}