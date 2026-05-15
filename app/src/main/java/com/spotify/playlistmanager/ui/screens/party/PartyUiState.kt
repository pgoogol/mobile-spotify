package com.spotify.playlistmanager.ui.screens.party

import com.spotify.playlistmanager.data.model.Playlist
import com.spotify.playlistmanager.domain.dj.model.AnalyzedTrack
import com.spotify.playlistmanager.domain.dj.model.Block
import com.spotify.playlistmanager.domain.dj.model.EnergyArc
import com.spotify.playlistmanager.domain.dj.model.EnergyShape
import com.spotify.playlistmanager.domain.dj.model.PartyMode
import com.spotify.playlistmanager.domain.dj.model.PartyState
import com.spotify.playlistmanager.domain.dj.model.Phase
import com.spotify.playlistmanager.domain.dj.model.Preset
import com.spotify.playlistmanager.domain.dj.model.Style
import com.spotify.playlistmanager.domain.dj.model.StyleRatio
import com.spotify.playlistmanager.domain.dj.model.SubstyleStrategy

/**
 * Stan zapisu playlisty do Spotify — kopia z `StepwiseViewModel.SaveState`.
 */
sealed class SaveState {
    data object Idle : SaveState()
    data object Saving : SaveState()
    data class Success(val playlistUrl: String, val trackCount: Int) : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * Konfiguracja trybu append — kopia z `StepwiseViewModel.AppendMode`.
 */
data class AppendMode(
    val playlistId: String,
    val playlistName: String,
    val originalTrackCount: Int
)

/**
 * Stan ekranu generatora "Impreza DJ".
 *
 * Pula utworów: `analyzedByStyle` — utwory już przeanalizowane przez
 * [TrackAnalyzer], grupowane wg stylu. Liczone raz przy `loadCorpus`,
 * rehydratowane z [PartyState.poolIdsByStyle] przy restarcie aplikacji.
 *
 * Bloki: `blocks` — lista bloków utworzonych w bieżącej sesji.
 * W trybie Planning: cały plan. W Live: rosnąca lista bloków na żądanie.
 */
data class PartyUiState(
    // ── Bootstrap ──────────────────────────────────────────────────────
    val isLoadingPlaylists: Boolean = true,
    val isAnalyzingCorpus: Boolean = false,
    val corpusLoaded: Boolean = false,
    val availablePlaylists: List<Playlist> = emptyList(),
    val analyzedByStyle: Map<Style, List<AnalyzedTrack>> = emptyMap(),

    // ── Stan sesji (trwały) ────────────────────────────────────────────
    val partyState: PartyState = PartyState(),

    // ── Bloki (tryb Planning ma cały plan; Live rosnący) ──────────────
    val blocks: List<Block> = emptyList(),
    /** Faza dla każdego bloku w `blocks` (równa długość). Tylko Planning. */
    val phaseByBlock: List<Phase> = emptyList(),

    // ── Wejścia Planning ────────────────────────────────────────────
    val planDurationMs: Long = 90 * 60_000L,
    val styleRatio: StyleRatio = StyleRatio(salsaPercent = 60),
    val energyArc: EnergyArc = EnergyArc.CLASSIC,
    val substyleStrategy: SubstyleStrategy = SubstyleStrategy.CLASSIC,
    val planBlockSize: Int = 5,
    val isGeneratingPlan: Boolean = false,

    // ── Wejścia Live ───────────────────────────────────────────────
    val liveSelectedStyle: Style = Style.SALSA,
    val livePreset: Preset = Preset.HOLD,
    val liveBlockSize: Int = 5,
    val liveShape: EnergyShape = EnergyShape.Wave(),
    val isGeneratingLiveBlock: Boolean = false,

    // ── Zapis do Spotify ──────────────────────────────────────────
    val newPlaylistName: String = "Impreza DJ",
    val newPlaylistDescription: String = "",
    val saveState: SaveState = SaveState.Idle,
    val appendMode: AppendMode? = null,
    val isLoadingAppendAnchors: Boolean = false,

    val error: String? = null
) {
    val mode: PartyMode get() = partyState.mode

    /** Tracki, które trafią do save (płaska lista, kolejność = kolejność bloków). */
    val allTracks: List<AnalyzedTrack>
        get() = blocks.flatMap { it.tracks }

    /** Można zapisać, jeśli są jakieś bloki i nie jesteśmy w trakcie zapisu. */
    val canSave: Boolean
        get() = allTracks.isNotEmpty() && saveState !is SaveState.Saving

    /** Statystyki puli per styl — do banneru po analizie. */
    val poolSizes: Map<Style, Int>
        get() = analyzedByStyle.mapValues { (_, list) -> list.count { it.passesGate } }
}
