package com.custom.treadmill.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramEntity
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ProgramViewModel

// ================================================================
//  Список программ
// ================================================================
@Composable
fun ProgramsScreen(
    mainVm: MainViewModel,
    programVm: ProgramViewModel,
    onBack: () -> Unit,
    onStartWorkout: (ProgramData) -> Unit
) {
    val ctx = LocalContext.current
    val programs by programVm.programs.collectAsState()

    // id == 0 → новая программа; id != 0 → редактирование
    var editingId by remember { mutableStateOf(-1L) }   // -1 = редактор закрыт
    var editingData by remember { mutableStateOf<ProgramData?>(null) }

    // ---------- Экспорт ----------
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val data = editingData ?: return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(programVm.exportJson(data).toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
        }
    }

    // ---------- Импорт ----------
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val text = ctx.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return@rememberLauncherForActivityResult
            val data = programVm.importJson(text)
            programVm.save(data)
        } catch (_: Exception) {
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // ---------- Шапка ----------
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Spacer(Modifier.width(8.dp))
            Text("Программы", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(Modifier.height(12.dp))

        // ---------- Кнопки ----------
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                editingId = 0L
                editingData = ProgramData(
                    name = "Новая программа",
                    description = "",
                    segments = listOf(
                        ProgramSegment(300, 5.0, 0.0, "Разминка"),
                        ProgramSegment(120, 10.0, 0.0, "Интервал 1"),
                        ProgramSegment(120, 6.0, 0.0, "Отдых"),
                        ProgramSegment(300, 4.0, 0.0, "Заминка")
                    )
                )
            }) { Text("Создать") }

            OutlinedButton(onClick = {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }) { Text("Импорт") }
        }

        Spacer(Modifier.height(12.dp))

        // ---------- Список ----------
        if (programs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нет программ. Нажмите «Создать».")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(programs, key = { it.id }) { p ->
                    ProgramCard(
                        entity = p,
                        onEdit = {
                            editingId = p.id
                            editingData = programVm.toData(p)
                        },
                        onDelete = { programVm.delete(p) },
                        onExport = {
                            editingData = programVm.toData(p)
                            exportLauncher.launch("${p.name}.json")
                        },
                        onStart = { onStartWorkout(programVm.toData(p)) }
                    )
                }
            }
        }
    }

    // ---------- Редактор ----------
    editingData?.let { data ->
        val isNew = editingId == 0L
        ProgramEditorScreen(
            initial = data,
            isNew = isNew,
            onSave = { newData ->
                programVm.save(newData, if (isNew) 0L else editingId)
                editingData = null
                editingId = -1L
            },
            onDismiss = {
                editingData = null
                editingId = -1L
            }
        )
    }
}

// ================================================================
//  Карточка программы в списке
// ================================================================
@Composable
private fun ProgramCard(
    entity: ProgramEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onStart: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(entity.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (entity.description.isNotBlank()) {
                Text(entity.description, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onStart) { Text("Старт") }
                OutlinedButton(onClick = onEdit) { Text("Изм.") }
                OutlinedButton(onClick = onExport) { Text("Экспорт") }
                OutlinedButton(onClick = onDelete) { Text("Удал.") }
            }
        }
    }
}

// ================================================================
//  UI-состояние одного сегмента (строки, чтобы можно было печатать)
// ================================================================
private data class SegmentEdit(
    val name: String = "",
    val durationStr: String = "60",
    val speedStr: String = "5.0",
    val inclineStr: String = "0.0"
)

