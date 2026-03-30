package com.spotify.playlistmanager.di

import android.content.Context
import androidx.room.Room
import com.spotify.playlistmanager.data.cache.TrackFeaturesDao
import com.spotify.playlistmanager.data.cache.TrackFeaturesDatabase
import com.spotify.playlistmanager.data.repository.TrackFeaturesRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTrackFeaturesDatabase(@ApplicationContext ctx: Context): TrackFeaturesDatabase =
        Room.databaseBuilder(ctx, TrackFeaturesDatabase::class.java, "track_features.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideTrackFeaturesDao(db: TrackFeaturesDatabase): TrackFeaturesDao =
        db.trackFeaturesDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindings {

    @Binds
    @Singleton
    abstract fun bindTrackFeaturesRepository(
        impl: TrackFeaturesRepository
    ): ITrackFeaturesRepository
}