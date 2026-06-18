package com.spotify.playlistmanager.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spotify.playlistmanager.desktop.theme.SpotifyDesktopTheme
import com.spotify.playlistmanager.desktop.ui.GeneratorScreen

/**
 * Punkt wejścia aplikacji desktopowej.
 *
 * Okno Compose for Desktop hostuje [GeneratorScreen], który napędzany jest
 * algorytmami z modułu :shared (te same, których używa aplikacja Android).
 */
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1180.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Spotify Playlist Manager — Desktop"
    ) {
        SpotifyDesktopTheme {
            GeneratorScreen()
        }
    }
}
