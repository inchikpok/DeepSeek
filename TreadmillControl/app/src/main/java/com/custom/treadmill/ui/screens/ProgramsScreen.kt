package com.custom.treadmill.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.custom.treadmill.data.database.ProgramData
import com.custom.treadmill.data.database.ProgramEntity
import com.custom.treadmill.data.database.ProgramSegment
import com.custom.treadmill.ui.viewmodels.MainViewModel
import com.custom.treadmill.ui.viewmodels.ProgramViewModel

@Composable
fun ProgramsScreen(
    mainVm: MainViewModel,
    programVm: ProgramViewModel,
    onBack: () -> Unit,
    onStartWorkout: (ProgramData) -> Unit
) {
    val ctx = LocalContext.current
    val programs by programVm.programs.collectAsState()

    var editing by remember { mutableStateOf<ProgramData?>(null) }
    var editingId by remember { mutableStateOf(0L) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        editing?.let { data ->
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(programVm.exportJson(data).toByteArray())
                }
            } catch (_: Exception) {}
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val text = ctx.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
            val data = programVm.importJson(text)
            programVm.save(data)
        } catch (_: Exception) {}
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("Программы", modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(1.dp))
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                editingId = 0L
                editing = ProgramData("Новая программа", "", listOf(
                    ProgramSegment(300, 5.0, "Разминка"),
                    ProgramSegment(120, 10.0, "Интервал 1"),
                    ProgramSegment(120, 6.0, "Отдых"),
                    ProgramSegment(300, 4.0, "Заминка")
                ))
            }) { Text("Создать") }

            OutlinedButton(onClick = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            }) { Text("Импорт") }
        }

        Spacer(Modifier.height(12.dp))

        if (programs.isEmpty()) {
            Text("Нет сохранённых программ. Нажмите «Создать».")
        } else {
            LazyColumn {
                items(programs, key = { it.id }) { p ->
                    ProgramCard(
                        entity = p,
                        onEdit = { editingId = p.id; editing = programVm.toData(p) },
                        onDelete = { programVm.delete(p) },
                        onExport = {
                            editing = programVm.toData(p)
                            exportLauncher.launch("${p.name}.json")
                        },
                        onStart = { onStartWorkout(programVm.toData(p)) }
                    )
                }
            }
        }
    }

    editing?.let { data ->
        ProgramEditorDialog(
            initial = data,
            onSave = { newData -> programVm.save(newData, editingId); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun ProgramCard(
    entity: ProgramEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onStart: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(entity.name)
            if (entity.description.isNotBlank()) Text(entity.description)
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

@Composable
private fun ProgramEditorDialog(
    initial: ProgramData,
    onSave: (ProgramData) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var segments by remember { mutableStateOf(initial.segments.toMutableList()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактор программы") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("Сегменты:")
                segments.forEachIndexed { index, seg ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = seg.name,
                            onValueChange = {
                                segments[index] = seg.copy(name = it)
                                segments = segments.toMutableList()
                            },
                            label = { Text("Имя") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = seg.durationSec.toString(),
                            onValueChange = {
                                val v = it.toIntOrNull() ?: 0
                                segments[index] = seg.copy(durationSec = v)
                                segments = segments.toMutableList()
                            },
                            label = { Text("Сек") },
                            modifier = Modifier.weight(0.5f)
                        )
                        OutlinedTextField(
                            value = seg.speedKmh.toString(),
                            onValueChange = {
                                val v = it.toDoubleOrNull() ?: 0.0
                                segments[index] = seg.copy(speedKmh = v)
                                segments = segments.toMutableList()
                            },
                            label = { Text("Км/ч") },
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    segments.add(ProgramSegment(60, 5.0, "Новый"))
                    segments = segments.toMutableList()
                }) { Text("+ Добавить сегмент") }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(ProgramData(name.ifBlank { "Без названия" }, description, segments))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
