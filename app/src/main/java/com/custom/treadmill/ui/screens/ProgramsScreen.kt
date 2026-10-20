package com.custom.treadmill.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.custom.treadmill.ble.BleConnectionState
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramEntity
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ProgramViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.UUID

enum class SortMode { NAME_ASC, NAME_DESC, NEWEST }

@OptIn(ExperimentalMaterial3Api::class)
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
    var showSortMenu by remember { mutableStateOf(false) }

    fun tryImportText(text: String, source: String) {
        try {
            if (text.isBlank()) { mainVm.showMessage("Текст пуст"); return }
            val data = programVm.importJson(text)
            if (data.name.isBlank() || data.segments.isEmpty()) { mainVm.showMessage("Ошибка: файл не содержит программы"); return }
            if (data.segments.any { it.durationSec <= 0 || it.speedKmh <= 0 }) { mainVm.showMessage("Ошибка: найдены сегменты с нулевым временем или скоростью"); return }
            programVm.save(data)
            mainVm.showMessage("Программа «${data.name}» импортирована ($source)")
        } catch (e: Exception) {
            mainVm.showMessage("Ошибка: ${e.message}")
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
            mainVm.showMessage("Ошибка сохранения: ${e.message}")
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
            mainVm.showMessage("Ошибка чтения: ${e.message}")
        }
    }

    val visible = remember(programs, searchQuery, sortMode) {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) programs
        else programs.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) }
        when (sortMode) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.NEWEST -> filtered.sortedByDescending { it.id }
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showTemplates = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Создать") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                } else {
                    Spacer(Modifier.width(16.dp))
                }
                Text("Программы", fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = "Импорт из файла")
                }
                IconButton(onClick = {
                    try {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = cm.primaryClip
                        if (clip == null || clip.itemCount == 0) mainVm.showMessage("Буфер пуст")
                        else tryImportText(clip.getItemAt(0).coerceToText(ctx)?.toString() ?: "", "буфер")
                    } catch (e: Exception) { mainVm.showMessage("Ошибка буфера: ${e.message}") }
                }) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = "Вставить из буфера")
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, contentDescription = "Очистить", modifier = Modifier.size(20.dp)) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).height(50.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )
                
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.size(50.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = "Сортировка", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        DropdownMenuItem(text = { Text("А-Я") }, onClick = { sortMode = SortMode.NAME_ASC; showSortMenu = false }, leadingIcon = { if (sortMode == SortMode.NAME_ASC) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) })
                        DropdownMenuItem(text = { Text("Я-А") }, onClick = { sortMode = SortMode.NAME_DESC; showSortMenu = false }, leadingIcon = { if (sortMode == SortMode.NAME_DESC) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) })
                        DropdownMenuItem(text = { Text("Сначала новые") }, onClick = { sortMode = SortMode.NEWEST; showSortMenu = false }, leadingIcon = { if (sortMode == SortMode.NEWEST) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Text(if (programs.isEmpty()) "Нет программ" else "Ничего не найдено", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (programs.isEmpty()) "Нажмите «Создать» или вставьте из буфера" else "Попробуйте изменить запрос",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
                ) {
                    items(visible, key = { it.id }) { p ->
                        val data = remember(p.id, p.segmentsJson) { programVm.toData(p) }
                        ProgramCardModern(
                            entity = p,
                            data = data,
                            canStart = treadmillReady,
                            onEdit = { editingId = p.id; editingData = data },
                            onDuplicate = { programVm.save(data.copy(name = "${data.name} (копия)"), 0L) },
                            onDelete = { 
                                if (workoutState.running && workoutState.programName == p.name) mainVm.showMessage("Нельзя удалить запущенную программу")
                                else deleteTarget = p 
                            },
                            onExport = { exportTarget = data; exportLauncher.launch("${p.name}.json") },
                            onStart = { if (treadmillReady) onStartWorkout(data) else showNotConnectedDialog = true }
                        )
                    }
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
            text = { Text("Программа «${target.name}» будет удалена.") },
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
            onDismiss = { editingData = null; editingId = -1L }
        )
    }
}

