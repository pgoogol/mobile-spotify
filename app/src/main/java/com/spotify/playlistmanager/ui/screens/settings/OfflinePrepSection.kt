package com.spotify.playlistmanager.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.domain.usecase.PrepareOfflineUseCase.OfflineProgress
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

/**
 * Sekcja "Przygotuj na event" w ekranie Ustawień.
 *
 * Pokazuje przycisk startowy, a po uruchomieniu — pasek postępu z fazą,
 * aktualnie przetwarzaną playlistą, i ewentualnymi błędami.
 *
 * @param progress aktualny postęp (null = nie uruchomiono)
 * @param onPrepareAll callback: preload wszystkich playlist
 * @param onCancel callback: anuluj (opcjonalny, ViewModel canceluje Job)
 */
@Composable
fun OfflinePrepSection(
    progress: OfflineProgress?,
    onPrepareAll: () -> Unit,
    onCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Nagłówek sekcji (styl jak SettingsSection)
        Text(
            text = "Tryb offline",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = SpotifyGreen,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Opis ─────────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.OfflinePin,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Pobierz playlisty, utwory i okładki przed eventem, " +
                                "żeby aplikacja działała bez internetu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ── Przycisk start / stan postępu ────────────────────────
                when {
                    progress == null || progress.phase == OfflineProgress.Phase.ERROR -> {
                        // Idle lub Error — pokaż przycisk
                        Button(
                            onClick = onPrepareAll,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Przygotuj na event",
                                modifier = Modifier.padding(start = 8.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Komunikat błędu z poprzedniego uruchomienia
                        if (progress?.phase == OfflineProgress.Phase.ERROR) {
                            ErrorsList(progress.errors)
                        }
                    }

                    progress.phase == OfflineProgress.Phase.DONE -> {
                        // Zakończono
                        DoneIndicator(progress)
                    }

                    else -> {
                        // W trakcie
                        ProgressIndicator(progress)

                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Anuluj")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressIndicator(progress: OfflineProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Etykieta fazy
        Text(
            text = when (progress.phase) {
                OfflineProgress.Phase.PLAYLISTS -> "Pobieranie listy playlist..."
                OfflineProgress.Phase.TRACKS -> "Pobieranie utworów..."
                OfflineProgress.Phase.IMAGES -> "Pobieranie okładek..."
                else -> "Przetwarzanie..."
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        )

        // Nazwa aktualnej playlisty
        progress.currentPlaylistName?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Pasek postępu
        if (progress.total > 0) {
            val fraction = progress.current.toFloat() / progress.total
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = SpotifyGreen,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Text(
                text = "${progress.current} / ${progress.total}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = SpotifyGreen,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
        }
    }
}

@Composable
private fun DoneIndicator(progress: OfflineProgress) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Check,
                null,
                tint = SpotifyGreen,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Gotowe! Pobrano ${progress.total} playlist.",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = SpotifyGreen
            )
        }

        // Błędy (jeśli niektóre playlisty się nie udały)
        if (progress.errors.isNotEmpty()) {
            ErrorsList(progress.errors)
        }
    }
}

@Composable
private fun ErrorsList(errors: List<String>) {
    AnimatedVisibility(visible = errors.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Error,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Problemy (${errors.size}):",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
            }
            errors.take(5).forEach { error ->
                Text(
                    text = "• $error",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (errors.size > 5) {
                Text(
                    text = "… i ${errors.size - 5} więcej",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
