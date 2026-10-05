package com.custom.treadmill.ui.screens

import android.bluetooth.le.ScanResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext


@Composable
fun MainScreen(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val ble = TreadmillApp.instance.bleManager

    val treadmillState by vm.treadmillState.collectAsState()
    val hrState by vm.hrState.collectAsState()
    val data by vm.treadmillData.collectAsState()
    val targetSpeed by vm.targetSpeed.collectAsState()
    val targetIncline by vm.targetIncline.collectAsState()
    val hr by vm.heartRate.collectAsState()
    val scanResults by vm.scanResults.collectAsState()
    val scanMode by vm.scanMode.collectAsState()
    val statusMessage by vm.statusMessage.collectAsState()
    val settings by vm.settings.collectAsState()

    var permsGranted by remember { mutableStateOf(hasBlePermissions(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permsGranted = hasBlePermissions(ctx) }

    LaunchedEffect(Unit) { if (!permsGranted) permLauncher.launch(requiredBlePermissions()) }
    LaunchedEffect(scanMode) {
        if (scanMode != ScanMode.NONE) { kotlinx.coroutines.delay(15_000); vm.stopScan() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // ---------- Шапка ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Treadmill Control",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "UNixFit-990x · ${settings.protocol}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onNavigate(Routes.DEBUG) }) {
                Icon(
                    Icons.Default.BugReport,
                    contentDescription = "Debug",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- Статусы подключений ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(
                label = "Дорожка",
                state = treadmillState,
                onConnect = { vm.startScanTreadmill() },
                onDisconnect = { vm.disconnectTreadmill() },
                modifier = Modifier.weight(1f)
            )
            StatusChip(
                label = "Пульсометр",
                state = hrState,
                onConnect = { vm.startScanHeartRate() },
                onDisconnect = { vm.disconnectHeartRate() },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---------- Плитки управления ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlTile(
                label = "Скорость",
                value = "%.1f".format(targetSpeed),
                unit = "км/ч",
                onMinus = { vm.setSpeed(targetSpeed - 0.5) },
                onPlus  = { vm.setSpeed(targetSpeed + 0.5) },
                modifier = Modifier.weight(1f)
            )
            ControlTile(
                label = "Наклон",
                value = "${targetIncline.roundToInt()}",
                unit = "%",
                onMinus = { vm.setIncline(max(0.0, targetIncline.roundToInt() - 1.0)) },
                onPlus  = { vm.setIncline(targetIncline.roundToInt() + 1.0) },
                onReset = { vm.setIncline(0.0) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---------- Метрики в одну строку ----------
        Card(
            Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Metric("Пульс", if (hr > 0) "$hr" else "—", "уд/мин")
                Metric("Время", formatTime(data.elapsedSec), "")
                Metric("Дистанция", "%.2f".format(data.distanceKm), "км")
                Metric("Калории", "${data.calories}", "ккал")
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------- Старт / Стоп ----------
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { vm.startTreadmill() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) { Text("Старт", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }

            FilledTonalButton(
                onClick = { vm.stopTreadmill() },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) { Text("Стоп", fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(8.dp))

        // ---------- Экстренная остановка ----------
        Button(
            onClick = { vm.emergencyStop() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Text("ЭКСТРЕННАЯ ОСТАНОВКА", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // ---------- Автопульс ----------
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Авторегулировка по пульсу",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = if (settings.hrEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            ) {
                Text(
                    if (settings.hrEnabled) "ВКЛ" else "выкл",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (settings.hrEnabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- Сообщение статуса ----------
        statusMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    TextButton(onClick = { vm.clearStatusMessage() }) { Text("OK") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    // ---------- Диалог сканирования ----------
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

// ================================================================
//  Компактная карточка статуса
// ================================================================
@Composable
private fun StatusChip(
    label: String,
    state: BleConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (dotColor, statusText) = when (state) {
        BleConnectionState.READY -> MaterialTheme.colorScheme.primary to "готово"
        BleConnectionState.CONNECTING,
        BleConnectionState.CONNECTED,
        BleConnectionState.DISCOVERING -> MaterialTheme.colorScheme.tertiary to "поиск…"
        BleConnectionState.FAILED -> MaterialTheme.colorScheme.error to "ошибка"
        BleConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.outline to "нет"
    }
    val connected = state == BleConnectionState.READY
    val busy = state == BleConnectionState.CONNECTING ||
            state == BleConnectionState.CONNECTED ||
            state == BleConnectionState.DISCOVERING

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = dotColor,
                    shape = CircleShape,
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                statusText,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            when {
                connected -> OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) { Text("Откл.", fontSize = 12.sp) }
                busy -> OutlinedButton(
                    onClick = onDisconnect,
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) { Text("…", fontSize = 12.sp) }
                else -> Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) { Text("Подкл.", fontSize = 12.sp) }
            }
        }
    }
}

// ================================================================
//  Плитка управления (скорость / наклон)
// ================================================================
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label.uppercase(),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilledTonalButton(
                    onClick = onMinus,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = "Меньше",
                        modifier = Modifier.size(18.dp)
                    )
                }
                FilledTonalButton(
                    onClick = onPlus,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Больше",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onReset != null) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 0.dp)
                ) { Text("Сброс", fontSize = 11.sp) }
            }
        }
    }
}

// ================================================================
//  Одна метрика (4 в ряд)
// ================================================================
@Composable
private fun Metric(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (unit.isNotEmpty()) {
            Text(
                unit,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ================================================================
//  Диалог сканирования
// ================================================================
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
