package com.spotify.playlistmanager.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.spotify.playlistmanager.desktop.theme.SpotifyAmber
import com.spotify.playlistmanager.desktop.theme.SpotifyGreen

/**
 * Mini-wykres krzywej energii sesji — desktopowy odpowiednik
 * `SessionEnergyMiniChart` z aplikacji mobilnej.
 *
 * Linia (szara) łączy kolejne utwory; kropki kolorowane wg puli
 * (A = zielony, B = bursztynowy). [scores] i [pools] muszą mieć tę samą długość;
 * `pools[i] == true` oznacza pulę A.
 */
@Composable
internal fun StepwiseSessionEnergyChart(
    scores: List<Float>,
    pools: List<Boolean>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = 8f
        val padY = 6f
        val plotW = w - padX * 2
        val plotH = h - padY * 2

        // Siatka pozioma: 0 / 0.5 / 1.0
        for (v in listOf(0f, 0.5f, 1f)) {
            val y = padY + plotH * (1f - v)
            drawLine(color = gridColor, start = Offset(padX, y), end = Offset(w - padX, y), strokeWidth = 1f)
        }

        if (scores.size < 2) return@Canvas

        val points = scores.mapIndexed { idx, s ->
            val x = padX + plotW * (idx.toFloat() / (scores.size - 1))
            val y = padY + plotH * (1f - s.coerceIn(0f, 1f))
            Offset(x, y)
        }

        val linePath = Path().apply {
            points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        drawPath(path = linePath, color = lineColor, style = Stroke(width = 2f))

        points.forEachIndexed { idx, p ->
            val isA = pools.getOrElse(idx) { true }
            drawCircle(color = if (isA) SpotifyGreen else SpotifyAmber, radius = 4f, center = p)
        }
    }
}
