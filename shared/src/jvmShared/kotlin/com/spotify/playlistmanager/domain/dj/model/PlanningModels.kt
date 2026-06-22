package com.spotify.playlistmanager.domain.dj.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Faza imprezy — spec sekcja 11.
 *
 * Każda faza ma sugerowany kształt energii (warmup → rising, peak → fast itd.)
 * i procentowy udział w czasie imprezy.
 */
@Serializable
enum class Phase(val displayName: String, val defaultShape: EnergyShape) {
    @SerialName("warmup")
    WARMUP("Rozgrzewka", EnergyShape.Rising),

    @SerialName("flow")
    FLOW("Flow", EnergyShape.Wave()),

    @SerialName("peak")
    PEAK("Peak", EnergyShape.Fast),

    @SerialName("recovery")
    RECOVERY("Oddech", EnergyShape.Falling),

    @SerialName("closer")
    CLOSER("Finał", EnergyShape.Wave())
}

/**
 * Łuk energii całej imprezy — preset rozkładu faz.
 */
@Serializable
enum class EnergyArc(val displayName: String) {
    /** Klasyk: rozgrzewka → flow → peak → oddech → finał. */
    @SerialName("classic")
    CLASSIC("Klasyczny łuk"),

    /** Szybki start: peak utrzymywany długo. */
    @SerialName("hot")
    HOT("Mocno od początku"),

    /** Delikatny: minimal peak, długie flow + recovery. */
    @SerialName("smooth")
    SMOOTH("Łagodny"),

    /** Stop-and-go: kilka mini-peaków. */
    @SerialName("intervals")
    INTERVALS("Interwały");

    /** Rozkład czasu fazami (sumuje się do 1.0). */
    fun phaseShares(): List<PhaseShare> = when (this) {
        CLASSIC -> listOf(
            PhaseShare(Phase.WARMUP, 0.15f),
            PhaseShare(Phase.FLOW, 0.25f),
            PhaseShare(Phase.PEAK, 0.30f),
            PhaseShare(Phase.RECOVERY, 0.15f),
            PhaseShare(Phase.CLOSER, 0.15f)
        )

        HOT -> listOf(
            PhaseShare(Phase.WARMUP, 0.10f),
            PhaseShare(Phase.PEAK, 0.50f),
            PhaseShare(Phase.FLOW, 0.20f),
            PhaseShare(Phase.RECOVERY, 0.10f),
            PhaseShare(Phase.CLOSER, 0.10f)
        )

        SMOOTH -> listOf(
            PhaseShare(Phase.WARMUP, 0.20f),
            PhaseShare(Phase.FLOW, 0.40f),
            PhaseShare(Phase.PEAK, 0.15f),
            PhaseShare(Phase.RECOVERY, 0.15f),
            PhaseShare(Phase.CLOSER, 0.10f)
        )

        INTERVALS -> listOf(
            PhaseShare(Phase.WARMUP, 0.10f),
            PhaseShare(Phase.PEAK, 0.20f),
            PhaseShare(Phase.RECOVERY, 0.15f),
            PhaseShare(Phase.PEAK, 0.20f),
            PhaseShare(Phase.RECOVERY, 0.15f),
            PhaseShare(Phase.CLOSER, 0.20f)
        )
    }
}

/** Pojedynczy fragment łuku — faza + jej udział w czasie. */
data class PhaseShare(val phase: Phase, val share: Float)

/**
 * Proporcja stylów — % salsy (reszta to bachata). 0..100.
 */
data class StyleRatio(val salsaPercent: Int) {
    init {
        require(salsaPercent in 0..100) { "salsaPercent musi być w 0..100, jest $salsaPercent" }
    }

    val bachataPercent: Int get() = 100 - salsaPercent

    /** Bresenham slot pattern — równomierne rozłożenie S/B w oknie. Spec sekcja 13. */
    fun slotPattern(windowSize: Int): List<Style> {
        if (windowSize == 0) return emptyList()
        if (salsaPercent == 100) return List(windowSize) { Style.SALSA }
        if (salsaPercent == 0) return List(windowSize) { Style.BACHATA }
        val result = mutableListOf<Style>()
        var error = 0
        val salsaPerSlot = salsaPercent
        val bachataPerSlot = bachataPercent
        repeat(windowSize) {
            error += salsaPerSlot
            if (error >= 50) {
                result += Style.SALSA
                error -= (salsaPerSlot + bachataPerSlot)
            } else {
                result += Style.BACHATA
            }
        }
        return result
    }
}

/**
 * Strategia rozkładu podstylów salsy — spec sekcja 11.
 * W v1 implementujemy tylko CLASSIC; inne wartości jako placeholder.
 */
@Serializable
enum class SubstyleStrategy(val displayName: String) {
    @SerialName("classic")
    CLASSIC("Klasyczna impreza"),

    @SerialName("cuban")
    CUBAN("Kubański klimat"),

    @SerialName("soft_social")
    SOFT_SOCIAL("Łagodny social"),

    @SerialName("peak_energy")
    PEAK_ENERGY("Peak energy");

    /** Docelowy udział każdego podstylu (0..1, suma = 1.0 w obrębie salsy). */
    fun shares(): Map<Substyle, Float> = when (this) {
        CLASSIC -> mapOf(
            Substyle.SALSA_GENERIC to 0.50f,
            Substyle.TIMBA to 0.25f,
            Substyle.SON_CUBANO to 0.15f,
            Substyle.RUMBA to 0.10f
        )

        CUBAN -> mapOf(
            Substyle.SALSA_GENERIC to 0.15f,
            Substyle.TIMBA to 0.55f,
            Substyle.SON_CUBANO to 0.25f,
            Substyle.RUMBA to 0.05f
        )

        SOFT_SOCIAL -> mapOf(
            Substyle.SALSA_GENERIC to 0.35f,
            Substyle.TIMBA to 0.20f,
            Substyle.SON_CUBANO to 0.40f,
            Substyle.RUMBA to 0.05f
        )

        PEAK_ENERGY -> mapOf(
            Substyle.SALSA_GENERIC to 0.30f,
            Substyle.TIMBA to 0.60f,
            Substyle.SON_CUBANO to 0.10f
        )
    }
}

/**
 * Stan parkietu — manual hint dla LiveAssistant. Spec sekcja 12.2.
 */
@Serializable
enum class DancefloorState(val displayName: String) {
    @SerialName("empty")
    EMPTY("Pusty"),

    @SerialName("warming")
    WARMING("Rozgrzewa się"),

    @SerialName("stable")
    STABLE("Stabilnie"),

    @SerialName("hot")
    HOT("Gorąco"),

    @SerialName("tired")
    TIRED("Zmęczony")
}
