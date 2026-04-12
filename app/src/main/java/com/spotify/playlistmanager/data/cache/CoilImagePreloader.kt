package com.spotify.playlistmanager.data.cache

import android.content.Context
import coil.imageLoader
import coil.request.ImageRequest
import com.spotify.playlistmanager.domain.cache.IImagePreloader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja IImagePreloader używająca Coila 2.x.
 *
 * Preloaduje obrazy do cache dyskowego Coila. Po preloadowaniu,
 * AsyncImage w Compose będzie serwować obraz z dysku bez sieci.
 *
 * Błędy pojedynczych obrazów są ciche — runCatching zabezpiecza
 * przed przerwaniem całego procesu offline prep.
 */
@Singleton
class CoilImagePreloader @Inject constructor(
    @ApplicationContext private val context: Context
) : IImagePreloader {

    override suspend fun preload(url: String) {
        runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(coil.request.CachePolicy.DISABLED)
                .build()
            context.imageLoader.execute(request)
        }
    }

    override suspend fun preloadBatch(
        urls: List<String>,
        onProgress: (current: Int, total: Int) -> Unit
    ) {
        val total = urls.size
        urls.forEachIndexed { index, url ->
            preload(url)
            onProgress(index + 1, total)
        }
    }
}
