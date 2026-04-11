package com.spotify.playlistmanager.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Segment (zrodlo) w szablonie konfiguracji generatora.
 * Tabela: template_sources
 *
 * Relacja: N template_sources -> 1 generator_templates (CASCADE delete).
 */
@Entity(
    tableName = "template_sources",
    foreignKeys = [
        ForeignKey(
            entity = GeneratorTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["template_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("template_id")]
)
data class TemplateSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "template_id")
    val templateId: Long,

    /** Pozycja segmentu w szablonie (0-based). */
    val position: Int,

    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "playlist_name")
    val playlistName: String,

    @ColumnInfo(name = "track_count")
    val trackCount: Int,

    /** Klucz SortOption.name, np. "NONE", "POPULARITY". */
    @ColumnInfo(name = "sort_by")
    val sortBy: String,

    /**
     * Typ krzywej energii — odpowiada @SerialName z EnergyCurve sealed class.
     * Wartosci: "none", "salsa_romantica", "salsa_clasica", "salsa_rapida", "timba",
     *           "bachata_rise", "bachata_arc", "crescendo", "peak", "wave"
     */
    @ColumnInfo(name = "curve_type")
    val curveType: String,

    /**
     * Parametry krzywej (tylko dla Wave): JSON z direction, tracksPerHalfWave i center.
     * Dla pozostalych krzywych: null.
     */
    @ColumnInfo(name = "curve_params")
    val curveParams: String? = null,

    /**
     * Lista pinned tracks zserializowana jako JSON Array. Null = brak pinned.
     * Format pojedynczego elementu:
     * {
     *   "id": "...", "title": "...", "artist": "...",
     *   "albumArtUrl": "...",
     *   "sourcePlaylistId": "...",
     *   "fullTrack": { Track... }    // opcjonalnie
     * }
     *
     * Dodane w migracji v3->v4. Stare templates maja tu null.
     */
    @ColumnInfo(name = "pinned_tracks_json")
    val pinnedTracksJson: String? = null
)