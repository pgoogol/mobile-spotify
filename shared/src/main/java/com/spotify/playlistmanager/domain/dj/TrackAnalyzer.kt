package com.spotify.playlistmanager.domain.dj

import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.data.model.TrackAudioFeatures
import com.spotify.playlistmanager.domain.dj.model.AnalyzedTrack
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.StyleProfile
import com.spotify.playlistmanager.domain.dj.model.Substyle

/**
 * Analizuje surowe utwory ([Track] + [TrackAudioFeatures]) w pule per styl.
 *
 * Kroki przetwarzania (spec sekcja 3.3, 6):
 *   1. Wykryj styl/podstyl z `audio.genres` ([StyleDetector]).
 *   2. Deduplikuj po `Track.id` (spec mówi po ISRC, ale `Track` go nie ma —
 *      `Track.id` to Spotify track id i jest praktycznie unikalny per utwór).
 *   3. Złóż oktawowo BPM: `bpmFolded = bpm < 100 ? bpm*2 : bpm`.
 *   4. Odrzuć śmieci: `durationMs == 0`, `audio.energy < 1`.
 *   5. Policz percentyle p5/p95 dla `popularity` OSOBNO dla każdego stylu.
 *      Dla `energy`/`valence`/`bpmFolded` używamy clipów ze [StyleProfile]
 *      (są skalibrowane na korpusie referencyjnym).
 *   6. Dla każdego utworu policz `EnergyScore`, `TempoFeeling`, `DanceFlowScore`.
 *   7. Oznacz `passesGate = effectiveDanceability >= profile.danceFloorGate`.
 *
 * UPROSZCZENIE wobec spec §5.2: dla obu stylów `tempoFeeling` liczone z
 * `bpmFolded` (nie z kategorii dla salsy). Patrz KDoc `StyleProfile`.
 */
