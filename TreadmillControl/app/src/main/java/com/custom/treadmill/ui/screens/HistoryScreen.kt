package com.custom.treadmill.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.custom.treadmill.ui.viewmodels.ProgramViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(programVm: ProgramViewModel, onBack: () -> Unit) {
    val logs by programVm.logs.collectAsState()
    val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Назад") }
        Text("Журнал тренировок", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (logs.isEmpty()) {
            Text("Нет записей")
        } else {
            LazyColumn {
                items(logs, key = { it.id }) { log ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(fmt.format(Date(log.dateMillis)), fontWeight = FontWeight.SemiBold)
                            Text("Программа: ${log.programName}")
                            Text("Время: ${log.durationSec / 60} мин ${log.durationSec % 60} сек")
                            Text("Дистанция: %.2f км".format(log.distanceKm))
                            Text("Калории: ${log.calories}")
                            Text("Средний пульс: ${log.avgHeartRate} (макс ${log.maxHeartRate})")
                            Text("Средняя скорость: %.1f км/ч".format(log.avgSpeedKmh))
                        }
                    }
                }
            }
        }
    }
}
