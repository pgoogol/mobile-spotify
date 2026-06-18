package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotify.playlistmanager.desktop.theme.SpotifyAmber
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen
import com.spotify.playlistmanager.domain.model.SegmentMatchResult

/**
 * Wykres krzywej energii dla wersji desktopowej.
 *
 * - Zielona linia ciągła — krzywa docelowa (target)
 * - Bursztynowa linia przerywana — rzeczywiste composite score
 *
 * Oś Y jest auto-skalowana do zakresu wartości segmentu (score'y są już
 * przeskalowane do rozkładu puli przez [com.spotify.playlistmanager.domain.model.EnergyCurveCalculator]).
 */
@Composable
fun EnergyCurveChart(segment: SegmentMatchResult, modifier: Modifier = Modifier) {
    val target = segment.targetScores
    val actual = segment.tracks.map { it.compositeScore }

    val all = target + actual
    val rawMin = all.minOrNull() ?: 0f
    val rawMax = all.maxOrNull() ?: 1f
    // Margines + zabezpieczenie przed zerową rozpiętością (płaska krzywa).
    val yMin = (rawMin - 0.05f)
    val yMax = (rawMax + 0.05f)
    val yRange = (yMax - yMin).coerceAtLeast(0.01f)

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LegendItem("Cel", SpotifyGreen, dashed = false)
            LegendItem("Rzeczywisty", SpotifyAmber, dashed = true)
            Spacer(Modifier.weight(1f))
            val pct = (segment.matchPercentage * 100).toInt()
            Text(
                text = "Dopasowanie: $pct%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    pct >= 80 -> SpotifyGreen
                    pct >= 60 -> SpotifyAmber
                    else -> MaterialTheme.colorScheme.error
                },
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(8.dp),
        ) {
            val pad = 18f
            if (target.size >= 2) {
                drawCurve(target, SpotifyGreen, dashed = false, yMin, yRange, pad)
            }
            if (actual.size >= 2) {
                drawCurve(actual, SpotifyAmber, dashed = true, yMin, yRange, pad)
            }
        }
    }
}

private fun DrawScope.drawCurve(
    scores: List<Float>,
    color: Color,
    dashed: Boolean,
    yMin: Float,
    yRange: Float,
    pad: Float,
) {
    val w = size.width
    val h = size.height
    fun px(i: Int) = pad + (w - 2 * pad) * i / (scores.size - 1).coerceAtLeast(1)
    fun py(score: Float) = h - pad - (h - 2 * pad) * ((score - yMin) / yRange).coerceIn(0f, 1f)

    val path = Path()
    scores.forEachIndexed { i, s ->
        val x = px(i)
        val y = py(s)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    val stroke = if (dashed) {
        Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)))
    } else {
        Stroke(width = 3f)
    }
    drawPath(path, color, style = stroke)

    scores.forEachIndexed { i, s ->
        drawCircle(color, radius = 4f, center = Offset(px(i), py(s)))
    }
}

@Composable
private fun LegendItem(label: String, color: Color, dashed: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(modifier = Modifier.size(18.dp, 3.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 3f,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
