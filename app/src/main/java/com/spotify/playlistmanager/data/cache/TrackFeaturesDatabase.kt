package com.spotify.playlistmanager.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackFeaturesEntity::class,
        GeneratorTemplateEntity::class,
        TemplateSourceEntity::class
    ],
    version  = 2,
    exportSchema = false
)
abstract class TrackFeaturesDatabase : RoomDatabase() {
    abstract fun trackFeaturesDao(): TrackFeaturesDao
    abstract fun generatorTemplateDao(): GeneratorTemplateDao

    companion object {
        /**
         * Migracja addytywna v1→v2.
         * Dodaje dwie nowe tabele — nie zmienia istniejącej track_features.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS generator_templates (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name       TEXT    NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS template_sources (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        template_id   INTEGER NOT NULL,
                        position      INTEGER NOT NULL,
                        playlist_id   TEXT    NOT NULL,
                        playlist_name TEXT    NOT NULL,
                        track_count   INTEGER NOT NULL,
                        sort_by       TEXT    NOT NULL,
                        curve_type    TEXT    NOT NULL,
                        curve_params  TEXT,
                        FOREIGN KEY (template_id) REFERENCES generator_templates(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_template_sources_template_id
                    ON template_sources(template_id)
                """.trimIndent())
            }
        }
    }
}