class TrackAnalyzer(
    private val styleDetector: StyleDetector = StyleDetector()
) {

    private data class StyledCandidate(
        val track: Track,
        val audio: TrackAudioFeatures,
        val style: Style,
        val substyle: com.spotify.playlistmanager.domain.dj.model.Substyle,
        val bpmFolded: Float
    )

    fun analyzePool(
        tracks: List<Track>,
        featuresMap: Map<String, TrackAudioFeatures>
    ): Map<Style, List<AnalyzedTrack>> {
        val seenIds = mutableSetOf<String>()
        val candidates = mutableListOf<StyledCandidate>()
        for (track in tracks) {
            val id = track.id ?: continue
            if (!seenIds.add(id)) continue
            val audio = featuresMap[id] ?: continue
            if (track.durationMs == 0) continue
            if (audio.energy < 1f) continue
            val detection = styleDetector.detect(audio)
            val style = detection.style ?: continue
            candidates += StyledCandidate(
                track = track,
                audio = audio,
                style = style,
                substyle = detection.substyle,
                bpmFolded = foldBpm(audio.bpm)
            )
        }

        val byStyle = candidates.groupBy { it.style }

        val result = mutableMapOf<Style, List<AnalyzedTrack>>()
        for ((style, group) in byStyle) {
            val profile = StyleProfile.forStyle(style)
            val popSorted = group.map { it.track.popularity.toFloat() }.sorted()
            val popLo = Percentiles.percentile(popSorted, 0.05f)
            val popHi = Percentiles.percentile(popSorted, 0.95f)

            result[style] = group.map { c -> c.toAnalyzed(profile, popLo, popHi) }
        }
        return result
    }

    /**
     * Analizuje pulę pod JEDEN, narzucony [style] — wszystkie utwory dostają
     * ten sam profil, niezależnie od wykrytego gatunku, a brak `genres` NIE
     * wyklucza utworu (inaczej niż [analyzePool], które grupuje po gatunku i
     * pomija utwory bez rozpoznanego stylu).
     *
     * Używane gdy źródłem puli jest konkretna playlista wybrana ręcznie przez
     * użytkownika (pula A / pula B na ekranie Krok). Dzięki temu blok/plan
     * czerpie dokładnie z tej playlisty (tanda = playlista), zamiast scalać obie
     * pule i dzielić je z powrotem po gatunku.
     *
     * Podstyl nadal wykrywamy z `genres` (potrzebny do `substyle_pen` w funkcji
     * kosztu), ale gdy wykryty styl nie pasuje do narzuconego — używamy podstylu
     * domyślnego dla [style].
     *
     * Pozostałe kroki (dedup po id, gate śmieci, percentyle popularności,
     * `passesGate`) są identyczne jak w [analyzePool].
     */
    fun analyzePoolForStyle(
        tracks: List<Track>,
        featuresMap: Map<String, TrackAudioFeatures>,
        style: Style
    ): List<AnalyzedTrack> {
        val seenIds = mutableSetOf<String>()
        val candidates = mutableListOf<StyledCandidate>()
        for (track in tracks) {
            val id = track.id ?: continue
            if (!seenIds.add(id)) continue
            val audio = featuresMap[id] ?: continue
            if (track.durationMs == 0) continue
            if (audio.energy < 1f) continue
            val detection = styleDetector.detect(audio)
            val substyle = if (detection.style == style) detection.substyle
            else defaultSubstyleFor(style)
            candidates += StyledCandidate(
                track = track,
                audio = audio,
                style = style,
                substyle = substyle,
                bpmFolded = foldBpm(audio.bpm)
            )
        }
        if (candidates.isEmpty()) return emptyList()

        val profile = StyleProfile.forStyle(style)
        val popSorted = candidates.map { it.track.popularity.toFloat() }.sorted()
        val popLo = Percentiles.percentile(popSorted, 0.05f)
        val popHi = Percentiles.percentile(popSorted, 0.95f)
        return candidates.map { c -> c.toAnalyzed(profile, popLo, popHi) }
    }

    /**
     * Dominujący (najczęstszy) rozpoznany styl puli — po `genres`. Pozwala
     * dobrać właściwy [StyleProfile] dla playlisty wybranej przez użytkownika.
     * Zwraca `null`, gdy żaden utwór nie ma rozpoznawalnego gatunku — wtedy
     * wołający wybiera domyślny styl puli.
     */
    fun dominantStyle(
        tracks: List<Track>,
        featuresMap: Map<String, TrackAudioFeatures>
    ): Style? {
        val counts = mutableMapOf<Style, Int>()
        val seen = mutableSetOf<String>()
        for (track in tracks) {
            val id = track.id ?: continue
            if (!seen.add(id)) continue
            val audio = featuresMap[id] ?: continue
            val style = styleDetector.detect(audio).style ?: continue
            counts[style] = (counts[style] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key
    }

    private fun defaultSubstyleFor(style: Style): Substyle = when (style) {
        Style.SALSA -> Substyle.SALSA_GENERIC
        Style.BACHATA -> Substyle.BACHATA
    }

    private fun StyledCandidate.toAnalyzed(
        profile: StyleProfile,
        popLo: Float,
        popHi: Float
    ): AnalyzedTrack {
        val energyN = Percentiles.normalize(audio.energy, profile.energyClip.lo, profile.energyClip.hi)
        val valenceN = Percentiles.normalize(audio.valence, profile.valenceClip.lo, profile.valenceClip.hi)
        val popularityN = Percentiles.normalize(track.popularity.toFloat(), popLo, popHi)
        val tempoFeeling = Percentiles.normalize(bpmFolded, profile.bpmFoldedClip.lo, profile.bpmFoldedClip.hi)

        val w = profile.energyWeights
        val energyScore = (
            w.energy * energyN +
                w.tempo * tempoFeeling +
                w.valence * valenceN
            ).coerceIn(0f, 1f)

        val effectiveDance = (audio.danceability / 100f).coerceIn(0f, 1f)
        val danceFlowScore = (
            0.50f * effectiveDance +
                0.25f * energyN +
                0.15f * valenceN +
                0.10f * popularityN
            ).coerceIn(0f, 1f)

        return AnalyzedTrack(
            track = track,
            audio = audio,
            style = style,
            substyle = substyle,
            bpmFolded = bpmFolded,
            energyN = energyN,
            valenceN = valenceN,
            popularityN = popularityN,
            tempoFeeling = tempoFeeling,
            energyScore = energyScore,
            danceFlowScore = danceFlowScore,
            passesGate = effectiveDance >= profile.danceFloorGate
        )
    }

    /** Złożenie oktawowe BPM — spec sekcja 3.3. */
    fun foldBpm(bpm: Float): Float = if (bpm in 1f..99.99f) bpm * 2f else bpm
}
