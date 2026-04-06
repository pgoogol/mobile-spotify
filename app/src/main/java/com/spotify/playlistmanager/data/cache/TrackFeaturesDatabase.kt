package com.spotify.playlistmanager.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackFeaturesEntity::class,
        GeneratorTemplateEntity::class,
        TemplateSourceEntity::class,
        PlaylistEntity::class,
        TrackEntity::class,
        PlaylistTrackCrossRef::class
    ],
    version  = 3,
    exportSchema = false
)
abstract class TrackFeaturesDatabase : RoomDatabase() {
    abstract fun trackFeaturesDao(): TrackFeaturesDao
    abstract fun generatorTemplateDao(): GeneratorTemplateDao
    abstract fun playlistCacheDao(): PlaylistCacheDao

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
        /**
         * Migracja addytywna v2→v3.
         * Dodaje trzy nowe tabele dla cache playlist i utworów:
         *  - playlists_cache      — nagłówki playlist z snapshot_id
         *  - tracks_cache         — metadane utworów (niezależne od playlist)
         *  - playlist_tracks      — relacja M:N z kolejnością (position)
         *
         * Nie rusza istniejących tabel (track_features, generator_templates,
         * template_sources). Nazwy indeksów zgodne z konwencją Room:
         * index_<tabela>_<kolumna>.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlists_cache (
                        id                  TEXT    PRIMARY KEY NOT NULL,
                        name                TEXT    NOT NULL,
                        description         TEXT,
                        image_url           TEXT,
                        track_count         INTEGER NOT NULL,
                        owner_id            TEXT    NOT NULL,
                        snapshot_id         TEXT,
                        fetched_at          INTEGER NOT NULL,
                        tracks_fetched_at   INTEGER,
                        tracks_snapshot_id  TEXT
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tracks_cache (
                        id              TEXT    PRIMARY KEY NOT NULL,
                        title           TEXT    NOT NULL,
                        artist          TEXT    NOT NULL,
                        album           TEXT    NOT NULL,
                        album_art_url   TEXT,
                        duration_ms     INTEGER NOT NULL,
                        popularity      INTEGER NOT NULL,
                        uri             TEXT,
                        release_date    TEXT,
                        preview_url     TEXT
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS playlist_tracks (
                        playlist_id TEXT    NOT NULL,
                        track_id    TEXT    NOT NULL,
                        position    INTEGER NOT NULL,
                        PRIMARY KEY (playlist_id, position),
                        FOREIGN KEY (playlist_id) REFERENCES playlists_cache(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlist_id
                    ON playlist_tracks(playlist_id)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_playlist_tracks_track_id
                    ON playlist_tracks(track_id)
                """.trimIndent())
            }
        }
    }
}
