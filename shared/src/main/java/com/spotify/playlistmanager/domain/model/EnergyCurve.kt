package com.spotify.playlistmanager.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Strategia doboru utworów do segmentu playlisty.
 *
 * Każda strategia definiuje dwa wymiary:
 *
 * 1. **Kształt** — [generateTargets] zwraca listę docelowych score'ów [0..1]
 *    dla kolejnych pozycji w segmencie. Matcher szuka w puli utworów o score
 *    najbliższym do targetu (po skalowaniu do rozkładu puli — patrz
 *    [EnergyCurveCalculator.matchTracks]).
 *
 * 2. **Oś** — [scoreAxis] określa, która funkcja score'uje utwory:
 *    - [ScoreAxis.DANCE] = BPM + energy + danceability (jak mocno się tańczy)
 *    - [ScoreAxis.MOOD]  = valence + acousticness-inv + dance (klimat/nastrój)
 *
 * Strategia = para (shape, axis). Niektóre strategie mają swoje minimalne N
 * (np. Arc/Valley wymagają min 3 utworów — poniżej degradują do Rising/Falling).
 *
 * Klasa zachowuje nazwę `EnergyCurve` ze względu na szeroką istniejącą
 * integrację (serializacja, UI, chart). Semantycznie to dziś „strategia
 * segmentu", nie tylko krzywa energii.
 */
@Serializable
sealed class EnergyCurve {

    /** Wyświetlana nazwa strategii z emoji. */
    abstract val displayName: String

    /** Krótki opis dla UI (podpowiedź kiedy użyć). */
    abstract val description: String

    /** Oś dopasowania — DANCE lub MOOD. Domyślnie DANCE. */
    open val scoreAxis: ScoreAxis = ScoreAxis.DANCE

    /**
     * Minimalna liczba utworów, przy której strategia ma sens.
     * Poniżej — matcher degraduje do prostszej strategii (patrz [degradeFor]).
     */
    open val minTrackCount: Int = 2

    /**
     * Generuje listę docelowych score'ów [0..1] dla podanej liczby pozycji.
     * Skalowanie do rozkładu puli robi [EnergyCurveCalculator].
     *
     * @param trackCount liczba pozycji w segmencie
     * @return lista logicznych target score'ów o rozmiarze [trackCount]
     */
    abstract fun generateTargets(trackCount: Int): List<Float>

    /**
     * Degradacja dla segmentów mniejszych niż [minTrackCount].
     * Domyślnie: zwraca siebie. Nadpisywane przez Arc/Valley.
     */
    open fun degradeFor(trackCount: Int): EnergyCurve = this

    // ════════════════════════════════════════════════════════
    //  Brak — sortowanie bez dopasowania
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("none")
    data object None : EnergyCurve() {
        override val displayName = "Brak"
        override val description = "Sortowanie wg ustawień (popularność/długość/…), bez dopasowania"
        override val minTrackCount = 1
        override fun generateTargets(trackCount: Int): List<Float> = emptyList()
    }

    // ════════════════════════════════════════════════════════
    //  Rising ↗ — energia rośnie od początku do końca
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("rising")
    data object Rising : EnergyCurve() {
        override val displayName = "↗ Narastająco"
        override val description = "Energia taneczna rośnie od najsłabszego do najmocniejszego utworu"

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.5f)
            return List(trackCount) { i -> i.toFloat() / (trackCount - 1) }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Falling ↘ — energia opada
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("falling")
    data object Falling : EnergyCurve() {
        override val displayName = "↘ Opadająco"
        override val description = "Energia opada — od najmocniejszego do najspokojniejszego"

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.5f)
            return List(trackCount) { i -> 1f - i.toFloat() / (trackCount - 1) }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Stable ━ — wszystkie utwory w okolicy mediany puli
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("stable")
    data object Stable : EnergyCurve() {
        override val displayName = "━ Stabilnie"
        override val description = "Wszystkie utwory o podobnym tempie — tanda, blok jednolity"

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            return List(trackCount) { 0.5f }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Arc 🎢 — rise-peak-fall w obrębie segmentu
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("arc")
    data object Arc : EnergyCurve() {
        override val displayName = "🎢 Łuk"
        override val description = "Narasta → pik → opada — stand-alone runda z pełnym łukiem"
        override val minTrackCount = 3

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(1f)
            if (trackCount == 2) return listOf(0.25f, 1f)

