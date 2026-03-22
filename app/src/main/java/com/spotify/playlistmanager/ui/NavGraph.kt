package com.spotify.playlistmanager.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.spotify.playlistmanager.ui.screens.generate.GenerateScreen
import com.spotify.playlistmanager.ui.screens.login.LoginScreen
import com.spotify.playlistmanager.ui.screens.playlists.PlaylistsScreen
import com.spotify.playlistmanager.ui.screens.settings.SettingsScreen
import com.spotify.playlistmanager.ui.screens.tracks.TracksScreen

sealed class Screen(val route: String) {
    data object Login     : Screen("login")
    data object Playlists : Screen("playlists")
    data object Tracks    : Screen("tracks/{playlistId}/{playlistName}") {
        fun createRoute(id: String, name: String) =
            "tracks/${java.net.URLEncoder.encode(id, "UTF-8")}/${java.net.URLEncoder.encode(name, "UTF-8")}"
    }
    data object Generate  : Screen("generate")
    data object Settings  : Screen("settings")
}

@Composable
fun AppNavGraph(
    navController:    NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(Screen.Playlists.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Playlists.route) {
            PlaylistsScreen(
                onPlaylistClick = { id, name ->
                    navController.navigate(Screen.Tracks.createRoute(id, name))
                },
                onGenerateClick = { navController.navigate(Screen.Generate.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Tracks.route) { back ->
            val id   = back.arguments?.getString("playlistId")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: return@composable
            val name = back.arguments?.getString("playlistName")
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: ""
            TracksScreen(
                playlistId   = id,
                playlistName = name,
                onBack       = { navController.popBackStack() }
            )
        }

        composable(Screen.Generate.route) {
            GenerateScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack   = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
