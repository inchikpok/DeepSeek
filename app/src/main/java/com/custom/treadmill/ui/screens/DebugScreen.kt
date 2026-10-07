package com.custom.treadmill.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.custom.treadmill.ble.BleConnectionState
import com.custom.treadmill.ui.viewmodels.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DebugScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val logs by vm.logs.collectAsState()
    val treadmillState by vm.treadmillState.collectAsState()
    val treadmillName by vm.treadmillName.collectAsState()
    val settings by vm.settings.collectAsState()

    val treadmillReady = treadmillState == BleConnectionState.READY

    var hexInput by remember { mutableStateOf("02 96 00") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }  // 0=Лог, 1=Справочник, 2=Конструктор

    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (tab == 0 && logs.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
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

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {

            // ---------- Шапка ----------
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

            savedMessage?.let {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { savedMessage = null }) { Text("OK") }
                    }
                }
            }

            // ---------- Статус ----------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Дорожка: ${treadmillName ?: "—"}", fontSize = 12.sp)
                    Text("Состояние: $treadmillState", fontSize = 12.sp)
                    Text("Протокол: ${settings.protocol}", fontSize = 12.sp)
                    Text("Записей в логе: ${logs.size}", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(6.dp))

            // ---------- Ручной ввод hex ----------
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("Отправить команду (hex)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
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
                        Button(
                            onClick = { vm.sendRawHex(hexInput) },
                            enabled = treadmillReady
                        ) { Text("Отпр.") }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ---------- Табы ----------
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TabButton("Лог", tab == 0) { tab = 0 }
                TabButton("Справочник", tab == 1) { tab = 1 }
                TabButton("Конструктор", tab == 2) { tab = 2 }
            }

            Spacer(Modifier.height(6.dp))

            // ---------- Контент таба ----------
            when (tab) {
                0 -> LogView(logs, listState, Modifier.weight(1f))
                1 -> CommandReference(
                    treadmillReady = treadmillReady,
                    onSend = { vm.sendRawHex(it) },
                    modifier = Modifier.weight(1f)
                )
                else -> CommandConstructor(
                    treadmillReady = treadmillReady,
                    onSend = { vm.sendRawHex(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// =====================================================================
//  Вспомогательные компоненты
// =====================================================================

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) { Text(text, fontSize = 13.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) { Text(text, fontSize = 13.sp) }
    }
}

@Composable
private fun LogView(
    logs: List<String>,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Text("Лог BLE", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                            line.contains("<-") -> Color(0xFF4CAF50)
                            line.contains("->") -> Color(0xFF64B5F6)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

// =====================================================================
//  Справочник команд
// =====================================================================

private data class CmdEntry(
    val label: String,
    val hex: String,
    val note: String = ""
)

/**
 * Строит справочник: параметризованные команды для типовых скоростей
 * и наклонов, плюс команды управления. Все значения соответствуют тому,
 * что реально работает на FS-E629DD (проверено логами).
 */
private fun buildCommandReference(): List<Pair<String, List<CmdEntry>>> {
    val speedsKmh = listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 12)

    val speedStart = speedsKmh.map { kmh ->
        val raw = kmh * 100
        CmdEntry(
            label = "$kmh км/ч",
            hex = "00 02 %02X %02X 07".format(raw and 0xFF, (raw shr 8) and 0xFF),
            note = "Старт с нуля"
        )
    }

    val speedChange = speedsKmh.map { kmh ->
        val raw = kmh * 100
        CmdEntry(
            label = "$kmh км/ч",
            hex = "02 %02X %02X".format(raw and 0xFF, (raw shr 8) and 0xFF),
            note = "Смена на ходу"
        )
    }

    val inclines = listOf(0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15)
    val inclineCmds = inclines.map { p ->
        val raw = p * 10
        CmdEntry(
            label = "$p %",
            hex = "03 %02X %02X".format(raw and 0xFF, (raw shr 8) and 0xFF)
        )
    }

    return listOf(
        "Управление" to listOf(
            CmdEntry("Request Control", "00", "Запросить контроль"),
            CmdEntry("Start / Resume", "07", "Запуск / продолжить"),
            CmdEntry("Stop (наш способ)", "02 00 00", "Скорость → 0"),
            CmdEntry("Pause (стандарт FTMS)", "08 01", "На этом belt игнорируется")
        ),
        "Скорость — старт с нуля" to speedStart,
        "Скорость — смена на ходу" to speedChange,
        "Наклон (целые %)" to inclineCmds
    )
}

@Composable
private fun CommandReference(
    treadmillReady: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember { buildCommandReference() }
    var lastSent by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        if (!treadmillReady) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "Дорожка не подключена — команды не отправятся",
                        Modifier.padding(10.dp),
                        fontSize = 12.sp
                    )
                }
            }
        }

        groups.forEach { (title, commands) ->
            item {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            items(commands) { cmd ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = treadmillReady) {
                            onSend(cmd.hex)
                            lastSent = "${cmd.label} (${cmd.hex})"
                        }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cmd.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (cmd.note.isNotBlank()) {
                                Text(
                                    cmd.note,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            cmd.hex,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            lastSent?.let {
                Card(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        "Отправлено: $it",
                        Modifier.padding(10.dp),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// =====================================================================
//  Конструктор команд
// =====================================================================

@Composable
private fun CommandConstructor(
    treadmillReady: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var speedStr by remember { mutableStateOf("3") }
    var inclineStr by remember { mutableStateOf("2") }
    var lastSent by remember { mutableStateOf<String?>(null) }

    val speed = speedStr.replace(',', '.').toDoubleOrNull() ?: 0.0
    val incline = inclineStr.replace(',', '.').toDoubleOrNull() ?: 0.0

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!treadmillReady) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    "Дорожка не подключена — команды не отправятся",
                    Modifier.padding(10.dp),
                    fontSize = 12.sp
                )
            }
        }

        // ---------- Скорость ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Скорость", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = speedStr,
                    onValueChange = { speedStr = it },
                    label = { Text("км/ч (целые, наш belt не понимает 0.5)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                val raw = (speed * 100).roundToInt().coerceIn(0, 65535)
                val b1 = raw and 0xFF
                val b2 = (raw shr 8) and 0xFF
                val startHex = "00 02 %02X %02X 07".format(b1, b2)
                val changeHex = "02 %02X %02X".format(b1, b2)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            onSend(startHex)
                            lastSent = "speed=$speed start ($startHex)"
                        },
                        enabled = treadmillReady && speed > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Старт с нуля", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = {
                            onSend(changeHex)
                            lastSent = "speed=$speed change ($changeHex)"
                        },
                        enabled = treadmillReady && speed > 0,
                        modifier = Modifier.weight(1f)
                    ) { Text("Смена на ходу", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "$startHex   /   $changeHex",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- Наклон ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Наклон", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = inclineStr,
                    onValueChange = { inclineStr = it },
                    label = { Text("% (целые — belt принимает только целые)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                val raw = (incline * 10).roundToInt().coerceIn(0, 32767)
                val b1 = raw and 0xFF
                val b2 = (raw shr 8) and 0xFF
                val hex = "03 %02X %02X".format(b1, b2)

                Button(
                    onClick = {
                        onSend(hex)
                        lastSent = "incline=$incline ($hex)"
                    },
                    enabled = treadmillReady,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Отправить наклон", fontSize = 13.sp) }
                Spacer(Modifier.height(4.dp))
                Text(
                    hex,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        lastSent?.let {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    "Отправлено: $it",
                    Modifier.padding(10.dp),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// =====================================================================
//  Экспорт лога
// =====================================================================

private fun buildLogContent(logs: List<String>, state: String, protocol: String): String {
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    return buildString {
        appendLine("=== Treadmill Debug Log ===")
        appendLine("Дата: $now")
        appendLine("Состояние дорожки: $state")
        appendLine("Протокол: $protocol")
        appendLine("Записей: ${logs.size}")
        appendLine()
        appendLine("--- Лог ---")
        logs.forEach { appendLine(it) }
        appendLine()
        appendLine("=== Конец ===")
    }
}
