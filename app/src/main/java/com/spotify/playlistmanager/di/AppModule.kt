package com.spotify.playlistmanager.di

import android.content.Context
import androidx.room.Room
import com.google.gson.GsonBuilder
import com.spotify.playlistmanager.BuildConfig
import com.spotify.playlistmanager.data.api.AuthEventBus
import com.spotify.playlistmanager.data.api.AuthInterceptor
import com.spotify.playlistmanager.data.api.SpotifyApiService
import com.spotify.playlistmanager.data.cache.AppDatabase
import com.spotify.playlistmanager.data.cache.TrackFeaturesDao
import com.spotify.playlistmanager.data.repository.SpotifyRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesCache
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val SPOTIFY_BASE_URL = "https://api.spotify.com/"

    // ── Retrofit / OkHttp ──────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG)
                        HttpLoggingInterceptor.Level.BODY
                    else
                        HttpLoggingInterceptor.Level.NONE
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(SPOTIFY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder().setLenient().create()
                )
            )
            .build()

    @Provides
    @Singleton
    fun provideSpotifyApiService(retrofit: Retrofit): SpotifyApiService =
        retrofit.create(SpotifyApiService::class.java)

    // ── Room ───────────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "spotify_playlist_manager.db")
            // Wersja DB zmieniona z 1 na 2 po przeniesieniu TrackFeaturesCache do :app
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideTrackFeaturesDao(db: AppDatabase): TrackFeaturesDao = db.trackFeaturesDao()

    // ── AuthEventBus ───────────────────────────────────────────────────────
    // Singleton dostarczany przez Hilt – nie wymaga osobnego @Provides
    // (Hilt sam tworzy @Singleton klasy z @Inject constructor)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds
    @Singleton
    abstract fun bindSpotifyRepository(impl: SpotifyRepository): ISpotifyRepository

    @Binds
    @Singleton
    abstract fun bindTrackFeaturesCache(impl: TrackFeaturesDao): ITrackFeaturesCache
}
