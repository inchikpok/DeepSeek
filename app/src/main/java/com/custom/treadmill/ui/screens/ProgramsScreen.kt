package com.custom.treadmill.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.custom.treadmill.DebugLog
import com.custom.treadmill.ble.BleConnectionState
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramEntity
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ProgramViewModel
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
    val workoutState by mainVm.workoutState.collectAsState()

    val treadmillReady = treadmillState == BleConnectionState.READY

    var editingId by remember { mutableStateOf(-1L) }
    var editingData by remember { mutableStateOf<ProgramData?>(null) }
    var exportTarget by remember { mutableStateOf<ProgramData?>(null) }
    var deleteTarget by remember { mutableStateOf<ProgramEntity?>(null) }

    var showTemplates by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.NAME_ASC) }
    var showNotConnectedDialog by remember { mutableStateOf(false) }

    // Общая валидация и импорт — используется и файлом, и буфером
    fun tryImportText(text: String, source: String) {
        try {
            if (text.isBlank()) {
                mainVm.showMessage("Текст пуст")
                return
            }
            val data = programVm.importJson(text)
            if (data.name.isBlank() || data.segments.isEmpty()) {
                mainVm.showMessage("Ошибка: файл не содержит программы")
                return
            }
            if (data.segments.any { it.durationSec <= 0 || it.speedKmh <= 0 }) {
                mainVm.showMessage("Ошибка: найдены сегменты с нулевым временем или скоростью")
                return
            }
            programVm.save(data)
            mainVm.showMessage("Программа «${data.name}» импортирована ($source)")
            DebugLog.d("VM", "import program '${data.name}' from $source, ${data.segments.size} segments")
        } catch (e: Exception) {
            mainVm.showMessage("Ошибка: ${e.message}")
            DebugLog.e("VM", "import failed from $source", e)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val data = exportTarget ?: return@rememberLauncherForActivityResult
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(programVm.exportJson(data).toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            mainVm.showMessage("Ошибка сохранения файла: ${e.message}")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val text = ctx.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return@rememberLauncherForActivityResult
            tryImportText(text, "файл")
        } catch (e: Exception) {
            mainVm.showMessage("Ошибка чтения файла: ${e.message}")
        }
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
                    TextButton(onClick = onBack, contentPadding = PaddingValues(4.dp)) { Text("← Назад") }
                    Spacer(Modifier.width(8.dp))
                }
                Text("Программы", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { showTemplates = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Создать")
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) { Text("Импорт из файла", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = {
                        try {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = cm.primaryClip
                            if (clip == null || clip.itemCount == 0) {
                                mainVm.showMessage("Буфер обмена пуст")
                                return@OutlinedButton
                            }
                            val text = clip.getItemAt(0).coerceToText(ctx)?.toString() ?: ""
                            tryImportText(text, "буфер")
                        } catch (e: Exception) {
                            mainVm.showMessage("Ошибка буфера: ${e.message}")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) { Text("Из буфера", fontSize = 12.sp) }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск по названию…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Очистить") }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Сортировка:", fontSize = 13.sp)
                SortChip("A→Я", sortMode == SortMode.NAME_ASC) { sortMode = SortMode.NAME_ASC }
                SortChip("Я→A", sortMode == SortMode.NAME_DESC) { sortMode = SortMode.NAME_DESC }
                SortChip("Новые", sortMode == SortMode.NEWEST) { sortMode = SortMode.NEWEST }
            }

            Spacer(Modifier.height(12.dp))
        }

        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (programs.isEmpty()) "У вас пока нет программ" else "Ничего не найдено", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (programs.isEmpty()) "Нажмите «Создать», чтобы добавить первую,\nили «Из буфера», чтобы вставить из блокнота."
                        else "Попробуйте изменить поиск",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
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
                        onDelete = {
                            if (workoutState.running && workoutState.programName == p.name) {
                                mainVm.showMessage("Нельзя удалить запущенную программу")
                            } else {
                                deleteTarget = p
                            }
                        },
                        onExport = {
                            exportTarget = data
                            exportLauncher.launch("${p.name}.json")
                        },
                        onStart = {
                            if (treadmillReady) onStartWorkout(data)
                            else showNotConnectedDialog = true
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
            confirmButton = { TextButton(onClick = { showNotConnectedDialog = false }) { Text("OK") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить программу?") },
            text = { Text("Программа «${target.name}» будет удалена навсегда.") },
            confirmButton = {
                TextButton(onClick = { programVm.delete(target); deleteTarget = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Отмена") } }
        )
    }

    editingData?.let { data ->
        val isNew = editingId == 0L
        ProgramEditorScreen(
            initial = data,
            isNew = isNew,
            onShowMessage = { mainVm.showMessage(it) },
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
        Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text(text, fontSize = 12.sp) }
    } else {
        OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text(text, fontSize = 12.sp) }
    }
}

@Composable
private fun TemplatesDialog(onPick: (ProgramData?) -> Unit, onDismiss: () -> Unit) {
    val templates = remember { buildTemplates() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Шаблон программы") },
        text = {
            Column {
                Text("Выберите готовый шаблон или начните с нуля.")
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) { Text("Пустая (с нуля)") }
                Spacer(Modifier.height(4.dp))
                templates.forEach { (title, data) ->
                    TextButton(onClick = { onPick(data) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text("${data.segments.size} сегм. · ${data.segments.sumOf { it.durationSec } / 60} мин", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        name = "Интервалы 4×3 мин", description = "VO₂max: 4 быстрых отрезка по 3 минуты",
        segments = listOf(
            ProgramSegment(390, 9.5, 1.0, "Разминка: лёгкий бег"), ProgramSegment(30, 16.0, 1.0, "Разминка: ускорение"),
            ProgramSegment(60, 8.0, 1.0, "Разминка: трусца"), ProgramSegment(120, 9.0, 1.0, "Разминка: спокойный бег"),
            ProgramSegment(195, 15.0, 1.0, "Интервал 1/4"), ProgramSegment(160, 8.5, 1.0, "Отдых: трусца"),
            ProgramSegment(195, 15.0, 1.0, "Интервал 2/4"), ProgramSegment(160, 8.5, 1.0, "Отдых: трусца"),
            ProgramSegment(195, 15.0, 1.0, "Интервал 3/4"), ProgramSegment(160, 8.5, 1.0, "Отдых: трусца"),
            ProgramSegment(195, 15.0, 1.0, "Интервал 4/4"), ProgramSegment(360, 9.0, 1.0, "Заминка: лёгкий бег"),
            ProgramSegment(240, 5.0, 0.0, "Заминка: ходьба")
        )
    )
    val pyramid = ProgramData(
        name = "Пирамида", description = "1-2-3-2-1 мин с отдыхом",
        segments = listOf(
            ProgramSegment(300, 9.5, 1.0, "Разминка"), ProgramSegment(60, 12.0, 1.0, "Пирамида 1 мин"),
            ProgramSegment(90, 8.5, 1.0, "Отдых"), ProgramSegment(120, 12.5, 1.0, "Пирамида 2 мин"),
            ProgramSegment(90, 8.5, 1.0, "Отдых"), ProgramSegment(180, 13.0, 1.0, "Пирамида 3 мин"),
            ProgramSegment(90, 8.5, 1.0, "Отдых"), ProgramSegment(120, 12.5, 1.0, "Пирамида 2 мин"),
            ProgramSegment(90, 8.5, 1.0, "Отдых"), ProgramSegment(60, 12.0, 1.0, "Пирамида 1 мин"),
            ProgramSegment(300, 9.0, 1.0, "Заминка")
        )
    )
    val hills = ProgramData(
        name = "Горки 6×1 мин", description = "6 минут работы в гору с отдыхом",
        segments = listOf(
            ProgramSegment(300, 9.5, 1.0, "Разминка"), ProgramSegment(60, 8.0, 1.0, "Разминка: трусца"),
            ProgramSegment(60, 12.0, 6.0, "Горка 1/6"), ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 2/6"), ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 3/6"), ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 4/6"), ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 5/6"), ProgramSegment(100, 6.0, 6.0, "Отдых в горку"),
            ProgramSegment(60, 12.0, 6.0, "Горка 6/6"), ProgramSegment(300, 9.0, 1.0, "Заминка")
        )
    )
    val fartlek = ProgramData(
        name = "Fartlek 20 мин", description = "Свободная игра скоростей",
        segments = listOf(
            ProgramSegment(300, 9.5, 1.0, "Разминка"), ProgramSegment(60, 14.0, 1.0, "Спринт"),
            ProgramSegment(120, 9.0, 1.0, "Трусца"), ProgramSegment(90, 13.0, 1.0, "Темповый"),
            ProgramSegment(120, 9.0, 1.0, "Трусца"), ProgramSegment(60, 15.0, 1.0, "Спринт"),
            ProgramSegment(120, 9.0, 1.0, "Трусца"), ProgramSegment(90, 13.0, 1.0, "Темповый"),
            ProgramSegment(120, 9.0, 1.0, "Трусца"), ProgramSegment(60, 15.0, 1.0, "Спринт"),
            ProgramSegment(300, 9.0, 1.0, "Заминка")
        )
    )
    return listOf("Интервалы 4×3 мин" to intervals, "Пирамида (1-2-3-2-1)" to pyramid, "Горки 6×1 мин @ 6%" to hills, "Fartlek 20 мин" to fartlek)
}

@Composable
private fun ProgramCard(
    entity: ProgramEntity, data: ProgramData, canStart: Boolean,
    onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit, onStart: () -> Unit
) {
    val totalSec = data.segments.sumOf { it.durationSec }
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val durationText = if (minutes > 0) "$minutes мин" else "$seconds с"

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(entity.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (entity.description.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(entity.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Дублировать", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip("${data.segments.size} сегм.")
                StatChip(durationText)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 12.dp)) {
                Text(if (canStart) "Старт" else "Дорожка не подключена", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp)) { Text("Изм.", maxLines = 1, fontSize = 13.sp) }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp)) { Text("Экспорт", maxLines = 1, fontSize = 13.sp) }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удал.", maxLines = 1, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private data class SegmentEdit(val name: String = "", val durationSec: Int = 60, val speedKmh: Double = 5.0, val inclinePercent: Double = 0.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEditorScreen(
    initial: ProgramData,
    isNew: Boolean,
    onShowMessage: (String) -> Unit,
    onSave: (ProgramData) -> Unit,
    onDismiss: () -> Unit
) {
    val segments = remember(initial) {
        mutableStateListOf<SegmentEdit>().apply {
            initial.segments.forEach { s ->
                add(SegmentEdit(s.name, s.durationSec, s.speedKmh, s.inclinePercent))
            }
            if (isEmpty()) add(SegmentEdit("Разминка", 300, 5.0, 0.0))
        }
    }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var description by remember(initial) { mutableStateOf(initial.description) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
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
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть") } },
                        actions = {
                            TextButton(onClick = {
                                if (name.isBlank()) {
                                    onShowMessage("Введите название программы")
                                    return@TextButton
                                }
                                if (segments.isEmpty() || segments.any { it.durationSec <= 0 || it.speedKmh <= 0 }) {
                                    onShowMessage("Проверьте сегменты: время и скорость должны быть больше нуля")
                                    return@TextButton
                                }
                                onSave(ProgramData(name, description, segments.map { ProgramSegment(it.durationSec, it.speedKmh, it.inclinePercent, it.name.ifBlank { "Сегмент" }) }))
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
                                    segments.add(SegmentEdit("Сегмент ${segments.size + 1}", 60, last?.speedKmh ?: 5.0, last?.inclinePercent ?: 0.0))
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Добавить сегмент", fontSize = 16.sp)
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название программы") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание (опционально)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        itemsIndexed(segments) { index, seg ->
                            SegmentEditor(
                                index = index, total = segments.size, seg = seg,
                                onChange = { newSeg -> segments[index] = newSeg },
                                onMoveUp = { if (index > 0) { val tmp = segments[index - 1]; segments[index - 1] = segments[index]; segments[index] = tmp } },
                                onMoveDown = { if (index < segments.size - 1) { val tmp = segments[index + 1]; segments[index + 1] = segments[index]; segments[index] = tmp } },
                                onDuplicate = { segments.add(index + 1, seg.copy(name = seg.name + " (копия)")) },
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
    index: Int, total: Int, seg: SegmentEdit,
    onChange: (SegmentEdit) -> Unit, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, canDelete: Boolean
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("#${index + 1}", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ArrowUpward, contentDescription = "Вверх", modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ArrowDownward, contentDescription = "Вниз", modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить", modifier = Modifier.size(18.dp), tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(value = seg.name, onValueChange = { onChange(seg.copy(name = it)) }, label = { Text("Название сегмента", fontSize = 12.sp) }, textStyle = MaterialTheme.typography.bodyMedium, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))

            val mins = seg.durationSec / 60
            val secs = seg.durationSec % 60
            val timeText = if (seg.durationSec >= 60) "${mins}м ${secs}с" else "${secs}с"

            CompactSlider(
                label = "Длительность", valueText = timeText, value = seg.durationSec.toFloat(),
                valueRange = 10f..3600f, steps = 358,
                onValueChange = { rawValue -> val rounded = (rawValue / 10).roundToInt() * 10; onChange(seg.copy(durationSec = rounded)) }
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            val speedWhole = seg.speedKmh.toInt()
            val speedTenth = (seg.speedKmh * 10).roundToInt() % 10

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactSlider(
                    label = "Скорость (целые)", valueText = "$speedWhole", value = speedWhole.toFloat(),
                    valueRange = 1f..20f, steps = 18, modifier = Modifier.weight(1f),
                    onValueChange = { rawValue -> val newWhole = rawValue.roundToInt(); val newSpeed = newWhole + (speedTenth / 10.0); onChange(seg.copy(speedKmh = newSpeed)) }
                )
                CompactSlider(
                    label = "Десятые", valueText = ".$speedTenth", value = speedTenth.toFloat(),
                    valueRange = 0f..9f, steps = 8, modifier = Modifier.weight(1f),
                    onValueChange = { rawValue -> val newTenth = rawValue.roundToInt(); val newSpeed = speedWhole + (newTenth / 10.0); onChange(seg.copy(speedKmh = newSpeed)) }
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            CompactSlider(
                label = "Наклон", valueText = "${seg.inclinePercent.roundToInt()} %", value = seg.inclinePercent.toFloat(),
                valueRange = 0f..15f, steps = 14,
                onValueChange = { rawValue -> onChange(seg.copy(inclinePercent = rawValue.roundToInt().toDouble())) }
            )
        }
    }
}

@Composable
private fun CompactSlider(label: String, valueText: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int, modifier: Modifier = Modifier, onValueChange: (Float) -> Unit) {
    Column(modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                Text(valueText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, modifier = Modifier.height(24.dp))
    }
}
