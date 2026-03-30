package com.spotify.playlistmanager.data.cache

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities     = [TrackFeaturesEntity::class],
    version      = 1,
    exportSchema = false
)
abstract class TrackFeaturesDatabase : RoomDatabase() {
    abstract fun trackFeaturesDao(): TrackFeaturesDao
}
