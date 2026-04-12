package com.spotify.playlistmanager.ui.screens.generate

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

/**
 * Tryb filtra: whitelist (uwzględnij tylko) lub blacklist (wyklucz).
 */
enum class FilterMode(val label: String) {
    INCLUDE("Uwzględnij tylko"),
    EXCLUDE("Wyklucz")
}

/**
 * Dialog multi-select do filtrowania gatunków lub wytwórni.
 *
 * Pokazuje listę dostępnych wartości z checkboxami.
 * Użytkownik wybiera tryb (include/exclude) i zaznacza elementy.
 * Zatwierdzenie propaguje wynik do PlaylistSource przez callback.
 *
 * @param title tytuł dialogu (np. "Filtruj gatunki")
 * @param availableItems lista dostępnych gatunków/wytwórni do wyboru
 * @param currentInclude aktualny whitelist (może być pusty)
 * @param currentExclude aktualny blacklist (może być pusty)
 * @param onConfirm callback z wynikiem: (includeSet, excludeSet)
 * @param onDismiss zamknięcie dialogu
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenreFilterDialog(
    title: String,
    availableItems: List<String>,
    currentInclude: Set<String>,
    currentExclude: Set<String>,
    onConfirm: (include: Set<String>, exclude: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // Inicjalizuj tryb na podstawie aktualnego stanu
    var mode by remember {
        mutableStateOf(
            when {
                currentExclude.isNotEmpty() -> FilterMode.EXCLUDE
                currentInclude.isNotEmpty() -> FilterMode.INCLUDE
                else -> FilterMode.INCLUDE
            }
        )
    }

    // Zaznaczone elementy — zaczynamy od aktualnych wartości
    var selected by remember {
        mutableStateOf(
            when {
                currentExclude.isNotEmpty() -> currentExclude
                currentInclude.isNotEmpty() -> currentInclude
                else -> emptySet()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FilterList, null, tint = SpotifyGreen)
                Text(title)
            }
        },
        text = {
            Column {
                // ── Przełącznik trybu ────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterMode.entries.forEach { filterMode ->
                        FilterChip(
                            selected = mode == filterMode,
                            onClick = {
                                mode = filterMode
                                // Resetuj zaznaczenie przy zmianie trybu
                                selected = emptySet()
                            },
                            label = { Text(filterMode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotifyGreen,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ── Info ─────────────────────────────────────────────────
                Text(
                    text = when (mode) {
                        FilterMode.INCLUDE -> "Zostaną użyte TYLKO zaznaczone"
                        FilterMode.EXCLUDE -> "Zaznaczone zostaną WYKLUCZONE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(Modifier.height(4.dp))

                // ── Lista z checkboxami ──────────────────────────────────
                if (availableItems.isEmpty()) {
                    Text(
                        "Brak danych — zaimportuj cechy audio z CSV",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    // Quick actions
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        TextButton(onClick = { selected = availableItems.toSet() }) {
                            Text("Zaznacz wszystko", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { selected = emptySet() }) {
                            Text("Odznacz wszystko", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(availableItems) { item ->
                            val isSelected = item in selected
                            Surface(
                                onClick = {
                                    selected = if (isSelected) selected - item
                                    else selected + item
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected)
                                    SpotifyGreen.copy(alpha = 0.10f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selected = if (isSelected) selected - item
                                            else selected + item
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = SpotifyGreen
                                        )
                                    )
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.SemiBold
                                        else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Podsumowanie zaznaczenia ─────────────────────────────
                if (selected.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selected.sorted().forEach { item ->
                            AssistChip(
                                onClick = { selected = selected - item },
                                label = {
                                    Text(item, style = MaterialTheme.typography.labelSmall)
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close, null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = SpotifyGreen.copy(alpha = 0.15f)
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val include = if (mode == FilterMode.INCLUDE) selected else emptySet()
                    val exclude = if (mode == FilterMode.EXCLUDE) selected else emptySet()
                    onConfirm(include, exclude)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                Text(
                    if (selected.isEmpty()) "Wyczyść filtr"
                    else "${mode.label}: ${selected.size}",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}

/**
 * Kompaktowy chip wyświetlający aktywny filtr gatunków/wytwórni.
 * Kliknięcie otwiera GenreFilterDialog.
 *
 * @param label etykieta (np. "Gatunki", "Wytwórnie")
 * @param includeCount liczba elementów w whitelist
 * @param excludeCount liczba elementów w blacklist
 * @param onClick callback otwierający dialog
 */
@Composable
fun FilterChipSummary(
    label: String,
    includeCount: Int,
    excludeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasFilter = includeCount > 0 || excludeCount > 0
    val summary = when {
        includeCount > 0 -> "+$includeCount"
        excludeCount > 0 -> "-$excludeCount"
        else -> ""
    }

    FilterChip(
        selected = hasFilter,
        onClick = onClick,
        label = {
            Text(
                if (hasFilter) "$label ($summary)" else label,
                style = MaterialTheme.typography.labelSmall
            )
        },
        leadingIcon = {
            Icon(
                if (hasFilter) Icons.Default.FilterList else Icons.Default.MusicNote,
                null,
                modifier = Modifier.size(16.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SpotifyGreen.copy(alpha = 0.15f),
            selectedLabelColor = SpotifyGreen
        ),
        modifier = modifier
    )
}
