package com.spotify.playlistmanager.domain.dj

import com.spotify.playlistmanager.domain.dj.model.AnalyzedTrack
import com.spotify.playlistmanager.domain.dj.model.Block
import com.spotify.playlistmanager.domain.dj.model.BlockFilters
import com.spotify.playlistmanager.domain.dj.model.CostWeights
import com.spotify.playlistmanager.domain.dj.model.EnergyShape
import com.spotify.playlistmanager.domain.dj.model.PartyMode
import com.spotify.playlistmanager.domain.dj.model.PartyState
import com.spotify.playlistmanager.domain.dj.model.Preset
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.StyleProfile

/**
 * Tryb B — asystent live. Buduje bloki na żądanie, kotwicząc start
 * energii ostatniego faktycznie zagranego utworu danego stylu.
 *
 * Operacje punktowe (spec sekcja 12.1):
 * - [nextBlock] — nowy blok z presetu
 * - [rerollSlot] — podmiana JEDNEGO slotu (reszta bloku bez zmian)
 * - [reshape] — przerwanie bieżącego bloku, niezagrany ogon wraca do puli
 * - [commitPlayed] — przeniesienie utworu z queueTail do playedTrackIds
 * - [softLockSlot] / [softUnlockSlot] — utwór nie zostanie podmieniony przez reroll
 */
class LiveAssistant(
    private val blockGenerator: BlockGenerator = BlockGenerator()
) {

    /**
     * Buduje nowy blok wg presetu.
     *
     * @param style styl docelowy (LiveAssistant nie zarządza wzorcem S/B —
     *              o styl prosi user lub UI sugeruje na podstawie ostatniego)
     */
    fun nextBlock(
        state: PartyState,
        style: Style,
        analyzedPool: List<AnalyzedTrack>,
        preset: Preset,
        n: Int,
        idHint: String = System.currentTimeMillis().toString()
    ): Result<Block> {
        val profile = StyleProfile.forStyle(style)
        val anchor = computeStartAnchor(state, analyzedPool, style)
        val liveState = state.copy(mode = PartyMode.LIVE)
        return blockGenerator.buildBlock(
            state = liveState,
            profile = profile,
            analyzedPool = analyzedPool,
            n = n,
            shape = preset.toShape(),
            startAnchor = anchor,
            filters = preset.toFilters(),
            weights = CostWeights.LIVE,
            idHint = "live_${idHint}_${preset.name.lowercase()}"
        )
    }

    /**
     * Podmiana pojedynczego slotu — pula bez utworów już użytych w bloku.
     * Zablokowane sloty (`lockedSlots`) odmawiają reroll'u.
     */
    fun rerollSlot(
        block: Block,
        slotIndex: Int,
        state: PartyState,
        analyzedPool: List<AnalyzedTrack>,
        filters: BlockFilters = BlockFilters()
    ): Result<Block> {
        require(slotIndex in block.tracks.indices) { "slotIndex $slotIndex poza zakresem 0..${block.tracks.lastIndex}" }
        if (slotIndex in block.lockedSlots) {
            return Result.failure(IllegalStateException("Slot $slotIndex jest zablokowany (lock)"))
        }

        val profile = StyleProfile.forStyle(block.style)
        val target = block.targetScores[slotIndex]
        val previous = if (slotIndex > 0) block.tracks[slotIndex - 1] else null
        val excludeIds = block.tracks.mapIndexedNotNull { i, t -> if (i != slotIndex) t.id else null }.toSet()
        val liveState = state.copy(mode = PartyMode.LIVE)

        val replacement = blockGenerator.pickBestForTarget(
            state = liveState,
            profile = profile,
            analyzedPool = analyzedPool,
            target = target,
            previous = previous,
            blockSoFar = block.tracks.subList(0, slotIndex),
            excludeIds = excludeIds,
            filters = filters,
            weights = CostWeights.LIVE
        ) ?: return Result.failure(
            BlockGenerator.BuildError.EmptyPool(block.style)
        )

        val newTracks = block.tracks.toMutableList().also { it[slotIndex] = replacement }
        return Result.success(block.copy(tracks = newTracks))
    }

    /**
     * Zmiana kształtu w trakcie — niezagrany ogon wraca do puli (czyścimy
     * `currentQueueTail`), startAnchor liczymy z ostatniego zagranego.
     *
     * Zwraca parę: nowy blok + zaktualizowany stan (z opróżnioną kolejką).
     */
    fun reshape(
        state: PartyState,
        style: Style,
        analyzedPool: List<AnalyzedTrack>,
        newShape: EnergyShape,
        n: Int,
        filters: BlockFilters = BlockFilters()
    ): Result<Pair<Block, PartyState>> {
        val cleanedState = state.copy(currentQueueTail = emptyList(), mode = PartyMode.LIVE)
        val anchor = computeStartAnchor(cleanedState, analyzedPool, style)
        val profile = StyleProfile.forStyle(style)
        return blockGenerator.buildBlock(
            state = cleanedState,
            profile = profile,
            analyzedPool = analyzedPool,
            n = n,
            shape = newShape,
            startAnchor = anchor,
            filters = filters,
            weights = CostWeights.LIVE,
            idHint = "reshape_${System.currentTimeMillis()}"
        ).map { block ->
            block to cleanedState
        }
    }

    /**
     * Przenosi utwór z queueTail do playedTrackIds (faktycznie zagrany).
     * Aktualizuje `lastPlayedIdByStyle` i `usedArtists`.
     */
    fun commitPlayed(state: PartyState, track: AnalyzedTrack): PartyState {
        val id = track.id ?: return state
        val newPlayed = state.playedTrackIds + id
        val newQueue = state.currentQueueTail.filterNot { it == id }
        val newLast = state.lastPlayedIdByStyle.toMutableMap().apply {
            this[track.style] = id
        }
        val newArtists = state.usedArtists.toMutableMap().apply {
            this[track.artist] = (this[track.artist] ?: 0) + 1
        }
        val newSubs = (state.recentSubstyles + track.substyle).takeLast(MAX_RECENT_SUBSTYLES)
        val newElapsed = state.elapsedMs + track.track.durationMs
        return state.copy(
            playedTrackIds = newPlayed,
            currentQueueTail = newQueue,
            lastPlayedIdByStyle = newLast,
            usedArtists = newArtists,
            recentSubstyles = newSubs,
            elapsedMs = newElapsed
        )
    }

    fun softLockSlot(block: Block, slotIndex: Int): Block =
        block.copy(lockedSlots = block.lockedSlots + slotIndex)

    fun softUnlockSlot(block: Block, slotIndex: Int): Block =
        block.copy(lockedSlots = block.lockedSlots - slotIndex)

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun computeStartAnchor(
        state: PartyState,
        analyzedPool: List<AnalyzedTrack>,
        style: Style
    ): Float? {
        val lastId = state.lastPlayedIdByStyle[style] ?: return null
        return analyzedPool.firstOrNull { it.id == lastId }?.energyScore
    }

    companion object {
        private const val MAX_RECENT_SUBSTYLES = 5
    }
}
