package com.spotify.playlistmanager.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Relacja M:N playlist ↔ utwory z zachowaniem kolejności.
 * Tabela: playlist_tracks
 *
 * Klucz główny złożony: (playlist_id, position) — gwarantuje unikalność
 * pozycji w obrębie jednej playlisty i pozwala trzymać ten sam utwór
 * na różnych pozycjach (rzadkie, ale dozwolone w Spotify).
 *
 * CASCADE delete na playlist_id: usunięcie wpisu w playlists_cache
 * automatycznie czyści powiązane wiersze w playlist_tracks.
 *
 * Nie ma FK na tracks_cache celowo — utwór może być usunięty z cache
 * (np. LRU w przyszłości) bez blokowania kasowania playlisty.
 * JOIN w zapytaniach po prostu pomija osierocone wiersze.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlist_id"),
        Index("track_id")
    ]
)
data class PlaylistTrackCrossRef(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    /** Pozycja utworu w playliście (0-based). */
    val position: Int
)