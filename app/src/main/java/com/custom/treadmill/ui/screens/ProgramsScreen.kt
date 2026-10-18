package com.custom.treadmill.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.custom.treadmill.ble.BleConnectionState
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramEntity
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ProgramViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SortMode { NAME_ASC, NAME_DESC, NEWEST }

@Composable
fun ProgramsScreen(
    mainVm: MainViewModel,
    programVm: ProgramViewModel,
    onBack: (() -> Unit)? = null,
    onStartWorkout: (ProgramData) -> Unit
) {
    val ctx = LocalContext.current
    val programs by programVm.programs.collectAsState()
    val treadmillState by mainVm.treadmillState.collectAsState()

    val treadmillReady = treadmillState == BleConnectionState.READY

    var editingId by remember { mutableStateOf(-1L) }
    var editingData by remember { mutableStateOf<ProgramData?>(null) }
    var showTemplates by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME_ASC) }
    var showNotConnectedDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val data = editingData ?: return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(programVm.exportJson(data).toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {}
    }

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
        } catch (_: Exception) {}
    }

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
        Column(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    TextButton(onClick = onBack, contentPadding = PaddingValues(4.dp)) {
                        Text("← Назад")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text("Программы", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))

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

        if (visible.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (programs.isEmpty()) "У вас пока нет программ" else "Ничего не найдено",
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
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(visible, key = { it.id }) { p ->
                    val data = remember(p.id, p.segmentsJson) { programVm.toData(p) }
                    ProgramCard(
                        entity = p,
                        data = data,
                        canStart = treadmillReady,
                        onEdit = { editingId = p.id; editingData = data },
                        onDuplicate = {
                            val copy = data.copy(name = "${data.name} (копия)")
                            programVm.save(copy, 0L)
                        },
                        onDelete = { programVm.delete(p) },
                        onExport = {
                            editingData = data
                            exportLauncher.launch("${p.name}.json")
                        },
                        onStart = {
                            if (treadmillReady) {
                                onStartWorkout(data)
                            } else {
                                showNotConnectedDialog = true
                            }
                        }
                    )
                }
            }
        }
    }

    if (showTemplates) {
        TemplatesDialog(
            onPick = { template ->
                showTemplates = false
                editingId = 0L
                editingData = template ?: ProgramData(
                    name = "Новая программа",
                    description = "",
                    segments = listOf(
                        ProgramSegment(300, 5.0, 0.0, "Разминка"),
                        ProgramSegment(120, 10.0, 0.0, "Интервал 1"),
                        ProgramSegment(120, 6.0, 0.0, "Отдых"),
                        ProgramSegment(300, 4.0, 0.0, "Заминка")
                    )
                )
            },
            onDismiss = { showTemplates = false }
        )
    }

    if (showNotConnectedDialog) {
        AlertDialog(
            onDismissRequest = { showNotConnectedDialog = false },
            title = { Text("Дорожка не подключена") },
            text = { Text("Подключите дорожку на главном экране, затем вернитесь и запустите программу.") },
            confirmButton = {
                TextButton(onClick = { showNotConnectedDialog = false }) { Text("OK") }
            }
        )
    }

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
                ) { Text("Пустая (с нуля)") }
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

