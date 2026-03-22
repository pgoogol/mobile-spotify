package com.spotify.playlistmanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.playlistmanager.data.model.PlaylistStats
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

@Composable
fun PlaylistStatsCard(
    stats:    PlaylistStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            StatItem(emoji = "🎵", value = "${stats.trackCount}", label = "Utwory")
            StatDivider()
            StatItem(emoji = "⏱",  value = stats.formattedDuration(), label = "Czas")
            stats.avgBpm?.let { bpm ->
                StatDivider()
                StatItem(
                    emoji    = "🥁",
                    value    = "%.0f".format(bpm),
                    label    = "BPM",
                    subLabel = stats.minBpm?.let { mn ->
                        stats.maxBpm?.let { mx -> "${mn.toInt()}–${mx.toInt()}" }
                    }
                )
            }
            stats.avgEnergy?.let { e ->
                StatDivider()
                StatItem(emoji = "⚡", value = "%.2f".format(e), label = "Energia")
            }
            stats.avgDanceability?.let { d ->
                StatDivider()
                StatItem(emoji = "💃", value = "%.2f".format(d), label = "Dance")
            }
        }
    }
}

@Composable
private fun StatItem(
    emoji:    String,
    value:    String,
    label:    String,
    subLabel: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = emoji, fontSize = 18.sp)
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = SpotifyGreen
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        subLabel?.let {
            Text(
                text  = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatDivider() {
    VerticalDivider(
        modifier = Modifier.height(40.dp),
        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}
