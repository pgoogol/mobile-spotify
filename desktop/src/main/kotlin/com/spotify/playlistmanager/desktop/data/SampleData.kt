package com.spotify.playlistmanager.desktop.data

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures

/**
 * Wbudowana pula utworów z cechami audio — pozwala uruchomić generator
 * desktopowy bez logowania do Spotify / Web API.
 *
 * Cechy (energia, taneczność, valence, BPM) są rozrzucone w pełnym zakresie
 * 0–100, dzięki czemu każda strategia [com.spotify.playlistmanager.domain.model.EnergyCurve]
 * ma z czego dopasowywać krzywą.
 *
 * Docelowo to miejsce zastąpi prawdziwy klient Spotify Web API + OAuth
 * (loopback redirect) — patrz README, sekcja „Wersja desktopowa".
 */
object SampleData {

    private data class Seed(val title: String, val artist: String, val genre: String)

    private val seeds = listOf(
        Seed("Vivir Mi Vida", "Marc Anthony", "salsa"),
        Seed("La Gozadera", "Gente de Zona", "salsa"),
        Seed("Propuesta Indecente", "Romeo Santos", "bachata"),
        Seed("Obsesión", "Aventura", "bachata"),
        Seed("Bailando", "Enrique Iglesias", "latin pop"),
        Seed("Danza Kuduro", "Don Omar", "reggaeton"),
        Seed("Vivir Bailando", "Silvestre Dangond", "vallenato"),
        Seed("El Perdón", "Nicky Jam", "reggaeton"),
        Seed("Darte un Beso", "Prince Royce", "bachata"),
        Seed("La Vida Es Un Carnaval", "Celia Cruz", "salsa"),
        Seed("Idilio", "Willie Colón", "salsa"),
        Seed("Burbujas de Amor", "Juan Luis Guerra", "bachata"),
        Seed("Suavemente", "Elvis Crespo", "merengue"),
        Seed("La Bilirrubina", "Juan Luis Guerra", "merengue"),
        Seed("Eres Mía", "Romeo Santos", "bachata"),
        Seed("Loca", "Shakira", "latin pop"),
        Seed("Gasolina", "Daddy Yankee", "reggaeton"),
        Seed("Lloraras", "Oscar D'León", "salsa"),
        Seed("Que Locura", "Eddie Santiago", "salsa"),
        Seed("Corazón Partío", "Alejandro Sanz", "latin pop"),
        Seed("Bésame", "Camila", "balada"),
        Seed("Te Boté", "Casper Mágico", "reggaeton"),
        Seed("Aguanile", "Héctor Lavoe", "salsa"),
        Seed("Pedro Navaja", "Rubén Blades", "salsa"),
        Seed("Mi Gente", "J Balvin", "reggaeton"),
        Seed("Hawái", "Maluma", "reggaeton"),
        Seed("Despacito", "Luis Fonsi", "latin pop"),
        Seed("Calma", "Pedro Capó", "latin pop"),
        Seed("Tusa", "Karol G", "reggaeton"),
        Seed("La Camisa Negra", "Juanes", "latin pop"),
        Seed("Vivo por Ella", "Andrea Bocelli", "balada"),
        Seed("Quizás", "Enrique Iglesias", "balada"),
    )

    private val camelotCycle = listOf("8B", "9B", "10B", "11A", "8A", "5A", "7B", "12B")
    private val keyCycle = listOf("C", "G", "D", "A", "E", "Am", "Em", "F")

    val tracks: List<Track> = seeds.mapIndexed { i, s ->
        Track(
            id = "sample-%02d".format(i),
            title = s.title,
            artist = s.artist,
            album = "Sample Album ${i / 4 + 1}",
            albumArtUrl = null,
            durationMs = 180_000 + (i % 7) * 15_000,
            popularity = 40 + (i * 7) % 55,
            uri = "spotify:track:sample$i",
            releaseDate = "${2005 + i % 18}",
            previewUrl = null,
        )
    }

    val featuresMap: Map<String, TrackAudioFeatures> = seeds.mapIndexed { i, s ->
        val id = "sample-%02d".format(i)
        // Deterministyczny rozrzut w pełnym zakresie 0–100.
        val energy = 22f + (i * 71) % 70
        val dance = 30f + (i * 37 + 11) % 62
        val valence = 18f + (i * 53 + 7) % 75
        val bpm = 84f + (i * 29) % 56
        id to TrackAudioFeatures(
            spotifyTrackId = id,
            bpm = bpm,
            energy = energy,
            danceability = dance,
            valence = valence,
            acousticness = 12f + (i * 17) % 70,
            instrumentalness = (i * 13 % 30).toFloat(),
            loudness = -12f + (energy / 100f) * 8f,
            camelot = camelotCycle[i % camelotCycle.size],
            musicalKey = keyCycle[i % keyCycle.size],
            timeSignature = 4,
            speechiness = 4f + i % 10,
            liveness = 8f + i % 25,
            genres = s.genre,
            label = "Sample Records",
            isrc = "SAMPLE${1000 + i}",
        )
    }.toMap()
}
