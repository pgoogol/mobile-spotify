package com.spotify.playlistmanager.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spotify.playlistmanager.domain.usecase.SuggestNextTrackUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.stepwisePrefs: DataStore<Preferences> by preferencesDataStore("stepwise_prefs")

/**
 * Przechowuje globalne wagi algorytmu doboru następnego utworu.
 *
 * Wagi są "kalibracją stylu DJ" — user dostraja raz i zostaje między sesjami.
 * Zakres każdej wagi: 0.0..2.0.
 */
@Singleton
class StepwisePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_W_FIT = floatPreferencesKey("w_fit")
        private val KEY_W_HARMONIC = floatPreferencesKey("w_harmonic")
        private val KEY_W_BPM_JUMP = floatPreferencesKey("w_bpm_jump")

        const val MIN_WEIGHT = 0f
        const val MAX_WEIGHT = 2f
    }

    /** Emituje aktualne wagi (z domyślnymi gdy brak). */
    val weightsFlow: Flow<SuggestNextTrackUseCase.Weights> =
        context.stepwisePrefs.data.map { prefs ->
            SuggestNextTrackUseCase.Weights(
                wFit = prefs[KEY_W_FIT] ?: SuggestNextTrackUseCase.W_FIT,
                wHarmonic = prefs[KEY_W_HARMONIC] ?: SuggestNextTrackUseCase.W_HARMONIC,
                wBpmJump = prefs[KEY_W_BPM_JUMP] ?: SuggestNextTrackUseCase.W_BPM_JUMP
            )
        }

    suspend fun setWeights(weights: SuggestNextTrackUseCase.Weights) {
        context.stepwisePrefs.edit { prefs ->
            prefs[KEY_W_FIT] = weights.wFit.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
            prefs[KEY_W_HARMONIC] = weights.wHarmonic.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
            prefs[KEY_W_BPM_JUMP] = weights.wBpmJump.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        }
    }

    suspend fun resetToDefaults() {
        context.stepwisePrefs.edit { prefs ->
            prefs.remove(KEY_W_FIT)
            prefs.remove(KEY_W_HARMONIC)
            prefs.remove(KEY_W_BPM_JUMP)
        }
    }
}
