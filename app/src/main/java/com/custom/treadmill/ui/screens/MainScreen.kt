package com.custom.treadmill.ui.screens

import android.bluetooth.le.ScanResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
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
import com.custom.treadmill.ui.viewmodels.ScanMode   // <-- ИСПРАВЛЕНО

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
            .padding(16.dp)
    ) {
        Text("Treadmill Control", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("UNixFit-990x · FTMS", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        StatusCard(
            title = "Дорожка",
            state = treadmillState,
            onConnect = { vm.startScanTreadmill() },
            onDisconnect = { vm.disconnectTreadmill() }
        )
        Spacer(Modifier.height(8.dp))
        StatusCard(
            title = "Пульсометр",
            state = hrState,
            onConnect = { vm.startScanHeartRate() },
            onDisconnect = { vm.disconnectHeartRate() }
        )

        Spacer(Modifier.height(16.dp))

        Card(elevation = CardDefaults.cardElevation(4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Текущие данные", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricBlock("Скорость", "%.1f км/ч".format(data.speedKmh))
                    MetricBlock("Пульс", if (hr > 0) "$hr уд/мин" else "—")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricBlock("Дистанция", "%.2f км".format(data.distanceKm))
                    MetricBlock("Время", formatTime(data.elapsedSec))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricBlock("Калории", "${data.calories} ккал")
                    MetricBlock("Автопульс", if (settings.hrEnabled) "ВКЛ" else "выкл")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.startTreadmill() }, modifier = Modifier.weight(1f)) { Text("Старт") }
            Button(
                onClick = { vm.stopTreadmill() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF616161))
            ) { Text("Стоп") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.setSpeedManual(data.speedKmh - 0.5) },
                modifier = Modifier.weight(1f)
            ) { Text("− 0.5") }
            OutlinedButton(
                onClick = { vm.setSpeedManual(data.speedKmh + 0.5) },
                modifier = Modifier.weight(1f)
            ) { Text("+ 0.5") }
            Button(
                onClick = { vm.emergencyStop() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
            ) { Text("СТОП!") }
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        NavButton("Тренировочные программы") { onNavigate(Routes.PROGRAMS) }
        NavButton("Настройки") { onNavigate(Routes.SETTINGS) }
        NavButton("Журнал тренировок") { onNavigate(Routes.HISTORY) }
        NavButton("Debug (BLE)") { onNavigate(Routes.DEBUG) }
        NavButton("О программе") { onNavigate(Routes.ABOUT) }

        statusMessage?.let { msg ->
            Spacer(Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(msg, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.clearStatusMessage() }) { Text("OK") }
                }
            }
        }
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
private fun MetricBlock(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusCard(
    title: String,
    state: BleConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val (color, text) = when (state) {
        BleConnectionState.READY -> Color(0xFF2E7D32) to "подключено"
        BleConnectionState.CONNECTING,
        BleConnectionState.CONNECTED,
        BleConnectionState.DISCOVERING -> Color(0xFFF9A825) to "подключение…"
        BleConnectionState.FAILED -> Color(0xFFC62828) to "ошибка"
        BleConnectionState.DISCONNECTED -> Color(0xFF9E9E9E) to "не подключено"
    }
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(text, color = color, fontSize = 13.sp)
            }
            if (state == BleConnectionState.DISCONNECTED || state == BleConnectionState.FAILED) {
                Button(onClick = onConnect) { Text("Подключить") }
            } else {
                OutlinedButton(onClick = onDisconnect) { Text("Отключить") }
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
