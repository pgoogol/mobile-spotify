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
 *
 * Krzywe są pogrupowane przez [CurveGroup]:
 * - [CurveGroup.SALSA]: wyższe BPM (composite ~0.45–1.00)
 * - [CurveGroup.BACHATA]: niższe BPM (composite ~0.20–0.60)
 * - [CurveGroup.UNIVERSAL]: niezależne od stylu, szeroki zakres
 * - [CurveGroup.NONE]: brak krzywej (sortowanie)
 */
@Serializable
sealed class EnergyCurve {

    /** Wyświetlana nazwa krzywej z emoji. */
    abstract val displayName: String

    /** Krótki opis kształtu dla UI. */
    abstract val description: String

    /** Grupa tematyczna krzywej (salsa / bachata / uniwersalne). */
    abstract val group: CurveGroup

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
        override val group = CurveGroup.NONE
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
        override val group = CurveGroup.SALSA

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
        override val group = CurveGroup.SALSA

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
        override val group = CurveGroup.SALSA

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
        override val group = CurveGroup.SALSA

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
    //  BachataRise 🌴 — liniowe narastanie 0.20 → 0.45
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("bachata_rise")
    data object BachataRise : EnergyCurve() {
        override val displayName = "🌴 Bachata — Narastanie"
        override val description = "Liniowe narastanie 0.20 → 0.45"
        override val group = CurveGroup.BACHATA

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.325f) // środek zakresu
            return List(trackCount) { i ->
                val t = i.toFloat() / (trackCount - 1)
                0.20f + t * 0.25f // 0.20 → 0.45
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  BachataArc 🌴 — trapez niski 0.35 → 0.60 → 0.50
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("bachata_arc")
    data object BachataArc : EnergyCurve() {
        override val displayName = "🌴 Bachata — Łuk"
        override val description = "Trapez niski: 0.35 → 0.60 → 0.50"
        override val group = CurveGroup.BACHATA

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.55f)

            // Podział: 25% rozgrzewka, 50% plateau, 25% zejście
            val rampUp   = (trackCount * 0.25f).toInt().coerceAtLeast(1)
            val rampDown = (trackCount * 0.25f).toInt().coerceAtLeast(1)
            val plateau  = trackCount - rampUp - rampDown

            return List(trackCount) { i ->
                when {
                    i < rampUp -> {
                        // Rozgrzewka: 0.35 → 0.60
                        val t = i.toFloat() / rampUp
                        0.35f + t * 0.25f
                    }
                    i < rampUp + plateau -> {
                        // Plateau: stałe 0.60
                        0.60f
                    }
                    else -> {
                        // Zejście: 0.60 → 0.50
                        val t = (i - rampUp - plateau).toFloat() / rampDown.coerceAtLeast(1)
                        0.60f - t * 0.10f
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Crescendo 📈 — liniowe 0.30 → 0.85, uniwersalne
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("crescendo")
    data object Crescendo : EnergyCurve() {
        override val displayName = "📈 Crescendo"
        override val description = "Liniowy wzrost 0.30 → 0.85"
        override val group = CurveGroup.UNIVERSAL

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.575f) // środek zakresu
            return List(trackCount) { i ->
                val t = i.toFloat() / (trackCount - 1)
                0.30f + t * 0.55f // 0.30 → 0.85
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Peak 🎢 — trójkąt symetryczny 0.35 → 0.80 → 0.40
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("peak")
    data object Peak : EnergyCurve() {
        override val displayName = "🎢 Szczyt"
        override val description = "Trójkąt symetryczny: 0.35 → 0.80 → 0.40"
        override val group = CurveGroup.UNIVERSAL

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.80f)

            // Szczyt w środku (lub tuż za środkiem dla parzystej liczby)
            val peakIndex = trackCount / 2
            return List(trackCount) { i ->
                if (i <= peakIndex) {
                    // Wznoszenie 0.35 → 0.80
                    val t = if (peakIndex == 0) 1f else i.toFloat() / peakIndex
                    0.35f + t * 0.45f
                } else {
                    // Opadanie 0.80 → 0.40
                    val denom = (trackCount - 1 - peakIndex).coerceAtLeast(1)
                    val t = (i - peakIndex).toFloat() / denom
                    0.80f - t * 0.40f
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Wave ∿ — konfigurowalna fala sinusoidalna
    //  Parametr [center] decyduje o grupie (bachata/uniwersalne/salsa)
    //  i displayName (liczony dynamicznie).
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("wave")
    data class Wave(
        val direction: WaveDirection = WaveDirection.RISING,
        val tracksPerHalfWave: Int = 3,
        val center: Float = DEFAULT_CENTER
    ) : EnergyCurve() {

        override val displayName: String
            get() {
                val arrow = when (direction) {
                    WaveDirection.RISING  -> "↗"
                    WaveDirection.FALLING -> "↘"
                }
                return when (group) {
                    CurveGroup.BACHATA   -> "🌴 Bachata — Fala $arrow"
                    CurveGroup.SALSA     -> "🎺 Salsa — Fala $arrow"
                    CurveGroup.UNIVERSAL -> "∿ Uniwersalne — Fala $arrow"
                    CurveGroup.NONE      -> "Fala $arrow" // nieosiągalne
                }
            }

        override val description: String
            get() = "Sinusoida ${direction.label}, center=${"%.2f".format(center)}, " +
                    "${tracksPerHalfWave} utw./półfalę"

        override val group: CurveGroup
            get() = when {
                center < BACHATA_UPPER -> CurveGroup.BACHATA
                center > UNIVERSAL_UPPER -> CurveGroup.SALSA
                else -> CurveGroup.UNIVERSAL
            }

        val fullWaveSize: Int get() = tracksPerHalfWave * 4

        /**
         * Amplituda dobrana tak, aby fala nigdy nie wyszła poza [0, 1]
         * niezależnie od wartości [center]. Dla center=0.50 amplituda = 0.30
         * (zachowuje zachowanie sprzed refaktoru).
         */
        val amplitude: Float
            get() {
                val safeMargin = minOf(center, 1f - center)
                return minOf(0.30f, safeMargin * 0.85f)
            }

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()

            val fws = fullWaveSize
            val amp = amplitude
            return List(trackCount) { i ->
                val angle = 2.0 * Math.PI * i.toDouble() / fws
                val sin = kotlin.math.sin(angle).toFloat()
                when (direction) {
                    WaveDirection.RISING  -> center + amp * sin
                    WaveDirection.FALLING -> center - amp * sin
                }
            }
        }

        companion object {
            const val DEFAULT_CENTER = 0.50f
            /** Wartości <= tego progu klasyfikują falę jako BACHATA. */
            const val BACHATA_UPPER = 0.45f
            /** Wartości > tego progu klasyfikują falę jako SALSA. */
            const val UNIVERSAL_UPPER = 0.60f

            /** Preset: fala bachatowa. */
            const val CENTER_BACHATA = 0.35f
            /** Preset: fala uniwersalna (zachowuje zachowanie sprzed refaktoru). */
            const val CENTER_UNIVERSAL = 0.50f
            /** Preset: fala salsowa. */
            const val CENTER_SALSA = 0.70f
        }
    }

    companion object {
        /**
         * Wszystkie predefiniowane krzywe (płaska lista).
         * Używane przez historię rund, serializację testów i miejsca,
         * które nie potrzebują grupowania.
         *
         * Dla UI dropdownu preferuj [groupedPresets].
         */
        val presets: List<EnergyCurve> get() = listOf(
            None,
            // SALSA
            SalsaRomantica,
            SalsaClasica,
            SalsaRapida,
            Timba,
            Wave(direction = WaveDirection.RISING,  center = Wave.CENTER_SALSA),
            Wave(direction = WaveDirection.FALLING, center = Wave.CENTER_SALSA),
            // BACHATA
            BachataRise,
            BachataArc,
            Wave(direction = WaveDirection.RISING,  center = Wave.CENTER_BACHATA),
            Wave(direction = WaveDirection.FALLING, center = Wave.CENTER_BACHATA),
            // UNIVERSAL
            Crescendo,
            Peak,
            Wave(direction = WaveDirection.RISING,  center = Wave.CENTER_UNIVERSAL),
            Wave(direction = WaveDirection.FALLING, center = Wave.CENTER_UNIVERSAL),
        )

        /**
         * Presety pogrupowane wg [CurveGroup], z zachowaniem kolejności:
         * NONE → SALSA → BACHATA → UNIVERSAL.
         *
         * Używane przez UI dropdownu do renderowania sekcji.
         */
        val groupedPresets: Map<CurveGroup, List<EnergyCurve>> get() {
            val byGroup = presets.groupBy { it.group }
            // LinkedHashMap zachowuje kolejność kluczy
            val ordered = linkedMapOf<CurveGroup, List<EnergyCurve>>()
            listOf(CurveGroup.NONE, CurveGroup.SALSA, CurveGroup.BACHATA, CurveGroup.UNIVERSAL)
                .forEach { g -> byGroup[g]?.let { ordered[g] = it } }
            return ordered
        }
    }
}

/**
 * Grupa tematyczna krzywej energii. Służy do wizualnego grupowania
 * w UI (nagłówki sekcji w dropdownie) oraz do dynamicznego wyliczania
 * [EnergyCurve.Wave.displayName] na podstawie parametru `center`.
 */
@Serializable
enum class CurveGroup(val displayName: String) {
    @SerialName("none")
    NONE("Brak"),

    @SerialName("salsa")
    SALSA("🎺 Salsa"),

    @SerialName("bachata")
    BACHATA("🌴 Bachata"),

    @SerialName("universal")
    UNIVERSAL("⚙️ Uniwersalne"),
}

@Serializable
enum class WaveDirection(val label: String) {
    @SerialName("rising")
    RISING("rosnąca ↗"),
    @SerialName("falling")
    FALLING("opadająca ↘")
}