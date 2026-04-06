package com.spotify.playlistmanager.domain.repository

/**
 * Strategia używania cache przy odczycie danych przez SpotifyRepository.
 *
 * Domyślna wartość to CACHE_FIRST — używane wszędzie poza pull-to-refresh
 * i ekranami offline.
 */
enum class CachePolicy {
    /**
     * Użyj cache jeśli jest świeży (wg TTL lub snapshot_id).
     * Inaczej pobierz z sieci i zaktualizuj cache.
     * To jest wartość domyślna dla wszystkich wywołań.
     */
    CACHE_FIRST,

    /**
     * Pomiń cache, wymuś request do API. Wynik trafia do cache.
     * Używane przy pull-to-refresh i po inwalidacji.
     */
    NETWORK_ONLY,

    /**
     * Tylko cache — nigdy nie sięgaj do sieci.
     * Używane do natychmiastowego pokazania danych w stale-while-revalidate
     * (potem osobny request odświeża w tle).
     */
    CACHE_ONLY
}