package com.spotify.playlistmanager.domain.model

import org.junit.Assert.*
import org.junit.Test

class EnergyCurveTest {

    // ══════════════════════════════════════════════════════════
    //  None
    // ══════════════════════════════════════════════════════════

    @Test
    fun `None returns empty targets`() {
        assertEquals(emptyList<Float>(), EnergyCurve.None.generateTargets(10))
    }

    @Test
    fun `None returns empty targets for zero count`() {
        assertEquals(emptyList<Float>(), EnergyCurve.None.generateTargets(0))
    }

    @Test
    fun `None uses DANCE axis`() {
        assertEquals(ScoreAxis.DANCE, EnergyCurve.None.scoreAxis)
    }

    // ══════════════════════════════════════════════════════════
    //  Rising ↗
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Rising starts at 0 and ends at 1 for N=10`() {
        val targets = EnergyCurve.Rising.generateTargets(10)
        assertEquals(10, targets.size)
        assertEquals(0f, targets.first(), 0.001f)
        assertEquals(1f, targets.last(), 0.001f)
    }

    @Test
    fun `Rising is monotonically increasing`() {
        val targets = EnergyCurve.Rising.generateTargets(20)
        for (i in 1 until targets.size) {
            assertTrue("Not rising at $i", targets[i] >= targets[i - 1])
        }
    }

    @Test
    fun `Rising for N=2 gives 0 and 1`() {
        val targets = EnergyCurve.Rising.generateTargets(2)
        assertEquals(2, targets.size)
        assertEquals(0f, targets[0], 0.001f)
        assertEquals(1f, targets[1], 0.001f)
    }

    @Test
    fun `Rising for N=1 gives 0_5`() {
        val targets = EnergyCurve.Rising.generateTargets(1)
        assertEquals(1, targets.size)
        assertEquals(0.5f, targets[0], 0.001f)
    }

    @Test
    fun `Rising for N=3 gives 0, 0_5, 1`() {
        val targets = EnergyCurve.Rising.generateTargets(3)
        assertEquals(0f, targets[0], 0.001f)
        assertEquals(0.5f, targets[1], 0.001f)
        assertEquals(1f, targets[2], 0.001f)
    }

    @Test
    fun `Rising is linear`() {
        val targets = EnergyCurve.Rising.generateTargets(11)
        val firstDelta = targets[1] - targets[0]
        for (i in 2 until targets.size) {
            assertEquals("Delta not constant at $i", firstDelta, targets[i] - targets[i - 1], 0.001f)
        }
    }

    @Test
    fun `Rising uses DANCE axis`() {
        assertEquals(ScoreAxis.DANCE, EnergyCurve.Rising.scoreAxis)
    }

    // ══════════════════════════════════════════════════════════
    //  Falling ↘
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Falling starts at 1 and ends at 0 for N=10`() {
        val targets = EnergyCurve.Falling.generateTargets(10)
        assertEquals(10, targets.size)
        assertEquals(1f, targets.first(), 0.001f)
        assertEquals(0f, targets.last(), 0.001f)
    }

    @Test
    fun `Falling is monotonically decreasing`() {
        val targets = EnergyCurve.Falling.generateTargets(20)
        for (i in 1 until targets.size) {
            assertTrue("Not falling at $i", targets[i] <= targets[i - 1])
        }
    }

    @Test
    fun `Falling for N=2 gives 1 and 0`() {
        val targets = EnergyCurve.Falling.generateTargets(2)
        assertEquals(1f, targets[0], 0.001f)
        assertEquals(0f, targets[1], 0.001f)
    }

    @Test
    fun `Falling for N=1 gives 0_5`() {
        val targets = EnergyCurve.Falling.generateTargets(1)
        assertEquals(0.5f, targets[0], 0.001f)
    }

    @Test
    fun `Falling uses DANCE axis`() {
        assertEquals(ScoreAxis.DANCE, EnergyCurve.Falling.scoreAxis)
    }

