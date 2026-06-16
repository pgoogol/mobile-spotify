package com.spotify.playlistmanager.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Testy generatora PKCE. Używa wyłącznie API JVM (java.util.Base64,
 * MessageDigest), więc nie wymaga Robolectric.
 */
class PkceGeneratorTest {

    /**
     * Oficjalny wektor testowy z RFC 7636, Appendix B:
     * verifier "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
     * → challenge "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
     */
    @Test
    fun `deriveCodeChallenge zgodny z wektorem RFC 7636`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expected, PkceGenerator.deriveCodeChallenge(verifier))
    }

    @Test
    fun `code_verifier mieści się w zakresie 43-128 znaków`() {
        repeat(50) {
            val verifier = PkceGenerator.generateCodeVerifier()
            assertTrue(
                "Długość ${verifier.length} poza zakresem 43..128",
                verifier.length in 43..128
            )
        }
    }

    @Test
    fun `code_verifier jest base64url bez paddingu i znaków niedozwolonych`() {
        val allowed = Regex("^[A-Za-z0-9\\-._~]+$") // unreserved per RFC 7636
        repeat(50) {
            val verifier = PkceGenerator.generateCodeVerifier()
            assertTrue("Niedozwolone znaki w '$verifier'", allowed.matches(verifier))
            assertFalse("Padding '=' niedozwolony", verifier.contains('='))
        }
    }

    @Test
    fun `kolejne verifiery są różne (entropia)`() {
        val set = (1..100).map { PkceGenerator.generateCodeVerifier() }.toSet()
        assertEquals(100, set.size)
    }

    @Test
    fun `challenge jest url-safe (brak plus, slash, padding)`() {
        val challenge = PkceGenerator.deriveCodeChallenge(PkceGenerator.generateCodeVerifier())
        assertFalse(challenge.contains('+'))
        assertFalse(challenge.contains('/'))
        assertFalse(challenge.contains('='))
    }

    @Test
    fun `state jest niepusty i unikalny`() {
        val a = PkceGenerator.generateState()
        val b = PkceGenerator.generateState()
        assertTrue(a.isNotBlank())
        assertNotEquals(a, b)
    }
}
