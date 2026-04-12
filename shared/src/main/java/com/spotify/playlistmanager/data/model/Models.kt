package com.spotify.playlistmanager.data.model

import com.spotify.playlistmanager.domain.model.EnergyCurve

// ════════════════════════════════════════════════════════════
//  Spotify Web API – modele odpowiedzi JSON
// ════════════════════════════════════════════════════════════

data class PlaylistsResponse(
    val items: List<SpotifyPlaylist>,
    val next: String?,
    val total: Int,
    val offset: Int,
    val limit: Int
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val images: List<SpotifyImage>,
    val tracks: TracksRef,
    val owner: SpotifyOwner,
    val public: Boolean?,
    val snapshot_id: String? = null
)

data class SpotifyOwner(val id: String, val display_name: String?)
data class SpotifyImage(val url: String, val height: Int?, val width: Int?)
data class TracksRef(val href: String, val total: Int)

data class PlaylistSnapshotResponse(
    val snapshot_id: String?
)

data class PlaylistTracksResponse(
    val items: List<PlaylistTrackItem>,
    val next: String?,
    val total: Int,
    val offset: Int
)

data class PlaylistTrackItem(
    val track: SpotifyTrack?,
    val added_at: String?
)

data class SpotifyTrack(
    val id: String?,
    val name: String,
    val artists: List<SpotifyArtist>,
    val album: SpotifyAlbum,
    val duration_ms: Int,
    val popularity: Int,
    val uri: String?,
    val preview_url: String?
)

data class SpotifyArtist(val id: String, val name: String)

data class SpotifyAlbum(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>,
    val release_date: String?
)

data class SpotifyUser(
    val id: String,
    val display_name: String?,
    val email: String?,
    val images: List<SpotifyImage>,
    val country: String? = null,
    val followers: FollowersRef? = null
)

data class CreatePlaylistRequest(
    val name: String,
    val description: String = "",
    val public: Boolean = false
)

data class AddTracksRequest(val uris: List<String>)

data class CreatePlaylistResponse(
    val id: String,
    val name: String,
    val external_urls: ExternalUrls
)

data class ExternalUrls(val spotify: String)

data class TopArtistsResponse(
    val items: List<SpotifyArtistFull>,
    val next: String?,
    val total: Int
)

data class SpotifyArtistFull(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>,
    val genres: List<String>,
    val popularity: Int,
    val followers: FollowersRef
)

data class FollowersRef(val total: Int)

// ════════════════════════════════════════════════════════════
//  Modele domenowe – bez zależności od Room / Android
// ════════════════════════════════════════════════════════════

data class Track(
    val id: String?,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUrl: String?,
    val durationMs: Int,
    val popularity: Int,
    val uri: String?,
    val releaseDate: String? = null,
    val previewUrl: String? = null
) {
    fun formattedDuration(): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }
}

data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val trackCount: Int,
    val ownerId: String,
    val snapshotId: String? = null
)

data class PlaylistStats(
    val trackCount: Int,
    val totalDurationMs: Long
) {
    fun formattedDuration(): String {
        val totalSeconds = totalDurationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
}

data class UserProfile(
    val id: String,
    val displayName: String?,
    val email: String?,
    val imageUrl: String?,
    val country: String?,
    val followers: Int
)

data class TopArtist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val genres: List<String>,
    val popularity: Int
)

// ════════════════════════════════════════════════════════════
//  Modele generatora playlist
// ════════════════════════════════════════════════════════════

data class PinnedTrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String? = null,
    val sourcePlaylistId: String? = null,
    val fullTrack: Track? = null
)

data class PlaylistSource(
    val id: String = randomId(),
    val playlist: Playlist? = null,
    val trackCount: Int = 10,
    val sortBy: SortOption = SortOption.NONE,
    val energyCurve: EnergyCurve = EnergyCurve.None,
    val pinnedTracks: List<PinnedTrackInfo> = emptyList(),
    /** Czy zastosować optymalizację harmoniczną (Camelot Wheel) po dopasowaniu do krzywej. */
    val harmonicMixing: Boolean = false
)

private fun randomId(): String =
    ByteArray(16)
        .also { kotlin.random.Random.Default.nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

enum class SortOption(val label: String) {
    NONE("Brak"),
    POPULARITY("Popularność"),
    DURATION("Długość"),
    RELEASE_DATE("Data wydania")
}
