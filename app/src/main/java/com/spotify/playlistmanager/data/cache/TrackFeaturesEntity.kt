package com.spotify.playlistmanager.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spotify.playlistmanager.data.model.TrackAudioFeatures

@Entity(tableName = "track_features")
data class TrackFeaturesEntity(
    @PrimaryKey
    @ColumnInfo(name = "spotify_track_id")
    val spotifyTrackId: String,

    val bpm: Float,
    val energy: Float,
    val danceability: Float,
    val valence: Float,
    val acousticness: Float,
    val instrumentalness: Float,
    val loudness: Float,
    val camelot: String,

    @ColumnInfo(name = "musical_key")
    val musicalKey: String,

    @ColumnInfo(name = "time_signature")
    val timeSignature: Int,

    val speechiness: Float,
    val liveness: Float,
    val genres: String,
    val label: String,
    val isrc: String
) {
    fun toDomain() = TrackAudioFeatures(
        spotifyTrackId = spotifyTrackId,
        bpm = bpm,
        energy = energy,
        danceability = danceability,
        valence = valence,
        acousticness = acousticness,
        instrumentalness = instrumentalness,
        loudness = loudness,
        camelot = camelot,
        musicalKey = musicalKey,
        timeSignature = timeSignature,
        speechiness = speechiness,
        liveness = liveness,
        genres = genres,
        label = label,
        isrc = isrc
    )

    companion object {
        fun fromDomain(f: TrackAudioFeatures) = TrackFeaturesEntity(
            spotifyTrackId = f.spotifyTrackId,
            bpm = f.bpm,
            energy = f.energy,
            danceability = f.danceability,
            valence = f.valence,
            acousticness = f.acousticness,
            instrumentalness = f.instrumentalness,
            loudness = f.loudness,
            camelot = f.camelot,
            musicalKey = f.musicalKey,
            timeSignature = f.timeSignature,
            speechiness = f.speechiness,
            liveness = f.liveness,
            genres = f.genres,
            label = f.label,
            isrc = f.isrc
        )
    }
}