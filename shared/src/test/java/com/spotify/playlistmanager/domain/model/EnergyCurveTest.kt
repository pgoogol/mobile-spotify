package com.spotify.playlistmanager.domain.model

import org.junit.Assert.*
import org.junit.Test

class EnergyCurveTest {

    @Test
    fun `None returns empty targets`() {
        assertEquals(emptyList<Float>(), EnergyCurve.None.generateTargets(10))
    }

    @Test
    fun `SalsaRomantica starts at 0_25 and ends at 0_55`() {
        val targets = EnergyCurve.SalsaRomantica.generateTargets(10)
        assertEquals(10, targets.size)
        assertEquals(0.25f, targets.first(), 0.001f)
        assertEquals(0.55f, targets.last(), 0.001f)
    }

    @Test
    fun `SalsaRomantica is monotonically increasing`() {
        val targets = EnergyCurve.SalsaRomantica.generateTargets(20)
        for (i in 1 until targets.size) {
            assertTrue(targets[i] >= targets[i - 1])
        }
    }

    @Test
    fun `SalsaRomantica single track returns middle value`() {
        assertEquals(0.40f, EnergyCurve.SalsaRomantica.generateTargets(1)[0], 0.001f)
    }

    @Test
    fun `SalsaClasica has trapezoid shape`() {
        val targets = EnergyCurve.SalsaClasica.generateTargets(20)
        assertEquals(20, targets.size)
        assertTrue(targets.first() < targets[targets.size / 2])
        assertEquals(0.70f, targets[targets.size / 2], 0.05f)
        assertTrue(targets.last() < 0.70f)
    }

    @Test
    fun `SalsaRapida starts at 0_50 and ends at 0_95`() {
        val targets = EnergyCurve.SalsaRapida.generateTargets(10)
        assertEquals(0.50f, targets.first(), 0.001f)
        assertEquals(0.95f, targets.last(), 0.001f)
    }

    @Test
    fun `SalsaRapida crescendo is convex`() {
        val targets = EnergyCurve.SalsaRapida.generateTargets(10)
        for (i in 2 until targets.size) {
            val delta1 = targets[i - 1] - targets[i - 2]
            val delta2 = targets[i] - targets[i - 1]
            assertTrue(delta2 >= delta1 - 0.001f)
        }
    }

    @Test
    fun `Timba generates correct count and stays in range`() {
        val targets = EnergyCurve.Timba.generateTargets(20)
        assertEquals(20, targets.size)
        targets.forEach { assertTrue(it in 0.50f..1.00f) }
    }

    @Test
    fun `Wave RISING starts at center`() {
        val wave = EnergyCurve.Wave(WaveDirection.RISING, tracksPerHalfWave = 4)
        val targets = wave.generateTargets(16)
        assertEquals(0.50f, targets[0], 0.01f)
        assertTrue(targets[wave.tracksPerHalfWave] > 0.50f)
    }

    @Test
    fun `Wave FALLING starts at center`() {
        val wave = EnergyCurve.Wave(WaveDirection.FALLING, tracksPerHalfWave = 4)
        val targets = wave.generateTargets(16)
        assertEquals(0.50f, targets[0], 0.01f)
        assertTrue(targets[wave.tracksPerHalfWave] < 0.50f)
    }

    @Test
    fun `Wave fullWaveSize is 4x tracksPerHalfWave`() {
        assertEquals(20, EnergyCurve.Wave(tracksPerHalfWave = 5).fullWaveSize)
    }

    @Test
    fun `Wave truncated when trackCount less than fullWaveSize`() {
        val targets = EnergyCurve.Wave(tracksPerHalfWave = 4).generateTargets(8)
        assertEquals(8, targets.size)
    }

    @Test
    fun `Wave repeated when trackCount greater than fullWaveSize`() {
        val wave = EnergyCurve.Wave(tracksPerHalfWave = 3)
        val targets = wave.generateTargets(24)
        assertEquals(24, targets.size)
        for (i in 0 until 12) assertEquals(targets[i], targets[i + 12], 0.001f)
    }

    @Test
    fun `all curves handle zero trackCount`() {
        EnergyCurve.presets.forEach { curve ->
            assertEquals(emptyList<Float>(), curve.generateTargets(0))
        }
    }

    @Test
    fun `all curves handle single track`() {
        EnergyCurve.presets.filter { it !is EnergyCurve.None }.forEach { curve ->
            val targets = curve.generateTargets(1)
            assertEquals(1, targets.size)
            assertTrue(targets[0] in 0f..1f)
        }
    }

    @Test
    fun `presets contains all curve types`() {
        val presets = EnergyCurve.presets
        assertTrue(presets.any { it is EnergyCurve.None })
        assertTrue(presets.any { it is EnergyCurve.SalsaRomantica })
        assertTrue(presets.any { it is EnergyCurve.SalsaClasica })
        assertTrue(presets.any { it is EnergyCurve.SalsaRapida })
        assertTrue(presets.any { it is EnergyCurve.Timba })
        assertTrue(presets.any { it is EnergyCurve.Wave && it.direction == WaveDirection.RISING })
        assertTrue(presets.any { it is EnergyCurve.Wave && it.direction == WaveDirection.FALLING })
    }
}
