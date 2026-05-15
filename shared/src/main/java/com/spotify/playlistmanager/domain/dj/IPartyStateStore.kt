package com.spotify.playlistmanager.domain.dj

import com.spotify.playlistmanager.domain.dj.model.PartyState

/**
 * Trwały magazyn stanu sesji DJ-asystenta.
 *
 * Spec sekcja 10: "PartyState musi być zapisywany lokalnie i odtwarzany
 * po restarcie aplikacji. Utrata stanu w środku imprezy = zagrane utwory
 * zaczną wracać."
 *
 * Implementacja (DataStore<Preferences> + JSON) żyje w module `app`,
 * bo wymaga kontekstu Androidowego.
 */
interface IPartyStateStore {
    suspend fun load(): PartyState?
    suspend fun save(state: PartyState)
    suspend fun clear()
}