@Composable
private fun ProgramCard(
    entity: ProgramEntity,
    data: ProgramData,
    canStart: Boolean,
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
                enabled = canStart,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Text(
                    if (canStart) "Старт" else "Дорожка не подключена",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
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

// Внутренний стейт для редактора (числа вместо строк)
private data class SegmentEdit(
    val name: String = "",
    val durationSec: Int = 60,
    val speedKmh: Double = 5.0,
    val inclinePercent: Double = 0.0
)

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
                        durationSec = s.durationSec,
                        speedKmh = s.speedKmh,
                        inclinePercent = s.inclinePercent
                    )
                )
            }
            if (isEmpty()) add(SegmentEdit("Разминка", 300, 5.0, 0.0))
        }
    }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var description by remember(initial) { mutableStateOf(initial.description) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(if (isNew) "Новая программа" else "Редактор", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Всего: ${segments.sumOf { it.durationSec } / 60} мин", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть")
                            }
                        },
                        actions = {
                            TextButton(onClick = {
                                if (segments.isEmpty()) return@TextButton
                                onSave(
                                    ProgramData(
                                        name = name.ifBlank { "Без названия" },
                                        description = description,
                                        segments = segments.map {
                                            ProgramSegment(it.durationSec, it.speedKmh, it.inclinePercent, it.name.ifBlank { "Сегмент" })
                                        }
                                    )
                                )
                            }) { Text("Сохранить", fontWeight = FontWeight.Bold) }
                        }
                    )
                },
                bottomBar = {
                    Surface(shadowElevation = 8.dp) {
                        Box(Modifier.fillMaxWidth().padding(16.dp)) {
                            Button(
                                onClick = {
                                    val last = segments.lastOrNull()
                                    segments.add(
                                        SegmentEdit(
                                            name = "Сегмент ${segments.size + 1}",
                                            durationSec = 60,
                                            speedKmh = last?.speedKmh ?: 5.0,
                                            inclinePercent = last?.inclinePercent ?: 0.0
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Добавить сегмент", fontSize = 16.sp)
                            }
                        }
                    }
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
                        label = { Text("Название программы") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Описание (опционально)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
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
                    }
                }
            }
        }
    }
}

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
    var showTimePicker by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showInclinePicker by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(10.dp)) {
            // Шапка карточки сегмента
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "#${index + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Вверх", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Вниз", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete, contentDescription = "Удалить",
                        modifier = Modifier.size(18.dp),
                        tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = seg.name,
                onValueChange = { onChange(seg.copy(name = it)) },
                label = { Text("Название сегмента", fontSize = 12.sp) },
                textStyle = MaterialTheme.typography.bodyMedium,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            // Кнопки-поля, при нажатии на которые открывается колесо выбора
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ValueButton(
                    label = "Время",
                    value = if (seg.durationSec >= 60) "${seg.durationSec / 60}м ${seg.durationSec % 60}с" else "${seg.durationSec}с",
                    modifier = Modifier.weight(1f),
                    onClick = { showTimePicker = true }
                )
                ValueButton(
                    label = "Скорость",
                    value = "%.1f".format(seg.speedKmh),
                    modifier = Modifier.weight(1f),
                    onClick = { showSpeedPicker = true }
                )
                ValueButton(
                    label = "Наклон %",
                    value = "${seg.inclinePercent.roundToInt()}",
                    modifier = Modifier.weight(1f),
                    onClick = { showInclinePicker = true }
                )
            }
        }
    }

    // --- Диалоги с колесами ---

    if (showTimePicker) {
        TimePickerDialog(
            initialSec = seg.durationSec,
            onConfirm = {
                onChange(seg.copy(durationSec = it))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showSpeedPicker) {
        val speedItems = remember { (10..200).map { it / 10.0 } } // от 1.0 до 20.0
        WheelPickerDialog(
            title = "Скорость",
            items = speedItems.map { "%.1f км/ч".format(it) },
            initialIndex = (seg.speedKmh * 10 - 10).roundToInt().coerceIn(0, 190),
            onConfirm = { idx ->
                onChange(seg.copy(speedKmh = speedItems[idx]))
                showSpeedPicker = false
            },
            onDismiss = { showSpeedPicker = false }
        )
    }

    if (showInclinePicker) {
        val inclineItems = remember { (0..15).toList() }
        WheelPickerDialog(
            title = "Наклон",
            items = inclineItems.map { "$it %" },
            initialIndex = seg.inclinePercent.roundToInt().coerceIn(0, 15),
            onConfirm = { idx ->
                onChange(seg.copy(inclinePercent = inclineItems[idx].toDouble()))
                showInclinePicker = false
            },
            onDismiss = { showInclinePicker = false }
        )
    }
}

@Composable
private fun ValueButton(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        onClick = onClick,
        modifier = modifier.height(60.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// --- Компоненты выбора как в календаре ---

@Composable
private fun TimePickerDialog(
    initialSec: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var mins by remember { mutableIntStateOf(initialSec / 60) }
    var secs by remember { mutableIntStateOf(initialSec % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Длительность", fontWeight = FontWeight.Bold) },
        text = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WheelPicker(
                    items = (0..120).map { "$it м" },
                    initialIndex = mins,
                    modifier = Modifier.weight(1f),
                    onItemSelected = { mins = it }
                )
                WheelPicker(
                    items = (0..59).map { "$it с" },
                    initialIndex = secs,
                    modifier = Modifier.weight(1f),
                    onItemSelected = { secs = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = (mins * 60 + secs).coerceAtLeast(1)
                onConfirm(total)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun WheelPickerDialog(
    title: String,
    items: List<String>,
    initialIndex: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(initialIndex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            WheelPicker(
                items = items,
                initialIndex = initialIndex,
                onItemSelected = { selectedIndex = it }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIndex) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    modifier: Modifier = Modifier,
    onItemSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    )
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // Текущий выбранный элемент — это тот, что прилип к верху (с учетом отступа)
    val selectedIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(selectedIndex) {
        onItemSelected(selectedIndex)
    }

    Box(
        modifier = modifier.height(150.dp).fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Рамка-подсветка по центру
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {}

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            // Padding сверху и снизу, чтобы первый элемент оказался ровно по центру
            contentPadding = PaddingValues(vertical = 55.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = index == selectedIndex
                // Плавное затухание и уменьшение отдаленных элементов
                val distance = abs(index - selectedIndex)
                val alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.4f
                    else -> 0.15f
                }
                val scale = when (distance) {
                    0 -> 1f
                    1 -> 0.85f
                    else -> 0.7f
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .alpha(alpha)
                        .scale(scale),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item,
                        fontSize = if (isSelected) 22.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
