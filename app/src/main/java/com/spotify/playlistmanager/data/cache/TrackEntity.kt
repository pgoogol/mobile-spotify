package com.spotify.playlistmanager.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spotify.playlistmanager.data.model.Track

/**
 * Cache metadanych utworu — niezależny od playlist.
 * Tabela: tracks_cache
 *
 * Utwory na Spotify są niezmienne (ten sam track_id = ten sam utwór),
 * więc cache może rosnąć bez agresywnego czyszczenia. Jedyna zmiana
 * jaka się zdarza to popularity, ale jest na tyle mało istotna dla
 * generatora że akceptujemy lekkie rozjechanie.
 *
 * Utwory bez id (lokalne pliki w playliście) nie są cache'owane —
 * TrackEntity.id jest non-null, a fromDomain() zwraca null dla Track z id == null.
 */
@Entity(tableName = "tracks_cache")
data class TrackEntity(
    @PrimaryKey
    val id: String,

    val title: String,
    val artist: String,
    val album: String,

    @ColumnInfo(name = "album_art_url")
    val albumArtUrl: String?,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Int,

    val popularity: Int,

    val uri: String?,

    @ColumnInfo(name = "release_date")
    val releaseDate: String?,

    @ColumnInfo(name = "preview_url")
    val previewUrl: String?
) {
    fun toDomain() = Track(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumArtUrl = albumArtUrl,
        durationMs = durationMs,
        popularity = popularity,
        uri = uri,
        releaseDate = releaseDate,
        previewUrl = previewUrl
    )

    companion object {
        /**
         * Konwersja z modelu domenowego.
         * Zwraca null dla utworów bez id (lokalne pliki) — nie cache'ujemy ich.
         */
        fun fromDomain(t: Track): TrackEntity? {
            val id = t.id ?: return null
            return TrackEntity(
                id = id,
                title = t.title,
                artist = t.artist,
                album = t.album,
                albumArtUrl = t.albumArtUrl,
                durationMs = t.durationMs,
                popularity = t.popularity,
                uri = t.uri,
                releaseDate = t.releaseDate,
                previewUrl = t.previewUrl
            )
        }
    }
}