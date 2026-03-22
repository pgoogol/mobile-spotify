package com.spotify.playlistmanager.util

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper dla Spotify App Remote SDK.
 *
 * ⚠️  App Remote SDK NIE jest dostępny przez MavenCentral ani JitPack.
 *     Musisz pobrać go ręcznie:
 *     1. https://github.com/spotify/android-sdk/releases
 *     2. Wypakuj -> skopiuj `app-remote-lib/spotify-app-remote-release-X.X.X.aar`
 *        do folderu `app/libs/`
 *     3. W `app/build.gradle.kts` dodaj:
 *        implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
 *        implementation("com.google.code.gson:gson:2.11.0")
 *     4. Odkomentuj sekcje oznaczone jako [APP_REMOTE_SDK] poniżej.
 *
 * Do czasu dodania AAR klasa działa jako no-op stub
 * i nie blokuje kompilacji pozostałej części projektu.
 */
@Singleton
class SpotifyAppRemoteManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "SpotifyAppRemote"

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting   : ConnectionState()
        data object Connected    : ConnectionState()
        data class  Failed(val error: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val isConnected: Boolean get() = _connectionState.value is ConnectionState.Connected

    // ─────────────────────────────────────────────────────────────────────────
    // STUB – zastąp poniższe implementacje gdy dodasz AAR do app/libs/
    // ─────────────────────────────────────────────────────────────────────────

    fun connect(onConnected: (() -> Unit)? = null) {
        Log.w(TAG, "App Remote SDK nie jest podłączony – pobierz AAR ze Spotify GitHub")
        /* [APP_REMOTE_SDK]
        _connectionState.value = ConnectionState.Connecting
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI)
            .showAuthView(true)
            .build()
        SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                _connectionState.value = ConnectionState.Connected
                onConnected?.invoke()
            }
            override fun onFailure(throwable: Throwable) {
                _connectionState.value = ConnectionState.Failed(throwable.message ?: "Unknown")
            }
        })
        */
    }

    fun disconnect() {
        /* [APP_REMOTE_SDK]
        spotifyAppRemote?.let { SpotifyAppRemote.disconnect(it) }
        spotifyAppRemote = null
        */
        _connectionState.value = ConnectionState.Disconnected
    }

    fun play(uri: String) {
        Log.d(TAG, "play($uri) – stub, dodaj AAR żeby działało")
        /* [APP_REMOTE_SDK]  spotifyAppRemote?.playerApi?.play(uri) */
    }

    fun pause()        { /* [APP_REMOTE_SDK] spotifyAppRemote?.playerApi?.pause()    */ }
    fun resume()       { /* [APP_REMOTE_SDK] spotifyAppRemote?.playerApi?.resume()   */ }
    fun skipNext()     { /* [APP_REMOTE_SDK] spotifyAppRemote?.playerApi?.skipNext() */ }
    fun skipPrevious() { /* [APP_REMOTE_SDK] spotifyAppRemote?.playerApi?.skipPrev() */ }

    fun subscribeToPlayerState(onUpdate: (title: String, artist: String) -> Unit) {
        /* [APP_REMOTE_SDK]
        spotifyAppRemote?.playerApi
            ?.subscribeToPlayerState()
            ?.setEventCallback { state ->
                state.track?.let { onUpdate(it.name, it.artist.name) }
            }
        */
    }
}
