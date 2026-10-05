package com.custom.treadmill.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.sp

/** Точка графика пульса: время (мс) + значение уд/мин. */
data class HrPoint(val timestamp: Long, val bpm: Int)

/**
 * Простой график пульса в реальном времени.
 * По X — последние 30 минут; по Y — динамический диапазон пульса.
 */
@Composable
fun HeartRateChart(
    points: List<HrPoint>,
    targetHr: Int = 0,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Ожидание данных пульса…",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
        return
    }

    val minT = points.first().timestamp
    val maxT = points.last().timestamp

    val targetInRange = targetHr.takeIf { it > 0 } ?: 0
    val minBpmRaw = points.minOf { it.bpm }
    val maxBpmRaw = points.maxOf { it.bpm }
    val refMin = if (targetInRange > 0) minOf(minBpmRaw, targetInRange) else minBpmRaw
    val refMax = if (targetInRange > 0) maxOf(maxBpmRaw, targetInRange) else maxBpmRaw

    val yMin = (refMin - 10).coerceAtLeast(40)
    val yMax = (refMax + 10).coerceAtMost(220)
    val yRange = (yMax - yMin).coerceAtLeast(30)

    val lineColor = Color(0xFFC62828)
    val targetColor = Color(0xFF1976D2)
    val gridColor = Color(0xFFE0E0E0)
    val textColor = Color(0xFF757575)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val padL = 36f
        val padR = 8f
        val padT = 8f
        val padB = 18f
        val innerW = w - padL - padR
        val innerH = h - padT - padB
        if (innerW <= 0f || innerH <= 0f) return@Canvas

        // Горизонтальные линии сетки
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val y = padT + innerH * i / gridSteps.toFloat()
            drawLine(
                color = gridColor,
                start = Offset(padL, y),
                end = Offset(w - padR, y),
                strokeWidth = 1f
            )
        }

        // Целевая линия
        if (targetInRange in yMin..yMax) {
            val ty = padT + innerH * (1f - (targetInRange - yMin).toFloat() / yRange)
            drawLine(
                color = targetColor,
                start = Offset(padL, ty),
                end = Offset(w - padR, ty),
                strokeWidth = 2f
            )
        }

        // Линия пульса
        val tRange = (maxT - minT).coerceAtLeast(1000L)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = padL + innerW * (p.timestamp - minT).toFloat() / tRange
            val y = padT + innerH * (1f - (p.bpm - yMin).toFloat() / yRange)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f))

        // Метки yMin / yMax
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = textColor.toArgb()
                textSize = 26f
                isAntiAlias = true
            }
            drawText("$yMax", 4f, padT + 8f, paint)
            drawText("$yMin", 4f, padT + innerH, paint)
        }
    }
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
