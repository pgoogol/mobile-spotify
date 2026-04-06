package com.spotify.playlistmanager.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.spotify.playlistmanager.data.model.Playlist

/**
 * Cache nagłówka playlisty.
 * Tabela: playlists_cache
 *
 * Dwa niezależne snapshot_id:
 *  - snapshot_id         — stan pobrany z /v1/me/playlists (lista playlist)
 *  - tracks_snapshot_id  — stan w momencie gdy pobieraliśmy utwory tej playlisty
 *
 * Porównanie tracks_snapshot_id z aktualnym snapshot_id z API mówi,
 * czy cache utworów jest nadal aktualny (bez pełnego fetcha).
 *
 * Liked Songs to specjalna "playlista" z id = "__liked__".
 * Dla niej snapshot_id zawsze = null — walidacja świeżości przez TTL.
 */
@Entity(tableName = "playlists_cache")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,

    val name: String,
    val description: String?,

    @ColumnInfo(name = "image_url")
    val imageUrl: String?,

    @ColumnInfo(name = "track_count")
    val trackCount: Int,

    @ColumnInfo(name = "owner_id")
    val ownerId: String,

    /** snapshot_id z /v1/me/playlists — null dla Liked Songs. */
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: String?,

    /** Kiedy nagłówek został zcache'owany (epoch ms). */
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,

    /** Kiedy utwory tej playlisty były ostatnio pobrane (epoch ms), null = nigdy. */
    @ColumnInfo(name = "tracks_fetched_at")
    val tracksFetchedAt: Long? = null,

    /** snapshot_id w momencie pobierania utworów — do walidacji cache utworów. */
    @ColumnInfo(name = "tracks_snapshot_id")
    val tracksSnapshotId: String? = null
) {
    fun toDomain() = Playlist(
        id = id,
        name = name,
        description = description,
        imageUrl = imageUrl,
        trackCount = trackCount,
        ownerId = ownerId,
        snapshotId = snapshotId
    )

    companion object {
        fun fromDomain(p: Playlist, now: Long) = PlaylistEntity(
            id = p.id,
            name = p.name,
            description = p.description,
            imageUrl = p.imageUrl,
            trackCount = p.trackCount,
            ownerId = p.ownerId,
            snapshotId = p.snapshotId,
            fetchedAt = now
        )
    }
}