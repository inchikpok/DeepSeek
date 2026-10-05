package com.custom.treadmill.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.ui.viewmodels.MainViewModel

@Composable
fun DebugScreen(vm: MainViewModel, onBack: () -> Unit) {
    val logs by vm.logs.collectAsState()
    val treadmillState by vm.treadmillState.collectAsState()
    val settings by vm.settings.collectAsState()

    var hexInput by remember { mutableStateOf("A4 05") }

    // Список быстрых команд для теста наклона
    val inclineTests = listOf(
        "A4 05"    to "A4 05 (наклон 5, простой)",
        "A4 32"    to "A4 32 (наклон 5, ×10)",
        "A4 01 05" to "A4 01 05 (префикс 01)",
        "A5 05"    to "A5 05 (команда A5)",
        "B0 05"    to "B0 05 (команда B0)",
        "A4 0A"    to "A4 0A (наклон 10)"
    )

    Column(Modifier.fillMaxSize().padding(16.dp)) {

        // ---------- Шапка ----------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("Debug", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { vm.clearLogs() }) { Text("Очистить") }
        }

        // ---------- Состояние ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Состояние дорожки: $treadmillState", fontSize = 13.sp)
                Text("Протокол: ${settings.protocol}", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Быстрые кнопки теста наклона ----------
        Card(
            Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "⚡ Тест наклона — нажимайте по одной, следите за дорожкой",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                inclineTests.forEach { (hex, label) ->
                    Button(
                        onClick = { vm.sendRawHex(hex) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(label, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Если дорожка отреагировала на одну из команд — запомните её номер и пришлите мне.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Проверка скорости (для сравнения) ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Проверка управления", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { vm.sendRawHex("A3 32") },  // 5.0 км/ч
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) { Text("Скор. 5", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { vm.sendRawHex("A3 64") },  // 10 км/ч
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) { Text("Скор. 10", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Если дорожка реагирует на скорость, но не на наклон — проблема в команде наклона.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Ручной ввод hex ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Своя команда (hex)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    label = { Text("Например: A4 05") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { vm.sendRawHex(hexInput) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Отправить") }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Лог ----------
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.padding(8.dp)) {
                Text("Лог BLE", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                LazyColumn {
                    items(logs.asReversed()) { line ->
                        Text(
                            line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.contains("<-")) Color(0xFF2E7D32)
                                    else if (line.contains("->")) Color(0xFF1976D2)
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
