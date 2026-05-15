package com.spotify.playlistmanager.domain.dj.model

/**
 * Preset DJ-a — pojedyncza akcja w trybie Live, która mapuje na parametry
 * `BlockGenerator.buildBlock`. Spec sekcja 12.2.
 *
 * Każdy preset wybiera kształt energii ([EnergyShape]) i opcjonalnie zestaw
 * filtrów. Nie kasuje stanu sesji — tylko dyktuje następny blok.
 */
enum class Preset(val label: String) {
    /** Narastający kształt — rozpalamy parkiet. */
    RAISE("Podnieś energię"),

    /** Opadający kształt — chłodzimy parkiet. */
    LOWER("Obniż energię"),

    /** Płaski (Wave) wokół średniej — utrzymanie klimatu. */
    HOLD("Utrzymaj klimat"),

    /** Opadający + filtr `minDanceFlow` (bezpieczne). */
    RECOVERY("Recovery"),

    /** Fast — peak energii. */
    PEAK("Peak"),

    /** Filtr `onlySubstyle = TIMBA`. */
    MORE_TIMBA("Więcej timby"),

    /** Filtr `classicsOnly = true`. */
    MORE_CLASSICS("Więcej klasyki"),

    /** Kształt Slow. */
    SLOWER("Wolniej"),

    /** Kształt Fast. */
    FASTER("Szybciej"),

    /** Filtr `noVeryFast = true`. */
    NO_VERY_FAST("Bez bardzo szybkich"),

    /** Filtr `minDanceFlow = 0.55` + premia popularności. */
    SAFE("Bez ryzyka"),

    /** Brak filtrów — szeroka pula, zwiększona losowość (placeholder na v3). */
    SURPRISE("Zaskocz mnie");

    /** Sugerowany kształt dla presetu. */
    fun toShape(): EnergyShape = when (this) {
        RAISE -> EnergyShape.Rising
        LOWER, RECOVERY -> EnergyShape.Falling
        HOLD, SURPRISE -> EnergyShape.Wave()
        PEAK, FASTER -> EnergyShape.Fast
        SLOWER -> EnergyShape.Slow
        MORE_TIMBA, MORE_CLASSICS, NO_VERY_FAST, SAFE -> EnergyShape.Wave()
    }

    /** Sugerowane filtry. */
    fun toFilters(): BlockFilters = when (this) {
        NO_VERY_FAST -> BlockFilters(noVeryFast = true)
        MORE_CLASSICS -> BlockFilters(classicsOnly = true)
        MORE_TIMBA -> BlockFilters(onlySubstyle = Substyle.TIMBA)
        SAFE, RECOVERY -> BlockFilters(minDanceFlow = 0.55f)
        else -> BlockFilters()
    }
}
