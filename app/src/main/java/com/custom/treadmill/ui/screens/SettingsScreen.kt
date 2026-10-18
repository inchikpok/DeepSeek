package com.custom.treadmill.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.data.repository.AppSettings
import com.custom.treadmill.data.repository.HrMode
import com.custom.treadmill.data.repository.ProtocolType
import com.custom.treadmill.data.repository.ThemeMode
import com.custom.treadmill.ui.viewmodels.MainViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: (() -> Unit)? = null,
    onOpenDebug: (() -> Unit)? = null,
    onOpenAbout: (() -> Unit)? = null
) {
    val settings by vm.settings.collectAsState()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("← Назад") }
                    }
                    Text(
                        "Настройки",
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
            }

            // ================= Внешний вид =================
            item { SectionHeader("Внешний вид") }
            item {
                SettingsCard {
                    EnumDropdown(
                        label = "Тема",
                        current = settings.themeMode,
                        options = ThemeMode.values().toList(),
                        optionLabel = {
                            when (it) {
                                ThemeMode.SYSTEM -> "Как в системе"
                                ThemeMode.LIGHT -> "Светлая"
                                ThemeMode.DARK -> "Тёмная"
                            }
                        },
                        onSelect = { v -> vm.updateSettings { it.copy(themeMode = v) } }
                    )
                }
            }

            // ================= Протокол =================
            item { SectionHeader("Протокол управления") }
            item {
                SettingsCard {
                    EnumDropdown(
                        label = "Протокол",
                        current = settings.protocol,
                        options = ProtocolType.values().toList(),
                        optionLabel = {
                            when (it) {
                                ProtocolType.FTMS -> "FTMS (стандартный)"
                                ProtocolType.FITSHOW -> "FitShow (proprietary)"
                            }
                        },
                        onSelect = { v -> vm.setProtocolType(v) }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    SwitchRow(
                        title = "Команда Start (0x07)",
                        subtitle = "Не выключайте — belt не поедет",
                        checked = settings.sendStartCommand,
                        onChange = { v -> vm.updateSettings { it.copy(sendStartCommand = v) } }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    ExpandableAdvanced(settings, vm)
                }
            }

            // ================= Скорость =================
            item { SectionHeader("Скорость") }
            item {
                SettingsCard {
                    SliderSetting(
                        label = "Минимум",
                        value = settings.minSpeedKmh.toFloat(),
                        valueRange = 0.5f..5.0f,
                        steps = 8,
                        display = { "%.1f км/ч".format(it) },
                        onValueChange = { v ->
                            vm.updateSettings {
                                it.copy(minSpeedKmh = (v * 10).roundToInt() / 10.0)
                            }
                        }
                    )
                    SliderSetting(
                        label = "Максимум",
                        value = settings.maxSpeedKmh.toFloat(),
                        valueRange = 5.0f..20.0f,
                        steps = 29,
                        display = { "%.1f км/ч".format(it) },
                        onValueChange = { v ->
                            vm.updateSettings {
                                it.copy(maxSpeedKmh = (v * 10).roundToInt() / 10.0)
                            }
                        }
                    )
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    EnumDropdown(
                        label = "Шаг кнопок +/−",
                        current = settings.speedStepKmh,
                        options = listOf(0.1, 0.25, 0.5, 1.0),
                        optionLabel = { "%.2f км/ч".format(it).replace(",00", "") },
                        onSelect = { v -> vm.updateSettings { it.copy(speedStepKmh = v) } }
                    )
                }
            }

            // ================= Наклон =================
            item { SectionHeader("Наклон") }
            item {
                SettingsCard {
                    SliderSetting(
                        label = "Максимум",
                        value = settings.maxInclinePercent.toFloat(),
                        valueRange = 5.0f..20.0f,
                        steps = 14,
                        display = { "${it.roundToInt()} %" },
                        onValueChange = { v ->
                            vm.updateSettings {
                                it.copy(maxInclinePercent = v.roundToInt().toDouble())
                            }
                        }
                    )
                    // Шаг наклона убран — всегда 1%
                }
            }

            // ================= Автопульс =================
            item { SectionHeader("Автопульс") }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "Авторегулировка по пульсу",
                        subtitle = if (settings.hrEnabled) "Включена" else "Выключена",
                        checked = settings.hrEnabled,
                        onChange = { vm.setAutoHrEnabled(it) }
                    )

                    AnimatedVisibility(visible = settings.hrEnabled) {
                        Column {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            EnumDropdown(
                                label = "Режим",
                                current = settings.hrMode,
                                options = HrMode.values().toList(),
                                optionLabel = {
                                    when (it) {
                                        HrMode.TARGET -> "Целевой пульс"
                                        HrMode.ZONE -> "Зона (мин-макс)"
                                    }
                                },
                                onSelect = { v -> vm.setHrMode(v) }
                            )

                            if (settings.hrMode == HrMode.TARGET) {
                                SliderSetting(
                                    label = "Целевой пульс",
                                    value = settings.targetHr.toFloat(),
                                    valueRange = 90f..190f,
                                    steps = 99,
                                    display = { "${it.roundToInt()} уд/мин" },
                                    onValueChange = { v ->
                                        vm.updateSettings { it.copy(targetHr = v.roundToInt()) }
                                    }
                                )
                                SliderSetting(
                                    label = "Допуск X (выше цели)",
                                    value = settings.thresholdHigh.toFloat(),
                                    valueRange = 1f..20f,
                                    steps = 18,
                                    display = { "${it.roundToInt()} уд" },
                                    onValueChange = { v ->
                                        vm.updateSettings { it.copy(thresholdHigh = v.roundToInt()) }
                                    }
                                )
                                SliderSetting(
                                    label = "Допуск Y (ниже цели)",
                                    value = settings.thresholdLow.toFloat(),
                                    valueRange = 1f..20f,
                                    steps = 18,
                                    display = { "${it.roundToInt()} уд" },
                                    onValueChange = { v ->
                                        vm.updateSettings { it.copy(thresholdLow = v.roundToInt()) }
                                    }
                                )
                            } else {
                                SliderSetting(
                                    label = "Мин пульс",
                                    value = settings.zoneMin.toFloat(),
                                    valueRange = 90f..180f,
                                    steps = 89,
                                    display = { "${it.roundToInt()} уд/мин" },
                                    onValueChange = { v ->
                                        vm.updateSettings { it.copy(zoneMin = v.roundToInt()) }
                                    }
                                )
                                SliderSetting(
                                    label = "Макс пульс",
                                    value = settings.zoneMax.toFloat(),
                                    valueRange = 100f..190f,
                                    steps = 89,
                                    display = { "${it.roundToInt()} уд/мин" },
                                    onValueChange = { v ->
                                        vm.updateSettings { it.copy(zoneMax = v.roundToInt()) }
                                    }
                                )
                            }

                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            SliderSetting(
                                label = "Интервал проверки",
                                value = settings.intervalSec.toFloat(),
                                valueRange = 5f..120f,
                                steps = 22,
                                display = { "${it.roundToInt()} сек" },
                                onValueChange = { v ->
                                    vm.updateSettings { it.copy(intervalSec = v.roundToInt()) }
                                }
                            )
                            SliderSetting(
                                label = "Шаг изменения скорости",
                                value = settings.stepKmh.toFloat(),
                                valueRange = 0.1f..2.0f,
                                steps = 18,
                                display = { "%.1f км/ч".format(it) },
                                onValueChange = { v ->
                                    vm.updateSettings {
                                        it.copy(stepKmh = (v * 10).roundToInt() / 10.0)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ================= О приложении =================
            if (onOpenDebug != null || onOpenAbout != null) {
                item { SectionHeader("О приложении") }
                item {
                    SettingsCard {
                        onOpenDebug?.let {
                            ActionRow(
                                icon = { Icon(Icons.Default.BugReport, null, Modifier.size(22.dp)) },
                                title = "Debug (BLE)",
                                subtitle = "Логи, hex-команды, справочник",
                                onClick = it
                            )
                        }
                        if (onOpenDebug != null && onOpenAbout != null) {
                            HorizontalDivider()
                        }
                        onOpenAbout?.let {
                            ActionRow(
                                icon = { Icon(Icons.Default.Info, null, Modifier.size(22.dp)) },
                                title = "О программе",
                                subtitle = "Инструкция и справочник",
                                onClick = it
                            )
                        }
                    }
                }
            }
        }
    }
}

// ================================================================
//  Компоненты
// ================================================================

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 0.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    current: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = optionLabel(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable) // Исправляет позиционирование меню
                .fillMaxWidth(),
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    display(value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpandableAdvanced(
    settings: AppSettings,
    vm: MainViewModel
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "rot")

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Дополнительно",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(top = 4.dp)) {
                OutlinedTextField(
                    value = settings.manualWriteUuid,
                    onValueChange = { v ->
                        vm.updateSettings { it.copy(manualWriteUuid = v) }
                    },
                    label = { Text("UUID записи (опц.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = settings.manualNotifyUuid,
                    onValueChange = { v ->
                        vm.updateSettings { it.copy(manualNotifyUuid = v) }
                    },
                    label = { Text("UUID уведомлений (опц.)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
