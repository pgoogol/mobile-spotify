package com.spotify.playlistmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.unit.sp
import com.spotify.playlistmanager.domain.model.MatchedTrack
import com.spotify.playlistmanager.domain.model.SegmentMatchResult
import com.spotify.playlistmanager.ui.theme.SpotifyGreen

private val TargetColor = SpotifyGreen
private val ActualColor = Color(0xFFFFA726) // Bursztynowy
private val SegmentBorderColor = Color.White.copy(alpha = 0.20f)
private val RoundBorderColor = Color.White.copy(alpha = 0.40f)
private val ChipGreen = Color(0xFF4CAF50)
private val ChipYellow = Color(0xFFFFC107)
private val ChipRed = Color(0xFFF44336)

private val PixelsPerTrack: Dp = 14.dp
private val ChartHeight: Dp = 140.dp
private val MinChartWidth: Dp = 280.dp

/**
 * Wykres krzywej energii po wygenerowaniu.
 *
 * - Zielona linia ciągła — docelowa krzywa (target)
 * - Bursztynowa linia przerywana — rzeczywiste score'y
 * - Pionowe przerywane (jasne) — granice segmentów wewnątrz rundy
 * - Pionowe przerywane (mocniejsze) + etykieta "Rn" — granice rund
 * - Naprzemienne tło per runda
 * - Tap na punkt → tooltip: Tytuł · Artysta
 * - Chip zgodności: 🟢 ≥80%, 🟡 60–79%, 🔴 <60%
 * - Toggle "Cała sesja / Ostatnia runda" — kontrolowany z zewnątrz
 * - Poziomy scroll dla długich sesji + auto-scroll do końca po nowej generacji
 */
