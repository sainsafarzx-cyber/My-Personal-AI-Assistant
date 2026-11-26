package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CellWifi
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ActionType
import com.example.data.AutomationRoutine
import com.example.data.DailyTask
import com.example.data.TaskPriority
import com.example.data.UiState
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.NeuralViolet

@Composable
fun AutomationScreen(
    viewModel: LeonViewModel,
    onOpenSocialPoster: () -> Unit = {},
    onOpenCallAssistant: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val musicPlaying by viewModel.musicPlaying.collectAsStateWithLifecycle()
    val activeTimerSeconds by viewModel.activeTimerSeconds.collectAsStateWithLifecycle()
    val routinesState by viewModel.routinesState.collectAsStateWithLifecycle()
    val tasksState by viewModel.tasksState.collectAsStateWithLifecycle()
    val callReceptionistState by viewModel.callReceptionistState.collectAsStateWithLifecycle()

    var showRoutineDialog by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }

    var volumeSlider by remember(telemetry.mediaVolumePercent) {
        mutableFloatStateOf(telemetry.mediaVolumePercent.toFloat())
    }
    var brightnessSlider by remember(telemetry.brightnessPercent) {
        mutableFloatStateOf(telemetry.brightnessPercent.toFloat())
    }

    val routines = (routinesState as? UiState.Success)?.data ?: emptyList()
    val tasks = (tasksState as? UiState.Success)?.data ?: emptyList()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                Text(
                    text = "LEON CONTROL DECK",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ArcCyan
                )
                Text(
                    text = "Hardware telemetry, multi-step routines & daily missions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Device Controls Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SMARTPHONE CONTROLS",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickControlButton(
                            icon = Icons.Rounded.FlashlightOn,
                            label = if (telemetry.flashlightOn) "Torch ON" else "Torch OFF",
                            active = telemetry.flashlightOn,
                            onClick = { viewModel.toggleFlashlight() },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlButton(
                            icon = Icons.Rounded.Wifi,
                            label = "Wi-Fi",
                            active = telemetry.wifiConnected,
                            onClick = { viewModel.openDeviceShortcut(ActionType.OPEN_WIFI_PANEL) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlButton(
                            icon = Icons.Rounded.Bluetooth,
                            label = "Bluetooth",
                            active = telemetry.bluetoothEnabled,
                            onClick = { viewModel.openDeviceShortcut(ActionType.OPEN_BLUETOOTH_SETTINGS) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickControlButton(
                            icon = Icons.Rounded.CameraAlt,
                            label = "Camera",
                            active = false,
                            onClick = { viewModel.openDeviceShortcut(ActionType.OPEN_CAMERA) },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlButton(
                            icon = if (musicPlaying) Icons.Rounded.Pause else Icons.Rounded.MusicNote,
                            label = if (musicPlaying) "Pause Music" else "Play Music",
                            active = musicPlaying,
                            onClick = { viewModel.toggleMusic() },
                            modifier = Modifier.weight(1f).testTag("music_toggle_button")
                        )
                        QuickControlButton(
                            icon = Icons.Rounded.SkipNext,
                            label = "Next Track",
                            active = false,
                            onClick = { viewModel.nextMusicTrack() },
                            modifier = Modifier.weight(1f).testTag("music_next_button")
                        )
                        QuickControlButton(
                            icon = Icons.Rounded.Timer,
                            label = if (activeTimerSeconds > 0) "${activeTimerSeconds}s" else "5m Timer",
                            active = activeTimerSeconds > 0,
                            onClick = { viewModel.submitPrompt("5 मिनट का टाइमर लगाओ", speakResponse = true) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickControlButton(
                            icon = Icons.Rounded.CellWifi,
                            label = if (telemetry.internetConnected) "Internet ON" else "Internet OFF",
                            active = telemetry.internetConnected,
                            onClick = {
                                viewModel.openDeviceShortcut(
                                    ActionType.TOGGLE_INTERNET,
                                    payload = if (telemetry.internetConnected) "off" else "on"
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("internet_toggle_button")
                        )
                        QuickControlButton(
                            icon = Icons.Rounded.PlayCircle,
                            label = "YouTube",
                            active = false,
                            onClick = { viewModel.openDeviceShortcut(ActionType.OPEN_APP, target = "YouTube") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlButton(
                            icon = Icons.Rounded.Alarm,
                            label = "Alarm 7 AM",
                            active = false,
                            onClick = { viewModel.openDeviceShortcut(ActionType.SET_ALARM, target = "LEON Alarm", payload = "07:00") },
                            modifier = Modifier.weight(1f)
                        )
                        QuickControlButton(
                            icon = Icons.Rounded.Share,
                            label = "Social Post",
                            active = false,
                            onClick = onOpenSocialPoster,
                            modifier = Modifier.weight(1f).testTag("control_social_poster_button")
                        )
                        QuickControlButton(
                            icon = Icons.Rounded.PhoneInTalk,
                            label = if (callReceptionistState.config.enabled) "Call AI ON" else "Call AI",
                            active = callReceptionistState.config.enabled,
                            onClick = onOpenCallAssistant,
                            modifier = Modifier.weight(1f).testTag("control_call_assistant_button")
                        )
                    }

                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Media Volume", style = MaterialTheme.typography.bodyMedium)
                            Text("${volumeSlider.toInt()}%", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        }
                        Slider(
                            value = volumeSlider,
                            onValueChange = { volumeSlider = it },
                            onValueChangeFinished = { viewModel.setVolumePercent(volumeSlider.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(thumbColor = ArcCyan, activeTrackColor = ArcCyan)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Screen Brightness", style = MaterialTheme.typography.bodyMedium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!telemetry.writeSettingsGranted) {
                                    TextButton(
                                        onClick = {
                                            viewModel.openDeviceShortcut(
                                                ActionType.OPEN_SETTINGS,
                                                target = "write_settings"
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("grant_brightness_permission_button")
                                    ) {
                                        Text(
                                            text = "Allow System",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ArcCyan
                                        )
                                    }
                                }
                                Text(
                                    "${brightnessSlider.toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeuralViolet
                                )
                            }
                        }
                        Slider(
                            value = brightnessSlider,
                            onValueChange = { brightnessSlider = it },
                            onValueChangeFinished = { viewModel.setBrightnessPercent(brightnessSlider.toInt()) },
                            valueRange = 5f..100f,
                            colors = SliderDefaults.colors(thumbColor = NeuralViolet, activeTrackColor = NeuralViolet)
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.openDeviceShortcut(ActionType.OPEN_SETTINGS, target = "main") },
                        border = BorderStroke(1.dp, HudBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Open Android System Settings")
                    }
                }
            }
        }

        // Routines Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AUTOMATION ROUTINES (${routines.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = ArcCyan
                )
                Button(
                    onClick = { showRoutineDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = DeepObsidian),
                    modifier = Modifier.testTag("add_routine_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("New Routine")
                }
            }
        }

        if (routines.isEmpty()) {
            item {
                Surface(
                    color = HudSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                ) {
                    Text(
                        text = "No custom routines yet. Create a multi-step voice sequence like \"Morning Briefing\" to run actions hands-free.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(routines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    onRun = { viewModel.runRoutine(routine) },
                    onToggle = { enabled -> viewModel.toggleRoutine(routine.id, enabled) },
                    onDelete = { viewModel.deleteRoutine(routine.id) }
                )
            }
        }

        // Daily Tasks Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DAILY TASKS & REMINDERS (${tasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = EmeraldTelemetry
                )
                Button(
                    onClick = { showTaskDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldTelemetry, contentColor = DeepObsidian),
                    modifier = Modifier.testTag("add_task_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("New Task")
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                Surface(
                    color = HudSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                ) {
                    Text(
                        text = "All clear, Boss! Ask LEON to add a reminder or tap New Task.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onToggle = { completed -> viewModel.toggleTaskCompleted(task.id, completed) },
                    onDelete = { viewModel.deleteTask(task.id) }
                )
            }
        }
    }

    if (showRoutineDialog) {
        CreateRoutineDialog(
            onDismiss = { showRoutineDialog = false },
            onCreate = { title, trigger, steps ->
                viewModel.createRoutine(title, trigger, steps)
                showRoutineDialog = false
            }
        )
    }

    if (showTaskDialog) {
        CreateTaskDialog(
            onDismiss = { showTaskDialog = false },
            onCreate = { title, notes, due, priority ->
                viewModel.createTask(title, notes, due, priority)
                showTaskDialog = false
            }
        )
    }
}

@Composable
private fun QuickControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (active) ArcCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (active) ArcCyan else HudBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(68.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) ArcCyan else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) ArcCyan else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun RoutineCard(
    routine: AutomationRoutine,
    onRun: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, HudBorder),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(routine.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "Trigger: \"${routine.triggerPhrase}\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )
                }
                Switch(
                    checked = routine.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = DeepObsidian, checkedTrackColor = ArcCyan)
                )
            }
            routine.steps.forEachIndexed { idx, step ->
                Text(
                    text = "${idx + 1}. $step",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onRun,
                    colors = ButtonDefaults.buttonColors(containerColor = NeuralViolet),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Run Sequence")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete routine", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: DailyTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, HudBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(checkedColor = EmeraldTelemetry)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (task.dueLabel.isNotBlank() || task.notes.isNotBlank()) {
                    Text(
                        text = listOf(task.dueLabel, task.notes).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete task", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CreateRoutineDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudSurface,
        title = { Text("Create Automation Routine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Routine Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it },
                    label = { Text("Voice Trigger Phrase") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("Steps (one per line or comma separated)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, trigger, steps) },
                enabled = title.isNotBlank() && trigger.isNotBlank() && steps.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, TaskPriority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var dueLabel by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudSurface,
        title = { Text("Add Daily Task / Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dueLabel,
                    onValueChange = { dueLabel = it },
                    label = { Text("Due Time (e.g. Today 6:00 PM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, notes, dueLabel, priority) },
                enabled = title.isNotBlank()
            ) {
                Text("Add Task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
