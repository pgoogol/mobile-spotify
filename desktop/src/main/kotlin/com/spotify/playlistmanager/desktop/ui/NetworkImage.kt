package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.util.concurrent.ConcurrentHashMap

/**
 * Asynchroniczne ładowanie okładek po URL — desktopowy odpowiednik Coil
 * `AsyncImage` z aplikacji mobilnej. Dekoduje bajty przez Skia i cache'uje
 * w pamięci. Bez nowych zależności (OkHttp + Skiko z Compose Desktop).
 */
@Composable
fun NetworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit = {},
) {
    if (url.isNullOrBlank()) {
        fallback()
        return
    }
    var bitmap by remember(url) { mutableStateOf(ImageCache.cached(url)) }
    LaunchedEffect(url) {
        if (bitmap == null) bitmap = ImageCache.load(url)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else {
        fallback()
    }
}

private object ImageCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val client = OkHttpClient()

    fun cached(url: String): ImageBitmap? = cache[url]

    suspend fun load(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    val bytes = resp.body?.bytes() ?: return@runCatching null
                    val img = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                    cache[url] = img
                    img
                }
            }.getOrNull()
        }
    }
}
