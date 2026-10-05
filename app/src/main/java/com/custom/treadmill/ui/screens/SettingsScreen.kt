package com.custom.treadmill.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.custom.treadmill.data.repository.HrMode
import com.custom.treadmill.data.repository.ProtocolType
import com.custom.treadmill.data.repository.ThemeMode
import com.custom.treadmill.ui.viewmodels.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: (() -> Unit)? = null) {
    val settings by vm.settings.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("← Назад") }
        }
        Text("Настройки", fontWeight = FontWeight.Bold, fontSize = 20.dp.value.sp())

        Spacer(Modifier.height(12.dp))

        // ---------- Внешний вид ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Внешний вид", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { vm.updateSettings { it.copy(themeMode = ThemeMode.SYSTEM) } }
                    )
                    Text("Как в системе")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.themeMode == ThemeMode.LIGHT,
                        onClick = { vm.updateSettings { it.copy(themeMode = ThemeMode.LIGHT) } }
                    )
                    Text("Светлая")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.themeMode == ThemeMode.DARK,
                        onClick = { vm.updateSettings { it.copy(themeMode = ThemeMode.DARK) } }
                    )
                    Text("Тёмная")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Протокол ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Протокол управления", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.protocol == ProtocolType.FTMS,
                        onClick = { vm.setProtocolType(ProtocolType.FTMS) }
                    )
                    Text("FTMS (стандартный)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.protocol == ProtocolType.FITSHOW,
                        onClick = { vm.setProtocolType(ProtocolType.FITSHOW) }
                    )
                    Text("FitShow (proprietary)")
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = settings.manualWriteUuid,
                    onValueChange = { v -> vm.updateSettings { it.copy(manualWriteUuid = v) } },
                    label = { Text("UUID записи (manual, опц.)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = settings.manualNotifyUuid,
                    onValueChange = { v -> vm.updateSettings { it.copy(manualNotifyUuid = v) } },
                    label = { Text("UUID уведомлений (manual, опц.)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Скорость ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Ограничения скорости", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = settings.minSpeedKmh.toString(),
                    onValueChange = { v ->
                        val d = v.toDoubleOrNull() ?: return@OutlinedTextField
                        vm.updateSettings { it.copy(minSpeedKmh = d) }
                    },
                    label = { Text("Минимум, км/ч") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = settings.maxSpeedKmh.toString(),
                    onValueChange = { v ->
                        val d = v.toDoubleOrNull() ?: return@OutlinedTextField
                        vm.updateSettings { it.copy(maxSpeedKmh = d) }
                    },
                    label = { Text("Максимум, км/ч (по умолчанию 12)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Наклон ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Наклон дорожки", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = settings.maxInclinePercent.toString(),
                    onValueChange = { v ->
                        val d = v.toDoubleOrNull() ?: return@OutlinedTextField
                        vm.updateSettings { it.copy(maxInclinePercent = d) }
                    },
                    label = { Text("Максимум, % (по умолчанию 15)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = settings.inclineStep.toString(),
                    onValueChange = { v ->
                        val d = v.toDoubleOrNull() ?: return@OutlinedTextField
                        vm.updateSettings { it.copy(inclineStep = d) }
                    },
                    label = { Text("Шаг кнопок, % (по умолчанию 1.0)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Авторегулировка ----------
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Авторегулировка по пульсу",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.hrEnabled,
                        onCheckedChange = { vm.setAutoHrEnabled(it) }
                    )
                }
                Divider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.hrMode == HrMode.TARGET,
                        onClick = { vm.setHrMode(HrMode.TARGET) }
                    )
                    Text("Целевой пульс")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.hrMode == HrMode.ZONE,
                        onClick = { vm.setHrMode(HrMode.ZONE) }
                    )
                    Text("Зона (мин–макс)")
                }
                Spacer(Modifier.height(8.dp))

                if (settings.hrMode == HrMode.TARGET) {
                    OutlinedTextField(
                        value = settings.targetHr.toString(),
                        onValueChange = { v ->
                            val i = v.toIntOrNull() ?: return@OutlinedTextField
                            vm.updateSettings { it.copy(targetHr = i) }
                        },
                        label = { Text("Целевой пульс") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Row {
                        OutlinedTextField(
                            value = settings.thresholdHigh.toString(),
                            onValueChange = { v ->
                                val i = v.toIntOrNull() ?: return@OutlinedTextField
                                vm.updateSettings { it.copy(thresholdHigh = i) }
                            },
                            label = { Text("X (выше цели, +)") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.height(1.dp))
                        OutlinedTextField(
                            value = settings.thresholdLow.toString(),
                            onValueChange = { v ->
                                val i = v.toIntOrNull() ?: return@OutlinedTextField
                                vm.updateSettings { it.copy(thresholdLow = i) }
                            },
                            label = { Text("Y (ниже цели, −)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row {
                        OutlinedTextField(
                            value = settings.zoneMin.toString(),
                            onValueChange = { v ->
                                val i = v.toIntOrNull() ?: return@OutlinedTextField
                                vm.updateSettings { it.copy(zoneMin = i) }
                            },
                            label = { Text("Мин пульс") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = settings.zoneMax.toString(),
                            onValueChange = { v ->
                                val i = v.toIntOrNull() ?: return@OutlinedTextField
                                vm.updateSettings { it.copy(zoneMax = i) }
                            },
                            label = { Text("Макс пульс") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.intervalSec.toString(),
                    onValueChange = { v ->
                        val i = v.toIntOrNull() ?: return@OutlinedTextField
                        vm.updateSettings { it.copy(intervalSec = i) }
                    },
                    label = { Text("Интервал корректировки, сек") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = settings.stepKmh.toString(),
                    onValueChange = { v ->
                        val d = v.toDoubleOrNull() ?: return@OutlinedTextField
                        vm.updateSettings { it.copy(stepKmh = d) }
                    },
                    label = { Text("Шаг изменения, км/ч") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.setAutoHrEnabled(settings.hrEnabled) }) {
                    Text("Применить")
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

private fun Int.sp() = this.toString()
