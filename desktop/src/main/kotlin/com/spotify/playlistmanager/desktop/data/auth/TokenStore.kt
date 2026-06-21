package com.spotify.playlistmanager.desktop.data.auth

import java.io.File
import java.util.Properties

/**
 * Trwałe przechowywanie tokenów Spotify dla desktopu.
 *
 * Zapis do `~/.spotify-playlist-manager/tokens.properties` — odpowiednik
 * Android DataStore z aplikacji mobilnej. Odczyt jest synchroniczny
 * (in-memory), bezpieczny do wołania z interceptora OkHttp.
 */
class TokenStore {

    private val file = File(
        System.getProperty("user.home"),
        ".spotify-playlist-manager/tokens.properties",
    )

    @Volatile var accessToken: String? = null
        private set
    @Volatile var refreshToken: String? = null
        private set
    @Volatile var expiresAt: Long = 0L
        private set
    @Volatile var userId: String? = null
        private set
    @Volatile var displayName: String? = null
        private set

    init { load() }

    val isLoggedIn: Boolean
        get() = accessToken != null &&
            (System.currentTimeMillis() < expiresAt || refreshToken != null)

    fun isExpired(): Boolean =
        expiresAt == 0L || System.currentTimeMillis() >= expiresAt - 60_000L

    @Synchronized
    fun saveTokens(access: String, refresh: String?, expiresInSec: Int) {
        accessToken = access
        expiresAt = System.currentTimeMillis() + expiresInSec * 1000L
        if (refresh != null) refreshToken = refresh
        persist()
    }

    @Synchronized
    fun saveUser(id: String, name: String?) {
        userId = id
        if (name != null) displayName = name
        persist()
    }

    @Synchronized
    fun clear() {
        accessToken = null
        refreshToken = null
        expiresAt = 0L
        userId = null
        displayName = null
        runCatching { file.delete() }
    }

    private fun persist() = runCatching {
        file.parentFile?.mkdirs()
        val props = Properties()
        accessToken?.let { props["access_token"] = it }
        refreshToken?.let { props["refresh_token"] = it }
        props["expires_at"] = expiresAt.toString()
        userId?.let { props["user_id"] = it }
        displayName?.let { props["display_name"] = it }
        file.outputStream().use { props.store(it, "Spotify Playlist Manager — desktop") }
    }

    private fun load() = runCatching {
        if (!file.exists()) return@runCatching
        val props = Properties().apply { file.inputStream().use { load(it) } }
        accessToken = props.getProperty("access_token")
        refreshToken = props.getProperty("refresh_token")
        expiresAt = props.getProperty("expires_at")?.toLongOrNull() ?: 0L
        userId = props.getProperty("user_id")
        displayName = props.getProperty("display_name")
    }
}
