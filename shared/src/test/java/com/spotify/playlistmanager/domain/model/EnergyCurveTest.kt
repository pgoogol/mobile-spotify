package com.spotify.playlistmanager.domain.model

import org.junit.Assert.*
import org.junit.Test

class EnergyCurveTest {

    // ══════════════════════════════════════════════════════════
    //  Brak
    // ══════════════════════════════════════════════════════════

    @Test
    fun `None returns empty targets`() {
        assertEquals(emptyList<Float>(), EnergyCurve.None.generateTargets(10))
    }

    @Test
    fun `None is in NONE group`() {
        assertEquals(CurveGroup.NONE, EnergyCurve.None.group)
    }

    // ══════════════════════════════════════════════════════════
    //  SalsaRomantica
    // ══════════════════════════════════════════════════════════

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
    fun `SalsaRomantica is in SALSA group`() {
        assertEquals(CurveGroup.SALSA, EnergyCurve.SalsaRomantica.group)
    }

    // ══════════════════════════════════════════════════════════
    //  SalsaClasica
    // ══════════════════════════════════════════════════════════

    @Test
    fun `SalsaClasica has trapezoid shape`() {
        val targets = EnergyCurve.SalsaClasica.generateTargets(20)
        assertEquals(20, targets.size)
        assertTrue(targets.first() < targets[targets.size / 2])
        assertEquals(0.70f, targets[targets.size / 2], 0.05f)
        assertTrue(targets.last() < 0.70f)
    }

    @Test
    fun `SalsaClasica is in SALSA group`() {
        assertEquals(CurveGroup.SALSA, EnergyCurve.SalsaClasica.group)
    }

    // ══════════════════════════════════════════════════════════
    //  SalsaRapida
    // ══════════════════════════════════════════════════════════

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
    fun `SalsaRapida is in SALSA group`() {
        assertEquals(CurveGroup.SALSA, EnergyCurve.SalsaRapida.group)
    }

    // ══════════════════════════════════════════════════════════
    //  Timba
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Timba generates correct count and stays in range`() {
        val targets = EnergyCurve.Timba.generateTargets(20)
        assertEquals(20, targets.size)
        targets.forEach { assertTrue(it in 0.50f..1.00f) }
    }

    @Test
    fun `Timba is in SALSA group`() {
        assertEquals(CurveGroup.SALSA, EnergyCurve.Timba.group)
    }

    // ══════════════════════════════════════════════════════════
    //  BachataRise (nowa)
    // ══════════════════════════════════════════════════════════

    @Test
    fun `BachataRise starts at 0_20 and ends at 0_45`() {
        val targets = EnergyCurve.BachataRise.generateTargets(10)
        assertEquals(10, targets.size)
        assertEquals(0.20f, targets.first(), 0.001f)
        assertEquals(0.45f, targets.last(), 0.001f)
    }

    @Test
    fun `BachataRise is monotonically increasing`() {
        val targets = EnergyCurve.BachataRise.generateTargets(20)
        for (i in 1 until targets.size) {
            assertTrue(targets[i] >= targets[i - 1])
        }
    }

    @Test
    fun `BachataRise single track returns middle value`() {
        assertEquals(0.325f, EnergyCurve.BachataRise.generateTargets(1)[0], 0.001f)
    }

    @Test
    fun `BachataRise values stay in low range`() {
        val targets = EnergyCurve.BachataRise.generateTargets(30)
        targets.forEach { assertTrue("Value $it out of bachata range", it in 0.20f..0.45f) }
    }

    @Test
    fun `BachataRise is in BACHATA group`() {
        assertEquals(CurveGroup.BACHATA, EnergyCurve.BachataRise.group)
    }

    // ══════════════════════════════════════════════════════════
    //  BachataArc (nowa)
    // ══════════════════════════════════════════════════════════

    @Test
    fun `BachataArc has trapezoid shape`() {
        val targets = EnergyCurve.BachataArc.generateTargets(20)
        assertEquals(20, targets.size)
        assertTrue(targets.first() < targets[targets.size / 2])
        assertEquals(0.60f, targets[targets.size / 2], 0.05f)
        assertTrue(targets.last() < 0.60f)
    }

    @Test
    fun `BachataArc plateau sits at 0_60`() {
        val targets = EnergyCurve.BachataArc.generateTargets(20)
        // Plateau = 50% środkowych pozycji (indeksy 5..14 dla n=20)
        for (i in 5..14) {
            assertEquals("Plateau at index $i", 0.60f, targets[i], 0.001f)
        }
    }

    @Test
    fun `BachataArc values stay in low range`() {
        val targets = EnergyCurve.BachataArc.generateTargets(30)
        targets.forEach { assertTrue("Value $it out of bachata range", it in 0.35f..0.60f) }
    }

    @Test
    fun `BachataArc is in BACHATA group`() {
        assertEquals(CurveGroup.BACHATA, EnergyCurve.BachataArc.group)
    }

    // ══════════════════════════════════════════════════════════
    //  Crescendo (nowa, uniwersalna)
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Crescendo starts at 0_30 and ends at 0_85`() {
        val targets = EnergyCurve.Crescendo.generateTargets(10)
        assertEquals(0.30f, targets.first(), 0.001f)
        assertEquals(0.85f, targets.last(), 0.001f)
    }

