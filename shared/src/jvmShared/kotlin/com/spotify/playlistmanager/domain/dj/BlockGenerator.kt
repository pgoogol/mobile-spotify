package com.spotify.playlistmanager.domain.dj

import com.spotify.playlistmanager.domain.dj.model.AnalyzedTrack
import com.spotify.playlistmanager.domain.dj.model.Block
import com.spotify.playlistmanager.domain.dj.model.BlockFilters
import com.spotify.playlistmanager.domain.dj.model.CostWeights
import com.spotify.playlistmanager.domain.dj.model.EnergyShape
import com.spotify.playlistmanager.domain.dj.model.PartyMode
import com.spotify.playlistmanager.domain.dj.model.PartyState
import com.spotify.playlistmanager.domain.dj.model.StyleProfile
import com.spotify.playlistmanager.domain.dj.model.Substyle
import kotlin.math.abs

/**
 * Rdzeń algorytmu — buduje pojedynczy blok 1–10 utworów zachłannie,
 * slot po slocie, minimalizując funkcję kosztu (spec sekcja 8).
 *
 * Pula = `state.poolIdsByStyle[style]` zmapowana na `AnalyzedTrack`y
 * (przekazane przez wywołującego, żeby uniknąć podwójnej analizy korpusu),
 * minus utwory już zagrane (`playedTrackIds`) i niezagrana kolejka
 * (`currentQueueTail`), minus utwory niespełniające filtrów ([BlockFilters]).
 */
class BlockGenerator {

    sealed class BuildError(message: String) : RuntimeException(message) {
        /** Pula stylu pusta po filtrach. */
        class EmptyPool(val style: com.spotify.playlistmanager.domain.dj.model.Style) :
            BuildError("Brak utworów spełniających kryteria dla stylu $style")

        /** Pula opróżniła się w trakcie wypełniania bloku (mniej niż N kandydatów). */
        class TooSmallPool(val style: com.spotify.playlistmanager.domain.dj.model.Style, val needed: Int, val got: Int) :
            BuildError("Pula stylu $style zbyt mała: trzeba $needed, dostępne $got")
    }

    /**
     * Buduje blok.
     *
     * @param state aktualny PartyState (do filtrowania played/queue + kontekst kosztu)
     * @param profile profil docelowego stylu
     * @param analyzedPool wszystkie przeanalizowane utwory tego stylu
     * @param n długość bloku 1..10 (tanda może mieć mniej niż 3 utwory)
     * @param shape kształt energii
     * @param startAnchor null = Planning, !=null = Live (kotwiczenie startu)
     * @param filters dodatkowe filtry (z presetu / UI)
     * @param weights wagi funkcji kosztu (PLANNING albo LIVE)
     * @param idHint sufiks ID nowego bloku (np. timestamp); generujemy ID
     */
    fun buildBlock(
        state: PartyState,
        profile: StyleProfile,
        analyzedPool: List<AnalyzedTrack>,
        n: Int,
        shape: EnergyShape,
        startAnchor: Float?,
        filters: BlockFilters = BlockFilters(),
        weights: CostWeights = if (state.mode == PartyMode.LIVE) CostWeights.LIVE else CostWeights.PLANNING,
        idHint: String = System.currentTimeMillis().toString()
    ): Result<Block> {
        require(n in 1..10) { "Rozmiar bloku N musi być w [1..10], jest $n" }

        // Wstępne filtrowanie puli — bramka, played, queueTail, filtry presetu
        val available = analyzedPool
            .asSequence()
            .filter { it.passesGate }
            .filter { it.id != null && it.id !in state.playedTrackIds }
            .filter { it.id !in state.currentQueueTail }
            .filter { applyFilters(it, filters) }
            .toMutableList()

        if (available.isEmpty()) {
            return Result.failure(BuildError.EmptyPool(profile.style))
        }

        val blockTracks = mutableListOf<AnalyzedTrack>()
        val targetScores = mutableListOf<Float>()
        var previous: AnalyzedTrack? = state.lastPlayedIdByStyle[profile.style]
            ?.let { lastId -> analyzedPool.firstOrNull { it.id == lastId } }

        val budgetMs = n * AVERAGE_TRACK_MS

        for (i in 0 until n) {
            if (available.isEmpty()) {
                return Result.failure(BuildError.TooSmallPool(profile.style, n, blockTracks.size))
            }
            val p = if (n == 1) 0f else i.toFloat() / (n - 1)
            val target = shape.target(p, startAnchor)
            targetScores += target

            val pickedIndex = pickArgmin(
                pool = available,
                target = target,
                previous = previous,
                blockSoFar = blockTracks,
                profile = profile,
                weights = weights,
                state = state,
                budgetMs = budgetMs
            )
            val picked = available.removeAt(pickedIndex)
            blockTracks += picked
            previous = picked
        }

        val block = Block(
            id = "blk_$idHint",
            style = profile.style,
            shape = shape,
            tracks = blockTracks,
            targetScores = targetScores,
            startAnchor = startAnchor
        )
        return Result.success(block)
    }

