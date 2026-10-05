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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    var hexInput by remember { mutableStateOf("02 96 00") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Автоскролл наверх при новых записях, если пользователь у начала
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
        } catch (e: Exception) {
            savedMessage = "Ошибка: ${e.message}"
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        // ---------- Шапка ----------
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(4.dp)) {
                Text("← Назад")
            }
            Text("Debug", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row {
                TextButton(onClick = { vm.clearLogs() }) { Text("Очистить") }
                TextButton(onClick = {
                    val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
                        .format(Date())
                    saveLauncher.launch("treadmill_log_$ts.txt")
                }) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Сохранить")
                }
            }
        }

        // ---------- Сообщение о сохранении ----------
        savedMessage?.let {
            Card(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    TextButton(onClick = { savedMessage = null }) { Text("OK") }
                }
            }
        }

        // ---------- Информация ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    InfoText("Дорожка", treadmillState.toString(), Modifier.weight(1f))
                    InfoText("Протокол", settings.protocol.toString(), Modifier.weight(1f))
                    InfoText("Записей", "${logs.size}", Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Ручная команда ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    "Отправить команду (hex)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { hexInput = it },
                        label = { Text("hex") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { vm.sendRawHex(hexInput) }) {
                        Text("Отпр.")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Примеры: 02 96 00 — скорость 1.5 км/ч;  03 0A 00 — наклон 1%",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Лог ----------
        Card(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxSize().padding(8.dp)) {
                Text(
                    "Лог BLE",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(4.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(logs.asReversed()) { line ->
                        Text(
                            line,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                line.contains("<-") -> MaterialTheme.colorScheme.primary
                                line.contains("->") -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoText(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
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
