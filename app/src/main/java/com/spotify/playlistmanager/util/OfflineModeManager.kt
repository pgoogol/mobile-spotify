package com.spotify.playlistmanager.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.spotify.playlistmanager.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.offlineDataStore: DataStore<Preferences> by preferencesDataStore("offline_mode")

/**
 * Singleton zarządzający globalną flagą "tryb offline".
 *
 * Gdy włączony, wszystkie odczyty z repozytorium są przekierowywane do
 * CachePolicy.CACHE_ONLY (zero requestów do Spotify Web API).
 *
 * Konwencja analogiczna do TokenManager:
 *  - reaktywny StateFlow (isEnabled) dla UI,
 *  - synchroniczny snapshot (isEnabledNow) dla repository wykonywanego
 *    w korutynie (uniknięcie suspend-call dla każdego fetcha).
 */
@Singleton
class OfflineModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope
) {
    companion object {
        private val KEY_OFFLINE_MODE = booleanPreferencesKey("offline_mode_enabled")
    }

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    init {
        appScope.launch {
            val initial = context.offlineDataStore.data.first()
            _isEnabled.value = initial[KEY_OFFLINE_MODE] ?: false

            context.offlineDataStore.data.collect { prefs ->
                _isEnabled.value = prefs[KEY_OFFLINE_MODE] ?: false
            }
        }
    }

    /** Synchroniczny snapshot — bezpieczny do odczytu z wątku IO repository. */
    fun isEnabledNow(): Boolean = _isEnabled.value

    suspend fun setEnabled(enabled: Boolean) {
        context.offlineDataStore.edit { it[KEY_OFFLINE_MODE] = enabled }
    }
}
