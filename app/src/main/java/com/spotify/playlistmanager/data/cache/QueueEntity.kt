package com.spotify.playlistmanager.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spotify.playlistmanager.data.model.Track

/**
 * Wpis lokalnej kolejki odtwarzania.
 *
 * Tabela jest świadomie zdenormalizowana — duplikujemy metadane utworu
 * (title, artist, album, ...) zamiast trzymać tylko FK do tracks_cache.
 * Powód: użytkownik może wyczyścić cache playlist (clearAll), a kolejka
 * powinna przetrwać. Denormalizacja kosztuje ~150B na wpis i sprawia,
 * że kolejka jest samodzielna.
 *
 * Kolejność = porządek wstawiania = ORDER BY id ASC (autoincrement).
 */
@Entity(tableName = "queue_entries")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "added_at")
    val addedAt: Long,

    val title: String,
    val artist: String,
    val album: String,

    @ColumnInfo(name = "album_art_url")
    val albumArtUrl: String?,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Int,

    val uri: String?
) {
    fun toDomain(): Track = Track(
        id = trackId,
        title = title,
        artist = artist,
        album = album,
        albumArtUrl = albumArtUrl,
        durationMs = durationMs,
        popularity = 0,
        uri = uri
    )

    companion object {
        fun fromDomain(t: Track, now: Long): QueueEntity = QueueEntity(
            trackId = t.id ?: "",
            addedAt = now,
            title = t.title,
            artist = t.artist,
            album = t.album,
            albumArtUrl = t.albumArtUrl,
            durationMs = t.durationMs,
            uri = t.uri
        )
    }
}
