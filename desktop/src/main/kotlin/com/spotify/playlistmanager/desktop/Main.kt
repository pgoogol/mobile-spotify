package com.spotify.playlistmanager.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.spotify.playlistmanager.desktop.theme.SpotifyDesktopTheme
import com.spotify.playlistmanager.desktop.ui.App

/**
 * Punkt wejścia aplikacji desktopowej.
 *
 * Okno Compose for Desktop hostuje [App], który routuje między logowaniem,
 * listą playlist (prawdziwe dane ze Spotify) i generatorem krzywych energii.
 * Logika domenowa pochodzi z modułu :shared (wspólna z aplikacją Android).
 */
fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1180.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "Spotify Playlist Manager — Desktop"
    ) {
        SpotifyDesktopTheme {
            App()
        }
    }
}
