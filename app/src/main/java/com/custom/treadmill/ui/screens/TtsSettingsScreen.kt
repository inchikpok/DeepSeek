package com.custom.treadmill.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.ui.viewmodels.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()

    var startTemplate by remember { mutableStateOf(settings.ttsStartTemplate) }
    var segmentTemplate by remember { mutableStateOf(settings.ttsSegmentTemplate) }
    var finishTemplate by remember { mutableStateOf(settings.ttsFinishTemplate) }
    var pauseText by remember { mutableStateOf(settings.ttsPauseText) }
    var resumeText by remember { mutableStateOf(settings.ttsResumeText) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Голосовые фразы", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    TextButton(onClick = {
                        vm.updateSettings {
                            it.copy(
                                ttsStartTemplate = startTemplate,
                                ttsSegmentTemplate = segmentTemplate,
                                ttsFinishTemplate = finishTemplate,
                                ttsPauseText = pauseText,
                                ttsResumeText = resumeText
                            )
                        }
                        onBack()
                    }) { Text("Сохранить", fontWeight = FontWeight.Bold) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Доступные переменные (теги):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Старт: {name}, {segName}, {speed}, {speed_text}", fontSize = 12.sp)
                    Text("Сегмент: {i}, {n}, {segName}, {speed}, {speed_text}, {angle}, {angle_text}", fontSize = 12.sp)
                    Text("Финиш: {dist}, {hr}", fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Примечание: {speed} даст цифру (8.5), а {speed_text} — словами (8.5 километров в час).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = startTemplate,
                onValueChange = { startTemplate = it },
                label = { Text("Старт тренировки") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = segmentTemplate,
                onValueChange = { segmentTemplate = it },
                label = { Text("Смена сегмента") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = finishTemplate,
                onValueChange = { finishTemplate = it },
                label = { Text("Финиш тренировки") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = pauseText,
                onValueChange = { pauseText = it },
                label = { Text("Пауза") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = resumeText,
                onValueChange = { resumeText = it },
                label = { Text("Продолжение") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            TextButton(
                onClick = {
                    startTemplate = "Тренировка «{name}» началась. Сегмент 1: {segName}, скорость {speed_text}"
                    segmentTemplate = "Сегмент {i} из {n}: {segName}, скорость {speed_text}, угол {angle_text}"
                    finishTemplate = "Тренировка завершена. Дистанция {dist} км, средний пульс {hr}"
                    pauseText = "Пауза"
                    resumeText = "Продолжаем"
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Сбросить настройки по умолчанию")
            }
        }
    }
}
