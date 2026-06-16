package com.spotify.playlistmanager.di

import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.spotify.playlistmanager.BuildConfig
import com.spotify.playlistmanager.data.api.AuthInterceptor
import com.spotify.playlistmanager.data.api.SpotifyApiService
import com.spotify.playlistmanager.data.api.SpotifyAuthService
import com.spotify.playlistmanager.data.api.TokenAuthenticator
import com.spotify.playlistmanager.data.repository.SpotifyRepository
import com.spotify.playlistmanager.domain.repository.ISpotifyRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
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
    private const val ACCOUNTS_BASE_URL = "https://accounts.spotify.com/"

    // ── ApplicationScope ───────────────────────────────────────────────────
    // Jeden współdzielony scope na cały proces aplikacji.
    // SupervisorJob: błąd jednej korutyny nie anuluje pozostałych.

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Retrofit / OkHttp ──────────────────────────────────────────────────

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    // ── Klient Web API (api.spotify.com) ───────────────────────────────────
    // AuthInterceptor dokleja Bearer; TokenAuthenticator odświeża token przy 401.

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(loggingInterceptor())
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
                    GsonBuilder().setStrictness(Strictness.LENIENT).create()
                )
            )
            .build()

    @Provides
    @Singleton
    fun provideSpotifyApiService(retrofit: Retrofit): SpotifyApiService =
        retrofit.create(SpotifyApiService::class.java)

    // ── Klient Accounts (accounts.spotify.com) ─────────────────────────────
    // Osobny klient BEZ AuthInterceptora i BEZ Authenticatora — endpoint tokenów
    // nie używa Bearer, a brak Authenticatora zapobiega rekurencji przy odświeżaniu.

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthRetrofit(@Named("auth") okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(ACCOUNTS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                GsonConverterFactory.create(
                    GsonBuilder().setStrictness(Strictness.LENIENT).create()
                )
            )
            .build()

    @Provides
    @Singleton
    fun provideSpotifyAuthService(@Named("auth") retrofit: Retrofit): SpotifyAuthService =
        retrofit.create(SpotifyAuthService::class.java)

}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {

    @Binds
    @Singleton
    abstract fun bindSpotifyRepository(impl: SpotifyRepository): ISpotifyRepository
}