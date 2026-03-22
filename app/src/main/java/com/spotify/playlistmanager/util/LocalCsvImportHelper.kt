package com.spotify.playlistmanager.util

import android.content.Context
import android.net.Uri
import com.spotify.playlistmanager.data.cache.TrackFeaturesDao
import com.spotify.playlistmanager.data.model.TrackFeaturesCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Odpowiednik load_features_from_csv() z cache.py.
 *
 * Wczytuje cechy audio z pliku CSV wybranego przez użytkownika
 * (przez system file picker) i zapisuje do Room.
 *
 * Oczekiwany format CSV (kolumny mogą być w dowolnej kolejności):
 *   Song, Artist, Spotify Track Id, BPM, Dance, Energy, ...
 */
@Singleton
class LocalCsvImportHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: TrackFeaturesDao
) {
    data class ImportResult(
        val newEntries: Int,
        val totalInCache: Int,
        val errors: List<String>
    )

    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val errors  = mutableListOf<String>()
        var newCount = 0

        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val lines = reader.readLines()
                if (lines.isEmpty()) return@use

                // Parsuj nagłówek
                val header = lines[0].split(",").map { it.trim().removeSurrounding("\"") }
                val idIdx    = header.indexOfFirst { it.equals("Spotify Track Id", true) || it.equals("id", true) }
                val bpmIdx   = header.indexOfFirst { it.equals("BPM", true) || it.equals("tempo", true) }
                val danceIdx = header.indexOfFirst { it.equals("Dance", true) || it.equals("danceability", true) }
                val energyIdx = header.indexOfFirst { it.equals("Energy", true) || it.equals("energy", true) }

                if (idIdx < 0) {
                    errors.add("Brak kolumny 'Spotify Track Id' w pliku CSV")
                    return@use
                }

                lines.drop(1).forEachIndexed { lineNo, line ->
                    runCatching {
                        val cols = parseCsvLine(line)
                        val id = cols.getOrNull(idIdx)?.trim()?.removeSurrounding("\"") ?: return@runCatching

                        val existing = dao.getFeatures(id)
                        if (existing == null) {
                            val bpm    = bpmIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toFloatOrNull() }
                            val dance  = danceIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toFloatOrNull() }
                            val energy = energyIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it)?.toFloatOrNull() }

                            dao.upsert(
                                TrackFeaturesCache(
                                    trackId         = id,
                                    tempo           = bpm,
                                    energy          = energy?.let { it / 100f }, // CSV często 0-100
                                    danceability    = dance?.let { it / 100f },
                                    valence         = null,
                                    acousticness    = null,
                                    instrumentalness = null,
                                    key             = null,
                                    mode            = null
                                )
                            )
                            newCount++
                        }
                    }.onFailure { e ->
                        errors.add("Linia ${lineNo + 2}: ${e.message}")
                    }
                }
            }
        }.onFailure { e ->
            errors.add("Błąd odczytu pliku: ${e.message}")
        }

        ImportResult(
            newEntries    = newCount,
            totalInCache  = dao.count(),
            errors        = errors
        )
    }

    /** Prosty parser CSV obsługujący pola w cudzysłowach. */
    private fun parseCsvLine(line: String): List<String> {
        val result  = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '"'           -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else                -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}
