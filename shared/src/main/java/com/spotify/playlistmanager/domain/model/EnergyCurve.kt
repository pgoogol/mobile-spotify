package com.spotify.playlistmanager.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Krzywe energii do generowania playlist.
 *
 * Każda krzywa definiuje kształt docelowych composite score'ów
 * dla kolejnych pozycji w segmencie playlisty.
 *
 * Composite score = 0.45 × bpmNorm + 0.35 × energyNorm + 0.20 × danceNorm
 */
@Serializable
sealed class EnergyCurve {

    /** Wyświetlana nazwa krzywej z emoji. */
    abstract val displayName: String

    /** Krótki opis kształtu dla UI. */
    abstract val description: String

    /**
     * Generuje listę docelowych score'ów [0..1] dla podanej liczby pozycji.
     * @param trackCount liczba pozycji w segmencie
     * @return lista target score'ów o rozmiarze [trackCount]
     */
    abstract fun generateTargets(trackCount: Int): List<Float>

    // ════════════════════════════════════════════════════════
    //  Brak krzywej — standardowe sortowanie
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("none")
    data object None : EnergyCurve() {
        override val displayName = "Brak"
        override val description = "Standardowe sortowanie bez krzywej energii"
        override fun generateTargets(trackCount: Int): List<Float> = emptyList()
    }

    // ════════════════════════════════════════════════════════
    //  SalsaRomantica 🌹 — liniowe narastanie 0.25 → 0.55
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("salsa_romantica")
    data object SalsaRomantica : EnergyCurve() {
        override val displayName = "🌹 Salsa Romántica"
        override val description = "Liniowe narastanie 0.25 → 0.55"

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.40f) // środek zakresu
            return List(trackCount) { i ->
                val t = i.toFloat() / (trackCount - 1)
                0.25f + t * 0.30f // 0.25 → 0.55
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  SalsaClasica 🎺 — trapez: rozgrzewka → plateau → zejście
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("salsa_clasica")
    data object SalsaClasica : EnergyCurve() {
        override val displayName = "🎺 Salsa Clásica"
        override val description = "Trapez: rozgrzewka → plateau → zejście"

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.65f)

            // Podział: 25% rozgrzewka, 50% plateau, 25% zejście
            val rampUp   = (trackCount * 0.25f).toInt().coerceAtLeast(1)
            val rampDown = (trackCount * 0.25f).toInt().coerceAtLeast(1)
            val plateau  = trackCount - rampUp - rampDown

            return List(trackCount) { i ->
                when {
                    i < rampUp -> {
                        // Rozgrzewka: 0.45 → 0.70
                        val t = i.toFloat() / rampUp
                        0.45f + t * 0.25f
                    }
                    i < rampUp + plateau -> {
                        // Plateau: stałe 0.70
                        0.70f
                    }
                    else -> {
                        // Zejście: 0.70 → 0.55
                        val t = (i - rampUp - plateau).toFloat() / rampDown.coerceAtLeast(1)
                        0.70f - t * 0.15f
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  SalsaRapida ⚡ — crescendo kwadratowe 0.50 → 0.95
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("salsa_rapida")
    data object SalsaRapida : EnergyCurve() {
        override val displayName = "⚡ Salsa Rápida"
        override val description = "Crescendo kwadratowe 0.50 → 0.95"

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.725f)
            return List(trackCount) { i ->
                val t = i.toFloat() / (trackCount - 1)
                // Kwadratowe: score = 0.50 + 0.45 × t²
                0.50f + 0.45f * t * t
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Timba 🔥 — asymetryczna fala clave 3-2
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("timba")
    data object Timba : EnergyCurve() {
        override val displayName = "🔥 Timba"
        override val description = "Fala clave 3-2: dwa asymetryczne piki"

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.85f)

            // Clave 3-2: podziel na fazę "3" (60% tracków) i fazę "2" (40%)
            // Faza 3: dwa szybkie piki sinusoidalne
            // Faza 2: jeden szeroki pik sinusoidalny
            // Base = 0.75, amplituda = 0.20
            val phase3Count = (trackCount * 0.6f).toInt().coerceAtLeast(1)

            return List(trackCount) { i ->
                val base = 0.75f
                val amplitude = 0.20f

                if (i < phase3Count) {
                    // Faza "3": dwa piki na pozycjach 1/3 i 2/3
                    val t = i.toFloat() / phase3Count
                    base + amplitude * kotlin.math.sin(2.0 * Math.PI * 2.0 * t).toFloat().coerceIn(-1f, 1f)
                } else {
                    // Faza "2": jeden pik sinusoidalny
                    val t = (i - phase3Count).toFloat() / (trackCount - phase3Count).coerceAtLeast(1)
                    base + amplitude * kotlin.math.sin(Math.PI * t).toFloat()
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Wave 〜 — konfigurowalna fala sinusoidalna
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("wave")
    data class Wave(
        val direction: WaveDirection = WaveDirection.RISING,
        val tracksPerHalfWave: Int = 3
    ) : EnergyCurve() {
        override val displayName: String
            get() = when (direction) {
                WaveDirection.RISING  -> "〜 Fala ↗"
                WaveDirection.FALLING -> "〜 Fala ↘"
            }

        override val description: String
            get() = "Sinusoida ${direction.label}, ${tracksPerHalfWave} utw./półfalę"

        val fullWaveSize: Int get() = tracksPerHalfWave * 4

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()

            val fws = fullWaveSize
            return List(trackCount) { i ->
                val angle = 2.0 * Math.PI * i.toDouble() / fws
                val sin = kotlin.math.sin(angle).toFloat()
                when (direction) {
                    WaveDirection.RISING  -> 0.50f + 0.30f * sin
                    WaveDirection.FALLING -> 0.50f - 0.30f * sin
                }
            }
        }
    }

    companion object {
        /** Wszystkie predefiniowane krzywe do wyświetlenia w dropdown. */
        val presets: List<EnergyCurve> get() = listOf(
            None,
            SalsaRomantica,
            SalsaClasica,
            SalsaRapida,
            Timba,
            Wave(WaveDirection.RISING),
            Wave(WaveDirection.FALLING)
        )
    }
}

@Serializable
enum class WaveDirection(val label: String) {
    @SerialName("rising")
    RISING("rosnąca ↗"),
    @SerialName("falling")
    FALLING("opadająca ↘")
}
