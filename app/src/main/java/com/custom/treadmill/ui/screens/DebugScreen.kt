package com.custom.treadmill.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.ui.viewmodels.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val logs by vm.logs.collectAsState()
    val treadmillState by vm.treadmillState.collectAsState()
    val settings by vm.settings.collectAsState()

    var hexInput by remember { mutableStateOf("A4 05") }
    var savedMessage by remember { mutableStateOf<String?>(null) }

    // ---------- Диалог «Сохранить как» ----------
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = buildLogContent(vm, logs, treadmillState.toString(), settings.protocol.toString())
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            savedMessage = "Лог сохранён"
        } catch (e: Exception) {
            savedMessage = "Ошибка сохранения: ${e.message}"
        }
    }

    // Быстрые команды для теста наклона
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
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("Debug", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Row {
                TextButton(onClick = { vm.clearLogs() }) { Text("Очистить") }
                TextButton(onClick = {
                    val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                        .format(Date())
                    saveLauncher.launch("treadmill_log_$ts.txt")
                }) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Text("Сохранить")
                }
            }
        }

        savedMessage?.let { msg ->
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(msg, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { savedMessage = null }) { Text("OK") }
                }
            }
        }

        // ---------- Состояние ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Состояние дорожки: $treadmillState", fontSize = 13.sp)
                Text("Протокол: ${settings.protocol}", fontSize = 13.sp)
                Text("Записей в логе: ${logs.size}", fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Быстрые команды теста наклона ----------
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "⚡ Тест наклона — по одной кнопке, следите за дорожкой",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                inclineTests.forEach { (hex, label) ->
                    Button(
                        onClick = { vm.sendRawHex(hex) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Проверка скорости ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Проверка управления", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { vm.sendRawHex("A3 32") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) { Text("Скор. 5", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { vm.sendRawHex("A3 64") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) { Text("Скор. 10", fontSize = 12.sp) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Ручной ввод ----------
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
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Лог BLE",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "синие → отправили  |  зелёные ← получили",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                LazyColumn {
                    items(logs.asReversed()) { line ->
                        Text(
                            line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                line.contains("<-") -> Color(0xFF2E7D32)
                                line.contains("->") -> Color(0xFF1976D2)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Формирует текстовое содержимое файла лога:
 * заголовок с метаданными + все строки лога.
 */
private fun buildLogContent(
    vm: MainViewModel,
    logs: List<String>,
    treadmillState: String,
    protocol: String
): String {
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .format(Date())
    val sb = StringBuilder()
    sb.appendLine("=== Treadmill Debug Log ===")
    sb.appendLine("Дата: $now")
    sb.appendLine("Состояние дорожки: $treadmillState")
    sb.appendLine("Протокол: $protocol")
    sb.appendLine("Записей: ${logs.size}")
    sb.appendLine()
    sb.appendLine("--- Лог (старые сверху, новые снизу) ---")
    logs.forEach { sb.appendLine(it) }
    sb.appendLine()
    sb.appendLine("=== Конец ===")
    return sb.toString()
}
