package com.custom.treadmill.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramEntity
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ProgramViewModel

enum class SortMode { NAME_ASC, NAME_DESC, NEWEST }

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

    var editingId by remember { mutableStateOf(-1L) }
    var editingData by remember { mutableStateOf<ProgramData?>(null) }
    var showTemplates by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME_ASC) }

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

    // ---------- Фильтр + сортировка ----------
    val visible = remember(programs, searchQuery, sortMode) {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) programs
        else programs.filter {
            it.name.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
        when (sortMode) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.NEWEST -> filtered.sortedByDescending { it.id }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // ---------- Шапка ----------
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(4.dp)) {
                    Text("← Назад")
                }
                Spacer(Modifier.width(8.dp))
                Text("Программы", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))

            // Кнопки создания
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showTemplates = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Создать")
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) { Text("Импорт") }
            }

            Spacer(Modifier.height(10.dp))

            // Поиск
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск по названию…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Сортировка
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Сортировка:", fontSize = 13.sp)
                SortChip("A→Я", sortMode == SortMode.NAME_ASC) { sortMode = SortMode.NAME_ASC }
                SortChip("Я→A", sortMode == SortMode.NAME_DESC) { sortMode = SortMode.NAME_DESC }
                SortChip("Новые", sortMode == SortMode.NEWEST) { sortMode = SortMode.NEWEST }
            }

            Spacer(Modifier.height(12.dp))
        }

        // ---------- Список ----------
        if (visible.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (programs.isEmpty()) "У вас пока нет программ"
                        else "Ничего не найдено",
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (programs.isEmpty())
                            "Нажмите «Создать», чтобы добавить первую,\n" +
                                    "или «Импорт», чтобы загрузить из JSON."
                        else "Попробуйте изменить поиск",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(visible, key = { it.id }) { p ->
                    val data = remember(p.id, p.segmentsJson) { programVm.toData(p) }
                    ProgramCard(
                        entity = p,
                        data = data,
                        onEdit = { editingId = p.id; editingData = data },
                        onDuplicate = {
                            // Сохраняем копию с новым id
                            val copy = data.copy(name = "${data.name} (копия)")
                            programVm.save(copy, 0L)
                        },
                        onDelete = { programVm.delete(p) },
                        onExport = {
                            editingData = data
                            exportLauncher.launch("${p.name}.json")
                        },
                        onStart = { onStartWorkout(data) }
                    )
                }
            }
        }
    }

    // ---------- Диалог шаблонов ----------
    if (showTemplates) {
        TemplatesDialog(
            onPick = { template ->
                showTemplates = false
                if (template == null) {
                    // С нуля
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
                } else {
                    editingId = 0L
                    editingData = template
                }
            },
            onDismiss = { showTemplates = false }
        )
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
//  Чип сортировки
// ================================================================
@Composable
private fun SortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) { Text(text, fontSize = 12.sp) }
    } else {
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) { Text(text, fontSize = 12.sp) }
    }
}

