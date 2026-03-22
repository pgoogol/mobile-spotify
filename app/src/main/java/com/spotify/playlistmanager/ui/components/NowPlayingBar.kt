package com.spotify.playlistmanager.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.ui.theme.SpotifyGreen
import com.spotify.playlistmanager.ui.theme.SpotifyMidGray
import com.spotify.playlistmanager.util.SpotifyAppRemoteManager

/**
 * Mini odtwarzacz u dołu ekranu.
 * Gdy App Remote SDK nie jest podłączony (stub), pasek jest ukryty.
 */
@Composable
fun NowPlayingBar(
    appRemote:  SpotifyAppRemoteManager,
    currentUri: String?,
    modifier:   Modifier = Modifier
) {
    val connectionState by appRemote.connectionState.collectAsState()
    var trackTitle  by remember { mutableStateOf<String?>(null) }
    var trackArtist by remember { mutableStateOf<String?>(null) }
    var isPlaying   by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState) {
        if (connectionState is SpotifyAppRemoteManager.ConnectionState.Connected) {
            appRemote.subscribeToPlayerState { title, artist ->
                trackTitle  = title
                trackArtist = artist
                isPlaying   = true
            }
        }
    }

    // Pasek widoczny tylko gdy App Remote jest połączony i gra utwór
    AnimatedVisibility(
        visible  = connectionState is SpotifyAppRemoteManager.ConnectionState.Connected
                   && trackTitle != null,
        enter    = slideInVertically { it } + fadeIn(),
        exit     = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier       = Modifier.fillMaxWidth(),
            color          = SpotifyMidGray,
            tonalElevation = 16.dp,
            shape          = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.MusicNote, contentDescription = null,
                    tint     = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = trackTitle ?: "",
                        style    = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text     = trackArtist ?: "",
                        style    = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { appRemote.skipPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, "Poprzedni")
                }
                IconButton(onClick = {
                    if (isPlaying) appRemote.pause() else appRemote.resume()
                    isPlaying = !isPlaying
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pauza" else "Odtwórz",
                        tint     = SpotifyGreen,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { appRemote.skipNext() }) {
                    Icon(Icons.Default.SkipNext, "Następny")
                }
            }
        }
    }
}
