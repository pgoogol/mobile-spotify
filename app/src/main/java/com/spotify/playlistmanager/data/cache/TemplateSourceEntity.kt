package com.spotify.playlistmanager.data.cache

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Segment (źródło) w szablonie konfiguracji generatora.
 * Tabela: template_sources
 *
 * Relacja: N template_sources → 1 generator_templates (CASCADE delete).
 * curveType + curveParams zamiast JSON blob — ułatwia query i jest odporne
 * na zmiany formatu serializacji.
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
     * Wartości: "none", "salsa_romantica", "salsa_clasica", "salsa_rapida", "timba", "wave"
     */
    @ColumnInfo(name = "curve_type")
    val curveType: String,

    /**
     * Parametry krzywej (tylko dla Wave): JSON z direction i tracksPerHalfWave.
     * Dla pozostałych krzywych: null.
     * Format: {"direction":"rising","tracksPerHalfWave":3}
     */
    @ColumnInfo(name = "curve_params")
    val curveParams: String? = null
)
