package com.spotify.playlistmanager.data.model

/**
 * Cechy audio jednego utworu – model domenowy (bez Room, bez Androida).
 *
 * Wartości procentowe (energy, danceability, …) są przechowywane
 * w skali 0–100 (tak jak w pliku CSV).
 * Konwersja do 0–1 jest robiona na poziomie algorytmów krzywych energii.
 */
data class TrackAudioFeatures(
    val spotifyTrackId:   String,
    val bpm:              Float,
    /** Energia 0–100 */
    val energy:           Float,
    /** Taneczność 0–100 */
    val danceability:     Float,
    /** Valence (nastrój) 0–100 */
    val valence:          Float,
    /** Akustyczność 0–100 */
    val acousticness:     Float,
    /** Instrumentalność 0–100 */
    val instrumentalness: Float,
    /** Głośność w dB (wartość ujemna) */
    val loudness:         Float,
    /** Klucz harmoniczny np. "10B", "9A" */
    val camelot:          String,
    /** Klucz muzyczny np. "G", "D" */
    val musicalKey:       String,
    /** Metrum np. 4 */
    val timeSignature:    Int,
    /** Speechiness 0–100 */
    val speechiness:      Float,
    /** Liveness 0–100 */
    val liveness:         Float,
    /** Gatunki z CSV np. "timba, salsa" */
    val genres:           String,
    /** Label (wytwórnia) */
    val label:            String,
    /** ISRC */
    val isrc:             String
)
