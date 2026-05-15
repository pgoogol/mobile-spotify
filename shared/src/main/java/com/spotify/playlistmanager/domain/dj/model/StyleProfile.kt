package com.spotify.playlistmanager.domain.dj.model

/**
 * Wagi członów [EnergyScore] — patrz spec sekcja 6.3.
 */
data class EnergyWeights(
    val energy: Float,
    val tempo: Float,
    val valence: Float
) {
    init {
        require(kotlin.math.abs(energy + tempo + valence - 1f) < 1e-3f) {
            "EnergyWeights muszą się sumować do 1.0 (jest ${energy + tempo + valence})"
        }
    }
}

/**
 * Zakres przycięcia (clip) p5..p95 do percentylowej normalizacji.
 * `lo == hi` jest dozwolone — w takim przypadku normalize zwraca 0.5
 * (degenerowany ranking puli).
 */
data class ClipRange(val lo: Float, val hi: Float)

/**
 * Profil stylu — wszystkie skalibrowane parametry zależne od stylu.
 *
 * Spec sekcja 5: "Jeden silnik, dwa profile". `BlockGenerator`,
 * `PartyPlanner`, `LiveAssistant` nie zawierają żadnych stałych
 * specyficznych dla salsy/bachaty — wszystko przychodzi w tym obiekcie.
 *
 * UWAGA — uproszczenie wobec spec v3:
 * Spec sekcja 5.2 sugeruje dla salsy `tempoFeelingSource = "category"`
 * z mapą czterech kategorii (Wolne/Normalne/Szybkie/Bardzo szybkie),
 * bo surowe BPM Spotify dla salsy jest "niewiarygodne" (rozkład bimodalny
 * przez błąd oktawowy). W tym projekcie CSV nie ma kolumny tempoCategory,
 * więc świadomie liczymy `tempoFeeling` z `bpmFolded` dla obu stylów
 * (po złożeniu oktawowym `bpm < 100 ? bpm*2 : bpm`). Ryzyko: w salsie
 * normalizacja będzie mniej precyzyjna niż w spec, ale dla MVP akceptowalne.
 */
data class StyleProfile(
    val style: Style,

    /** Clip p5..p95 dla pola `energy` (Spotify 0..100 → po normalizacji 0..1). */
    val energyClip: ClipRange,
    /** Clip p5..p95 dla pola `valence`. */
    val valenceClip: ClipRange,
    /** Clip p5..p95 dla `danceability` (używane do `DanceFlowScore`, nie do bramki). */
    val danceClip: ClipRange,

    /** Clip p5..p95 dla `bpmFolded` (po złożeniu oktawowym). */
    val bpmFoldedClip: ClipRange,

    /** Wagi do `EnergyScore = w.energy*E + w.tempo*T + w.valence*V`. */
    val energyWeights: EnergyWeights,

    /** Próg `effectiveDanceability` — utwory poniżej nie wchodzą do puli generatora. */
    val danceFloorGate: Float,

    /**
     * Mnożnik kary podstylu w funkcji kosztu. Dla bachaty 0.0 (monostyl).
     * Dla salsy 1.0 (cztery znaczące podstyle).
     */
    val substylePenaltyWeight: Float
) {
    companion object {
        /**
         * Profil salsy — kalibracja z spec sekcja 5.2.
         * `bpmFoldedClip` rozszerzony do realnego zakresu salsy
         * (po fold bardzo wolne ~140, bardzo szybkie ~230).
         */
        val SALSA = StyleProfile(
            style = Style.SALSA,
            energyClip = ClipRange(0.55f * 100, 0.95f * 100),
            valenceClip = ClipRange(0.53f * 100, 0.96f * 100),
            danceClip = ClipRange(0.50f * 100, 0.83f * 100),
            bpmFoldedClip = ClipRange(140f, 230f),
            energyWeights = EnergyWeights(energy = 0.45f, tempo = 0.35f, valence = 0.20f),
            danceFloorGate = 0.55f,
            substylePenaltyWeight = 1.0f
        )

        /**
         * Profil bachaty — kalibracja z spec sekcja 5.3.
         * `bpmFoldedClip` = [102, 195] (z spec).
         */
        val BACHATA = StyleProfile(
            style = Style.BACHATA,
            energyClip = ClipRange(0.54f * 100, 0.94f * 100),
            valenceClip = ClipRange(0.51f * 100, 0.96f * 100),
            danceClip = ClipRange(0.53f * 100, 0.85f * 100),
            bpmFoldedClip = ClipRange(102f, 195f),
            energyWeights = EnergyWeights(energy = 0.55f, tempo = 0.25f, valence = 0.20f),
            danceFloorGate = 0.55f,
            substylePenaltyWeight = 0.0f
        )

        /** Profil dla wskazanego stylu — wygodny lookup. */
        fun forStyle(style: Style): StyleProfile = when (style) {
            Style.SALSA -> SALSA
            Style.BACHATA -> BACHATA
        }
    }
}
