package com.spotify.playlistmanager.domain.usecase

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.model.CamelotWheel
import com.spotify.playlistmanager.domain.model.CompositeScoreCalculator
import com.spotify.playlistmanager.domain.model.NextTrackTarget
import com.spotify.playlistmanager.domain.model.ScoreAxis
import com.spotify.playlistmanager.domain.repository.ITrackFeaturesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Sugeruje kolejne utwory do trybu „krok po kroku" (stepwise playlist builder).
 *
 * Dla każdego utworu z puli, poza już użytymi, liczy łączny koszt:
 *   koszt = W_FIT × |score - target|
 *         + W_HARMONIC × (1 - camelot_compat)
 *         + W_BPM_JUMP × normalized_bpm_jump
 *
 * Zwraca top-K kandydatów posortowanych rosnąco po koszcie.
 *
 * Deduplikacja jest twarda: utwór z [alreadyPickedIds] NIE pojawi się w wyniku.
 * Wyjątek to pin/manual — poza scope'em use case'a, obsługa po stronie UI
 * (user świadomie omija algorytm).
 */
@Singleton
class SuggestNextTrackUseCase @Inject constructor(
    private val featuresRepository: ITrackFeaturesRepository
) {

    /** Kandydat z policzonym score i metadanymi do wyświetlenia. */
    data class Candidate(
        val track: Track,
        val score: Float,
        val scoreAxis: ScoreAxis,
        val totalCost: Float,
        /** Dystans do targetu [0..1] — 0 = idealne dopasowanie. */
        val fitDistance: Float,
        /** Kompatybilność harmoniczna z ostatnim [0..1] — 1 = najlepsza. */
        val harmonicCompat: Float,
        /** Różnica BPM vs ostatni utwór (Float.NaN gdy brak kontekstu). */
        val bpmDelta: Float
    )

    /**
     * Wynik obliczenia — lista kandydatów + zrealizowany target.
     *
     * resolvedTarget pokazuje UI jaki dokładnie cel został użyty po
     * rozwiązaniu relatywnych trybów (Warmup/Hold/Chill).
     */
    data class Suggestion(
        val candidates: List<Candidate>,
        val resolvedTargetScore: Float,
        val resolvedAxis: ScoreAxis
    )

    /**
     * @param pool pełna pula utworów (np. playlista salsa)
     * @param alreadyPickedIds utwory już użyte w tej sesji — twardo wykluczane
     * @param lastPickedTrack ostatni wybrany utwór — kontekst dla harmonii/BPM
     * @param target cel (mood)
     * @param currentAxis oś, na której był wybierany ostatni utwór (dla [NextTrackTarget.Hold],
     *        [NextTrackTarget.Warmup], [NextTrackTarget.Chill] i [NextTrackTarget.SwitchAxis])
     * @param k ile kandydatów zwrócić (default 5)
     */
    suspend fun suggest(
        pool: List<Track>,
        alreadyPickedIds: Set<String>,
        lastPickedTrack: Track?,
        target: NextTrackTarget,
        currentAxis: ScoreAxis = ScoreAxis.DANCE,
        k: Int = DEFAULT_K
    ): Suggestion {
        // Hard exclude — dedup
        val candidatesPool = pool.filter { it.id != null && it.id !in alreadyPickedIds }
        if (candidatesPool.isEmpty()) {
            return Suggestion(emptyList(), 0f, currentAxis)
        }

        val allIds = candidatesPool.mapNotNull { it.id } +
                listOfNotNull(lastPickedTrack?.id)
        val featuresMap = featuresRepository.getFeaturesMap(allIds.distinct())

        val lastFeatures = lastPickedTrack?.id?.let { featuresMap[it] }
        val lastScoreOnCurrentAxis = lastFeatures?.let {
            CompositeScoreCalculator.calculate(it, currentAxis)
        }

        val (resolvedScore, resolvedAxis) = resolveTarget(
            target = target,
            currentAxis = currentAxis,
            lastScoreOnCurrentAxis = lastScoreOnCurrentAxis,
            lastFeatures = lastFeatures
        )

        val lastCamelot = lastFeatures?.let { CamelotWheel.parseCamelot(it.camelot) }
        val lastBpm = lastFeatures?.bpm

        val scored = candidatesPool.mapNotNull { track ->
            val id = track.id ?: return@mapNotNull null
            val features = featuresMap[id]
            val score = features?.let { CompositeScoreCalculator.calculate(it, resolvedAxis) }
                ?: CompositeScoreCalculator.DEFAULT_SCORE

            val fitDist = abs(score - resolvedScore).coerceIn(0f, 1f)

            val harmonicCompat = if (features != null && lastCamelot != null) {
                val candidateKey = CamelotWheel.parseCamelot(features.camelot)
                if (candidateKey != null) {
                    CamelotWheel.compatibilityScore(lastCamelot, candidateKey)
                } else {
                    NEUTRAL_HARMONIC_WHEN_UNKNOWN
                }
            } else {
                NEUTRAL_HARMONIC_WHEN_UNKNOWN
            }

            val bpmDelta = if (features != null && lastBpm != null) {
                features.bpm - lastBpm
            } else {
                Float.NaN
            }

            val bpmJumpNorm = if (!bpmDelta.isNaN()) {
                (abs(bpmDelta) / BPM_JUMP_NORMALIZER).coerceIn(0f, 1f)
            } else {
                0f
            }

            val totalCost = W_FIT * fitDist +
                    W_HARMONIC * (1f - harmonicCompat) +
                    W_BPM_JUMP * bpmJumpNorm

            Candidate(
                track = track,
                score = score,
                scoreAxis = resolvedAxis,
                totalCost = totalCost,
                fitDistance = fitDist,
                harmonicCompat = harmonicCompat,
                bpmDelta = bpmDelta
            )
        }

        val top = scored.sortedBy { it.totalCost }.take(k)
        return Suggestion(
            candidates = top,
            resolvedTargetScore = resolvedScore,
            resolvedAxis = resolvedAxis
        )
    }

    /**
     * Rozwiązuje [NextTrackTarget] do konkretnego (score, axis).
     * Visible for testing.
     */
    internal fun resolveTarget(
        target: NextTrackTarget,
        currentAxis: ScoreAxis,
        lastScoreOnCurrentAxis: Float?,
        lastFeatures: TrackAudioFeatures?
    ): Pair<Float, ScoreAxis> = when (target) {
        is NextTrackTarget.Absolute ->
            target.score.coerceIn(0f, 1f) to target.axis

        NextTrackTarget.Peak ->
            NextTrackTarget.PEAK_SCORE to ScoreAxis.DANCE

        NextTrackTarget.Cooldown ->
            NextTrackTarget.COOLDOWN_SCORE to ScoreAxis.MOOD

        NextTrackTarget.Hold -> {
            val base = lastScoreOnCurrentAxis ?: NEUTRAL_WHEN_NO_CONTEXT
            base.coerceIn(0f, 1f) to currentAxis
        }

        NextTrackTarget.Warmup -> {
            val base = lastScoreOnCurrentAxis ?: NEUTRAL_WHEN_NO_CONTEXT
            (base + NextTrackTarget.WARMUP_DELTA).coerceIn(0f, 1f) to currentAxis
        }

        NextTrackTarget.Chill -> {
            val base = lastScoreOnCurrentAxis ?: NEUTRAL_WHEN_NO_CONTEXT
            (base - NextTrackTarget.CHILL_DELTA).coerceIn(0f, 1f) to currentAxis
        }

        NextTrackTarget.SwitchAxis -> {
            val newAxis = when (currentAxis) {
                ScoreAxis.DANCE -> ScoreAxis.MOOD
                ScoreAxis.MOOD -> ScoreAxis.DANCE
            }
            val score = lastFeatures?.let {
                CompositeScoreCalculator.calculate(it, newAxis)
            } ?: NEUTRAL_WHEN_NO_CONTEXT
            score.coerceIn(0f, 1f) to newAxis
        }
    }

    companion object {
        const val DEFAULT_K = 5

        /** Waga dopasowania do targetu (1.0 = najważniejsze). */
        const val W_FIT = 1.0f

        /** Waga kompatybilności harmonicznej. */
        const val W_HARMONIC = 0.4f

        /** Waga kary za skok BPM. */
        const val W_BPM_JUMP = 0.3f

        /** Skok BPM przy którym kara = 1.0 (większy skok tnie do 1). */
        const val BPM_JUMP_NORMALIZER = 30f

        /** Używane gdy brak ostatniego utworu — neutralny cel. */
        const val NEUTRAL_WHEN_NO_CONTEXT = 0.5f

        /** Używane gdy brak Camelot key — neutralna kompatybilność. */
        const val NEUTRAL_HARMONIC_WHEN_UNKNOWN = 0.5f
    }
}