// ================================================================
//  Полноэкранный редактор программы
// ================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEditorScreen(
    initial: ProgramData,
    isNew: Boolean,
    onSave: (ProgramData) -> Unit,
    onDismiss: () -> Unit
) {
    // mutableStateListOf → изменения элементов триггерят рекомпозицию корректно
    val segments = remember(initial) {
        mutableStateListOf<SegmentEdit>().apply {
            initial.segments.forEach { s ->
                add(
                    SegmentEdit(
                        name = s.name,
                        durationStr = s.durationSec.toString(),
                        speedStr = s.speedKmh.toString(),
                        inclineStr = s.inclinePercent.toString()
                    )
                )
            }
            if (isEmpty()) add(SegmentEdit("Разминка", "300", "5.0", "0.0"))
        }
    }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var description by remember(initial) { mutableStateOf(initial.description) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(if (isNew) "Новая программа" else "Редактор программы") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                val parsed = parseSegments(segments)
                                if (parsed == null) {
                                    errorText = "Проверьте поля: время и скорость должны быть числами"
                                    return@TextButton
                                }
                                if (parsed.isEmpty()) {
                                    errorText = "Добавьте хотя бы один сегмент"
                                    return@TextButton
                                }
                                onSave(
                                    ProgramData(
                                        name = name.ifBlank { "Без названия" },
                                        description = description,
                                        segments = parsed
                                    )
                                )
                            }) { Text("Сохранить", fontWeight = FontWeight.Bold) }
                        }
                    )
                }
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp)
                ) {
                    // --- Название и описание ---
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Сегменты (${segments.size})",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Всего: ${totalMinutes(segments)} мин",
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    // --- Список сегментов ---
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(segments) { index, seg ->
                            SegmentEditor(
                                index = index,
                                seg = seg,
                                onChange = { newSeg ->
                                    segments[index] = newSeg
                                },
                                onDelete = {
                                    if (segments.size > 1) segments.removeAt(index)
                                },
                                canDelete = segments.size > 1
                            )
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }

                    // --- Кнопка добавления ---
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val last = segments.lastOrNull()
                            val newSpeed = last?.speedStr ?: "5.0"
                            val newIncline = last?.inclineStr ?: "0.0"
                            segments.add(
                                SegmentEdit(
                                    name = "Сегмент ${segments.size + 1}",
                                    durationStr = "60",
                                    speedStr = newSpeed,
                                    inclineStr = newIncline
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Добавить сегмент")
                    }

                    errorText?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ================================================================
//  Редактор одного сегмента
// ================================================================
@Composable
private fun SegmentEditor(
    index: Int,
    seg: SegmentEdit,
    onChange: (SegmentEdit) -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            // Заголовок с номером и кнопкой удаления
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "#${index + 1}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить сегмент")
                }
            }

            // Название сегмента
            OutlinedTextField(
                value = seg.name,
                onValueChange = { onChange(seg.copy(name = it)) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))

            // Время / Скорость / Наклон в одну строку
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = seg.durationStr,
                    onValueChange = { onChange(seg.copy(durationStr = filterNumeric(it))) },
                    label = { Text("Сек") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = seg.speedStr,
                    onValueChange = { onChange(seg.copy(speedStr = filterDecimal(it))) },
                    label = { Text("Км/ч") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = seg.inclineStr,
                    onValueChange = { onChange(seg.copy(inclineStr = filterDecimal(it))) },
                    label = { Text("Наклон %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ================================================================
//  Утилиты
// ================================================================

/** Оставляем только цифры. */
private fun filterNumeric(s: String): String = s.filter { it.isDigit() }

/** Оставляем цифры, точку и (на всякий случай) запятую — превратим в точку при парсинге. */
private fun filterDecimal(s: String): String =
    s.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')

/** Парсинг сегментов; возвращает null, если где-то невалидное число. */
private fun parseSegments(list: List<SegmentEdit>): List<ProgramSegment>? {
    val result = ArrayList<ProgramSegment>(list.size)
    for (s in list) {
        val dur = s.durationStr.toIntOrNull() ?: return null
        val spd = s.speedStr.replace(',', '.').toDoubleOrNull() ?: return null
        val inc = s.inclineStr.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (dur <= 0) return null
        result.add(
            ProgramSegment(
                durationSec = dur,
                speedKmh = spd,
                inclinePercent = inc,
                name = s.name.ifBlank { "Сегмент" }
            )
        )
    }
    return result
}

/** Сумма минут (для отображения). */
private fun totalMinutes(list: List<SegmentEdit>): Int {
    var sum = 0
    for (s in list) sum += s.durationStr.toIntOrNull() ?: 0
    return sum / 60
}
