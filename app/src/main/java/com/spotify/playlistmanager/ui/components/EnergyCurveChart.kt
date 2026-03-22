package com.spotify.playlistmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotify.playlistmanager.data.model.EnergyCurve
import com.spotify.playlistmanager.data.model.Track
import com.spotify.playlistmanager.ui.theme.SpotifyGreen
import com.spotify.playlistmanager.util.EnergyCurveCalculator

/**
 * Wykres krzywej energii playlisty – port energy_visualization.py.
 * Rysuje docelową krzywą (zielona) i rzeczywistą energię utworów (białe punkty).
 */
@Composable
fun EnergyCurveChart(
    tracks:   List<Track>,
    curve:    EnergyCurve,
    modifier: Modifier = Modifier
) {
    val targetPoints = remember(curve) {
        EnergyCurveCalculator.curvePoints(curve, steps = 200)
    }
    val trackPoints = remember(tracks) {
        if (tracks.isEmpty()) emptyList()
        else tracks.mapIndexed { i, t ->
            val pos = if (tracks.size > 1) i.toFloat() / (tracks.size - 1) else 0f
            pos to (t.energy ?: 0f)
        }
    }

    val green = SpotifyGreen
    val white = Color.White
    val grid  = Color.White.copy(alpha = 0.1f)

    Column(modifier = modifier) {
        Text(
            text     = "Krzywa energii: ${curve.label} ${curve.emoji}",
            style    = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
        ) {
            // Grid
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { y ->
                val cy = size.height - y * size.height
                drawLine(grid, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)
            }

            // Docelowa krzywa (zielona)
            if (targetPoints.size >= 2) {
                drawCurveFill(targetPoints, green.copy(alpha = 0.15f))
                drawCurvePath(targetPoints, green, strokeWidth = 2.5f)
            }

            // Rzeczywiste punkty energii (białe)
            trackPoints.forEach { (x, y) ->
                drawCircle(
                    color  = white,
                    radius = 4.dp.toPx(),
                    center = Offset(x * size.width, size.height - y * size.height)
                )
            }

            // Linia łącząca punkty trackPoints
            if (trackPoints.size >= 2) {
                drawCurvePath(trackPoints, white.copy(alpha = 0.6f), strokeWidth = 1.5f)
            }
        }

        // Legenda
        Row(
            modifier              = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendDot(color = SpotifyGreen, label = "Docelowa krzywa")
            LegendDot(color = Color.White,  label = "Energia utworów")
        }
    }
}

private fun DrawScope.drawCurvePath(
    points:      List<Pair<Float, Float>>,
    color:       Color,
    strokeWidth: Float = 2f
) {
    val path = Path()
    points.forEachIndexed { i, (x, y) ->
        val cx = x * size.width
        val cy = size.height - y * size.height
        if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth.dp.toPx()))
}

private fun DrawScope.drawCurveFill(
    points: List<Pair<Float, Float>>,
    color:  Color
) {
    val path = Path()
    points.forEachIndexed { i, (x, y) ->
        val cx = x * size.width
        val cy = size.height - y * size.height
        if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
    }
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    drawPath(path, color)
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color) }
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
