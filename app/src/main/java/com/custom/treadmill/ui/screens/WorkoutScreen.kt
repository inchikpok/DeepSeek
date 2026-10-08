package com.custom.treadmill.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.ble.BleConnectionState
import com.custom.treadmill.data.repository.HrMode
import com.custom.treadmill.ui.components.HeartRateChart
import com.custom.treadmill.ui.components.RollingText
import com.custom.treadmill.ui.viewmodels.MainViewModel
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun WorkoutScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ws by vm.workoutState.collectAsState()
    val targetSpeed by vm.targetSpeed.collectAsState()
    val targetIncline by vm.targetIncline.collectAsState()
    val hr by vm.heartRate.collectAsState()
    val hrHistory by vm.hrHistory.collectAsState()
    val settings by vm.settings.collectAsState()
    val treadmillState by vm.treadmillState.collectAsState()

    val elapsedSec by vm.uiElapsedSec.collectAsState()
    val distanceKm by vm.uiDistanceKm.collectAsState()
    val calories by vm.uiCalories.collectAsState()

    val treadmillReady = treadmillState == BleConnectionState.READY
    val speedStep = settings.speedStepKmh

    LaunchedEffect(ws.finished) { if (ws.finished) vm.saveWorkoutLog() }

    val targetHr = if (settings.hrMode == HrMode.TARGET) settings.targetHr
                   else (settings.zoneMin + settings.zoneMax) / 2

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ==========================================================
            //  Шапка: ← Назад | Название | Далее →
            // ==========================================================
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { vm.stopWorkout(); onBack() },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                ) { Text("← Назад", fontSize = 13.sp) }

                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Тренировка", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        ws.programName.ifBlank { "Ручная" },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val isLast = !ws.hasNextSegment
                Button(
                    onClick = { vm.skipWorkoutSegment() },
                    enabled = treadmillReady && !ws.paused && !isLast,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("Далее", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(4.dp))

            // ==========================================================
            //  Прогресс + следующая сегмент
            // ==========================================================
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    val progress = if (ws.totalSec > 0)
                        ws.totalElapsedSec.toFloat() / ws.totalSec else 0f
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Сегмент ${ws.currentSegmentIndex + 1}/${ws.totalSegments}: " +
                                            ws.currentSegmentName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (ws.paused) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFF9A825),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            "ПАУЗА",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        val remainSec = (ws.segmentDurationSec - ws.segmentElapsedSec).coerceAtLeast(0)
                        RollingText(
                            text = formatTime(remainSec),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Информация о СЛЕДУЮЩЕМ сегменте
                    if (ws.hasNextSegment) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Далее:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                ws.nextSegmentName.ifBlank { "Сегмент ${ws.currentSegmentIndex + 2}" },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "%.1f км/ч · %d%%".format(
                                    ws.nextSegmentSpeedKmh,
                                    ws.nextSegmentInclinePercent.roundToInt()
                                ),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = progress.coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Общий прогресс: ${(progress * 100).toInt()}%",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniControl(
                    label = "Скорость",
                    value = "%.1f".format(targetSpeed),
                    unit = "км/ч",
                    onMinus = { vm.setSpeed(targetSpeed - speedStep) },
                    onPlus = { vm.setSpeed(targetSpeed + speedStep) },
                    modifier = Modifier.weight(1f)
                )
                MiniControl(
                    label = "Наклон",
                    value = "${targetIncline.roundToInt()}",
                    unit = "%",
                    onMinus = { vm.setIncline(max(0.0, targetIncline.roundToInt() - 1.0)) },
                    onPlus = { vm.setIncline(targetIncline.roundToInt() + 1.0) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))

            Card(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            "Пульс (30 мин)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        RollingText(
                            text = if (hr > 0) "$hr" else "—",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "уд/мин",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                        if (targetHr > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Цель: $targetHr",
                                fontSize = 10.sp,
                                color = Color(0xFF1976D2),
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HeartRateChart(
                        points = hrHistory,
                        targetHr = targetHr,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    WorkoutMetric("Время", formatTime(elapsedSec), "", Modifier.weight(1f))
                    WorkoutMetric("Дистанция", "%.2f".format(distanceKm), "км", Modifier.weight(1f))
                    WorkoutMetric("Калории", "$calories", "ккал", Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(6.dp))

            // ---------- Пауза / Стоп ----------
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val paused = ws.paused

                Button(
                    onClick = { vm.togglePauseWorkout() },
                    enabled = treadmillReady || paused,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = if (paused)
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    else
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                ) {
                    Text(
                        if (paused) "Продолжить" else "Пауза",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = { vm.stopWorkout(); onBack() },
                    enabled = treadmillReady,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                ) {
                    Text("Стоп", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MiniControl(
    label: String,
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(2.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label.uppercase(),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                RollingText(
                    text = value,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    unit,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                    onClick = onMinus,
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Меньше", modifier = Modifier.size(16.dp))
                }
                FilledTonalButton(
                    onClick = onPlus,
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Больше", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkoutMetric(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            RollingText(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, fontSize = 9.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

private fun formatTime(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
