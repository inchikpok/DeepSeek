package com.custom.treadmill.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val template = remember { buildTemplate() }
    val instructions = remember { buildInstructions() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Заголовок
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Назад")
                }
                Spacer(Modifier.weight(1f))
                Text("О программе", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
            }

            // Информация о приложении
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Treadmill Control", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("v2.0 — управление беговой дорожкой по BLE", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Работает полностью офлайн. Разрешение INTERNET намеренно не запрашивается.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            InfoSection("Оборудование", listOf(
                "Дорожка: UNixFit-990x",
                "Модуль: FitShow FS-E629DD",
                "Протокол: FTMS (Bluetooth SIG)",
                "Пульсометр: HR Service 0x180D"
            ))

            InfoSection("Возможности", listOf(
                "Подключение по BLE, автопереподключение",
                "Нагрудный пульсометр + график по зонам",
                "Авторегулировка скорости по пульсу",
                "Программы тренировок с сегментами",
                "Пульсовые зоны по формуле Карвонена",
                "Работа в фоне (Foreground Service)",
                "Журнал тренировок (Room)",
                "Debug-экран с полным логом"
            ))

            InfoSection("Стек", listOf(
                "Kotlin 1.9.22 · JVM 17",
                "Jetpack Compose · Material 3",
                "Room 2.6.1 · kotlinx.serialization",
                "minSdk 21 · targetSdk 34 · AGP 8.2.2"
            ))

            // Создание тренировок через JSON
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Создать свою тренировку",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Скопируйте шаблон, отредактируйте в блокноте, сохраните как .json и импортируйте через экран «Программы».",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                copyToClipboard(ctx, "workout_template.json", template)
                                Toast.makeText(ctx, "Шаблон JSON скопирован", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Шаблон", fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(ctx, "workout_instructions", instructions)
                                Toast.makeText(ctx, "Инструкция скопирована", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Инструкция", fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = template,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            Text(
                "Сделано для себя. Работает офлайн.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun InfoSection(title: String, items: List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            items.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun buildTemplate(): String = """
{
  "name": "Моя тренировка",
  "segments": [
    {
      "name": "Разминка",
      "durationSec": 300,
      "speedKmh": 4.0,
      "inclinePercent": 0.0
    },
    {
      "name": "Бег",
      "durationSec": 600,
      "speedKmh": 8.0,
      "inclinePercent": 1.0
    },
    {
      "name": "Заминка",
      "durationSec": 300,
      "speedKmh": 4.0,
      "inclinePercent": 0.0
    }
  ]
}
""".trimIndent()

private fun buildInstructions(): String = """
КАК СОЗДАТЬ СВОЮ ТРЕНИРОВКУ

1. Скопируйте шаблон (кнопка "Шаблон")
2. Откройте любой текстовый редактор
3. Вставьте шаблон
4. Отредактируйте:
   - "name" — название тренировки
   - "segments" — массив сегментов
   Для каждого сегмента:
     - "name" — название сегмента
     - "durationSec" — длительность, секунды (300 = 5 мин)
     - "speedKmh" — скорость, км/ч (0.5 – 20.0)
     - "inclinePercent" — наклон, % (0 – 15)
5. Сохраните как my_workout.json (UTF-8)
6. Импортируйте через "Программы"

ПРИМЕР — интервальная тренировка:
Разминка: 5 мин × 5 км/ч
Интервал: 1 мин × 10 км/ч
Отдых:    1 мин × 5 км/ч
(повторить 5 раз)
Заминка:  5 мин × 4 км/ч

ОГРАНИЧЕНИЯ:
- Минимум 1 сегмент
- durationSec > 0
- speedKmh: 0.5 – 20.0
- inclinePercent: 0 – 15
""".trimIndent()
