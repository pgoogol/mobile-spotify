package com.spotify.playlistmanager.util

import com.spotify.playlistmanager.data.model.EnergyCurve
import com.spotify.playlistmanager.data.model.Track
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Odpowiednik energy_visualization.py – oblicza wartość krzywej energii
 * dla każdego utworu w zależności od jego pozycji w playliście.
 */
object EnergyCurveCalculator {

    /**
     * Sortuje/porządkuje listę utworów zgodnie z wybraną krzywą energii.
     * Zwraca nową listę w odpowiedniej kolejności.
     */
    fun applyEnergyCurve(tracks: List<Track>, curve: EnergyCurve): List<Track> {
        if (tracks.isEmpty() || curve == EnergyCurve.NONE || curve == EnergyCurve.CONSTANT) {
            return tracks
        }

        // Oblicz docelową wartość energii dla każdej pozycji
        val targetValues = tracks.indices.map { idx ->
            val position = if (tracks.size > 1) idx.toFloat() / (tracks.size - 1) else 0f
            targetEnergyAt(position, curve)
        }.sorted().let { sorted ->
            if (curve == EnergyCurve.FALLING) sorted.reversed() else sorted
        }

        // Posortuj utwory wg ich własnej energii
        val sorted = tracks.sortedBy { it.energy ?: 0f }

        // Przypisz utwór do pozycji z najbliższą docelową energią (greedy matching)
        val result = MutableList<Track?>(tracks.size) { null }
        val used = BooleanArray(sorted.size)

        targetValues.forEachIndexed { pos, target ->
            var bestIdx = -1
            var bestDiff = Float.MAX_VALUE
            sorted.forEachIndexed { i, track ->
                if (!used[i]) {
                    val diff = abs((track.energy ?: 0f) - target)
                    if (diff < bestDiff) {
                        bestDiff = diff
                        bestIdx = i
                    }
                }
            }
            if (bestIdx >= 0) {
                result[pos] = sorted[bestIdx]
                used[bestIdx] = true
            }
        }
        return result.filterNotNull()
    }

    /**
     * Oblicza docelową wartość energii (0..1) na danej pozycji (0..1)
     * dla konkretnego typu krzywej.
     */
    fun targetEnergyAt(position: Float, curve: EnergyCurve): Float = when (curve) {
        EnergyCurve.RISING    -> position
        EnergyCurve.FALLING   -> 1f - position
        EnergyCurve.WAVE      -> (0.5f + 0.5f * sin(position * PI.toFloat() * 2 - PI.toFloat() / 2)).coerceIn(0f, 1f)
        EnergyCurve.RANDOM    -> randomSmoothAt(position)
        EnergyCurve.SALSA     -> salsaWave(position)
        EnergyCurve.BACHATA   -> bachataWave(position)
        EnergyCurve.REGGAETON -> reggaetonWave(position)
        EnergyCurve.MERENGUE  -> merengueWave(position)
        EnergyCurve.CUMBIA    -> cumbiaWave(position)
        EnergyCurve.CONSTANT,
        EnergyCurve.NONE      -> 0.5f
    }

    // ── Krzywe taneczne ────────────────────────────────────────────────────

    /** Salsa: rytm clave 3-2, energia z wyraźnymi pulsami */
    private fun salsaWave(p: Float): Float {
        val base = 0.5f + 0.3f * sin(p * 2 * PI.toFloat())
        val clave = 0.2f * sin(p * 5 * PI.toFloat())
        return (base + clave).coerceIn(0f, 1f)
    }

    /** Bachata: powolne narastanie z chwilami relaksu, charakterystyczny rytm 4/4 */
    private fun bachataWave(p: Float): Float {
        val base = 0.45f + 0.25f * sin(p * PI.toFloat())
        val pulse = 0.15f * sin(p * 8 * PI.toFloat())
        return (base + pulse).coerceIn(0f, 1f)
    }

    /** Reggaeton: wysokie plateau z krótkim wstępem i zejściem */
    private fun reggaetonWave(p: Float): Float {
        val ramp = if (p < 0.15f) p / 0.15f else if (p > 0.85f) (1f - p) / 0.15f else 1f
        val pulse = 0.1f * sin(p * 16 * PI.toFloat())
        return (0.6f * ramp + 0.3f + pulse).coerceIn(0f, 1f)
    }

    /** Merengue: szybkie, stale wysokie tempo z minimalnymi wahaniami */
    private fun merengueWave(p: Float): Float {
        val pulse = 0.15f * sin(p * 12 * PI.toFloat())
        return (0.75f + pulse).coerceIn(0f, 1f)
    }

    /** Cumbia: budowanie przez całą imprezę, dwie fale */
    private fun cumbiaWave(p: Float): Float {
        val base = 0.3f + 0.4f * p
        val wave = 0.2f * sin(p * 4 * PI.toFloat())
        return (base + wave).coerceIn(0f, 1f)
    }

    /** Płynna losowa krzywa (pseudo-random seed od pozycji) */
    private fun randomSmoothAt(p: Float): Float {
        val s1 = sin(p * 7.3f + 1.1f)
        val s2 = sin(p * 3.7f + 2.4f)
        val s3 = sin(p * 13.1f + 0.7f)
        return (0.5f + 0.3f * s1 + 0.12f * s2 + 0.08f * s3).coerceIn(0f, 1f)
    }

    /** Oblicza punkty wykresu krzywej – używane przez EnergyChartView */
    fun curvePoints(curve: EnergyCurve, steps: Int = 100): List<Pair<Float, Float>> =
        (0 until steps).map { i ->
            val p = i.toFloat() / (steps - 1)
            p to targetEnergyAt(p, curve)
        }
}
