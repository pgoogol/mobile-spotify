package com.spotify.playlistmanager.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack:    () -> Unit,
    onLogout:  () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { viewModel.importCsv(it) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.importMessage) {
        state.importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wstecz"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Konto ────────────────────────────────────────────────────
            SettingsSection(title = "Konto") {
                state.displayName?.let { name ->
                    SettingsInfoRow(
                        icon  = Icons.Default.Person,
                        label = "Zalogowany jako",
                        value = name
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                }
                SettingsActionRow(
                    icon    = Icons.AutoMirrored.Filled.Logout,
                    label   = "Wyloguj",
                    tint    = MaterialTheme.colorScheme.error,
                    onClick = { viewModel.logout(); onLogout() }
                )
            }

            // ── Cache cech audio ─────────────────────────────────────────
            SettingsSection(title = "Cache cech audio") {
                SettingsInfoRow(
                    icon  = Icons.Default.Storage,
                    label = "Wpisy w cache",
                    value = "${state.cacheCount} utworów"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsActionRow(
                    icon     = Icons.Default.FileOpen,
                    label    = "Wczytaj cechy z CSV",
                    sublabel = "Format: Song, Artist, Spotify Track Id, BPM, Dance, Energy",
                    onClick  = { csvLauncher.launch("text/*") }
                )
                if (state.isImporting) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = SpotifyGreen
                    )
                }
                if (state.cacheCount > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    SettingsActionRow(
                        icon    = Icons.Default.DeleteSweep,
                        label   = "Wyczyść cache",
                        tint    = MaterialTheme.colorScheme.error,
                        onClick = viewModel::clearCache
                    )
                }
            }

            // ── O aplikacji ──────────────────────────────────────────────
            SettingsSection(title = "O aplikacji") {
                SettingsInfoRow(
                    icon  = Icons.Default.Info,
                    label = "Wersja",
                    value = "0.2.0"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                SettingsInfoRow(
                    icon  = Icons.Default.Code,
                    label = "API",
                    value = "Spotify Web API + Auth SDK 3.1.0"
                )
            }
        }
    }
}

// ── Komponenty sekcji ────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title:   String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text     = title,
            style    = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color    = SpotifyGreen,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsInfoRow(
    icon:  ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(20.dp)
        )
        Text(
            text     = label,
            modifier = Modifier.weight(1f),
            style    = MaterialTheme.typography.bodyMedium
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon:     ImageVector,
    label:    String,
    sublabel: String? = null,
    tint:     Color   = MaterialTheme.colorScheme.onSurface,
    onClick:  () -> Unit
) {
    Surface(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        color    = Color.Transparent
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = tint,
                modifier           = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tint
                )
                sublabel?.let {
                    Text(
                        text  = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(16.dp)
            )
        }
    }
}
