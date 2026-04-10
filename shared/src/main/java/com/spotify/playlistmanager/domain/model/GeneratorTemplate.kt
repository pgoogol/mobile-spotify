package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.PinnedTrackInfo
import com.spotify.playlistmanager.data.model.SortOption

/**
 * Model domenowy szablonu konfiguracji generatora.
 * Nie zalezy od Room — czysty Kotlin.
 */
data class GeneratorTemplate(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sources: List<TemplateSource> = emptyList()
)

/**
 * Segment (zrodlo) w szablonie — odpowiednik PlaylistSource
 * ale z trwalymi danymi (playlistId/name zamiast obiektow Playlist).
 *
 * pinnedTracks: lista przypietych utworow zachowana w templacie. Moga pochodzic
 * z OBCYCH playlist (cross-playlist pinning) — w takim wypadku PinnedTrackInfo
 * niesie sourcePlaylistId i fullTrack, dzieki czemu po zaladowaniu templatu
 * utwory beda dostepne nawet jesli obca playlista nie jest aktualnie zaladowana.
 */
data class TemplateSource(
    val position: Int,
    val playlistId: String,
    val playlistName: String,
    val trackCount: Int,
    val sortBy: SortOption = SortOption.NONE,
    val energyCurve: EnergyCurve = EnergyCurve.None,
    val pinnedTracks: List<PinnedTrackInfo> = emptyList()
)