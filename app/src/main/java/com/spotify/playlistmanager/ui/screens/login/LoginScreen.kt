package com.spotify.playlistmanager.ui.screens.login

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.playlistmanager.ui.theme.SpotifyBlack
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val authLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleActivityResult(result.resultCode, result.data)
    }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) onLoginSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A2A1A), SpotifyBlack))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp)
        ) {
            PulsingLogo()
            Spacer(Modifier.height(8.dp))
            Text(
                "Spotify\nPlaylist Manager",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold, lineHeight = 42.sp
                ),
                textAlign = TextAlign.Center, color = Color.White
            )
            Text(
                "Zarządzaj playlistami i twórz nowe\nz krzywymi energii dla muzyki latynoskiej",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(uiState is LoginUiState.Error) {
                if (uiState is LoginUiState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            (uiState as LoginUiState.Error).message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Button(
                onClick = {
                    val activity = context as? androidx.activity.ComponentActivity ?: return@Button
                    val request = viewModel.buildAuthRequest()
                    val intent  = AuthorizationClient.createLoginActivityIntent(activity, request)
                    authLauncher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled  = uiState !is LoginUiState.Loading,
                shape    = RoundedCornerShape(28.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen, contentColor = SpotifyBlack
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                if (uiState is LoginUiState.Loading) {
                    CircularProgressIndicator(Modifier.size(24.dp), SpotifyBlack, strokeWidth = 2.5.dp)
                } else {
                    Text("Zaloguj przez Spotify",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
            Text(
                "Wymagana aplikacja Spotify zainstalowana na urządzeniu",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun PulsingLogo() {
    val inf = rememberInfiniteTransition(label = "logo")
    val scale by inf.animateFloat(
        initialValue = 1f, targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = EaseInOutSine), RepeatMode.Reverse
        ), label = "scale"
    )
    Box(
        modifier = Modifier.size(112.dp).scale(scale).clip(CircleShape).background(SpotifyGreen),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, null, tint = SpotifyBlack, modifier = Modifier.size(60.dp))
    }
}
