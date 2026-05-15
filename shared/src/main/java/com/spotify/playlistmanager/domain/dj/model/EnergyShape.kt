package com.spotify.playlistmanager.domain.dj.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kształt przebiegu energii w bloku — spec sekcja 7.
 *
 * Każdy kształt definiuje `target(p)` ∈ [0, 1], gdzie `p = i/(N-1)` to
 * pozycja slotu w bloku. `BlockGenerator` wybiera dla każdego slotu utwór,
 * którego `EnergyScore` jest najbliżej tego targetu (z karami za inne człony).
 *
 * Kotwiczenie startu ([target] z `anchor != null`) realizuje spec sekcja 7.2 —
 * pierwsza ~1/3 bloku płynnie wychodzi z energy ostatniego zagranego utworu
 * w profil bazowy. Bez tego pierwszy utwór nowego bloku tworzy słyszalny szew.
 */
@Serializable
sealed class EnergyShape {

    /** Wyświetlana nazwa kształtu. */
    abstract val displayName: String

    /**
     * Profil bazowy R(p) zgodnie z spec sekcja 7.1 — bez kotwiczenia.
     */
    protected abstract fun baseTarget(p: Float): Float

    /**
     * Docelowa wartość energii dla pozycji `p` ∈ [0, 1].
     *
     * Gdy `anchor == null` (tryb Planning) — czysty profil R(p).
     * Gdy `anchor != null` (tryb Live) — pierwsza ~1/3 bloku
     * interpoluje liniowo od kotwicy do profilu.
     */
    fun target(p: Float, anchor: Float?): Float {
        val base = baseTarget(p.coerceIn(0f, 1f))
        if (anchor == null) return base
        val blend = (p / 0.34f).coerceIn(0f, 1f)
        return anchor + (base - anchor) * blend
    }

    @Serializable
    @SerialName("slow")
    data object Slow : EnergyShape() {
        override val displayName = "Wolno"
        override fun baseTarget(p: Float): Float = 0.30f + 0.10f * p
    }

    @Serializable
    @SerialName("fast")
    data object Fast : EnergyShape() {
        override val displayName = "Szybko"
        override fun baseTarget(p: Float): Float = 0.80f + 0.10f * p
    }

    @Serializable
    @SerialName("rising")
    data object Rising : EnergyShape() {
        override val displayName = "Narastająco"
        override fun baseTarget(p: Float): Float = 0.35f + 0.60f * p
    }

    @Serializable
    @SerialName("falling")
    data object Falling : EnergyShape() {
        override val displayName = "Opadająco"
        override fun baseTarget(p: Float): Float = 0.90f - 0.55f * p
    }

    /**
     * `wave` — sinusoida wokół środka 0.58, amplituda 0.32.
     * Liczba cykli rośnie z długością bloku (N ≤ 5 → 1 cykl; więcej → 2).
     */
    @Serializable
    @SerialName("wave")
    data class Wave(val cycles: Int = 1) : EnergyShape() {
        override val displayName: String = "Fala"

        override fun baseTarget(p: Float): Float {
            val phase = 2.0 * Math.PI * cycles * p - Math.PI / 2.0
            return (0.58f + 0.32f * kotlin.math.sin(phase).toFloat()).coerceIn(0f, 1f)
        }

        companion object {
            /** Liczba cykli zgodnie z spec sekcja 7.1: N≤5 → 1, więcej → 2. */
            fun forBlockSize(n: Int): Wave = Wave(cycles = if (n <= 5) 1 else 2)
        }
    }

    companion object {
        /** Wszystkie predefiniowane kształty — dla UI pickera. */
        val presets: List<EnergyShape> get() = listOf(
            Slow,
            Rising,
            Wave(),
            Falling,
            Fast
        )
    }
}
