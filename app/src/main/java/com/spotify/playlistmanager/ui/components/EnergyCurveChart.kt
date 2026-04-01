package com.spotify.playlistmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.domain.model.MatchedTrack
import com.spotify.playlistmanager.domain.model.SegmentMatchResult
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

private val TargetColor = SpotifyGreen
private val ActualColor = Color(0xFFFFA726) // Bursztynowy
private val SegmentBorderColor = Color.White.copy(alpha = 0.3f)
private val ChipGreen = Color(0xFF4CAF50)
private val ChipYellow = Color(0xFFFFC107)
private val ChipRed = Color(0xFFF44336)

/**
 * Wykres krzywej energii po wygenerowaniu.
 *
 * - Zielona linia ciągła — docelowa krzywa (target)
 * - Bursztynowa linia przerywana — rzeczywiste score'y
 * - Pionowe przerywane — granice między segmentami
 * - Tap na punkt → tooltip: Tytuł · Artysta
 * - Chip zgodności: 🟢 ≥80%, 🟡 60–79%, 🔴 <60%
 */
@Composable
fun EnergyCurveChart(
    segments: List<SegmentMatchResult>,
    overallMatchPercentage: Float,
    isDryRun: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Zbierz wszystkie punkty
    val allTargets = segments.flatMap { it.targetScores }
    val allMatched = segments.flatMap { it.tracks }
    val segmentBoundaries = buildSegmentBoundaries(segments)

    var tooltipTrack by remember { mutableStateOf<MatchedTrack?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Chip zgodności
        if (!isDryRun && allTargets.isNotEmpty()) {
            MatchPercentageChip(overallMatchPercentage)
            Spacer(Modifier.height(4.dp))
        }

        // Canvas
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(allMatched) {
                        if (isDryRun || allMatched.isEmpty()) return@pointerInput
                        detectTapGestures { tapOffset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val padding = 16f
                            val totalPoints = allMatched.size
                            if (totalPoints == 0) return@detectTapGestures

                            // Znajdź najbliższy punkt
                            var closest: MatchedTrack? = null
                            var minDist = Float.MAX_VALUE
                            allMatched.forEachIndexed { i, mt ->
                                val x = padding + (w - 2 * padding) * i / (totalPoints - 1).coerceAtLeast(1)
                                val y = h - padding - (h - 2 * padding) * mt.compositeScore
                                val dist = kotlin.math.hypot((tapOffset.x - x).toDouble(), (tapOffset.y - y).toDouble()).toFloat()
                                if (dist < minDist && dist < 40f) {
                                    minDist = dist
                                    closest = mt
                                }
                            }
                            tooltipTrack = closest
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val padding = 16f

                // Rysuj granice segmentów
                drawSegmentBoundaries(segmentBoundaries, allTargets.size, w, h, padding)

                // Rysuj target (zielona ciągła)
                if (allTargets.isNotEmpty()) {
                    drawCurveLine(allTargets, w, h, padding, TargetColor, dashed = false)
                }

                // Rysuj actual (bursztynowa przerywana) — tylko gdy nie dry-run
                if (!isDryRun && allMatched.isNotEmpty()) {
                    drawCurveLine(
                        allMatched.map { it.compositeScore },
                        w, h, padding, ActualColor, dashed = true
                    )
                }
            }

            // Tooltip
            tooltipTrack?.let { mt ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
                ) {
                    Text(
                        "${mt.track.title} · ${mt.track.artist}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1
                    )
                }
            }
        }

        // Legenda
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem("Cel", TargetColor, dashed = false)
            if (!isDryRun) {
                LegendItem("Rzeczywisty", ActualColor, dashed = true)
            }
        }
    }
}

// ── Chip dopasowania ─────────────────────────────────────────────────────────

@Composable
private fun MatchPercentageChip(percentage: Float) {
    val pct = (percentage * 100).toInt()
    val (emoji, chipColor) = when {
        pct >= 80 -> "🟢" to ChipGreen
        pct >= 60 -> "🟡" to ChipYellow
        else      -> "🔴" to ChipRed
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = chipColor.copy(alpha = 0.15f),
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            "$emoji Dopasowanie: $pct%",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = chipColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── Helpery rysowania ────────────────────────────────────────────────────────

private fun DrawScope.drawCurveLine(
    scores: List<Float>,
    w: Float, h: Float, padding: Float,
    color: Color, dashed: Boolean
) {
    if (scores.size < 2) return

    val path = Path()
    scores.forEachIndexed { i, score ->
        val x = padding + (w - 2 * padding) * i / (scores.size - 1)
        val y = h - padding - (h - 2 * padding) * score.coerceIn(0f, 1f)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }

    val stroke = if (dashed) {
        Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
    } else {
        Stroke(width = 2.5f)
    }
    drawPath(path, color, style = stroke)

    // Punkty
    scores.forEachIndexed { i, score ->
        val x = padding + (w - 2 * padding) * i / (scores.size - 1)
        val y = h - padding - (h - 2 * padding) * score.coerceIn(0f, 1f)
        drawCircle(color, radius = 3f, center = Offset(x, y))
    }
}

private fun DrawScope.drawSegmentBoundaries(
    boundaries: List<Int>,
    totalPoints: Int,
    w: Float, h: Float, padding: Float
) {
    if (totalPoints < 2) return
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))

    boundaries.forEach { idx ->
        val x = padding + (w - 2 * padding) * idx / (totalPoints - 1)
        drawLine(
            SegmentBorderColor, Offset(x, padding), Offset(x, h - padding),
            strokeWidth = 1f, pathEffect = dashEffect
        )
    }
}

private fun buildSegmentBoundaries(segments: List<SegmentMatchResult>): List<Int> {
    val boundaries = mutableListOf<Int>()
    var offset = 0
    segments.dropLast(1).forEach { seg ->
        offset += seg.tracks.size
        boundaries.add(offset)
    }
    return boundaries
}

// ── Legenda ──────────────────────────────────────────────────────────────────

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(16.dp, 2.dp)) {
            val stroke = if (dashed) {
                Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f)))
            } else {
                Stroke(width = 2f)
            }
            drawLine(color, Offset(0f, 1f), Offset(size.width, 1f), strokeWidth = stroke.width,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
