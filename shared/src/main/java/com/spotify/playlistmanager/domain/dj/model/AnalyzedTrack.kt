package com.spotify.playlistmanager.domain.dj.model

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures

/**
 * Utwór po analizie — `Track` + `TrackAudioFeatures` + wykryty styl/podstyl
 * + policzone score'y. Wszystkie wartości w przedziale [0, 1] (poza `bpmFolded`,
 * który jest w BPM-ach).
 *
 * `passesGate` — utwór spełnił `danceFloorGate` profilu i przejdzie do puli.
 * `BlockGenerator` filtruje na `passesGate = true`.
 */
data class AnalyzedTrack(
    val track: Track,
    val audio: TrackAudioFeatures,
    val style: Style,
    val substyle: Substyle,

    val bpmFolded: Float,
    val energyN: Float,
    val valenceN: Float,
    val popularityN: Float,
    val tempoFeeling: Float,
    val energyScore: Float,
    val danceFlowScore: Float,

    val passesGate: Boolean
) {
    /** Wygodny lookup ID utworu (Track może mieć null id w skrajnych przypadkach). */
    val id: String? get() = track.id
    val artist: String get() = track.artist
}