@Composable
fun EnergyCurveChart(
    segments: List<SegmentMatchResult>,
    overallMatchPercentage: Float,
    isDryRun: Boolean = false,
    showOnlyLastRound: Boolean = false,
    onToggleScope: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Filtrowanie według trybu
    val displayedSegments = remember(segments, showOnlyLastRound) {
        if (!showOnlyLastRound) {
            segments
        } else {
            val lastRound = segments.maxOfOrNull { it.roundNumber } ?: 0
            if (lastRound == 0) segments
            else segments.filter { it.roundNumber == lastRound }
        }
    }

    val allTargets = displayedSegments.flatMap { it.targetScores }
    val allMatched = displayedSegments.flatMap { it.tracks }
    val segmentBoundaries = remember(displayedSegments) {
        buildSegmentBoundaries(displayedSegments)
    }
    val roundBoundaries = remember(displayedSegments) {
        buildRoundBoundaries(displayedSegments)
    }
    val roundCount = remember(displayedSegments) {
        displayedSegments.map { it.roundNumber }.distinct().count { it > 0 }
    }

    var tooltipTrack by remember { mutableStateOf<MatchedTrack?>(null) }

    val density = LocalDensity.current
    val totalPoints = allMatched.size
    val canvasWidth: Dp = remember(totalPoints) {
        val w = PixelsPerTrack * totalPoints.coerceAtLeast(1)
        if (w < MinChartWidth) MinChartWidth else w
    }

    val scrollState = rememberScrollState()

    // Auto-scroll do końca po pojawieniu się nowych utworów
    LaunchedEffect(totalPoints) {
        if (totalPoints > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Górny pasek: chip zgodności + toggle ─────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isDryRun && allTargets.isNotEmpty()) {
                MatchPercentageChip(overallMatchPercentage)
            }
            Spacer(Modifier.weight(1f))
            // Toggle widoczny tylko gdy są ≥2 rundy i mamy callback
            if (onToggleScope != null && roundCount >= 2 || (showOnlyLastRound && onToggleScope != null)) {
                AssistChip(
                    onClick = onToggleScope!!,
                    label = {
                        Text(
                            if (showOnlyLastRound) "Ostatnia runda" else "Cała sesja",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // ── Wykres ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .horizontalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .width(canvasWidth)
                    .height(ChartHeight)
                    .pointerInput(allMatched) {
                        if (isDryRun || allMatched.isEmpty()) return@pointerInput
                        detectTapGestures { tapOffset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val padding = 16f
                            val n = allMatched.size
                            if (n == 0) return@detectTapGestures

                            var closest: MatchedTrack? = null
                            var minDist = Float.MAX_VALUE
                            allMatched.forEachIndexed { i, mt ->
                                val x = padding + (w - 2 * padding) * i / (n - 1).coerceAtLeast(1)
                                val y = h - padding - (h - 2 * padding) * mt.compositeScore
                                val dist = kotlin.math.hypot(
                                    (tapOffset.x - x).toDouble(),
                                    (tapOffset.y - y).toDouble()
                                ).toFloat()
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
                val n = allMatched.size

                // 1. Striping per runda (tło)
                drawRoundStripes(displayedSegments, n, w, h, padding)

                // 2. Granice segmentów wewnątrz rundy (jasne)
                drawSegmentBoundaries(segmentBoundaries, n, w, h, padding)

                // 3. Granice rund (mocniejsze) + etykiety
                drawRoundBoundaries(
                    roundBoundaries = roundBoundaries,
                    totalPoints = n,
                    w = w, h = h, padding = padding,
                    density = density
                )

                // 4. Target curve
                if (allTargets.isNotEmpty() && allTargets.size == n) {
                    drawCurveLine(allTargets, w, h, padding, TargetColor, dashed = false)
                }

                // 5. Actual curve
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
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
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

        // Legenda + licznik utworów
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem("Cel", TargetColor, dashed = false)
            if (!isDryRun) {
                LegendItem("Rzeczywisty", ActualColor, dashed = true)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "$totalPoints utworów",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        color = chipColor.copy(alpha = 0.15f)
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

/**
 * Rysuje granice rund (mocniejsze niż segment boundaries) + etykiety "Rn"
 * nad każdym blokiem rundy.
 */
private fun DrawScope.drawRoundBoundaries(
    roundBoundaries: List<RoundBoundary>,
    totalPoints: Int,
    w: Float, h: Float, padding: Float,
    density: androidx.compose.ui.unit.Density
) {
    if (totalPoints < 2 || roundBoundaries.isEmpty()) return
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
    val labelPaint = Paint().apply {
        color = android.graphics.Color.argb(180, 255, 255, 255)
        textSize = with(density) { 9.sp.toPx() }
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    roundBoundaries.forEach { rb ->
        // Pionowa linia tylko jeśli to nie pierwsza runda (startIdx > 0)
        if (rb.startIdx > 0) {
            val x = padding + (w - 2 * padding) * rb.startIdx / (totalPoints - 1)
            drawLine(
                RoundBorderColor, Offset(x, padding), Offset(x, h - padding),
                strokeWidth = 1.5f, pathEffect = dashEffect
            )
        }

        // Etykieta "Rn" w lewym górnym rogu bloku rundy
        if (rb.roundNumber > 0) {
            val labelX = padding + (w - 2 * padding) * rb.startIdx / (totalPoints - 1) + 4f
            val labelY = padding + with(density) { 10.sp.toPx() }
            drawContext.canvas.nativeCanvas.drawText(
                "R${rb.roundNumber}",
                labelX,
                labelY,
                labelPaint
            )
        }
    }
}

/**
 * Naprzemienne tło per runda — bardzo subtelny stripe.
 */
private fun DrawScope.drawRoundStripes(
    segments: List<SegmentMatchResult>,
    totalPoints: Int,
    w: Float, h: Float, padding: Float
) {
    if (totalPoints < 2 || segments.isEmpty()) return
    val rounds = buildRoundBoundaries(segments)
    if (rounds.size < 2) return

    val stripeColor = Color.White.copy(alpha = 0.04f)

    rounds.forEachIndexed { i, rb ->
        if (i % 2 == 1) { // co drugą rundę
            val xStart = padding + (w - 2 * padding) * rb.startIdx / (totalPoints - 1)
            val xEnd = padding + (w - 2 * padding) * rb.endIdx / (totalPoints - 1)
            drawRect(
                color = stripeColor,
                topLeft = Offset(xStart, padding),
                size = androidx.compose.ui.geometry.Size(xEnd - xStart, h - 2 * padding)
            )
        }
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

private data class RoundBoundary(
    val roundNumber: Int,
    val startIdx: Int,
    val endIdx: Int
)

/**
 * Grupuje segmenty po roundNumber i zwraca przedziały indeksów [startIdx, endIdx]
 * dla każdej rundy w przestrzeni allTracks.
 */
private fun buildRoundBoundaries(segments: List<SegmentMatchResult>): List<RoundBoundary> {
    if (segments.isEmpty()) return emptyList()
    val result = mutableListOf<RoundBoundary>()
    var offset = 0
    var currentRound = segments.first().roundNumber
    var currentStart = 0

    for (seg in segments) {
        if (seg.roundNumber != currentRound) {
            result.add(RoundBoundary(currentRound, currentStart, offset))
            currentRound = seg.roundNumber
            currentStart = offset
        }
        offset += seg.tracks.size
    }
    result.add(RoundBoundary(currentRound, currentStart, offset))
    return result
}

// ── Legenda ──────────────────────────────────────────────────────────────────

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(16.dp, 2.dp)) {
            drawLine(
                color, Offset(0f, 1f), Offset(size.width, 1f),
                strokeWidth = 2f,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}