    // ══════════════════════════════════════════════════════════
    //  Stable ━
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Stable returns all 0_5 for N=10`() {
        val targets = EnergyCurve.Stable.generateTargets(10)
        assertEquals(10, targets.size)
        targets.forEach { assertEquals(0.5f, it, 0.001f) }
    }

    @Test
    fun `Stable returns all 0_5 for N=2`() {
        val targets = EnergyCurve.Stable.generateTargets(2)
        assertEquals(2, targets.size)
        targets.forEach { assertEquals(0.5f, it, 0.001f) }
    }

    @Test
    fun `Stable returns all 0_5 for N=3`() {
        val targets = EnergyCurve.Stable.generateTargets(3)
        targets.forEach { assertEquals(0.5f, it, 0.001f) }
    }

    @Test
    fun `Stable uses DANCE axis`() {
        assertEquals(ScoreAxis.DANCE, EnergyCurve.Stable.scoreAxis)
    }

    // ══════════════════════════════════════════════════════════
    //  Arc 🎢
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Arc has minTrackCount of 3`() {
        assertEquals(3, EnergyCurve.Arc.minTrackCount)
    }

    @Test
    fun `Arc degrades to Rising for N=2`() {
        val degraded = EnergyCurve.Arc.degradeFor(2)
        assertTrue("Expected Rising, got $degraded", degraded is EnergyCurve.Rising)
    }

    @Test
    fun `Arc does not degrade for N=3`() {
        val degraded = EnergyCurve.Arc.degradeFor(3)
        assertTrue(degraded is EnergyCurve.Arc)
    }

    @Test
    fun `Arc for N=3 rises then falls`() {
        val targets = EnergyCurve.Arc.generateTargets(3)
        assertEquals(3, targets.size)
        assertTrue("Should rise to peak", targets[1] > targets[0])
        assertTrue("Should fall from peak", targets[2] < targets[1])
    }

    @Test
    fun `Arc for N=10 has maximum not at first or last`() {
        val targets = EnergyCurve.Arc.generateTargets(10)
        val maxIdx = targets.indexOf(targets.max())
        assertTrue("Peak should not be at start", maxIdx > 0)
        assertTrue("Peak should not be at end", maxIdx < targets.lastIndex)
    }

    @Test
    fun `Arc starts near 0_25`() {
        val targets = EnergyCurve.Arc.generateTargets(10)
        assertEquals(0.25f, targets.first(), 0.001f)
    }

    @Test
    fun `Arc peak is 1_0`() {
        val targets = EnergyCurve.Arc.generateTargets(10)
        assertEquals(1f, targets.max(), 0.001f)
    }

    @Test
    fun `Arc all values in 0_1 range`() {
        EnergyCurve.Arc.generateTargets(20).forEach {
            assertTrue("Value $it out of range", it in 0f..1f)
        }
    }

    @Test
    fun `Arc uses DANCE axis`() {
        assertEquals(ScoreAxis.DANCE, EnergyCurve.Arc.scoreAxis)
    }

    // ══════════════════════════════════════════════════════════
    //  Valley 🌀
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Valley has minTrackCount of 3`() {
        assertEquals(3, EnergyCurve.Valley.minTrackCount)
    }

    @Test
    fun `Valley degrades to Falling for N=2`() {
        val degraded = EnergyCurve.Valley.degradeFor(2)
        assertTrue("Expected Falling, got $degraded", degraded is EnergyCurve.Falling)
    }

    @Test
    fun `Valley does not degrade for N=3`() {
        val degraded = EnergyCurve.Valley.degradeFor(3)
        assertTrue(degraded is EnergyCurve.Valley)
    }

    @Test
    fun `Valley for N=3 falls then rises`() {
        val targets = EnergyCurve.Valley.generateTargets(3)
        assertEquals(3, targets.size)
        assertTrue("Should fall to bottom", targets[1] < targets[0])
        assertTrue("Should rise from bottom", targets[2] > targets[1])
    }

    @Test
    fun `Valley for N=10 has minimum not at first or last`() {
        val targets = EnergyCurve.Valley.generateTargets(10)
        val minIdx = targets.indexOf(targets.min())
        assertTrue("Bottom should not be at start", minIdx > 0)
        assertTrue("Bottom should not be at end", minIdx < targets.lastIndex)
    }

    @Test
    fun `Valley starts near 0_9`() {
        val targets = EnergyCurve.Valley.generateTargets(10)
        assertEquals(0.9f, targets.first(), 0.001f)
    }

    @Test
    fun `Valley bottom is near 0_3`() {
        val targets = EnergyCurve.Valley.generateTargets(10)
        assertEquals(0.3f, targets.min(), 0.05f)
    }

    @Test
    fun `Valley all values in 0_1 range`() {
        EnergyCurve.Valley.generateTargets(20).forEach {
            assertTrue("Value $it out of range", it in 0f..1f)
        }
    }

    @Test
    fun `Valley uses DANCE axis`() {
        assertEquals(ScoreAxis.DANCE, EnergyCurve.Valley.scoreAxis)
    }

    // ══════════════════════════════════════════════════════════
    //  Wave ∿
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Wave RISING has first target near center`() {
        val wave = EnergyCurve.Wave(WaveDirection.RISING, tracksPerHalfWave = 4)
        val targets = wave.generateTargets(16)
        assertEquals(0.5f, targets[0], 0.01f)
    }