// ================================================================
//  Диалог выбора шаблона
// ================================================================
@Composable
private fun TemplatesDialog(
    onPick: (ProgramData?) -> Unit,
    onDismiss: () -> Unit
) {
    val templates = remember { buildTemplates() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Шаблон программы") },
        text = {
            Column {
                Text("Выберите готовый шаблон или начните с нуля.")
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { onPick(null) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Пустая (с нуля)", modifier = Modifier.fillMaxWidth()) }
                Spacer(Modifier.height(4.dp))
                templates.forEach { (title, data) ->
                    TextButton(
                        onClick = { onPick(data) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${data.segments.size} сегм. · " +
                                        "${data.segments.sumOf { it.durationSec } / 60} мин",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun buildTemplates(): List<Pair<String, ProgramData>> {
    val intervals = ProgramData(
        name = "Интервалы 4×3 мин",
        description = "VO₂max: 4 быстрых отрезка по 3 минуты",
        segments = listOf(
            ProgramSegment(390, 9.5, 1.0, "Разминка: лёгкий бег"),
            ProgramSegment(30, 16.0, 1.0, "Разминка: ускорение"),
            ProgramSegment(60, 8.0, 1.0, "Разминка: трусца"),
            ProgramSegment(120, 9.0, 1.0, "Разминка: спокойный бег"),
            ProgramSegment(195, 15.0, 1.0, "Интервал 1/4"),
            ProgramSegment(160, 8.5, 1.0, "Отдых: трусца"),
            ProgramSegment(195, 15.0, 1.0, "Интервал 2/4"),
            ProgramSegment(160, 8.5, 1.0, "Отдых: трусца"),
            ProgramSegment(195, 15.0, 1.0, "Интервал 3/4"),
            ProgramSegment(160, 8.5, 1.0, "Отдых: трусца"),
            ProgramSegment(195, 15.0, 1.0, "Интервал 4/4"),
            ProgramSegment(360, 9.0, 1.0, "Заминка: лёгкий бег"),
            ProgramSegment(240, 5.0, 0.0, "Заминка: ходьба")
        )
    )
    val pyramid = ProgramData(
        name = "Пирамида",
        description = "1-2-3-2-1 мин с отдыхом",
        segments = listOf(
            ProgramSegment(300, 9.5, 1.0, "Разминка"),
            ProgramSegment(60, 12.0, 1.0, "Пирамида 1 мин"),
            ProgramSegment(90, 8.5, 1.0, "Отдых"),
            ProgramSegment(120, 12.5, 1.0, "Пирамида 2 мин"),
            ProgramSegment(90, 8.5, 1.0, "Отдых"),
            ProgramSegment(180, 13.0, 1.0, "Пирамида 3 мин"),
            ProgramSegment(90, 8.5, 1.0, "Отдых"),
            ProgramSegment(120, 12.5, 1.0, "Пирамида 2 мин"),
            ProgramSegment(90, 8.5, 1.0, "Отдых"),
            ProgramSegment(60, 12.0, 1.0, "Пирамида 1 мин"),
            ProgramSegment(300, 9.0, 1.0, "Заминка")
        )
    )
    val hills = ProgramData(
        name = "Горки 6×1 мин",
        description = "6 минут работы в гору с отдыхом",
        segments = listOf(
            ProgramSegment(300, 9.5, 1.0, "Разминка"),
            ProgramSegment(60, 8.0, 1.0, "Разминка: трусца"),
            ProgramSegment(60, 12.0, 6.0, "Горка 1/6"),
            ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 2/6"),
            ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 3/6"),
            ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 4/6"),
            ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 5/6"),
            ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 6/6"),
            ProgramSegment(300, 9.0, 1.0, "Заминка")
        )
    )
    val fartlek = ProgramData(
        name = "Fartlek 20 мин",
        description = "Свободная игра скоростей",
        segments = listOf(
            ProgramSegment(300, 9.5, 1.0, "Разминка"),
            ProgramSegment(60, 14.0, 1.0, "Спринт"),
            ProgramSegment(120, 9.0, 1.0, "Трусца"),
            ProgramSegment(90, 13.0, 1.0, "Темповый"),
            ProgramSegment(120, 9.0, 1.0, "Трусца"),
            ProgramSegment(60, 15.0, 1.0, "Спринт"),
            ProgramSegment(120, 9.0, 1.0, "Трусца"),
            ProgramSegment(90, 13.0, 1.0, "Темповый"),
            ProgramSegment(120, 9.0, 1.0, "Трусца"),
            ProgramSegment(60, 15.0, 1.0, "Спринт"),
            ProgramSegment(300, 9.0, 1.0, "Заминка")
        )
    )
    return listOf(
        "Интервалы 4×3 мин" to intervals,
        "Пирамида (1-2-3-2-1)" to pyramid,
        "Горки 6×1 мин @ 6%" to hills,
        "Fartlek 20 мин" to fartlek
    )
}

// ================================================================
//  Карточка программы
// ================================================================
@Composable
private fun ProgramCard(
    entity: ProgramEntity,
    data: ProgramData,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onStart: () -> Unit
) {
    val totalSec = data.segments.sumOf { it.durationSec }
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val durationText = if (minutes > 0) "$minutes мин" else "$seconds с"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(entity.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (entity.description.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            entity.description,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Дублировать программу",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip("${data.segments.size} сегм.")
                StatChip(durationText)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text("Старт", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) { Text("Изм.", maxLines = 1, fontSize = 13.sp) }
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) { Text("Экспорт", maxLines = 1, fontSize = 13.sp) }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Удал.", maxLines = 1, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================================================================
//  UI-состояние сегмента
// ================================================================
private data class SegmentEdit(
    val name: String = "",
    val durationStr: String = "60",
    val speedStr: String = "5.0",
    val inclineStr: String = "0.0"
)

// ================================================================
//  Полноэкранный редактор
// ================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEditorScreen(
    initial: ProgramData,
    isNew: Boolean,
    onSave: (ProgramData) -> Unit,
    onDismiss: () -> Unit
) {
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
                                    errorText = "Проверьте поля: время и скорость — числа"
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
                        Text("Всего: ${totalMinutes(segments)} мин", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(segments) { index, seg ->
                            SegmentEditor(
                                index = index,
                                total = segments.size,
                                seg = seg,
                                onChange = { newSeg -> segments[index] = newSeg },
                                onMoveUp = {
                                    if (index > 0) {
                                        val tmp = segments[index - 1]
                                        segments[index - 1] = segments[index]
                                        segments[index] = tmp
                                    }
                                },
                                onMoveDown = {
                                    if (index < segments.size - 1) {
                                        val tmp = segments[index + 1]
                                        segments[index + 1] = segments[index]
                                        segments[index] = tmp
                                    }
                                },
                                onDuplicate = {
                                    segments.add(index + 1, seg.copy(name = seg.name + " (копия)"))
                                },
                                onDelete = { if (segments.size > 1) segments.removeAt(index) },
                                canDelete = segments.size > 1
                            )
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            val last = segments.lastOrNull()
                            segments.add(
                                SegmentEdit(
                                    name = "Сегмент ${segments.size + 1}",
                                    durationStr = "60",
                                    speedStr = last?.speedStr ?: "5.0",
                                    inclineStr = last?.inclineStr ?: "0.0"
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
    total: Int,
    seg: SegmentEdit,
    onChange: (SegmentEdit) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            // Верхняя строка: номер + кнопки управления
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "#${index + 1}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // ↑
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = "Вверх",
                        modifier = Modifier.size(18.dp)
                    )
                }
                // ↓
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < total - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = "Вниз",
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Копировать
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Копировать",
                        modifier = Modifier.size(18.dp)
                    )
                }
                // Удалить
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить",
                        modifier = Modifier.size(18.dp),
                        tint = if (canDelete) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = seg.name,
                onValueChange = { onChange(seg.copy(name = it)) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))

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
private fun filterNumeric(s: String): String = s.filter { it.isDigit() }

private fun filterDecimal(s: String): String =
    s.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')

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

private fun totalMinutes(list: List<SegmentEdit>): Int {
    var sum = 0
    for (s in list) sum += s.durationStr.toIntOrNull() ?: 0
    return sum / 60
}
