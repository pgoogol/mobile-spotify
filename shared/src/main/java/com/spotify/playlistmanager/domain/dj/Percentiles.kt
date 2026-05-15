package com.spotify.playlistmanager.domain.dj

/**
 * Pure helpers do percentyli i normalizacji percentylowej.
 *
 * Spec sekcja 6.1: `normalize(x, [lo, hi]) = clamp((x-lo)/(hi-lo), 0, 1)`
 * z `[lo, hi] = [p5, p95]` puli stylu. Outliery (>p95) → 1.0; (<p5) → 0.0.
 */
object Percentiles {

    /**
     * Percentyl z posortowanej listy (interpolacja liniowa między sąsiadami).
     * @param sorted lista posortowana rosnąco
     * @param q kwantyl w [0..1]
     */
    fun percentile(sorted: List<Float>, q: Float): Float {
        if (sorted.isEmpty()) return 0f
        if (sorted.size == 1) return sorted[0]
        val idx = q.coerceIn(0f, 1f) * (sorted.size - 1)
        val lo = idx.toInt()
        val hi = (lo + 1).coerceAtMost(sorted.lastIndex)
        val frac = idx - lo
        return sorted[lo] + (sorted[hi] - sorted[lo]) * frac
    }

    /**
     * Normalizacja percentylowa pojedynczej wartości do [0, 1].
     * Gdy `lo == hi` (puste/zdegenerowane okno) zwraca 0.5.
     */
    fun normalize(value: Float, lo: Float, hi: Float): Float {
        val range = hi - lo
        if (range <= 0f) return 0.5f
        return ((value - lo) / range).coerceIn(0f, 1f)
    }
}