    @Test
    fun `Wave RISING quarter wave is above center`() {
        val wave = EnergyCurve.Wave(WaveDirection.RISING, tracksPerHalfWave = 4)
        val targets = wave.generateTargets(16)
        assertTrue(targets[wave.tracksPerHalfWave] > 0.5f)
    }

    @Test
    fun `Wave FALLING has first target near center`() {
        val wave = EnergyCurve.Wave(WaveDirection.FALLING, tracksPerHalfWave = 4)
        val targets = wave.generateTargets(16)
        assertEquals(0.5f, targets[0], 0.01f)
    }

    @Test
    fun `Wave FALLING quarter wave is below center`() {
        val wave = EnergyCurve.Wave(WaveDirection.FALLING, tracksPerHalfWave = 4)
        val targets = wave.generateTargets(16)
        assertTrue(targets[wave.tracksPerHalfWave] < 0.5f)
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
    fun `Wave never produces values outside 0_1 range`() {
        listOf(WaveDirection.RISING, WaveDirection.FALLING).forEach { dir ->
            listOf(2, 3, 4, 6).forEach { tphw ->
                val targets = EnergyCurve.Wave(dir, tphw).generateTargets(50)
                targets.forEach { assertTrue("dir=$dir tphw=$tphw: $it out of range", it in 0f..1f) }
            }
        }
    }

    @Test
    fun `Wave uses DANCE axis`() {
        assertEquals(ScoreAxis.DANCE, EnergyCurve.Wave().scoreAxis)
    }

    // ══════════════════════════════════════════════════════════
    //  Romantic 🌹
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Romantic uses MOOD axis`() {
        assertEquals(ScoreAxis.MOOD, EnergyCurve.Romantic.scoreAxis)
    }

    @Test
    fun `Romantic all targets are 1_0 for N=10`() {
        val targets = EnergyCurve.Romantic.generateTargets(10)
        assertEquals(10, targets.size)
        targets.forEach { assertEquals(1f, it, 0.001f) }
    }

    @Test
    fun `Romantic all targets are 1_0 for N=2`() {
        val targets = EnergyCurve.Romantic.generateTargets(2)
        assertEquals(2, targets.size)
        targets.forEach { assertEquals(1f, it, 0.001f) }
    }

    @Test
    fun `Romantic all targets are 1_0 for N=3`() {
        val targets = EnergyCurve.Romantic.generateTargets(3)
        targets.forEach { assertEquals(1f, it, 0.001f) }
    }

    // ══════════════════════════════════════════════════════════
    //  Calm 🌙
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Calm uses MOOD axis`() {
        assertEquals(ScoreAxis.MOOD, EnergyCurve.Calm.scoreAxis)
    }

    @Test
    fun `Calm starts at 1_0 and ends near 0_3`() {
        val targets = EnergyCurve.Calm.generateTargets(10)
        assertEquals(10, targets.size)
        assertEquals(1f, targets.first(), 0.001f)
        assertEquals(0.3f, targets.last(), 0.001f)
    }

    @Test
    fun `Calm is monotonically decreasing`() {
        val targets = EnergyCurve.Calm.generateTargets(20)
        for (i in 1 until targets.size) {
            assertTrue("Not decreasing at $i", targets[i] <= targets[i - 1])
        }
    }

    @Test
    fun `Calm for N=2 gives 1_0 and 0_3`() {
        val targets = EnergyCurve.Calm.generateTargets(2)
        assertEquals(1f, targets[0], 0.001f)
        assertEquals(0.3f, targets[1], 0.001f)
    }

    @Test
    fun `Calm for N=1 gives 0_6`() {
        val targets = EnergyCurve.Calm.generateTargets(1)
        assertEquals(0.6f, targets[0], 0.001f)
    }

    @Test
    fun `Calm all values in 0_1 range`() {
        EnergyCurve.Calm.generateTargets(20).forEach {
            assertTrue("Value $it out of range", it in 0f..1f)
        }
    }

    // ══════════════════════════════════════════════════════════
    //  Wspólne testy edge case
    // ══════════════════════════════════════════════════════════

    @Test
    fun `all curves handle zero trackCount`() {
        EnergyCurve.presets.forEach { curve ->
            assertEquals(
                "Curve ${curve.displayName} returned non-empty for N=0",
                emptyList<Float>(),
                curve.generateTargets(0)
            )
        }
    }

    @Test
    fun `all non-None curves handle single track`() {
        EnergyCurve.presets.filter { it !is EnergyCurve.None }.forEach { curve ->
            val targets = curve.generateTargets(1)
            assertEquals("Curve ${curve.displayName}: wrong size", 1, targets.size)
            assertTrue(
                "Curve ${curve.displayName} out of range: ${targets[0]}",
                targets[0] in 0f..1f
            )
        }
    }

    @Test
    fun `all non-Wave non-None curves produce values in 0_1 range for various counts`() {
        val counts = listOf(2, 3, 5, 10, 20, 50)
        EnergyCurve.presets
            .filter { it !is EnergyCurve.None && it !is EnergyCurve.Wave }
            .forEach { curve ->
                counts.forEach { n ->
                    curve.generateTargets(n).forEach {
                        assertTrue("${curve.displayName} n=$n value $it out of range", it in 0f..1f)
                    }
                }
            }
    }

    @Test
    fun `degradeFor returns self for N above minTrackCount`() {
        EnergyCurve.presets.forEach { curve ->
            val bigN = curve.minTrackCount + 10
            val degraded = curve.degradeFor(bigN)
            assertTrue(
                "Expected same strategy for ${curve.displayName} with N=$bigN, got $degraded",
                degraded::class == curve::class
            )
        }
    }

    // ══════════════════════════════════════════════════════════
    //  presets
    // ══════════════════════════════════════════════════════════

    @Test
    fun `presets contains all new strategy types`() {
        val presets = EnergyCurve.presets
        assertTrue(presets.any { it is EnergyCurve.None })
        assertTrue(presets.any { it is EnergyCurve.Rising })
        assertTrue(presets.any { it is EnergyCurve.Falling })
        assertTrue(presets.any { it is EnergyCurve.Stable })
        assertTrue(presets.any { it is EnergyCurve.Arc })
        assertTrue(presets.any { it is EnergyCurve.Valley })
        assertTrue(presets.any { it is EnergyCurve.Wave && it.direction == WaveDirection.RISING })
        assertTrue(presets.any { it is EnergyCurve.Wave && it.direction == WaveDirection.FALLING })
        assertTrue(presets.any { it is EnergyCurve.Romantic })
        assertTrue(presets.any { it is EnergyCurve.Calm })
    }

    @Test
    fun `presets starts with None`() {
        assertTrue(EnergyCurve.presets.first() is EnergyCurve.None)
    }

    @Test
    fun `presets MOOD-axis strategies are Romantic and Calm`() {
        val moodStrategies = EnergyCurve.presets.filter { it.scoreAxis == ScoreAxis.MOOD }
        assertEquals(2, moodStrategies.size)
        assertTrue(moodStrategies.any { it is EnergyCurve.Romantic })
        assertTrue(moodStrategies.any { it is EnergyCurve.Calm })
    }
}
