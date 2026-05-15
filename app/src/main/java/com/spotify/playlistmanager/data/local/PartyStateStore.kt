package com.spotify.playlistmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spotify.playlistmanager.domain.dj.IPartyStateStore
import com.spotify.playlistmanager.domain.dj.model.PartyState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.partyStatePrefs: DataStore<Preferences> by preferencesDataStore("party_state")

/**
 * Trwały magazyn `PartyState` jako pojedynczy JSON w DataStore<Preferences>.
 * Klucz: `party_state_json`. Brak klucza = świeża sesja (load → null).
 *
 * Spec sekcja 10: pula w stanie trzymana jako lista `Track.id` — pełne
 * `AnalyzedTrack` są rehydratowane po restarcie przez `TrackAnalyzer.analyzePool`
 * na bieżącym korpusie.
 */
@Singleton
class PartyStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) : IPartyStateStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun load(): PartyState? {
        val raw = context.partyStatePrefs.data.first()[KEY_JSON] ?: return null
        return runCatching { json.decodeFromString(PartyState.serializer(), raw) }.getOrNull()
    }

    override suspend fun save(state: PartyState) {
        val raw = json.encodeToString(PartyState.serializer(), state)
        context.partyStatePrefs.edit { prefs -> prefs[KEY_JSON] = raw }
    }

    override suspend fun clear() {
        context.partyStatePrefs.edit { prefs -> prefs.remove(KEY_JSON) }
    }

    private companion object {
        val KEY_JSON = stringPreferencesKey("party_state_json")
    }
}