@Composable
private fun ProgramCardModern(
    entity: ProgramEntity, data: ProgramData, canStart: Boolean,
    onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onExport: () -> Unit, onStart: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    val totalSec = data.segments.sumOf { it.durationSec }
    val minutes = totalSec / 60
    val durationText = if (minutes > 0) "$minutes мин" else "${totalSec % 60} с"

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(entity.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    if (entity.description.isNotBlank()) {
                        Text(entity.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StatChip("${data.segments.size} сегм.")
                        StatChip(durationText)
                    }
                }
                
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                FilledIconButton(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Старт")
                }
                
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Изменить") }, onClick = { showMenu = false; onEdit() }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(text = { Text("Дублировать") }, onClick = { showMenu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                        DropdownMenuItem(text = { Text("Экспорт в JSON") }, onClick = { showMenu = false; onExport() }, leadingIcon = { Icon(Icons.Default.Share, null) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onDelete() }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    HorizontalDivider(Modifier.padding(bottom = 8.dp))
                    data.segments.forEachIndexed { i, seg ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${i + 1}.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                            Text(seg.name.ifBlank { "Сегмент" }, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                            val m = seg.durationSec / 60
                            val s = seg.durationSec % 60
                            val timeStr = if (m > 0) "${m}м ${s}с" else "${s}с"
                            Text("$timeStr · %.1f км/ч · %.0f%%".format(seg.speedKmh, seg.inclinePercent), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(text: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(6.dp)) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
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

// ==========================================
// 🚀 РЕДАКТОР С ПЕРЕТАСКИВАНИЕМ
// ==========================================
private data class SegmentEdit(
    val id: String = UUID.randomUUID().toString(), // ID нужен для стабильной работы LazyColumn
    val name: String = "", 
    val durationSec: Int = 60, 
    val speedKmh: Double = 5.0, 
    val inclinePercent: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramEditorScreen(
    initial: ProgramData, isNew: Boolean, onShowMessage: (String) -> Unit, onSave: (ProgramData) -> Unit, onDismiss: () -> Unit
) {
    val segments = remember(initial) {
        mutableStateListOf<SegmentEdit>().apply {
            initial.segments.forEach { add(SegmentEdit(name = it.name, durationSec = it.durationSec, speedKmh = it.speedKmh, inclinePercent = it.inclinePercent)) }
            if (isEmpty()) add(SegmentEdit(name = "Разминка", durationSec = 300, speedKmh = 5.0, inclinePercent = 0.0))
        }
    }
    var name by remember(initial) { mutableStateOf(initial.name) }
    var description by remember(initial) { mutableStateOf(initial.description) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(if (isNew) "Новая программа" else "Редактор", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text("Всего: ${segments.sumOf { it.durationSec } / 60} мин", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Закрыть") } },
                        actions = {
                            TextButton(onClick = {
                                if (name.isBlank()) { onShowMessage("Введите название программы"); return@TextButton }
                                if (segments.isEmpty() || segments.any { it.durationSec <= 0 || it.speedKmh <= 0 }) { onShowMessage("Проверьте сегменты: время и скорость должны быть больше нуля"); return@TextButton }
                                onSave(ProgramData(name, description, segments.map { ProgramSegment(it.durationSec, it.speedKmh, it.inclinePercent, it.name.ifBlank { "Сегмент" }) }))
                            }) { Text("Сохранить", fontWeight = FontWeight.Bold) }
                        }
                    )
                },
                bottomBar = {
                    Surface(shadowElevation = 8.dp) {
                        Box(Modifier.fillMaxWidth().padding(12.dp)) {
                            Button(
                                onClick = { 
                                    val last = segments.lastOrNull()
                                    segments.add(SegmentEdit(name = "Сегмент ${segments.size + 1}", durationSec = 60, speedKmh = last?.speedKmh ?: 5.0, inclinePercent = last?.inclinePercent ?: 0.0)) 
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Добавить сегмент", fontSize = 15.sp)
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                    
                    SlimTextField(
                        value = name, 
                        onValueChange = { name = it }, 
                        label = "Название программы", 
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    SlimTextField(
                        value = description, 
                        onValueChange = { description = it }, 
                        label = "Описание (опционально)", 
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    Text("Сегменты (зажмите ≡ для перетаскивания)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f), 
                        verticalArrangement = Arrangement.spacedBy(8.dp), 
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(segments, key = { _, item -> item.id }) { index, seg ->
                            SegmentEditor(
                                index = index, total = segments.size, seg = seg,
                                onChange = { newSeg -> segments[index] = newSeg },
                                onMoveUp = { 
                                    if (index > 0) { 
                                        val tmp = segments[index - 1]; 
                                        segments[index - 1] = segments[index]; 
                                        segments[index] = tmp 
                                    } 
                                },
                                onMoveDown = { 
                                    if (index < segments.size - 1) { 
                                        val tmp = segments[index + 1]; 
                                        segments[index + 1] = segments[index]; 
                                        segments[index] = tmp 
                                    } 
                                },
                                onDuplicate = { segments.add(index + 1, seg.copy(id = UUID.randomUUID().toString(), name = seg.name + " (копия)")) },
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
private fun SlimTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                    innerTextField()
                }
            }
        )
    }
}

// 🚀 ИСПРАВЛЕННАЯ КНОПКА (с rememberUpdatedState)
@Composable
private fun HoldableStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val currentOnClick by rememberUpdatedState(onClick)

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(400)
            while (true) {
                currentOnClick()
                delay(80)
            }
        }
    }

    IconButton(
        onClick = { if (enabled) currentOnClick() },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .size(40.dp)
            .background(
                color = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TimePickerDialog(
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val values = remember { (0..3600 step 5).toList() }
    var selectedIndex by remember { mutableIntStateOf((initialSeconds / 5).coerceIn(0, values.size - 1)) }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Выберите время", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                
                AndroidView(
                    factory = { ctx ->
                        NumberPicker(ctx).apply {
                            minValue = 0
                            maxValue = values.size - 1
                            value = selectedIndex
                            wrapSelectorWheel = false
                            setFormatter { i ->
                                val sec = values[i]
                                "%02d:%02d".format(sec / 60, sec % 60)
                            }
                            setOnValueChangedListener { _, _, newVal ->
                                selectedIndex = newVal
                            }
                            try {
                                val f = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                                f.isAccessible = true
                                (f.get(this) as android.graphics.Paint).color = textColor
                            } catch (_: Exception) {}
                            try {
                                val f = NumberPicker::class.java.getDeclaredField("mInputText")
                                f.isAccessible = true
                                (f.get(this) as android.widget.EditText).setTextColor(textColor)
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.height(180.dp)
                )

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Button(onClick = { onConfirm(values[selectedIndex]) }) { Text("Готово") }
                }
            }
        }
    }
}

@Composable
private fun SegmentEditor(
    index: Int, total: Int, seg: SegmentEdit,
    onChange: (SegmentEdit) -> Unit, 
    onMoveUp: () -> Unit, 
    onMoveDown: () -> Unit, 
    onDuplicate: () -> Unit, 
    onDelete: () -> Unit, 
    canDelete: Boolean
) {
    var nameState by remember(seg) { mutableStateOf(seg.name) }
    var showTimePicker by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth(), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            
            // --- ШАПКА СЕГМЕНТА ---
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                
                // 🚀 ИКОНКА ДЛЯ ПЕРЕТАСКИВАНИЯ (СВАЙП ВВЕРХ/ВНИЗ)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                if (dragAmount > 20f) { onMoveDown() }
                                else if (dragAmount < -20f) { onMoveUp() }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu, // Три полоски
                        contentDescription = "Перетащить",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))

                Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.size(22.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${index + 1}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                
                BasicTextField(
                    value = nameState,
                    onValueChange = { nameState = it; onChange(seg.copy(name = it)) },
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.fillMaxSize()) {
                            if (nameState.isEmpty()) {
                                Text("Название сегмента", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    }
                )

                Spacer(Modifier.width(4.dp))
                
                // Кнопки точного перемещения (на случай, если свайп неудобен)
                Column {
                    Row {
                        IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp)) }
                    }
                    Row {
                        IconButton(onClick = onDuplicate, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)) }
                        IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- ВРЕМЯ ---
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Время", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoldableStepButton(
                        icon = Icons.Default.Remove,
                        onClick = { onChange(seg.copy(durationSec = (seg.durationSec - 5).coerceAtLeast(5))) },
                        enabled = seg.durationSec > 5
                    )
                    Text(
                        text = "%02d:%02d".format(seg.durationSec / 60, seg.durationSec % 60),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable { showTimePicker = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    HoldableStepButton(
                        icon = Icons.Default.Add,
                        onClick = { onChange(seg.copy(durationSec = (seg.durationSec + 5).coerceAtMost(3600))) },
                        enabled = seg.durationSec < 3600
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // --- СКОРОСТЬ ---
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Скорость (км/ч)", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoldableStepButton(
                        icon = Icons.Default.Remove,
                        onClick = { onChange(seg.copy(speedKmh = (seg.speedKmh - 0.1).coerceAtLeast(0.5).roundToTenth())) },
                        enabled = seg.speedKmh > 0.5
                    )
                    Text(
                        text = "%.1f".format(seg.speedKmh),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(50.dp),
                        textAlign = TextAlign.Center
                    )
                    HoldableStepButton(
                        icon = Icons.Default.Add,
                        onClick = { onChange(seg.copy(speedKmh = (seg.speedKmh + 0.1).coerceAtMost(20.0).roundToTenth())) },
                        enabled = seg.speedKmh < 20.0
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // --- НАКЛОН ---
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Наклон (%)", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HoldableStepButton(
                        icon = Icons.Default.Remove,
                        onClick = { onChange(seg.copy(inclinePercent = (seg.inclinePercent - 1.0).coerceAtLeast(0.0))) },
                        enabled = seg.inclinePercent > 0.0
                    )
                    Text(
                        text = "%.0f".format(seg.inclinePercent),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(50.dp),
                        textAlign = TextAlign.Center
                    )
                    HoldableStepButton(
                        icon = Icons.Default.Add,
                        onClick = { onChange(seg.copy(inclinePercent = (seg.inclinePercent + 1.0).coerceAtMost(15.0))) },
                        enabled = seg.inclinePercent < 15.0
                    )
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialSeconds = seg.durationSec,
            onConfirm = { newSec ->
                onChange(seg.copy(durationSec = newSec))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

private fun Double.roundToTenth(): Double = (this * 10).roundToInt() / 10.0
