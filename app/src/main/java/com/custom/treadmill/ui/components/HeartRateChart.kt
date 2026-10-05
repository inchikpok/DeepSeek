package com.custom.treadmill.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Точка графика пульса: время (мс) + значение уд/мин. */
data class HrPoint(val timestamp: Long, val bpm: Int)

/**
 * Простой график пульса в реальном времени.
 * По X — последние 30 минут; по Y — динамический диапазон.
 * Целевой пульс рисуется синей горизонтальной линией.
 */
@Composable
fun HeartRateChart(
    points: List<HrPoint>,
    targetHr: Int = 0,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
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

    val target = targetHr.coerceAtLeast(0)
    val minBpmRaw = points.minOf { it.bpm }
    val maxBpmRaw = points.maxOf { it.bpm }
    val refMin = if (target > 0) minOf(minBpmRaw, target) else minBpmRaw
    val refMax = if (target > 0) maxOf(maxBpmRaw, target) else maxBpmRaw

    val yMin = (refMin - 10).coerceAtLeast(40)
    val yMax = (refMax + 10).coerceAtMost(220)
    val yRange = (yMax - yMin).coerceAtLeast(30)

    val lineColor = Color(0xFFC62828)   // красный — пульс
    val targetColor = Color(0xFF1976D2) // синий — цель
    val gridColor = Color(0xFFE0E0E0)

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val padL = 40f
            val padR = 8f
            val padT = 8f
            val padB = 8f
            val innerW = w - padL - padR
            val innerH = h - padT - padB
            if (innerW <= 0f || innerH <= 0f) return@Canvas

            // Горизонтальная сетка
            val steps = 4
            for (i in 0..steps) {
                val y = padT + innerH * i / steps.toFloat()
                drawLine(
                    color = gridColor,
                    start = Offset(padL, y),
                    end = Offset(w - padR, y),
                    strokeWidth = 1f
                )
            }

            // Целевая линия
            if (target in yMin..yMax) {
                val ty = padT + innerH * (1f - (target - yMin).toFloat() / yRange)
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
        }

        // Метки по Y — обычными Text поверх Canvas
        Text(
            "$yMax",
            fontSize = 11.sp,
            color = Color(0xFF757575),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 2.dp, top = 2.dp)
        )
        Text(
            "$yMin",
            fontSize = 11.sp,
            color = Color(0xFF757575),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 2.dp, bottom = 2.dp)
        )
    }
}
