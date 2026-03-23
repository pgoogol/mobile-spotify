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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier dla CoroutineScope powiązanego z życiem procesu aplikacji.
 * Wstrzykiwany do TokenManager — zastępuje ręczny CoroutineScope(SupervisorJob()),
 * który nie był anulowany (wyciek).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val SPOTIFY_BASE_URL = "https://api.spotify.com/"

    // ── ApplicationScope ───────────────────────────────────────────────────
    // Jeden współdzielony scope na cały proces aplikacji.
    // SupervisorJob: błąd jednej korutyny nie anuluje pozostałych.

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Retrofit / OkHttp ──────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
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
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
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
            // Room 2.7+ wymaga jawnego parametru dropAllTables — stary bezargumentowy
            // wariant jest deprecated. Zachowanie identyczne: dane usuwane przy
            // niezgodności schematu (akceptowalne bo cache jest odtwarzalny z API).
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideTrackFeaturesDao(db: AppDatabase): TrackFeaturesDao = db.trackFeaturesDao()
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