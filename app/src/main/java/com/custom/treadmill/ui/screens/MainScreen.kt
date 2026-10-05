package com.custom.treadmill.ui.screens

import android.bluetooth.le.ScanResult
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.TreadmillApp
import com.custom.treadmill.ble.BleConnectionState
import com.custom.treadmill.ui.Routes
import com.custom.treadmill.ui.hasBlePermissions
import com.custom.treadmill.ui.requiredBlePermissions
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ScanMode

@Composable
fun MainScreen(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val ble = TreadmillApp.instance.bleManager

    val treadmillState by vm.treadmillState.collectAsState()
    val hrState by vm.hrState.collectAsState()
    val data by vm.treadmillData.collectAsState()
    val hr by vm.heartRate.collectAsState()
    val scanResults by vm.scanResults.collectAsState()
    val scanMode by vm.scanMode.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val settings by vm.settings.collectAsState()

    var permsGranted by remember { mutableStateOf(hasBlePermissions(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permsGranted = hasBlePermissions(ctx) }

    LaunchedEffect(Unit) {
        if (!permsGranted) permLauncher.launch(requiredBlePermissions())
    }
    LaunchedEffect(scanMode) {
        if (scanMode != ScanMode.NONE) {
            kotlinx.coroutines.delay(15_000)
            vm.stopScan()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Text("Treadmill Control", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "UNixFit-990x · FTMS",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        // ---------- Статусы (две в ряд, компактно) ----------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactStatus(
                label = "Дорожка",
                state = treadmillState,
                onConnect = { vm.startScanTreadmill() },
                onDisconnect = { vm.disconnectTreadmill() },
                modifier = Modifier.weight(1f)
            )
            CompactStatus(
                label = "Пульсометр",
                state = hrState,
                onConnect = { vm.startScanHeartRate() },
                onDisconnect = { vm.disconnectHeartRate() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- ПЛИТКИ УПРАВЛЕНИЯ (сверху!) ----------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ControlTile(
                label = "Скорость",
                value = "%.1f".format(data.speedKmh),
                unit = "км/ч",
                onMinus = { vm.setSpeedManual(data.speedKmh - 0.5) },
                onPlus = { vm.setSpeedManual(data.speedKmh + 0.5) },
                modifier = Modifier.weight(1f)
            )
            ControlTile(
                label = "Наклон",
                value = "%.1f".format(data.inclinePercent),
                unit = "%",
                onMinus = { vm.setIncline(data.inclinePercent - settings.inclineStep) },
                onPlus = { vm.setIncline(data.inclinePercent + settings.inclineStep) },
                onReset = { vm.setIncline(0.0) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Метрики ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    MetricBlock("Пульс", if (hr > 0) "$hr" else "—", "уд/мин", Modifier.weight(1f))
                    MetricBlock("Время", formatTime(data.elapsedSec), "", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    MetricBlock("Дистанция", "%.2f".format(data.distanceKm), "км", Modifier.weight(1f))
                    MetricBlock("Калории", "${data.calories}", "ккал", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Автопульс",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = if (settings.hrEnabled)
                            Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ) {
                        Text(
                            if (settings.hrEnabled) "ВКЛ" else "выкл",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            color = if (settings.hrEnabled)
                                Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Старт / Стоп ----------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.startTreadmill() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) { Text("Старт", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = { vm.stopTreadmill() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("Стоп", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Экстренная остановка ----------
        Button(
            onClick = { vm.emergencyStop() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) { Text("ЭКСТРЕННАЯ ОСТАНОВКА", fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(16.dp))

        // ---------- Навигация ----------
        NavButton("Тренировочные программы") { onNavigate(Routes.PROGRAMS) }
        NavButton("Настройки") { onNavigate(Routes.SETTINGS) }
        NavButton("Журнал тренировок") { onNavigate(Routes.HISTORY) }
        NavButton("Debug (BLE)") { onNavigate(Routes.DEBUG) }
        NavButton("О программе") { onNavigate(Routes.ABOUT) }

        statusMessage?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.clearStatusMessage() }) { Text("OK") }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    if (scanMode != ScanMode.NONE) {
        val filtered = remember(scanResults, scanMode) {
            scanResults.filter {
                if (scanMode == ScanMode.TREADMILL) ble.isTreadmillDevice(it)
                else ble.isHeartRateDevice(it)
            }
        }
        ScanDialog(
            title = if (scanMode == ScanMode.TREADMILL) "Выбор дорожки" else "Выбор пульсометра",
            devices = filtered,
            nameProvider = { ble.safeName(it) },
            onSelect = { r ->
                if (scanMode == ScanMode.TREADMILL) vm.connectTreadmill(r.device)
                else vm.connectHeartRate(r.device)
            },
            onDismiss = { vm.stopScan() }
        )
    }
}

@Composable
private fun CompactStatus(
    label: String,
    state: BleConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (dotColor, statusText) = when (state) {
        BleConnectionState.READY -> Color(0xFF2E7D32) to "подключено"
        BleConnectionState.CONNECTING,
        BleConnectionState.CONNECTED,
        BleConnectionState.DISCOVERING -> Color(0xFFF9A825) to "подключение…"
        BleConnectionState.FAILED -> Color(0xFFC62828) to "ошибка"
        BleConnectionState.DISCONNECTED -> Color(0xFF9E9E9E) to "не подключено"
    }
    val connected = state == BleConnectionState.READY

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = dotColor, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                statusText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            if (connected) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) { Text("Откл.", fontSize = 12.sp) }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) { Text("Подкл.", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ControlTile(
    label: String,
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label.uppercase(),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    value,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onMinus,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Меньше")
                }
                FilledTonalButton(
                    onClick = onPlus,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Больше")
                }
            }
            if (onReset != null) {
                Spacer(Modifier.height(2.dp))
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) { Text("Сбросить", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun MetricBlock(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun NavButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) { Text(text) }
}

@Composable
private fun ScanDialog(
    title: String,
    devices: List<ScanResult>,
    nameProvider: (ScanResult) -> String,
    onSelect: (ScanResult) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (devices.isEmpty()) {
                    Text("Поиск устройств…\nУбедитесь, что устройство включено и рядом.")
                } else {
                    devices.forEach { r ->
                        TextButton(onClick = { onSelect(r) }) {
                            Text("${nameProvider(r)}  [${r.rssi} dBm]")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun formatTime(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%02d:%02d".format(m, s)
}
