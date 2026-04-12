package com.spotify.playlistmanager.domain.cache

/**
 * Kontrakt preloadowania obrazów do cache.
 *
 * Interfejs mieszka w :shared (warstwa domenowa) — bez zależności od Coil.
 * Implementacja (CoilImagePreloader) mieszka w :app/data/cache.
 *
 * Używany przez [PrepareOfflineUseCase] do pre-fetchowania okładek albumów
 * przed eventem offline.
 */
interface IImagePreloader {

    /**
     * Preloaduje pojedynczy obraz do cache dyskowego.
     * Suspenduje do momentu zakończenia (sukces lub błąd).
     * Nie rzuca wyjątków — ciche ignorowanie błędów.
     */
    suspend fun preload(url: String)

    /**
     * Preloaduje listę obrazów sekwencyjnie.
     *
     * @param urls lista URL-i do preloadowania
     * @param onProgress callback z postępem (current, total) — wywoływany po każdym obrazie
     */
    suspend fun preloadBatch(
        urls: List<String>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    )
}
