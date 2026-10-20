package com.custom.treadmill.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.DebugLog
import com.custom.treadmill.ble.BleConnectionState
import com.custom.treadmill.ui.viewmodels.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val logs by vm.logs.collectAsState()
    val treadmillState by vm.treadmillState.collectAsState()
    val treadmillName by vm.treadmillName.collectAsState()
    val data by vm.treadmillData.collectAsState()

    val treadmillReady = treadmillState == BleConnectionState.READY

    var hexInput by rememberSaveable { mutableStateOf("02 96 00") }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var hideTreadmillFrames by rememberSaveable { mutableStateOf(true) }

    val listState = rememberLazyListState()

    val visibleLogs = remember(logs, hideTreadmillFrames) {
        if (hideTreadmillFrames) logs.filter { !it.contains("<- 00002acd") }
        else logs
    }

    LaunchedEffect(visibleLogs.size) {
        if (tab == 0 && visibleLogs.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = DebugLog.exportText()
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            savedMessage = "Сохранено ${logs.size} записей"
        } catch (e: Exception) {
            savedMessage = "Ошибка: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        DebugLog.d("UI", "Debug: Очистить лог")
                        vm.clearLogs()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Очистить")
                    }
                    IconButton(onClick = {
                        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                        saveLauncher.launch("treadmill_log_$ts.txt")
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Сохранить в файл")
                    }
                    IconButton(onClick = {
                        val content = DebugLog.exportText()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Treadmill debug log")
                            putExtra(Intent.EXTRA_TEXT, content)
                        }
                        ctx.startActivity(Intent.createChooser(intent, "Поделиться логом"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Поделиться")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {

            savedMessage?.let {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(it, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(onClick = { savedMessage = null }) { Text("OK") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Дорожка: ${treadmillName ?: "—"}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(treadmillState.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (treadmillReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Протокол: FTMS", fontSize = 12.sp)
                    Text("Скорость: %.1f · Наклон: %.0f%%".format(data.speedKmh, data.inclinePercent), fontSize = 12.sp)
                    Text("Записей: ${logs.size}   (показано: ${visibleLogs.size})", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Отправить команду (hex)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { hexInput = it },
                            label = { Text("hex") },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { vm.sendRawHex(hexInput) },
                            enabled = treadmillReady,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) { Text("Отпр.") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Табы
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TabButton("Лог", tab == 0) { tab = 0 }
                TabButton("Справочник", tab == 1) { tab = 1 }
                TabButton("Конструктор", tab == 2) { tab = 2 }
            }

            Spacer(Modifier.height(8.dp))

            when (tab) {
                0 -> LogView(
                    logs = visibleLogs,
                    listState = listState,
                    hideTreadmillFrames = hideTreadmillFrames,
                    onToggleHide = { hideTreadmillFrames = !hideTreadmillFrames },
                    modifier = Modifier.weight(1f)
                )
                1 -> CommandReference(
                    treadmillReady = treadmillReady,
                    currentSpeedKmh = data.speedKmh,
                    onSend = { vm.sendRawHex(it) },
                    onStartBelt = { vm.startTreadmill() },
                    onAdjustSpeed = { delta -> vm.setSpeed(data.speedKmh + delta) },
                    modifier = Modifier.weight(1f)
                )
                else -> CommandConstructor(
                    treadmillReady = treadmillReady,
                    onSend = { vm.sendRawHex(it) },
                    onStartBelt = { vm.startTreadmill() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(text, fontSize = 13.sp) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(text, fontSize = 13.sp) }
    }
}

@Composable
private fun LogView(logs: List<String>, listState: LazyListState, hideTreadmillFrames: Boolean, onToggleHide: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxSize().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Лог событий", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Кадры 2ACD", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    // Компактный свитч
                    Switch(
                        checked = !hideTreadmillFrames,
                        onCheckedChange = { onToggleHide() },
                        modifier = Modifier.size(width = 36.dp, height = 20.dp)
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(logs.asReversed()) { line ->
                    Text(
                        line,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = lineColor(line),
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun lineColor(line: String): Color = when {
    line.contains("[ERR/") -> Color(0xFFE57373)
    line.contains("[UI]") -> Color(0xFF80DEEA)
    line.contains("[NAV]") -> Color(0xFFCE93D8)
    line.contains("[VM]") -> Color(0xFFFFB74D)
    line.contains("[WM]") -> Color(0xFFBA68C8)
    line.contains("[SVC]") -> Color(0xFF4DD0E1)
    line.contains("<-") -> Color(0xFF4CAF50)
    line.contains("->") -> Color(0xFF64B5F6)
    line.contains("[PROTO]") || line.contains("[SESSION]") -> Color(0xFFFFB300)
    else -> Color(0xFFBDBDBD)
}

private data class CmdEntry(val label: String, val hex: String, val note: String = "")

private fun buildCommandReference(): List<Pair<String, List<CmdEntry>>> {
    val speedsKmh = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 5.0, 6.0, 7.0, 8.0, 10.0, 12.0, 15.0, 20.0)
    val speedChange = speedsKmh.map { kmh ->
        val raw = (kmh * 100).roundToInt()
        CmdEntry("%.1f км/ч".format(kmh), "02 %02X %02X".format(raw and 0xFF, (raw shr 8) and 0xFF), "Только на ходу")
    }
    val inclines = listOf(0, 1, 2, 3, 4, 5, 6, 8, 10, 12, 15)
    val inclineCmds = inclines.map { p ->
        val raw = p * 10
        CmdEntry("$p %", "03 %02X %02X".format(raw and 0xFF, (raw shr 8) and 0xFF))
    }
    return listOf(
        "Управление" to listOf(
            CmdEntry("Request Control", "00", "Запросить контроль"),
            CmdEntry("Reset", "01", "Стандартный FTMS Reset"),
            CmdEntry("Start / Resume", "07", "Только ПОСЛЕ 00 + паузы"),
            CmdEntry("Stop (наш способ)", "02 00 00", "Скорость → 0"),
            CmdEntry("Pause (стандарт FTMS)", "08 01", "Сбрасывает таймер на дорожке")
        ),
        "Скорость — смена на ходу" to speedChange,
        "Наклон (целые %)" to inclineCmds
    )
}

@Composable
private fun CommandReference(treadmillReady: Boolean, currentSpeedKmh: Double, onSend: (String) -> Unit, onStartBelt: () -> Unit, onAdjustSpeed: (Double) -> Unit, modifier: Modifier = Modifier) {
    val groups = remember { buildCommandReference() }
    var lastSent by remember { mutableStateOf<String?>(null) }
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        if (!treadmillReady) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("Дорожка не подключена", Modifier.padding(10.dp), fontSize = 12.sp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Быстрая регулировка", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Текущая: %.1f км/ч".format(currentSpeedKmh), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { onAdjustSpeed(-1.0) }, enabled = treadmillReady && currentSpeedKmh > 1.0, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("−1", fontSize = 12.sp) }
                        OutlinedButton(onClick = { onAdjustSpeed(-0.5) }, enabled = treadmillReady && currentSpeedKmh > 0.5, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("−0.5", fontSize = 12.sp) }
                        Button(onClick = { onAdjustSpeed(0.5) }, enabled = treadmillReady && currentSpeedKmh > 0.5, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+0.5", fontSize = 12.sp) }
                        Button(onClick = { onAdjustSpeed(1.0) }, enabled = treadmillReady && currentSpeedKmh > 0.5, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("+1", fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onStartBelt, enabled = treadmillReady, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Запустить belt (00→07)")
                    }
                }
            }
        }
        groups.forEach { (title, commands) ->
            item { Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)) }
            items(commands) { cmd ->
                Card(Modifier.fillMaxWidth().clickable(enabled = treadmillReady) { onSend(cmd.hex); lastSent = "${cmd.label} (${cmd.hex})" }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cmd.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (cmd.note.isNotBlank()) Text(cmd.note, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(cmd.hex, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item {
            lastSent?.let {
                Card(Modifier.fillMaxWidth().padding(top = 6.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text("Отправлено: $it", Modifier.padding(10.dp), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun CommandConstructor(treadmillReady: Boolean, onSend: (String) -> Unit, onStartBelt: () -> Unit, modifier: Modifier = Modifier) {
    var speedStr by rememberSaveable { mutableStateOf("3") }
    var inclineStr by rememberSaveable { mutableStateOf("2") }
    var lastSent by remember { mutableStateOf<String?>(null) }
    val speed = speedStr.replace(',', '.').toDoubleOrNull() ?: 0.0
    val incline = inclineStr.replace(',', '.').toDoubleOrNull() ?: 0.0

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Скорость", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = speedStr, onValueChange = { speedStr = it }, label = { Text("км/ч") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                val raw = (speed * 100).roundToInt().coerceIn(0, 65535)
                val changeHex = "02 %02X %02X".format(raw and 0xFF, (raw shr 8) and 0xFF)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { onSend(changeHex); lastSent = "speed=$speed ($changeHex)" }, enabled = treadmillReady && speed > 0, modifier = Modifier.weight(1f)) { Text("Применить", fontSize = 12.sp) }
                    OutlinedButton(onClick = { onSend("02 00 00"); lastSent = "стоп (02 00 00)" }, enabled = treadmillReady, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Стоп", fontSize = 12.sp) }
                }
                Text(changeHex, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Наклон", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = inclineStr, onValueChange = { inclineStr = it }, label = { Text("%") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                val raw = (incline * 10).roundToInt().coerceIn(0, 32767)
                val hex = "03 %02X %02X".format(raw and 0xFF, (raw shr 8) and 0xFF)
                Button(onClick = { onSend(hex); lastSent = "incline=$incline ($hex)" }, enabled = treadmillReady, modifier = Modifier.fillMaxWidth()) { Text("Отправить наклон", fontSize = 13.sp) }
                Text(hex, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        lastSent?.let {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text("Отправлено: $it", Modifier.padding(10.dp), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
