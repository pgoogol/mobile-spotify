package com.spotify.playlistmanager.domain.cache

/**
 * Kontrakt czyszczenia cache obrazów (okładki playlist, awatary).
 *
 * Implementacja w :app zna Coila; domain jest niezależny od biblioteki obrazów.
 * Używane przez LogoutUseCase i SettingsViewModel.clearPlaylistCache().
 *
 * Operacje są idempotentne — można wołać wielokrotnie bez efektów ubocznych.
 */
interface IImageCacheCleaner {
    /**
     * Czyści cache obrazów w pamięci (RAM).
     * Wywołanie nie blokuje — jest synchroniczne i bardzo szybkie.
     */
    fun clearMemoryCache()

    /**
     * Czyści cache obrazów na dysku.
     * Wywoływane na Dispatchers.IO przez wywołującego, sama operacja
     * nie jest suspend bo Coil API jest synchroniczne.
     */
    fun clearDiskCache()

    /** Skrót: clearMemoryCache + clearDiskCache. */
    fun clearAll() {
        clearMemoryCache()
        clearDiskCache()
    }
}