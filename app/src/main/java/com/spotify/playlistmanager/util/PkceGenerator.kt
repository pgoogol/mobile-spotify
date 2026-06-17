package com.spotify.playlistmanager.util

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generator parametrów PKCE (Proof Key for Code Exchange, RFC 7636).
 *
 * Authorization Code Flow z PKCE jest zalecanym przez Spotify przepływem
 * dla aplikacji mobilnych — w odróżnieniu od Implicit Grant zwraca
 * refresh_token, dzięki czemu sesja może być odświeżana w tle bez
 * ponownego logowania użytkownika.
 *
 * Używa wyłącznie API JVM (java.util.Base64 dostępne od API 26 = minSdk),
 * więc logika jest w pełni testowalna jednostkowo bez Robolectric.
 */
object PkceGenerator {

    private val secureRandom = SecureRandom()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()

    /**
     * code_verifier — losowy ciąg o wysokiej entropii.
     * 64 bajty → ~86 znaków base64url, mieści się w wymaganym zakresie 43–128.
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        return urlEncoder.encodeToString(bytes)
    }

    /**
     * code_challenge = BASE64URL( SHA-256( ASCII(code_verifier) ) ).
     * Metoda S256 — jedyna akceptowana przez Spotify.
     */
    fun deriveCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return urlEncoder.encodeToString(digest)
    }

    /** Losowy parametr `state` — ochrona przed CSRF na callbacku OAuth. */
    fun generateState(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return urlEncoder.encodeToString(bytes)
    }
}
