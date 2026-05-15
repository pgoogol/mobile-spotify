package com.spotify.playlistmanager.domain.dj

import com.spotify.playlistmanager.domain.dj.model.AnalyzedTrack
import com.spotify.playlistmanager.domain.dj.model.Block
import com.spotify.playlistmanager.domain.dj.model.CostWeights
import com.spotify.playlistmanager.domain.dj.model.EnergyArc
import com.spotify.playlistmanager.domain.dj.model.EnergyShape
import com.spotify.playlistmanager.domain.dj.model.PartyMode
import com.spotify.playlistmanager.domain.dj.model.PartyState
import com.spotify.playlistmanager.domain.dj.model.Phase
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.StyleProfile
import com.spotify.playlistmanager.domain.dj.model.StyleRatio
import com.spotify.playlistmanager.domain.dj.model.SubstyleStrategy

/**
 * Tryb A — planuje całą imprezę z góry.
 *
 * Algorytm (spec sekcja 11):
 *   1. Rozpisz fazy wg [EnergyArc] — np. CLASSIC: warmup 15% → flow 25% →
 *      peak 30% → recovery 15% → closer 15%.
 *   2. Każda faza dzieli się na bloki rozmiaru `defaultBlockSize` (5).
 *   3. Wzorzec slotów S/B z [StyleRatio.slotPattern] przeplata bloki obu stylów
 *      równomiernie w obrębie całej imprezy (Bresenham).
 *   4. Dla każdego bloku wywołujemy [BlockGenerator.buildBlock] z `startAnchor=null`
 *      i kształtem energii z fazy.
 *
 * Strategie podstylów ([SubstyleStrategy]) są zaczepione na poziomie API,
 * w v1 implementowana tylko CLASSIC (50/25/15/10) — przekazana, ale nie
 * używana do filtrowania bloków (wpływa na `substyle_pen` w przyszłej iteracji).
 */
class PartyPlanner(
    private val blockGenerator: BlockGenerator = BlockGenerator()
) {

    /**
     * Wynik planowania. `blocks` — uporządkowana lista bloków. `errors` —
     * nie-fatalne błędy (np. brak utworów dla jednego bloku) z indeksem bloku
     * w planie. Pozostałe bloki zostały zbudowane.
     */
    data class PlanResult(
        val blocks: List<Block>,
        val errors: List<Pair<Int, Throwable>>,
        val phaseByBlock: List<Phase>
    )

    /**
     * @param blockSizeByStyle rozmiar bloku osobno dla każdego stylu
     *        (np. `{SALSA: 3, BACHATA: 4}` = tanda 3:4). Spec sekcja 13.
     *        Brak klucza dla stylu → [DEFAULT_BLOCK_SIZE].
     */
    fun plan(
        state: PartyState,
        analyzedByStyle: Map<Style, List<AnalyzedTrack>>,
        durationMs: Long,
        ratio: StyleRatio,
        arc: EnergyArc,
        @Suppress("UNUSED_PARAMETER") strategy: SubstyleStrategy = SubstyleStrategy.CLASSIC,
        blockSizeByStyle: Map<Style, Int> = mapOf(
            Style.SALSA to DEFAULT_BLOCK_SIZE,
            Style.BACHATA to DEFAULT_BLOCK_SIZE
        )
    ): PlanResult {
        blockSizeByStyle.values.forEach { size ->
            require(size in 3..10) { "Rozmiar bloku musi być w [3..10], jest $size" }
        }

        val avgBlockSize = blockSizeByStyle.values.average().toInt().coerceAtLeast(3)

        // Faza → liczba bloków (proporcjonalnie do share, min 1 blok / faza)
        val shares = arc.phaseShares()
        val totalSlots = (durationMs / BlockGenerator.AVERAGE_TRACK_MS).toInt().coerceAtLeast(avgBlockSize)
        val totalBlocks = (totalSlots / avgBlockSize).coerceAtLeast(shares.size)
        val blocksPerShare = shares.map { share ->
            (share.share * totalBlocks).toInt().coerceAtLeast(1) to share.phase
        }
        val phasesByBlock = blocksPerShare.flatMap { (count, phase) -> List(count) { phase } }
        val effectiveTotalBlocks = phasesByBlock.size

        // Wzorzec stylów per blok (Bresenham — równomierne rozłożenie)
        val stylePattern = ratio.slotPattern(effectiveTotalBlocks)

        // Mutable kopia stanu — w trakcie planowania symulujemy "zagrane"
        // utwory poprzez dopisywanie do queueTail, żeby `BlockGenerator`
        // automatycznie ich nie wybrał ponownie.
        var simState = state.copy(mode = PartyMode.PLANNING)

        val blocks = mutableListOf<Block>()
        val errors = mutableListOf<Pair<Int, Throwable>>()
        val phasesUsed = mutableListOf<Phase>()

        for (i in 0 until effectiveTotalBlocks) {
            val phase = phasesByBlock[i]
            val style = stylePattern[i]
            val profile = StyleProfile.forStyle(style)
            val analyzedPool = analyzedByStyle[style].orEmpty()
            val n = blockSizeByStyle[style] ?: DEFAULT_BLOCK_SIZE

            val shape: EnergyShape = phase.defaultShape

            val result = blockGenerator.buildBlock(
                state = simState,
                profile = profile,
                analyzedPool = analyzedPool,
                n = n,
                shape = shape,
                startAnchor = null,
                weights = CostWeights.PLANNING,
                idHint = "plan_${i}_${phase.name.lowercase()}"
            )
            result.onSuccess { block ->
                blocks += block
                phasesUsed += phase
                // Dopisujemy ID utworów do queueTail symulowanego stanu —
                // żeby kolejne bloki nie wybrały ich ponownie.
                val ids = block.tracks.mapNotNull { it.id }
                simState = simState.copy(
                    currentQueueTail = simState.currentQueueTail + ids
                )
            }.onFailure { e ->
                errors += i to e
            }
        }

        return PlanResult(blocks = blocks, errors = errors, phaseByBlock = phasesUsed)
    }

    companion object {
        /** Sensowny domyślny rozmiar bloku — środek dopuszczalnego zakresu. */
        const val DEFAULT_BLOCK_SIZE = 5
    }
}