    // ── Filtry presetów ────────────────────────────────────────────────────

    private fun applyFilters(t: AnalyzedTrack, f: BlockFilters): Boolean {
        if (f.noVeryFast && t.tempoFeeling > 0.85f) return false
        if (f.classicsOnly) {
            val year = t.track.releaseDate?.take(4)?.toIntOrNull()
            if (year == null || year > f.classicsOlderThanYear) return false
        }
        if (f.minDanceFlow != null && t.danceFlowScore < f.minDanceFlow) return false
        if (f.onlySubstyle != null && t.substyle != f.onlySubstyle) return false
        return true
    }

    /**
     * Pojedynczy "argmin" — wybiera najlepszy utwór z puli dla zadanego targetu.
     * Używane przez [LiveAssistant.rerollSlot] do podmiany jednego slotu bez
     * przepisywania całego bloku.
     *
     * Filtruje pulę: bramka, NOT in playedTracks, NOT in queueTail, filtry.
     * Wyklucza dodatkowo wszystkie `excludeIds` (zwykle ID utworów już użytych
     * w bloku oprócz slotu rerollowanego).
     *
     * Zwraca `null` jeśli pula jest pusta po filtrach.
     */
    fun pickBestForTarget(
        state: PartyState,
        profile: StyleProfile,
        analyzedPool: List<AnalyzedTrack>,
        target: Float,
        previous: AnalyzedTrack?,
        blockSoFar: List<AnalyzedTrack>,
        excludeIds: Set<String>,
        filters: BlockFilters = BlockFilters(),
        weights: CostWeights = CostWeights.LIVE
    ): AnalyzedTrack? {
        val available = analyzedPool
            .asSequence()
            .filter { it.passesGate }
            .filter { it.id != null && it.id !in state.playedTrackIds }
            .filter { it.id !in state.currentQueueTail }
            .filter { it.id !in excludeIds }
            .filter { applyFilters(it, filters) }
            .toList()
        if (available.isEmpty()) return null

        val budgetMs = (blockSoFar.size + 1) * AVERAGE_TRACK_MS
        val idx = pickArgmin(
            pool = available,
            target = target,
            previous = previous,
            blockSoFar = blockSoFar,
            profile = profile,
            weights = weights,
            state = state,
            budgetMs = budgetMs
        )
        return available[idx]
    }

    // ── argmin po koszcie ──────────────────────────────────────────────────