    @Test
    fun `Crescendo is monotonically increasing`() {
        val targets = EnergyCurve.Crescendo.generateTargets(20)
        for (i in 1 until targets.size) {
            assertTrue(targets[i] >= targets[i - 1])
        }
    }

    @Test
    fun `Crescendo is linear`() {
        val targets = EnergyCurve.Crescendo.generateTargets(11)
        // Sprawdź że różnice są równe (liniowe)
        val firstDelta = targets[1] - targets[0]
        for (i in 2 until targets.size) {
            val delta = targets[i] - targets[i - 1]
            assertEquals("Delta at $i", firstDelta, delta, 0.001f)
        }
    }

    @Test
    fun `Crescendo is in UNIVERSAL group`() {
        assertEquals(CurveGroup.UNIVERSAL, EnergyCurve.Crescendo.group)
    }

    // ══════════════════════════════════════════════════════════
    //  Peak (nowa, uniwersalna)
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Peak has maximum in the middle`() {
        val targets = EnergyCurve.Peak.generateTargets(21)
        val peakIndex = targets.indexOf(targets.max())
        // Dla n=21 oczekujemy środka czyli indeksu 10
        assertEquals(10, peakIndex)
        assertEquals(0.80f, targets[peakIndex], 0.001f)
    }

    @Test
    fun `Peak starts at 0_35 and ends at 0_40`() {
        val targets = EnergyCurve.Peak.generateTargets(11)
        assertEquals(0.35f, targets.first(), 0.001f)
        assertEquals(0.40f, targets.last(), 0.001f)
    }

    @Test
    fun `Peak rises then falls`() {
        val targets = EnergyCurve.Peak.generateTargets(21)
        val peakIdx = targets.indexOf(targets.max())
        // Wznoszenie
        for (i in 1..peakIdx) {
            assertTrue("Should rise at $i", targets[i] >= targets[i - 1])
        }
        // Opadanie
        for (i in peakIdx + 1 until targets.size) {
            assertTrue("Should fall at $i", targets[i] <= targets[i - 1])
        }
    }

    @Test
    fun `Peak stays in valid range`() {
        val targets = EnergyCurve.Peak.generateTargets(30)
        targets.forEach { assertTrue("Value $it out of range", it in 0.35f..0.80f) }
    }

    @Test
    fun `Peak is in UNIVERSAL group`() {
        assertEquals(CurveGroup.UNIVERSAL, EnergyCurve.Peak.group)
    }

    // ══════════════════════════════════════════════════════════
    //  Wave — obecne testy (center = DEFAULT = 0.50)
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Wave default center equals 0_50`() {
        assertEquals(0.50f, EnergyCurve.Wave().center, 0.001f)
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

    // ══════════════════════════════════════════════════════════
    //  Wave — nowe testy dla parametru center
    // ══════════════════════════════════════════════════════════

    @Test
    fun `Wave with default center is in UNIVERSAL group`() {
        assertEquals(CurveGroup.UNIVERSAL, EnergyCurve.Wave().group)
    }

    @Test
    fun `Wave with bachata center is in BACHATA group`() {
        val wave = EnergyCurve.Wave(center = EnergyCurve.Wave.CENTER_BACHATA)
        assertEquals(CurveGroup.BACHATA, wave.group)
    }

    @Test
    fun `Wave with salsa center is in SALSA group`() {
        val wave = EnergyCurve.Wave(center = EnergyCurve.Wave.CENTER_SALSA)
        assertEquals(CurveGroup.SALSA, wave.group)
    }

    @Test
    fun `Wave RISING starts at its own center for bachata`() {
        val wave = EnergyCurve.Wave(
            direction = WaveDirection.RISING,
            center = EnergyCurve.Wave.CENTER_BACHATA,
            tracksPerHalfWave = 4
        )
        val targets = wave.generateTargets(16)
        assertEquals(EnergyCurve.Wave.CENTER_BACHATA, targets[0], 0.01f)
    }

    @Test
    fun `Wave bachata values stay in low band`() {
        val wave = EnergyCurve.Wave(center = EnergyCurve.Wave.CENTER_BACHATA)
        val targets = wave.generateTargets(40)
        // Bachata fala: center 0.35, amplituda ≤ 0.30 → zakres ~[0.05, 0.65]
        targets.forEach { assertTrue("Bachata wave value $it too high", it < 0.65f) }
    }

    @Test
    fun `Wave salsa values stay in high band`() {
        val wave = EnergyCurve.Wave(center = EnergyCurve.Wave.CENTER_SALSA)
        val targets = wave.generateTargets(40)
        // Salsa fala: center 0.70, amplituda ograniczona do 1-center=0.30 → zakres ~[0.445, 0.955]
        targets.forEach { assertTrue("Salsa wave value $it too low", it > 0.35f) }
    }

    @Test
    fun `Wave never produces values outside 0_1 range`() {
        // Testuj dla skrajnych wartości center
        listOf(0.10f, 0.25f, 0.35f, 0.50f, 0.70f, 0.85f, 0.95f).forEach { c ->
            val wave = EnergyCurve.Wave(center = c)
            val targets = wave.generateTargets(50)
            targets.forEach {
                assertTrue("center=$c produced out-of-range value $it", it in 0f..1f)
            }
        }
    }

    @Test
    fun `Wave displayName contains Salsa for salsa center`() {
        val wave = EnergyCurve.Wave(center = EnergyCurve.Wave.CENTER_SALSA)
        assertTrue("Got: ${wave.displayName}", wave.displayName.contains("Salsa"))
    }

    @Test
    fun `Wave displayName contains Bachata for bachata center`() {
        val wave = EnergyCurve.Wave(center = EnergyCurve.Wave.CENTER_BACHATA)
        assertTrue("Got: ${wave.displayName}", wave.displayName.contains("Bachata"))
    }

    @Test
    fun `Wave displayName contains Uniwersalne for default center`() {
        val wave = EnergyCurve.Wave()
        assertTrue("Got: ${wave.displayName}", wave.displayName.contains("Uniwersalne"))
    }

    @Test
    fun `Wave copy preserves center`() {
        val original = EnergyCurve.Wave(center = EnergyCurve.Wave.CENTER_BACHATA)
        val copied = original.copy(direction = WaveDirection.FALLING)
        assertEquals(EnergyCurve.Wave.CENTER_BACHATA, copied.center, 0.001f)
    }

    // ══════════════════════════════════════════════════════════
    //  Wspólne testy edge case
    // ══════════════════════════════════════════════════════════

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
            assertEquals("Curve ${curve.displayName} failed", 1, targets.size)
            assertTrue("Curve ${curve.displayName} out of range: ${targets[0]}", targets[0] in 0f..1f)
        }
    }

    @Test
    fun `all non-Wave curves produce values in 0_1 range for various counts`() {
        val counts = listOf(5, 10, 20, 50, 100)
        EnergyCurve.presets.filter { it !is EnergyCurve.None && it !is EnergyCurve.Wave }
            .forEach { curve ->
                counts.forEach { n ->
                    val targets = curve.generateTargets(n)
                    targets.forEach {
                        assertTrue("${curve.displayName} n=$n value $it out of range", it in 0f..1f)
                    }
                }
            }
    }

    // ══════════════════════════════════════════════════════════
    //  presets / groupedPresets
    // ══════════════════════════════════════════════════════════

    @Test
    fun `presets contains all curve types`() {
        val presets = EnergyCurve.presets
        assertTrue(presets.any { it is EnergyCurve.None })
        assertTrue(presets.any { it is EnergyCurve.SalsaRomantica })
        assertTrue(presets.any { it is EnergyCurve.SalsaClasica })
        assertTrue(presets.any { it is EnergyCurve.SalsaRapida })
        assertTrue(presets.any { it is EnergyCurve.Timba })
        assertTrue(presets.any { it is EnergyCurve.BachataRise })
        assertTrue(presets.any { it is EnergyCurve.BachataArc })
        assertTrue(presets.any { it is EnergyCurve.Crescendo })
        assertTrue(presets.any { it is EnergyCurve.Peak })
        // Wave: bachata rising/falling, universal rising/falling, salsa rising/falling
        assertTrue(presets.any {
            it is EnergyCurve.Wave && it.direction == WaveDirection.RISING &&
                    it.center == EnergyCurve.Wave.CENTER_BACHATA
        })
        assertTrue(presets.any {
            it is EnergyCurve.Wave && it.direction == WaveDirection.FALLING &&
                    it.center == EnergyCurve.Wave.CENTER_BACHATA
        })
        assertTrue(presets.any {
            it is EnergyCurve.Wave && it.direction == WaveDirection.RISING &&
                    it.center == EnergyCurve.Wave.CENTER_UNIVERSAL
        })
        assertTrue(presets.any {
            it is EnergyCurve.Wave && it.direction == WaveDirection.FALLING &&
                    it.center == EnergyCurve.Wave.CENTER_UNIVERSAL
        })
        assertTrue(presets.any {
            it is EnergyCurve.Wave && it.direction == WaveDirection.RISING &&
                    it.center == EnergyCurve.Wave.CENTER_SALSA
        })
        assertTrue(presets.any {
            it is EnergyCurve.Wave && it.direction == WaveDirection.FALLING &&
                    it.center == EnergyCurve.Wave.CENTER_SALSA
        })
    }

    @Test
    fun `groupedPresets contains all four groups`() {
        val grouped = EnergyCurve.groupedPresets
        assertTrue(grouped.containsKey(CurveGroup.NONE))
        assertTrue(grouped.containsKey(CurveGroup.SALSA))
        assertTrue(grouped.containsKey(CurveGroup.BACHATA))
        assertTrue(grouped.containsKey(CurveGroup.UNIVERSAL))
    }

    @Test
    fun `groupedPresets NONE contains only None`() {
        val none = EnergyCurve.groupedPresets[CurveGroup.NONE]!!
        assertEquals(1, none.size)
        assertTrue(none[0] is EnergyCurve.None)
    }

    @Test
    fun `groupedPresets SALSA contains 4 objects plus 2 waves`() {
        val salsa = EnergyCurve.groupedPresets[CurveGroup.SALSA]!!
        assertEquals(6, salsa.size)
        assertEquals(4, salsa.count { it !is EnergyCurve.Wave })
        assertEquals(2, salsa.count { it is EnergyCurve.Wave })
    }

    @Test
    fun `groupedPresets BACHATA contains 2 objects plus 2 waves`() {
        val bachata = EnergyCurve.groupedPresets[CurveGroup.BACHATA]!!
        assertEquals(4, bachata.size)
        assertEquals(2, bachata.count { it !is EnergyCurve.Wave })
        assertEquals(2, bachata.count { it is EnergyCurve.Wave })
    }

    @Test
    fun `groupedPresets UNIVERSAL contains 2 objects plus 2 waves`() {
        val universal = EnergyCurve.groupedPresets[CurveGroup.UNIVERSAL]!!
        assertEquals(4, universal.size)
        assertEquals(2, universal.count { it !is EnergyCurve.Wave })
        assertEquals(2, universal.count { it is EnergyCurve.Wave })
    }

    @Test
    fun `groupedPresets total size equals flat presets size`() {
        val flatSize = EnergyCurve.presets.size
        val groupedSize = EnergyCurve.groupedPresets.values.sumOf { it.size }
        assertEquals(flatSize, groupedSize)
    }

    @Test
    fun `groupedPresets ordering is NONE then SALSA then BACHATA then UNIVERSAL`() {
        val keys = EnergyCurve.groupedPresets.keys.toList()
        assertEquals(
            listOf(CurveGroup.NONE, CurveGroup.SALSA, CurveGroup.BACHATA, CurveGroup.UNIVERSAL),
            keys
        )
    }
}