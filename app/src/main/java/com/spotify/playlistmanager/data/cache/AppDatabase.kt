package com.spotify.playlistmanager.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Główna baza danych aplikacji.
 *
 * [TrackFeaturesCache] jest teraz zdefiniowane w :app (nie w :shared),
 * co eliminuje zależność :shared od biblioteki Room.
 */
@Database(
    entities = [TrackFeaturesCache::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackFeaturesDao(): TrackFeaturesDao
}
