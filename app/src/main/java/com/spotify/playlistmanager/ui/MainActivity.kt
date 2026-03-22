package com.spotify.playlistmanager.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.spotify.playlistmanager.ui.components.NowPlayingBar
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

                // Połącz App Remote gdy zalogowany
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) mainViewModel.connectAppRemote()
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Główna nawigacja
                        Box(modifier = Modifier.weight(1f)) {
                            AppNavGraph(
                                navController    = navController,
                                startDestination = if (isLoggedIn) Screen.Playlists.route
                                                   else Screen.Login.route
                            )
                        }

                        // Mini odtwarzacz (tylko gdy zalogowany)
                        if (isLoggedIn) {
                            NowPlayingBar(
                                appRemote  = mainViewModel.appRemoteManager,
                                currentUri = null
                            )
                        }
                    }
                }
            }
        }
    }

    /** Callback OAuth – przeglądarka wraca tu przez Intent (launchMode=singleTop) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            loginViewModel.handleAuthCallback(uri)
        }
    }

    override fun onStop() {
        super.onStop()
        // Rozłącz App Remote gdy app idzie w tło (best practice wg Spotify docs)
        mainViewModel.appRemoteManager.disconnect()
    }

    override fun onStart() {
        super.onStart()
        // Ponownie połącz gdy app wraca na pierwszy plan
        if (mainViewModel.isLoggedIn.value) {
            mainViewModel.connectAppRemote()
        }
    }
}