            // Pik w 65% długości (lekka asymetria — dłużej rozkręca niż schodzi)
            val peakIdx = ((trackCount - 1) * 0.65f).toInt().coerceIn(1, trackCount - 2)
            return List(trackCount) { i ->
                if (i <= peakIdx) {
                    val t = i.toFloat() / peakIdx
                    0.25f + t * 0.75f  // 0.25 → 1.0
                } else {
                    val denom = (trackCount - 1 - peakIdx).coerceAtLeast(1)
                    val t = (i - peakIdx).toFloat() / denom
                    1f - t * 0.60f     // 1.0 → 0.40
                }
            }
        }

        override fun degradeFor(trackCount: Int): EnergyCurve =
            if (trackCount < minTrackCount) Rising else this
    }

    // ════════════════════════════════════════════════════════
    //  Valley 🌀 — fall-bottom-rise (intermezzo, oddech)
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("valley")
    data object Valley : EnergyCurve() {
        override val displayName = "🌀 Dolina"
        override val description = "Opada → dno → narasta — moment oddechu w środku segmentu"
        override val minTrackCount = 3

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.3f)
            if (trackCount == 2) return listOf(0.9f, 0.3f)

            // Dno w środku
            val bottomIdx = (trackCount - 1) / 2
            return List(trackCount) { i ->
                if (i <= bottomIdx) {
                    val t = i.toFloat() / bottomIdx.coerceAtLeast(1)
                    0.9f - t * 0.6f    // 0.9 → 0.3
                } else {
                    val denom = (trackCount - 1 - bottomIdx).coerceAtLeast(1)
                    val t = (i - bottomIdx).toFloat() / denom
                    0.3f + t * 0.5f    // 0.3 → 0.8
                }
            }
        }

        override fun degradeFor(trackCount: Int): EnergyCurve =
            if (trackCount < minTrackCount) Falling else this
    }

    // ════════════════════════════════════════════════════════
    //  Wave ∿ — parametryczna sinusoida
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("wave")
    data class Wave(
        val direction: WaveDirection = WaveDirection.RISING,
        val tracksPerHalfWave: Int = 3
    ) : EnergyCurve() {

        override val displayName: String
            get() = "∿ Fala ${direction.arrow}"

        override val description: String
            get() = "Sinusoida ${direction.label} wokół mediany, " +
                    "$tracksPerHalfWave utw. na półfalę"

        val fullWaveSize: Int get() = tracksPerHalfWave * 4

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()

            val fws = fullWaveSize
            val center = 0.5f
            val amplitude = 0.3f
            return List(trackCount) { i ->
                val angle = 2.0 * Math.PI * i.toDouble() / fws
                val sin = kotlin.math.sin(angle).toFloat()
                when (direction) {
                    WaveDirection.RISING  -> center + amplitude * sin
                    WaveDirection.FALLING -> center - amplitude * sin
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Romantic 🌹 — top wg MOOD, stabilnie wysoko
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("romantic")
    data object Romantic : EnergyCurve() {
        override val displayName = "🌹 Romantycznie"
        override val description = "Utwory o najwyższym klimacie (valence + dance) — tanda romantyczna"
        override val scoreAxis = ScoreAxis.MOOD

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            // Wszystkie targety w górnym ekstremum MOOD — matcher wybierze top-N
            return List(trackCount) { 1f }
        }
    }

    // ════════════════════════════════════════════════════════
    //  Calm 🌙 — od najmroczniejszych klimatów do średnich
    // ════════════════════════════════════════════════════════

    @Serializable
    @SerialName("calm")
    data object Calm : EnergyCurve() {
        override val displayName = "🌙 Spokojnie"
        override val description = "Od najbardziej nastrojowych do spokojniejszych — finał wieczoru"
        override val scoreAxis = ScoreAxis.MOOD

        override fun generateTargets(trackCount: Int): List<Float> {
            if (trackCount <= 0) return emptyList()
            if (trackCount == 1) return listOf(0.6f)
            return List(trackCount) { i ->
                val t = i.toFloat() / (trackCount - 1)
                1f - t * 0.7f  // 1.0 → 0.3
            }
        }
    }

    companion object {
        /**
         * Wszystkie predefiniowane strategie w kolejności sensownej dla UI:
         * None → shape-based (rising → falling → stable → arc → valley → wave)
         * → mood-based (romantic → calm).
         */
        val presets: List<EnergyCurve> get() = listOf(
            None,
            Rising,
            Falling,
            Stable,
            Arc,
            Valley,
            Wave(direction = WaveDirection.RISING),
            Wave(direction = WaveDirection.FALLING),
            Romantic,
            Calm,
        )
    }
}

@Serializable
enum class WaveDirection(val label: String, val arrow: String) {
    @SerialName("rising")
    RISING("rosnąca", "↗"),

    @SerialName("falling")
    FALLING("opadająca", "↘")
}
