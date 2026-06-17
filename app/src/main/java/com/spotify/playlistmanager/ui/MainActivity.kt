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

        // Zimny start z deep-linku OAuth (proces mógł zginąć podczas Custom Tab) —
        // przekaż redirect do tej samej instancji ViewModelu co ekran logowania.
        // Tylko przy świeżym utworzeniu — żeby nie przetwarzać kodu ponownie po obrocie.
        if (savedInstanceState == null) {
            intent?.data?.let { uri -> loginViewModel.handleAuthCallback(uri) }
        }

        setContent {
            SpotifyPlaylistManagerTheme {
                val navController = rememberNavController()
                val isLoggedIn by mainViewModel.isLoggedIn.collectAsStateWithLifecycle()

                // Wyczyść sesję po 401, którego nie dało się odświeżyć.
                // Nawigacja do logowania nastąpi przez obserwację isLoggedIn poniżej.
                LaunchedEffect(Unit) {
                    mainViewModel.sessionExpired.collect {
                        mainViewModel.forceLogout()
                    }
                }

                // Jedno źródło prawdy dla nawigacji logowanie <-> aplikacja.
                // Po udanej wymianie tokenów isLoggedIn = true → wejście do aplikacji;
                // po wylogowaniu/utracie sesji → powrót na ekran logowania.
                LaunchedEffect(isLoggedIn) {
                    val currentRoute = navController.currentDestination?.route
                    if (isLoggedIn) {
                        mainViewModel.onAppForeground()
                        if (currentRoute == Screen.Login.route) {
                            navController.navigate(Screen.Playlists.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        }
                    } else if (currentRoute != null && currentRoute != Screen.Login.route) {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
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