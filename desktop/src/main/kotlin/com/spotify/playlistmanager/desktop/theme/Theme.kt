package com.spotify.playlistmanager.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Spotify brand colors (1:1 z aplikacją mobilną) ──────────────────────────
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyGreenDark = Color(0xFF158A3E)
val SpotifyBlack = Color(0xFF121212)
val SpotifyDarkGray = Color(0xFF181818)
val SpotifyMidGray = Color(0xFF282828)
val SpotifyLightGray = Color(0xFFB3B3B3)
val SpotifyWhite = Color(0xFFFFFFFF)
val ErrorRed = Color(0xFFCF6679)

/** Bursztyn — krzywa „rzeczywista" na wykresie energii (jak na Androidzie). */
val SpotifyAmber = Color(0xFFFFA726)

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = SpotifyBlack,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = SpotifyWhite,
    secondary = SpotifyLightGray,
    onSecondary = SpotifyBlack,
    background = SpotifyBlack,
    onBackground = SpotifyWhite,
    surface = SpotifyDarkGray,
    onSurface = SpotifyWhite,
    surfaceVariant = SpotifyMidGray,
    onSurfaceVariant = SpotifyLightGray,
    error = ErrorRed,
    onError = SpotifyBlack,
    outline = SpotifyMidGray,
)

/** Ciemny motyw Spotify — odpowiednik `SpotifyPlaylistManagerTheme` z `:app`. */
@Composable
fun SpotifyDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
