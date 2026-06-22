package com.spotify.playlistmanager.domain.model

import org.junit.Assert.*
import org.junit.Test

class CamelotWheelTest {

    // ── Parsowanie ──────────────────────────────────────────────────────

    @Test
    fun `parseCamelot parses standard format`() {
        val key = CamelotWheel.parseCamelot("8B")
        assertNotNull(key)
        assertEquals(8, key!!.number)
        assertEquals('B', key.letter)
    }

    @Test
    fun `parseCamelot parses lowercase letter`() {
        val key = CamelotWheel.parseCamelot("11a")
        assertNotNull(key)
        assertEquals(11, key!!.number)
        assertEquals('A', key.letter)
    }

    @Test
    fun `parseCamelot parses two-digit number`() {
        val key = CamelotWheel.parseCamelot("12B")
        assertNotNull(key)
        assertEquals(12, key!!.number)
    }

    @Test
    fun `parseCamelot rejects out of range number`() {
        assertNull(CamelotWheel.parseCamelot("0B"))
        assertNull(CamelotWheel.parseCamelot("13A"))
    }

    @Test
    fun `parseCamelot rejects invalid letter`() {
        assertNull(CamelotWheel.parseCamelot("8C"))
        assertNull(CamelotWheel.parseCamelot("8"))
    }

    @Test
    fun `parseCamelot parses musical key Ab`() {
        val key = CamelotWheel.parseCamelot("Ab")
        assertNotNull(key)
        assertEquals(4, key!!.number)
        assertEquals('B', key.letter)
    }

    @Test
    fun `parseCamelot parses musical key Am`() {
        val key = CamelotWheel.parseCamelot("Am")
        assertNotNull(key)
        assertEquals(8, key!!.number)
        assertEquals('A', key.letter)
    }

    @Test
    fun `parseCamelot handles empty string`() {
        assertNull(CamelotWheel.parseCamelot(""))
    }

    @Test
    fun `parseCamelot handles whitespace`() {
        val key = CamelotWheel.parseCamelot("  8B  ")
        assertNotNull(key)
        assertEquals(8, key!!.number)
    }

    @Test
    fun `parseCamelot handles unknown key`() {
        assertNull(CamelotWheel.parseCamelot("X#"))
    }

    // ── Kompatybilność ──────────────────────────────────────────────────

    @Test
    fun `same key has score 1_0`() {
        val key = CamelotWheel.CamelotKey(8, 'B')
        assertEquals(1.0f, CamelotWheel.compatibilityScore(key, key), 0.001f)
    }

    @Test
    fun `adjacent key same mode has score 0_9`() {
        val a = CamelotWheel.CamelotKey(8, 'B')
        val b = CamelotWheel.CamelotKey(9, 'B')
        assertEquals(0.9f, CamelotWheel.compatibilityScore(a, b), 0.001f)
    }

    @Test
    fun `adjacent key wraps around 12 to 1`() {
        val a = CamelotWheel.CamelotKey(12, 'B')
        val b = CamelotWheel.CamelotKey(1, 'B')
        assertEquals(0.9f, CamelotWheel.compatibilityScore(a, b), 0.001f)
    }

    @Test
    fun `relative key (A to B same number) has score 0_85`() {
        val a = CamelotWheel.CamelotKey(8, 'A')
        val b = CamelotWheel.CamelotKey(8, 'B')
        assertEquals(0.85f, CamelotWheel.compatibilityScore(a, b), 0.001f)
    }

    @Test
    fun `adjacent plus mode change has score 0_7`() {
        val a = CamelotWheel.CamelotKey(8, 'B')
        val b = CamelotWheel.CamelotKey(9, 'A')
        assertEquals(0.7f, CamelotWheel.compatibilityScore(a, b), 0.001f)
    }

    @Test
    fun `two steps same mode has score 0_5`() {
        val a = CamelotWheel.CamelotKey(8, 'B')
        val b = CamelotWheel.CamelotKey(10, 'B')
        assertEquals(0.5f, CamelotWheel.compatibilityScore(a, b), 0.001f)
    }

    @Test
    fun `distant keys have score 0_0`() {
        val a = CamelotWheel.CamelotKey(1, 'A')
        val b = CamelotWheel.CamelotKey(7, 'B')
        assertEquals(0.0f, CamelotWheel.compatibilityScore(a, b), 0.001f)
    }

    @Test
    fun `areCompatible true for adjacent`() {
        val a = CamelotWheel.CamelotKey(8, 'B')
        val b = CamelotWheel.CamelotKey(7, 'B')
        assertTrue(CamelotWheel.areCompatible(a, b))
    }

    @Test
    fun `areCompatible false for distant`() {
        val a = CamelotWheel.CamelotKey(1, 'A')
        val b = CamelotWheel.CamelotKey(6, 'B')
        assertFalse(CamelotWheel.areCompatible(a, b))
    }

    // ── Compatible keys ─────────────────────────────────────────────────

    @Test
    fun `compatibleKeys returns 7 keys`() {
        val key = CamelotWheel.CamelotKey(8, 'B')
        val compatible = CamelotWheel.compatibleKeys(key)
        // 8B, 8A, 7B, 9B, 7A, 9A = 6 unikalne
        assertEquals(6, compatible.size)
        assertTrue(key in compatible)
    }

    @Test
    fun `compatibleKeys wraps around correctly`() {
        val key = CamelotWheel.CamelotKey(1, 'A')
        val compatible = CamelotWheel.compatibleKeys(key)
        assertTrue(CamelotWheel.CamelotKey(12, 'A') in compatible)
        assertTrue(CamelotWheel.CamelotKey(2, 'A') in compatible)
    }

    // ── Symetria ────────────────────────────────────────────────────────

    @Test
    fun `compatibility is symmetric`() {
        val keys = (1..12).flatMap { n -> listOf('A', 'B').map { CamelotWheel.CamelotKey(n, it) } }
        for (a in keys) {
            for (b in keys) {
                assertEquals(
                    "$a vs $b not symmetric",
                    CamelotWheel.compatibilityScore(a, b),
                    CamelotWheel.compatibilityScore(b, a),
                    0.001f
                )
            }
        }
    }
}
