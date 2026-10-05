package com.custom.treadmill.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

    val listState = rememberLazyListState()

    // Автоскролл наверх, если пользователь у верхнего края и пришли новые строки
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = buildLogContent(logs, treadmillState.toString(), settings.protocol.toString())
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            savedMessage = "Лог сохранён"
        } catch (e: Exception) { savedMessage = "Ошибка: ${e.message}" }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(4.dp)) { Text("← Назад") }
            Text("Debug", fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { vm.clearLogs() }) { Text("Очистить") }
                TextButton(onClick = {
                    val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                    saveLauncher.launch("treadmill_log_$ts.txt")
                }) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Сохранить")
                }
            }
        }

        savedMessage?.let {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = { savedMessage = null }) { Text("OK") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("Дорожка: $treadmillState", fontSize = 12.sp)
                Text("Протокол: ${settings.protocol}", fontSize = 12.sp)
                Text("Записей: ${logs.size}", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("⚡ Тест наклона", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                listOf("A4 05", "A4 32", "A4 01 05", "A5 05", "B0 05", "A4 0A").forEach { hex ->
                    Button(
                        onClick = { vm.sendRawHex(hex) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) { Text(hex, fontSize = 11.sp) }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = hexInput, onValueChange = { hexInput = it },
                label = { Text("hex") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { vm.sendRawHex(hexInput) }, modifier = Modifier.padding(top = 8.dp)) {
                Text("Отпр.")
            }
        }

        Spacer(Modifier.height(6.dp))

        // ЛОГ — теперь скроллится
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Text("Лог BLE", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f)   // ← ключ к скроллу
                ) {
                    items(logs.asReversed()) { line ->
                        Text(
                            line, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
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

private fun buildLogContent(logs: List<String>, state: String, protocol: String): String {
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("=== Treadmill Debug Log ===")
        appendLine("Дата: $now")
        appendLine("Состояние: $state")
        appendLine("Протокол: $protocol")
        appendLine("Записей: ${logs.size}")
        appendLine()
        logs.forEach { appendLine(it) }
        appendLine()
        appendLine("=== Конец ===")
    }
}
