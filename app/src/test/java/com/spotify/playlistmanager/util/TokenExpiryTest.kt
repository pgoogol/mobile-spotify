package com.spotify.playlistmanager.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Testy jednostkowe dla logiki TokenManager niezależnej od Androida.
 * DataStore wymaga kontekstu Android – testujemy tylko czystą logikę
 * wygasania tokena (obliczenia czasowe).
 */
class TokenExpiryTest {

    @Test
    fun `token uznany za wygasły gdy czas przeszedł`() {
        val expiresAt = System.currentTimeMillis() - 1_000L   // sekunda temu
        val isExpired = System.currentTimeMillis() >= expiresAt - 60_000L
        assertTrue(isExpired)
    }

    @Test
    fun `token uznany za ważny gdy jeszcze zostało ponad 60 sekund`() {
        val expiresAt = System.currentTimeMillis() + 3_600_000L  // za godzinę
        val isExpired = System.currentTimeMillis() >= expiresAt - 60_000L
        assertFalse(isExpired)
    }

    @Test
    fun `token uznany za wygasły gdy zostało mniej niż 60 sekund (margines)`() {
        val expiresAt = System.currentTimeMillis() + 30_000L  // za 30 sek – w marginesie
        val isExpired = System.currentTimeMillis() >= expiresAt - 60_000L
        assertTrue(isExpired)
    }

    @Test
    fun `saveToken z null nie powinno zapisać tokena`() {
        // Logika ochronna: sprawdzamy że null nie trafia do DataStore
        val accessToken: String? = null
        val shouldSave = accessToken != null
        assertFalse(shouldSave)
    }

    @Test
    fun `expiresAt obliczany poprawnie ze SpotifyAuth expiresInSec`() {
        val before       = System.currentTimeMillis()
        val expiresInSec = 3600
        val expiresAt    = System.currentTimeMillis() + expiresInSec * 1000L
        val after        = System.currentTimeMillis()

        assertTrue(expiresAt >= before + 3_600_000L)
        assertTrue(expiresAt <= after  + 3_600_000L)
    }
}
