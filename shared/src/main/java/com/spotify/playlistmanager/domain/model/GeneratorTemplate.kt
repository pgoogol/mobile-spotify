package com.spotify.playlistmanager.domain.model

import com.spotify.playlistmanager.data.model.SortOption

/**
 * Model domenowy szablonu konfiguracji generatora.
 * Nie zależy od Room — czysty Kotlin.
 */
data class GeneratorTemplate(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sources: List<TemplateSource> = emptyList()
)

/**
 * Segment (źródło) w szablonie — odpowiednik PlaylistSource
 * ale z trwałymi danymi (playlistId/name zamiast obiektów Playlist).
 */
data class TemplateSource(
    val position: Int,
    val playlistId: String,
    val playlistName: String,
    val trackCount: Int,
    val sortBy: SortOption = SortOption.NONE,
    val energyCurve: EnergyCurve = EnergyCurve.None
)
