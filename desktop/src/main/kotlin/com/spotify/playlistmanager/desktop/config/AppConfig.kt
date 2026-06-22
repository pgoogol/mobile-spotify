package com.spotify.playlistmanager.desktop.config

import java.io.File
import java.util.Properties

/**
 * Konfiguracja desktopowego klienta Spotify.
 *
 * Spotify **Client ID** jest czytany (w tej kolejności) z:
 *  1. właściwości systemowej `-Dspotify.clientId=...`
 *  2. zmiennej środowiskowej `SPOTIFY_CLIENT_ID`
 *  3. pliku `local.properties` w katalogu repo (klucz `spotify.clientId`)
 *
 * Redirect URI dla desktopu to loopback `http://127.0.0.1:8888/callback` —
 * trzeba go dodać w Spotify Developer Dashboard (obok mobilnego deep-linku).
 */
object AppConfig {

    const val REDIRECT_PORT = 8888
    const val REDIRECT_URI = "http://127.0.0.1:$REDIRECT_PORT/callback"

    const val AUTHORIZE_ENDPOINT = "https://accounts.spotify.com/authorize"
    const val ACCOUNTS_BASE_URL = "https://accounts.spotify.com/"
    const val API_BASE_URL = "https://api.spotify.com/"

    /** Zakresy zgodne z aplikacją Android. */
    val scopes = listOf(
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-library-read",
        "playlist-modify-public",
        "playlist-modify-private",
        "user-read-private",
        "user-read-email",
        "user-top-read",
        "user-modify-playback-state",
    )

    val clientId: String? by lazy {
        (System.getProperty("spotify.clientId")
            ?: System.getenv("SPOTIFY_CLIENT_ID")
            ?: readFromLocalProperties())
            ?.trim()
            ?.ifEmpty { null }
    }

    private fun readFromLocalProperties(): String? {
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val lp = dir?.resolve("local.properties")
            if (lp != null && lp.exists()) {
                val props = Properties().apply { lp.inputStream().use { load(it) } }
                (props.getProperty("spotify.clientId") ?: props.getProperty("SPOTIFY_CLIENT_ID"))
                    ?.let { return it }
            }
            dir = dir?.parentFile
        }
        return null
    }
}
