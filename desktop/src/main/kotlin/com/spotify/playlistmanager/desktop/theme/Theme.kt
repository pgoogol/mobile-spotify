package com.spotify.playlistmanager.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Spotify green — akcent marki. */
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyAmber = Color(0xFFFFA726)

private val SpotifyDarkColors = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    secondary = SpotifyGreen,
    background = Color(0xFF121212),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFB3B3B3),
    error = Color(0xFFCF6679),
)

/**
 * Ciemny motyw Spotify dla wersji desktopowej (Material3).
 * Odpowiednik motywu z modułu :app, przeniesiony na Compose Multiplatform.
 */
@Composable
fun SpotifyDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpotifyDarkColors,
        content = content
    )
}
