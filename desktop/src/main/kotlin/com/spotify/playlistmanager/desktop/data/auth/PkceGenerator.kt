package com.spotify.playlistmanager.desktop.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generator parametrów PKCE (RFC 7636) — czyste API JVM.
 *
 * Identyczny algorytm jak w aplikacji Android (S256). Spotify akceptuje
 * wyłącznie metodę S256.
 */
object PkceGenerator {

    private val secureRandom = SecureRandom()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return urlEncoder.encodeToString(bytes)
    }

    fun deriveCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return urlEncoder.encodeToString(digest)
    }

    fun generateState(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return urlEncoder.encodeToString(bytes)
    }
}
