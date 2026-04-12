package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures

/**
 * Filtruje pulę utworów po gatunkach muzycznych i wytwórniach.
 *
 * Genres i label w [TrackAudioFeatures] to stringi z CSV, np.:
 *   genres = "timba, salsa dura, cuban"
 *   label  = "Fania Records"
 *
 * Matchowanie: case-insensitive contains. Filtr "timba" matchuje
 * "timba", "Timba cubana", "neo-timba" itp. Jest to celowe — gatunki
 * w CSV rzadko są dokładnymi tokenami, częściej opisowymi frazami.
 *
 * Kolejność filtrów:
 *   1. includeGenres (whitelist — jeśli niepuste, zostaw tylko matchujące)
 *   2. excludeGenres (blacklist — usuń matchujące)
 *   3. includeLabels (whitelist)
 *   4. excludeLabels (blacklist)
 *
 * Pustą listę include/exclude traktujemy jako "brak filtra" (przepuść wszystko).
 */
object TrackFilter {

    /**
     * Filtruje utwory na podstawie gatunków i wytwórni.
     *
     * @param tracks pula utworów do przefiltrowania
     * @param featuresMap mapa trackId → audio features (źródło genres/label)
     * @param includeGenres whitelist gatunków (puste = brak filtra)
     * @param excludeGenres blacklist gatunków (puste = brak filtra)
     * @param includeLabels whitelist wytwórni (puste = brak filtra)
     * @param excludeLabels blacklist wytwórni (puste = brak filtra)
     * @return przefiltrowana lista (może być pusta jeśli nic nie pasuje)
     */
    fun apply(
        tracks: List<Track>,
        featuresMap: Map<String, TrackAudioFeatures>,
        includeGenres: Set<String> = emptySet(),
        excludeGenres: Set<String> = emptySet(),
        includeLabels: Set<String> = emptySet(),
        excludeLabels: Set<String> = emptySet()
    ): List<Track> {
        // Brak żadnych filtrów → zwróć oryginalną listę (fast path)
        if (includeGenres.isEmpty() && excludeGenres.isEmpty() &&
            includeLabels.isEmpty() && excludeLabels.isEmpty()
        ) {
            return tracks
        }

        return tracks.filter { track ->
            val features = track.id?.let { featuresMap[it] }
            val genres = features?.genres?.lowercase() ?: ""
            val label = features?.label?.lowercase() ?: ""

            // 1. Include genres (whitelist)
            if (includeGenres.isNotEmpty()) {
                val matchesAny = includeGenres.any { filter ->
                    genres.contains(filter.lowercase())
                }
                if (!matchesAny) return@filter false
            }

            // 2. Exclude genres (blacklist)
            if (excludeGenres.isNotEmpty()) {
                val matchesAny = excludeGenres.any { filter ->
                    genres.contains(filter.lowercase())
                }
                if (matchesAny) return@filter false
            }

            // 3. Include labels (whitelist)
            if (includeLabels.isNotEmpty()) {
                val matchesAny = includeLabels.any { filter ->
                    label.contains(filter.lowercase())
                }
                if (!matchesAny) return@filter false
            }

            // 4. Exclude labels (blacklist)
            if (excludeLabels.isNotEmpty()) {
                val matchesAny = excludeLabels.any { filter ->
                    label.contains(filter.lowercase())
                }
                if (matchesAny) return@filter false
            }

            true
        }
    }

    /**
     * Wyciąga unikalne gatunki z puli utworów (do UI pickera).
     * Rozdziela pole genres po przecinkach i zwraca posortowaną listę.
     */
    fun extractUniqueGenres(
        tracks: List<Track>,
        featuresMap: Map<String, TrackAudioFeatures>
    ): List<String> {
        return tracks
            .mapNotNull { track -> track.id?.let { featuresMap[it] } }
            .flatMap { it.genres.split(",").map { g -> g.trim() } }
            .filter { it.isNotBlank() }
            .map { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            .distinct()
            .sorted()
    }

    /**
     * Wyciąga unikalne wytwórnie z puli utworów (do UI pickera).
     */
    fun extractUniqueLabels(
        tracks: List<Track>,
        featuresMap: Map<String, TrackAudioFeatures>
    ): List<String> {
        return tracks
            .mapNotNull { track -> track.id?.let { featuresMap[it] } }
            .map { it.label.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
}
