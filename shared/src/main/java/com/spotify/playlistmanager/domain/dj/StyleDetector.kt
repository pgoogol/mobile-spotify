package com.spotify.playlistmanager.domain.dj

import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.Substyle

/**
 * Wykrywa styl i podstyl utworu na podstawie pola `TrackAudioFeatures.genres`
 * (surowy string z CSV, np. "timba, salsa" albo "bachata, bachata rosa").
 *
 * Priorytety wykrywania podstylu — od najbardziej specyficznego:
 *   timba → TIMBA
 *   son cubano / son → SON_CUBANO
 *   rumba → RUMBA
 *   salsa (catch-all dla salsy) → SALSA_GENERIC
 *   bachata → BACHATA
 *
 * `Style == null` oznacza, że utwór nie pasuje do żadnego stylu generatora
 * i powinien być pominięty (nie wchodzi do puli).
 */
class StyleDetector {

    data class Detection(val style: Style?, val substyle: Substyle)

    fun detect(audio: TrackAudioFeatures): Detection {
        val tokens = audio.genres
            .lowercase()
            .split(',', ';', '/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return Detection(null, Substyle.SALSA_GENERIC)

        val joined = tokens.joinToString(" ")

        // Bachata wykrywamy NAJPIERW — żeby utwór tagowany "bachata, salsa fusion"
        // nie wpadł do salsy.
        if (tokens.any { it.contains("bachata") }) {
            return Detection(Style.BACHATA, Substyle.BACHATA)
        }

        return when {
            "timba" in joined -> Detection(Style.SALSA, Substyle.TIMBA)
            "son cubano" in joined || tokens.any { it == "son" } ->
                Detection(Style.SALSA, Substyle.SON_CUBANO)
            "rumba" in joined -> Detection(Style.SALSA, Substyle.RUMBA)
            "salsa" in joined -> Detection(Style.SALSA, Substyle.SALSA_GENERIC)
            else -> Detection(null, Substyle.SALSA_GENERIC)
        }
    }
}
