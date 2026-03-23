package com.spotify.playlistmanager.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.spotify.playlistmanager.ui.screens.login.LoginViewModel
import com.spotify.playlistmanager.ui.theme.SpotifyPlaylistManagerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val loginViewModel: LoginViewModel by viewModels()
    private val mainViewModel:  MainViewModel  by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SpotifyPlaylistManagerTheme {
                val navController = rememberNavController()
                val isLoggedIn by mainViewModel.isLoggedIn.collectAsStateWithLifecycle()

                // Obsługa wygasłego tokena (401) — ViewModel eksponuje sessionExpired
                LaunchedEffect(Unit) {
                    mainViewModel.sessionExpired.collect {
                        mainViewModel.forceLogout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) mainViewModel.onAppForeground()
                }

                AppScaffold(
                    navController    = navController,
                    startDestination = if (isLoggedIn) Screen.Playlists.route
                    else Screen.Login.route
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            loginViewModel.handleAuthCallback(uri)
        }
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        mainViewModel.onAppBackground()
    }
}