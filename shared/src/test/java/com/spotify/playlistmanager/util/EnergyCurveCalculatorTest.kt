package com.spotify.playlistmanager.util

import com.spotify.playlistmanager.data.model.EnergyCurve
import com.spotify.playlistmanager.data.model.Track
import org.junit.Assert.*
import org.junit.Test

/**
 * Testy jednostkowe EnergyCurveCalculator.
 * Czyste Kotlin – brak Androida, brak emulatora.
 */
class EnergyCurveCalculatorTest {

    private fun makeTrack(id: String, energy: Float?) = Track(
        id          = id,
        title       = "Track $id",
        artist      = "Artysta",
        album       = "Album",
        albumArtUrl = null,
        durationMs  = 200_000,
        popularity  = 50,
        uri         = "spotify:track:$id",
        energy      = energy
    )

    private val sampleTracks = listOf(
        makeTrack("t1", 0.1f),
        makeTrack("t2", 0.9f),
        makeTrack("t3", 0.4f),
        makeTrack("t4", 0.6f),
        makeTrack("t5", 0.3f),
        makeTrack("t6", 0.8f),
    )

    // ── applyEnergyCurve – właściwości ogólne ────────────────────────────────

    @Test
    fun `NONE nie zmienia kolejności`() {
        val result = EnergyCurveCalculator.applyEnergyCurve(sampleTracks, EnergyCurve.NONE)
        assertEquals(sampleTracks.map { it.id }, result.map { it.id })
    }

    @Test
    fun `CONSTANT nie zmienia kolejności`() {
        val result = EnergyCurveCalculator.applyEnergyCurve(sampleTracks, EnergyCurve.CONSTANT)
        assertEquals(sampleTracks.map { it.id }, result.map { it.id })
    }

    @Test
    fun `pusta lista zwraca pustą listę`() {
        val result = EnergyCurveCalculator.applyEnergyCurve(emptyList(), EnergyCurve.RISING)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `jeden utwór wraca bez zmian dla RISING`() {
        val single = listOf(makeTrack("solo", 0.5f))
        val result = EnergyCurveCalculator.applyEnergyCurve(single, EnergyCurve.RISING)
        assertEquals(1, result.size)
        assertEquals("solo", result[0].id)
    }

    @Test
    fun `żadna krzywa nie gubi utworów`() {
        EnergyCurve.entries.forEach { curve ->
            val result = EnergyCurveCalculator.applyEnergyCurve(sampleTracks, curve)
            assertEquals(
                "Krzywa $curve zgubiła utwory",
                sampleTracks.size,
                result.size
            )
        }
    }

    @Test
    fun `żadna krzywa nie duplikuje utworów`() {
        EnergyCurve.entries.forEach { curve ->
            val result = EnergyCurveCalculator.applyEnergyCurve(sampleTracks, curve)
            assertEquals(
                "Krzywa $curve zduplikowała utwory",
                result.map { it.id }.toSet().size,
                result.size
            )
        }
    }

    // ── RISING – energia powinna rosnąć ──────────────────────────────────────

    @Test
    fun `RISING – pierwszy utwór ma najniższą energię`() {
        val result = EnergyCurveCalculator.applyEnergyCurve(sampleTracks, EnergyCurve.RISING)
        val energies = result.mapNotNull { it.energy }
        assertEquals(energies.min(), energies.first(), 0.001f)
    }

    @Test
    fun `RISING – ostatni utwór ma najwyższą energię`() {
        val result = EnergyCurveCalculator.applyEnergyCurve(sampleTracks, EnergyCurve.RISING)
        val energies = result.mapNotNull { it.energy }
        assertEquals(energies.max(), energies.last(), 0.001f)
    }

    // ── FALLING – energia powinna maleć ─────────────────────────────────────

    @Test
    fun `FALLING – pierwszy utwór ma najwyższą energię`() {
        val result = EnergyCurveCalculator.applyEnergyCurve(sampleTracks, EnergyCurve.FALLING)
        val energies = result.mapNotNull { it.energy }
        assertEquals(energies.max(), energies.first(), 0.001f)
    }

    @Test
    fun `FALLING – ostatni utwór ma najniższą energię`() {
        val result = EnergyCurveCalculator.applyEnergyCurve(sampleTracks, EnergyCurve.FALLING)
        val energies = result.mapNotNull { it.energy }
        assertEquals(energies.min(), energies.last(), 0.001f)
    }

    // ── curvePoints ─────────────────────────────────────────────────────────

    @Test
    fun `curvePoints zwraca żądaną liczbę punktów`() {
        val points = EnergyCurveCalculator.curvePoints(EnergyCurve.WAVE, steps = 50)
        assertEquals(50, points.size)
    }

    @Test
    fun `curvePoints – wszystkie wartości energy w zakresie 0-1`() {
        EnergyCurve.entries.forEach { curve ->
            val points = EnergyCurveCalculator.curvePoints(curve, steps = 100)
            points.forEach { (_, energy) ->
                assertTrue(
                    "Krzywa $curve: energia $energy poza zakresem [0,1]",
                    energy in 0f..1f
                )
            }
        }
    }

    @Test
    fun `curvePoints RISING – wartości rosną monotonicznie`() {
        val points = EnergyCurveCalculator.curvePoints(EnergyCurve.RISING, steps = 10)
        val energies = points.map { it.second }
        for (i in 1 until energies.size) {
            assertTrue(
                "RISING: pozycja $i (${energies[i]}) < pozycja ${i-1} (${energies[i-1]})",
                energies[i] >= energies[i - 1]
            )
        }
    }

    @Test
    fun `curvePoints FALLING – wartości maleją monotonicznie`() {
        val points = EnergyCurveCalculator.curvePoints(EnergyCurve.FALLING, steps = 10)
        val energies = points.map { it.second }
        for (i in 1 until energies.size) {
            assertTrue(
                "FALLING: pozycja $i (${energies[i]}) > pozycja ${i-1} (${energies[i-1]})",
                energies[i] <= energies[i - 1]
            )
        }
    }

    // ── targetEnergyAt – brzegi zakresu ─────────────────────────────────────

    @Test
    fun `RISING przy position=0 zwraca 0`() {
        val v = EnergyCurveCalculator.targetEnergyAt(0f, EnergyCurve.RISING)
        assertEquals(0f, v, 0.001f)
    }

    @Test
    fun `RISING przy position=1 zwraca 1`() {
        val v = EnergyCurveCalculator.targetEnergyAt(1f, EnergyCurve.RISING)
        assertEquals(1f, v, 0.001f)
    }

    @Test
    fun `FALLING przy position=0 zwraca 1`() {
        val v = EnergyCurveCalculator.targetEnergyAt(0f, EnergyCurve.FALLING)
        assertEquals(1f, v, 0.001f)
    }

    @Test
    fun `FALLING przy position=1 zwraca 0`() {
        val v = EnergyCurveCalculator.targetEnergyAt(1f, EnergyCurve.FALLING)
        assertEquals(0f, v, 0.001f)
    }

    @Test
    fun `NONE zwraca 0_5 dla dowolnej pozycji`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { pos ->
            val v = EnergyCurveCalculator.targetEnergyAt(pos, EnergyCurve.NONE)
            assertEquals("NONE pos=$pos", 0.5f, v, 0.001f)
        }
    }

    @Test
    fun `CONSTANT zwraca 0_5 dla dowolnej pozycji`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { pos ->
            val v = EnergyCurveCalculator.targetEnergyAt(pos, EnergyCurve.CONSTANT)
            assertEquals("CONSTANT pos=$pos", 0.5f, v, 0.001f)
        }
    }
}
