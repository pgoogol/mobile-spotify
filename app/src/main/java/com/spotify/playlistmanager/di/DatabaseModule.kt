package com.spotify.playlistmanager.di

import android.content.Context
import androidx.room.Room
import com.spotify.playlistmanager.data.cache.GeneratorTemplateDao
import com.spotify.playlistmanager.data.cache.PlaylistCacheDao
import com.spotify.playlistmanager.data.cache.TrackFeaturesDao
import com.spotify.playlistmanager.data.cache.TrackFeaturesDatabase
import com.spotify.playlistmanager.data.repository.GeneratorTemplateRepository
import com.spotify.playlistmanager.data.repository.TrackFeaturesRepository
import com.spotify.playlistmanager.domain.repository.IGeneratorTemplateRepository
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
            .addMigrations(
                TrackFeaturesDatabase.MIGRATION_1_2,
                TrackFeaturesDatabase.MIGRATION_2_3
            )
            .build()

    @Provides
    @Singleton
    fun provideTrackFeaturesDao(db: TrackFeaturesDatabase): TrackFeaturesDao =
        db.trackFeaturesDao()

    @Provides
    @Singleton
    fun provideGeneratorTemplateDao(db: TrackFeaturesDatabase): GeneratorTemplateDao =
        db.generatorTemplateDao()

    @Provides
    @Singleton
    fun providePlaylistCacheDao(db: TrackFeaturesDatabase): PlaylistCacheDao =
        db.playlistCacheDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindings {

    @Binds
    @Singleton
    abstract fun bindTrackFeaturesRepository(
        impl: TrackFeaturesRepository
    ): ITrackFeaturesRepository

    @Binds
    @Singleton
    abstract fun bindGeneratorTemplateRepository(
        impl: GeneratorTemplateRepository
    ): IGeneratorTemplateRepository
}