    private fun pickArgmin(
        pool: List<AnalyzedTrack>,
        target: Float,
        previous: AnalyzedTrack?,
        blockSoFar: List<AnalyzedTrack>,
        profile: StyleProfile,
        weights: CostWeights,
        state: PartyState,
        budgetMs: Long
    ): Int {
        var bestIdx = 0
        var bestCost = Float.POSITIVE_INFINITY
        for (i in pool.indices) {
            val t = pool[i]
            val cost = cost(t, target, previous, blockSoFar, profile, weights, state, budgetMs)
            if (cost < bestCost) {
                bestCost = cost
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun cost(
        t: AnalyzedTrack,
        target: Float,
        previous: AnalyzedTrack?,
        blockSoFar: List<AnalyzedTrack>,
        profile: StyleProfile,
        w: CostWeights,
        state: PartyState,
        budgetMs: Long
    ): Float {
        // Człony karzące (+)
        val energyMatch = abs(t.energyScore - target)
        val tempoMatch = if (previous != null) abs(t.tempoFeeling - previous.tempoFeeling) else 0f
        val energyJump = if (previous != null && abs(t.energyScore - previous.energyScore) > 0.25f) 1f else 0f
        val artistPen = if (previous != null && t.artist.equals(previous.artist, ignoreCase = true)) 1f else 0f
        val substylePen = if (profile.substylePenaltyWeight > 0f) substyleViolation(t, blockSoFar, state) else 0f
        val similarPen = if (isNearDuplicate(t, blockSoFar)) 1f else 0f
        val aggressivePen = if (overAggressiveLimit(t, blockSoFar)) 1f else 0f
        val durationSum = blockSoFar.sumOf { it.track.durationMs.toLong() } + t.track.durationMs.toLong()
        val durationPen = ((durationSum - budgetMs).toFloat() / budgetMs.toFloat()).coerceAtLeast(0f)

        // Człony premiujące (-)
        val danceflowBonus = t.danceFlowScore
        val freshnessBonus = 0f // brak danych lastPlayedAt/timesPlayed w v1
        val popularityBonus = if (target > 0.70f) t.popularityN else 0f
        val skipPen = 0f // FeedbackEngine w v3

        return w.energy * energyMatch +
            w.tempo * tempoMatch +
            w.energyJump * energyJump +
            w.artist * artistPen +
            profile.substylePenaltyWeight * substylePen +
            w.similar * similarPen +
            w.aggressive * aggressivePen +
            w.duration * durationPen +
            w.skip * skipPen -
            w.danceflow * danceflowBonus -
            w.fresh * freshnessBonus -
            w.popularity * popularityBonus
    }

    /**
     * Kara podstylu — uwzględnia 3 ostatnie podstyle (state.recentSubstyles + blockSoFar)
     * i karze powtórzenie zbyt często.
     */
    private fun substyleViolation(
        t: AnalyzedTrack,
        blockSoFar: List<AnalyzedTrack>,
        state: PartyState
    ): Float {
        val recent = (state.recentSubstyles + blockSoFar.map { it.substyle }).takeLast(3)
        val repeats = recent.count { it == t.substyle }
        return repeats / 3f
    }

    /**
     * Prawie-duplikat = ten sam tytuł (znormalizowany), inny mix/remaster.
     * Heurystyka: jednakowy `title` po zlowercase i usunięciu nawiasów.
     */
    private fun isNearDuplicate(t: AnalyzedTrack, blockSoFar: List<AnalyzedTrack>): Boolean {
        val key = normalizeTitle(t.track.title)
        return blockSoFar.any { normalizeTitle(it.track.title) == key }
    }

    private fun normalizeTitle(s: String): String =
        s.lowercase().replace(Regex("\\s*\\([^)]*\\)"), "").trim()

    /**
     * Limit agresywnych Timb pod rząd — spec sekcja 9.
     * Domyślnie max 2 Timby z rzędu (3-cia karana).
     */
    private fun overAggressiveLimit(t: AnalyzedTrack, blockSoFar: List<AnalyzedTrack>): Boolean {
        if (t.substyle != Substyle.TIMBA) return false
        val tail = blockSoFar.takeLast(2)
        return tail.size == 2 && tail.all { it.substyle == Substyle.TIMBA }
    }

    companion object {
        /** Domyślny szacowany czas utworu (3:30) — używany do budżetu czasu bloku. */
        const val AVERAGE_TRACK_MS = 210_000L
    }
}
