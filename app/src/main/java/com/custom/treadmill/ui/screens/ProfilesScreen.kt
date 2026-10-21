package com.custom.treadmill.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.custom.treadmill.data.repository.ExperienceLevel
import com.custom.treadmill.data.repository.UserProfile
import com.custom.treadmill.ui.viewmodels.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val profiles = settings.profiles
    val activeId = settings.activeProfileId

    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профили", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Добавить") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(profiles) { profile ->
                val isActive = profile.id == activeId
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isActive,
                                onClick = { vm.setActiveProfile(profile.id) }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(profile.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "${profile.weightKg} кг · ${profile.heightCm} см · ${profile.age} лет",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingProfile = profile }) {
                                Icon(Icons.Default.Edit, "Редактировать", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (profiles.size > 1) {
                                IconButton(onClick = { vm.deleteProfile(profile.id) }) {
                                    Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingProfile != null) {
        val initial = editingProfile ?: UserProfile()
        ProfileEditDialog(
            initial = initial,
            isNew = editingProfile == null,
            onSave = { p ->
                if (editingProfile == null) {
                    vm.addProfile(p)
                    vm.setActiveProfile(p.id)
                } else {
                    vm.updateProfile(p)
                }
                showAddDialog = false
                editingProfile = null
            },
            onDismiss = { showAddDialog = false; editingProfile = null }
        )
    }
}

@Composable
private fun ProfileEditDialog(initial: UserProfile, isNew: Boolean, onSave: (UserProfile) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var weight by remember { mutableStateOf(initial.weightKg.toString()) }
    var height by remember { mutableStateOf(initial.heightCm.toString()) }
    var age by remember { mutableStateOf(initial.age.toString()) }
    var restingHr by remember { mutableStateOf(initial.restingHr.toString()) }
    var isMale by remember { mutableStateOf(initial.isMale) }
    var level by remember { mutableStateOf(initial.level) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новый профиль" else "Редактировать профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text("Вес (кг)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height, onValueChange = { height = it },
                        label = { Text("Рост (см)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = age, onValueChange = { age = it },
                        label = { Text("Возраст") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = restingHr, onValueChange = { restingHr = it },
                        label = { Text("Пульс покоя") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Пол:", modifier = Modifier.weight(1f))
                    Text("Ж", fontSize = 14.sp)
                    Switch(checked = isMale, onCheckedChange = { isMale = it }, modifier = Modifier.padding(horizontal = 4.dp))
                    Text("М", fontSize = 14.sp)
                }

                Text("Уровень подготовки:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ExperienceLevel.values().forEach { lvl ->
                        val label = when(lvl) {
                            ExperienceLevel.BEGINNER -> "Новичок"
                            ExperienceLevel.INTERMEDIATE -> "Средний"
                            ExperienceLevel.ADVANCED -> "Профи"
                        }
                        FilterChip(
                            selected = level == lvl,
                            onClick = { level = lvl },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = weight.toDoubleOrNull() ?: 75.0
                val h = height.toIntOrNull() ?: 175
                val a = age.toIntOrNull() ?: 30
                val r = restingHr.toIntOrNull() ?: 60
                onSave(initial.copy(name = name, weightKg = w, heightCm = h, age = a, restingHr = r, isMale = isMale, level = level))
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
