package com.spotify.playlistmanager.domain.dj.model

import kotlinx.serialization.Serializable

/**
 * Tryb pracy sesji.
 */
@Serializable
enum class PartyMode { PLANNING, LIVE }

/**
 * Trwały stan sesji — spec sekcja 10. Musi przeżyć restart aplikacji.
 *
 * Optymalizacja serializacji: pule trzymamy jako listę `spotifyTrackId`,
 * nie pełne `AnalyzedTrack` (kilkaset utworów × ~30 pól byłoby kilka MB).
 * Pula rehydratuje się po restarcie przez `TrackAnalyzer.analyzePool` na
 * aktualnym korpusie — zachowane ID nadal blokują utwory z `playedTrackIds`.
 *
 * UWAGA — różnica wobec spec §10:
 * - `playedTrackIds` (set spotify track id) zamiast set<isrc>. Powód: `Track`
 *   w tym projekcie nie ma ISRC; ISRC jest tylko w `TrackAudioFeatures`.
 *   Dedup po `Track.id` jest praktycznie równoważny dla biblioteki użytkownika
 *   (jeden Spotify track id ≈ jeden ISRC z perspektywy puli).
 */
@Serializable
data class PartyState(
    val mode: PartyMode = PartyMode.PLANNING,

    /** Lista ID utworów per styl — po fold/dedup/gate. */
    val poolIdsByStyle: Map<Style, List<String>> = emptyMap(),

    /** Faktycznie zagrane (commitPlayed) — blokują utwór na stałe w tej sesji. */
    val playedTrackIds: Set<String> = emptySet(),

    /** Ostatnio zagrany utwór per styl — do `startAnchor` w Live. */
    val lastPlayedIdByStyle: Map<Style, String?> = emptyMap(),

    /** W kolejce, ale jeszcze nie zagrane (reshape je oddaje do puli). */
    val currentQueueTail: List<String> = emptyList(),

    /** Bieżąca faza imprezy (Planning tylko). */
    val currentPhase: Phase? = null,

    /** Licznik użycia artystów w sesji — do `artist_pen` w funkcji kosztu. */
    val usedArtists: Map<String, Int> = emptyMap(),

    /** Ostatnie N użytych podstylów — do `substyle_pen`. */
    val recentSubstyles: List<Substyle> = emptyList(),

    val elapsedMs: Long = 0L,
    val plannedMs: Long = 0L,

    val dancefloorState: DancefloorState? = null
)
