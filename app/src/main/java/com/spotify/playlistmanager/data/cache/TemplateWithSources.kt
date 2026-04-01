package com.spotify.playlistmanager.data.cache

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room relation: szablon z powiązanymi segmentami.
 * Używane z @Transaction query.
 */
data class TemplateWithSources(
    @Embedded
    val template: GeneratorTemplateEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "template_id"
    )
    val sources: List<TemplateSourceEntity>
)
