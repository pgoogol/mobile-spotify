package com.spotify.playlistmanager.data.csv

import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import java.io.InputStream

/**
 * Parser CSV z cechami audio.
 *
 * Kolumny (kolejność z nagłówka pliku CSV):
 *  0=#  1=Song  2=Artist  3=BPM  4=Camelot  5=Energy  6=Added At
 *  7=Duration  8=Popularity  9=Genres  10=Parent Genres  11=Album
 *  12=Album Date  13=Dance  14=Acoustic  15=Instrumental  16=Valence
 *  17=Speech  18=Live  19=Loud (Db)  20=Key  21=Time Signature
 *  22=Spotify Track Id  23=Label  24=ISRC
 *
 * Obsługuje cudzysłowy RFC-4180, podwójne cudzysłowy wewnątrz pól,
 * puste wartości numeryczne (→ 0).
 */
object CsvParser {

    data class ParseResult(
        val features: List<TrackAudioFeatures>,
        val skipped: Int,
        val errors: List<String>
    )

    fun parse(stream: InputStream): ParseResult {
        val features = mutableListOf<TrackAudioFeatures>()
        val errors = mutableListOf<String>()
        var skipped = 0
        var isHeader = true

        stream.bufferedReader(Charsets.UTF_8).forEachLine { raw ->
            if (isHeader) {
                isHeader = false; return@forEachLine
            }
            val line = raw.trim()
            if (line.isEmpty()) return@forEachLine

            val cols = splitCsvLine(line)
            if (cols.size < 25) {
                skipped++
                errors += "Pominięto (za mało kolumn ${cols.size}/25): ${line.take(60)}"
                return@forEachLine
            }

            val trackId = cols[22].trim()
            if (trackId.isEmpty()) {
                skipped++
                errors += "Pominięto (brak Spotify Track Id): ${line.take(60)}"
                return@forEachLine
            }

            runCatching {
                TrackAudioFeatures(
                    spotifyTrackId = trackId,
                    bpm = cols[3].toFloatOrZero(),
                    energy = cols[5].toFloatOrZero(),
                    danceability = cols[13].toFloatOrZero(),
                    valence = cols[16].toFloatOrZero(),
                    acousticness = cols[14].toFloatOrZero(),
                    instrumentalness = cols[15].toFloatOrZero(),
                    loudness = cols[19].toFloatOrZero(),
                    camelot = cols[4].trim(),
                    musicalKey = cols[20].trim(),
                    timeSignature = cols[21].toIntOrZero(),
                    speechiness = cols[17].toFloatOrZero(),
                    liveness = cols[18].toFloatOrZero(),
                    genres = cols[9].trim(),
                    label = cols[23].trim(),
                    isrc = cols[24].trim()
                )
            }.onSuccess { features += it }
                .onFailure { e ->
                    skipped++
                    errors += "Błąd wiersza: ${e.message} → ${line.take(60)}"
                }
        }

        return ParseResult(features, skipped, errors)
    }

    // ── RFC-4180 tokenizer ────────────────────────────────────────────────────

    private fun splitCsvLine(line: String): List<String> {
        val cols = mutableListOf<String>()
        val buf = StringBuilder()
        var inQ = false
        var i = 0
        while (i < line.length) {
            when {
                line[i] == '"' && !inQ -> inQ = true
                line[i] == '"' && inQ ->
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        buf.append('"'); i++
                    } else inQ = false

                line[i] == ',' && !inQ -> {
                    cols += buf.toString(); buf.clear()
                }

                else -> buf.append(line[i])
            }
            i++
        }
        cols += buf.toString()
        return cols
    }

    private fun String.toFloatOrZero() = trim().toFloatOrNull() ?: 0f
    private fun String.toIntOrZero() = trim().toIntOrNull() ?: 0
}