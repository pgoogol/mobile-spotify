package com.spotify.playlistmanager.ui.screens.generate

import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.TrackFilter

/**
 * Helpery do wyciągania unikalnych gatunków i wytwórni dostępnych
 * w aktualnym kontekście ekranu generatora.
 *
 * Wejście:
 *  - featuresMap — mapa trackId → audio features (pochodzi z GenerateViewModel,
 *    zawiera features wszystkich utworów w aktualnym podglądzie + wszystkich
 *    pobranych playlist źródłowych)
 *
 * Ponieważ podgląd może być jeszcze pusty (przed generowaniem), a użytkownik
 * chce już otworzyć dialog filtra, korzystamy ze wszystkich features w mapie —
 * niezależnie od tego, czy są w podglądzie czy nie.
 *
 * Gdy CSV nie był jeszcze zaimportowany, listy są puste i dialog pokazuje
 * odpowiedni komunikat.
 */
object GenreExtractor {

    /**
     * Wyciąga wszystkie unikalne gatunki z features w mapie.
     * Każdy wpis w polu genres może zawierać wiele gatunków rozdzielonych przecinkiem.
     */
    fun extractGenres(
        featuresMap: Map<String, TrackAudioFeatures>
    ): List<String> {
        if (featuresMap.isEmpty()) return emptyList()
        return featuresMap.values
            .flatMap { it.genres.split(",").map { g -> g.trim() } }
            .filter { it.isNotBlank() }
            .map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            .distinct()
            .sorted()
    }

    /**
     * Wyciąga wszystkie unikalne wytwórnie z features w mapie.
     */
    fun extractLabels(
        featuresMap: Map<String, TrackAudioFeatures>
    ): List<String> {
        if (featuresMap.isEmpty()) return emptyList()
        return featuresMap.values
            .map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
}
