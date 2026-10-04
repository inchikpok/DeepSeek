package com.custom.treadmill.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.ui.viewmodels.MainViewModel

@Composable
fun WorkoutScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ws by vm.workoutState.collectAsState()
    val data by vm.treadmillData.collectAsState()
    val hr by vm.heartRate.collectAsState()

    LaunchedEffect(ws.finished) { if (ws.finished) vm.saveWorkoutLog() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { vm.stopWorkout(); onBack() }) { Text("← Назад") }
            Text("Тренировка", modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(1.dp))
        }
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    ws.programName.ifBlank { "Ручная тренировка" },
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                val progress = if (ws.totalSec > 0) ws.totalElapsedSec.toFloat() / ws.totalSec else 0f
                LinearProgressIndicator(progress = progress.coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Общий прогресс: ${(progress * 100).toInt()}%")
                Spacer(Modifier.height(12.dp))
                Text(
                    "Сегмент ${ws.currentSegmentIndex + 1}: ${ws.currentSegmentName}",
                    fontWeight = FontWeight.SemiBold
                )
                Text("Осталось в сегменте: ${ws.segmentDurationSec - ws.segmentElapsedSec} сек")
            }
        }

        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Текущие показатели")
                Spacer(Modifier.height(6.dp))
                Text("Скорость: %.1f км/ч".format(data.speedKmh), fontSize = 20.sp)
                Text("Пульс: ${if (hr > 0) "$hr уд/мин" else "—"}")
                Text("Дистанция: %.2f км".format(data.distanceKm))
                Text("Калории: ${data.calories} ккал")
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.stopWorkout() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
            ) { Text("Пауза") }
            Button(
                onClick = { vm.emergencyStop() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) { Text("СТОП!") }
            OutlinedButton(
                onClick = { vm.resetWorkout() },
                modifier = Modifier.weight(1f)
            ) { Text("Сброс") }
        }
    }
}
