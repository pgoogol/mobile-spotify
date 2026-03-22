package com.spotify.playlistmanager.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase
import com.spotify.playlistmanager.data.model.TrackFeaturesCache

@Database(
    entities = [TrackFeaturesCache::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackFeaturesDao(): TrackFeaturesDao
}
