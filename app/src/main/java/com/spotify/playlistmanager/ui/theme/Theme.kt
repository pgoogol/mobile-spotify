package com.spotify.playlistmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Spotify brand colors ───────────────────────────────────────────────────
val SpotifyGreen       = Color(0xFF1DB954)
val SpotifyGreenDark   = Color(0xFF158A3E)
val SpotifyBlack       = Color(0xFF121212)
val SpotifyDarkGray    = Color(0xFF181818)
val SpotifyMidGray     = Color(0xFF282828)
val SpotifyLightGray   = Color(0xFFB3B3B3)
val SpotifyWhite       = Color(0xFFFFFFFF)
val ErrorRed           = Color(0xFFCF6679)

// ── Dark color scheme (domyślny – Spotify jest zawsze ciemny) ─────────────
private val DarkColorScheme = darkColorScheme(
    primary          = SpotifyGreen,
    onPrimary        = SpotifyBlack,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = SpotifyWhite,
    secondary        = SpotifyLightGray,
    onSecondary      = SpotifyBlack,
    background       = SpotifyBlack,
    onBackground     = SpotifyWhite,
    surface          = SpotifyDarkGray,
    onSurface        = SpotifyWhite,
    surfaceVariant   = SpotifyMidGray,
    onSurfaceVariant = SpotifyLightGray,
    error            = ErrorRed,
    onError          = SpotifyBlack,
    outline          = SpotifyMidGray
)

// ── Light color scheme (fallback) ─────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary          = SpotifyGreenDark,
    onPrimary        = SpotifyWhite,
    primaryContainer = SpotifyGreen,
    onPrimaryContainer = SpotifyBlack,
    secondary        = Color(0xFF535353),
    onSecondary      = SpotifyWhite,
    background       = Color(0xFFF5F5F5),
    onBackground     = SpotifyBlack,
    surface          = SpotifyWhite,
    onSurface        = SpotifyBlack,
    surfaceVariant   = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF535353),
    error            = ErrorRed,
    outline          = Color(0xFFCCCCCC)
)

@Composable
fun SpotifyPlaylistManagerTheme(
    darkTheme: Boolean = true,   // Spotify zawsze dark – wymuszamy domyślnie
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography(),
        content     = content
    )
}
