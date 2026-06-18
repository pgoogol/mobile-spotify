package com.spotify.playlistmanager.data.cache

import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.spotify.playlistmanager.domain.cache.IImageCacheCleaner
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementacja IImageCacheCleaner używająca Coila 2.x.
 *
 * Coil utrzymuje globalny ImageLoader przez Coil.imageLoader(context),
 * który ma niezależne MemoryCache (LRU w RAM) i DiskCache (na storage).
 * Obie metody clear() są synchroniczne.
 */
@ExperimentalCoilApi
@Singleton
class CoilImageCacheCleaner @Inject constructor(
    @ApplicationContext private val context: Context
) : IImageCacheCleaner {

    override fun clearMemoryCache() {
        runCatching {
            Coil.imageLoader(context).memoryCache?.clear()
        }
    }

    override fun clearDiskCache() {
        runCatching {
            Coil.imageLoader(context).diskCache?.clear()
        }
    }
}