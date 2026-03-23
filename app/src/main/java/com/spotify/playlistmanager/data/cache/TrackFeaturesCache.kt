package com.spotify.playlistmanager.data.cache

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spotify.playlistmanager.data.model.TrackAudioFeatures

/**
 * Encja Room – persystentny cache cech audio.
 *
 * Celowo oddzielona od domenowego modelu [TrackAudioFeatures]:
 * - [TrackFeaturesCache] żyje w :app i zna Room (@Entity)
 * - [TrackAudioFeatures] żyje w :shared i nie ma żadnych zależności od Androida
 *
 * Mapery między nimi znajdują się w SpotifyRepository.
 */
@Entity(tableName = "track_features_cache")
data class TrackFeaturesCache(
    @PrimaryKey val trackId: String,
    val tempo:            Float?,
    val energy:           Float?,
    val danceability:     Float?,
    val valence:          Float?,
    val acousticness:     Float?,
    val instrumentalness: Float?,
    val key:              Int?,
    val mode:             Int?
) {
    fun toDomain() = TrackAudioFeatures(
        trackId          = trackId,
        tempo            = tempo,
        energy           = energy,
        danceability     = danceability,
        valence          = valence,
        acousticness     = acousticness,
        instrumentalness = instrumentalness,
        key              = key,
        mode             = mode
    )

    companion object {
        fun fromDomain(d: TrackAudioFeatures) = TrackFeaturesCache(
            trackId          = d.trackId,
            tempo            = d.tempo,
            energy           = d.energy,
            danceability     = d.danceability,
            valence          = d.valence,
            acousticness     = d.acousticness,
            instrumentalness = d.instrumentalness,
            key              = d.key,
            mode             = d.mode
        )
    }
}
