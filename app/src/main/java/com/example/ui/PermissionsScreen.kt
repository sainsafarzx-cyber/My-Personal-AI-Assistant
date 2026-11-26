package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ActionType
import com.example.data.CapturedNotification
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.NeuralViolet

@Composable
fun PermissionsScreen(
    viewModel: LeonViewModel,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val draftedReply by viewModel.draftedReply.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshTelemetry()
    }

    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshTelemetry()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                Text(
                    text = "SECURITY & PERMISSIONS VAULT",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ArcCyan
                )
                Text(
                    text = "Full transparency and user control over every Android capability",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Emergency Stop Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Zero-Bypass Security Guarantee", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                        Text(
                            text = "Payments, deletions, calls, WhatsApp & SMS always require explicit confirmation. Say \"Stop\" or tap Halt anytime.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Button(
                        onClick = { viewModel.emergencyStop() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Halt All")
                    }
                }
            }
        }

        // Runtime Permissions
        item {
            PermissionRowCard(
                icon = Icons.Rounded.Mic,
                title = "Microphone (Voice & Hey Leon)",
                description = "Required for speech recognition and hands-free wake word.",
                granted = telemetry.micPermissionGranted,
                actionLabel = "Grant Mic",
                onAction = { singlePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                PermissionRowCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Post Notifications",
                    description = "Required for Always-Ready foreground indicator and reminders.",
                    granted = telemetry.notificationsPermissionGranted,
                    actionLabel = "Grant Alert",
                    onAction = { singlePermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }
        }

        item {
            PermissionRowCard(
                icon = Icons.Rounded.CameraAlt,
                title = "Camera & Flashlight",
                description = "Controls hardware torch and launches camera capture.",
                granted = telemetry.cameraPermissionGranted,
                actionLabel = "Grant Camera",
                onAction = { singlePermissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }

        item {
            PermissionRowCard(
                icon = Icons.Rounded.Contacts,
                title = "Contacts Lookup",
                description = "Resolves Hindi/English contact names for voice calls even when the screen is off.",
                granted = telemetry.contactsPermissionGranted,
                actionLabel = "Grant Contacts",
                onAction = { singlePermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )
        }

        item {
            PermissionRowCard(
                icon = Icons.Rounded.Call,
                title = "Direct Calling (Screen-Off Voice Call)",
                description = "Required to place voice calls directly after 'Hey Leon', even when the screen is off.",
                granted = telemetry.phonePermissionGranted,
                actionLabel = "Grant Phone",
                onAction = { singlePermissionLauncher.launch(Manifest.permission.CALL_PHONE) }
            )
        }

        // Special System Authorizations
        item {
            PermissionRowCard(
                icon = Icons.Rounded.AccessibilityNew,
                title = "LEON Accessibility Helper",
                description = "Enables user-authorized Back, Home, Recents, and notification shade actions.",
                granted = telemetry.accessibilityEnabled,
                actionLabel = "Open Settings",
                onAction = { viewModel.openDeviceShortcut(ActionType.OPEN_SETTINGS, target = "accessibility") }
            )
        }

        item {
            PermissionRowCard(
                icon = Icons.Rounded.Shield,
                title = "Notification Listener Access",
                description = "Allows LEON to read incoming messages aloud and draft replies.",
                granted = telemetry.notificationListenerEnabled,
                actionLabel = "Open Settings",
                onAction = { viewModel.openDeviceShortcut(ActionType.OPEN_SETTINGS, target = "notifications") }
            )
        }

        item {
            PermissionRowCard(
                icon = Icons.Rounded.SettingsBrightness,
                title = "Modify System Settings",
                description = "Allows LEON to adjust system screen brightness directly.",
                granted = telemetry.writeSettingsGranted,
                actionLabel = "Open Settings",
                onAction = { viewModel.openDeviceShortcut(ActionType.OPEN_SETTINGS, target = "write_settings") }
            )
        }

        item {
            PermissionRowCard(
                icon = Icons.Rounded.BatterySaver,
                title = "Battery Unrestricted (Lock-Screen Mode)",
                description = "Optional: Set LEON AI to Unrestricted in Android battery settings so voice keeps running when locked.",
                granted = false,
                actionLabel = "Battery Settings",
                onAction = { viewModel.openDeviceShortcut(ActionType.OPEN_SETTINGS, target = "battery") }
            )
        }

        // Notification Reader & WhatsApp Reply Drafter
        item {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LIVE NOTIFICATIONS (${notifications.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = ArcCyan
                )
                OutlinedButton(
                    onClick = { viewModel.readNotificationsAloud() },
                    border = BorderStroke(1.dp, ArcCyan),
                    modifier = Modifier.testTag("read_notifications_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Read Aloud", color = ArcCyan)
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                Surface(
                    color = HudSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                ) {
                    Text(
                        text = if (telemetry.notificationListenerEnabled) {
                            "Notification Listener active. New incoming WhatsApp/SMS notifications will appear here."
                        } else {
                            "Enable Notification Listener Access above so LEON can read messages aloud and draft WhatsApp replies."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(notifications, key = { it.key }) { item ->
                NotificationPreviewCard(
                    item = item,
                    onDraftReply = { viewModel.draftReplyForNotification(item) }
                )
            }
        }
    }

    draftedReply?.let { (notif, initialDraft) ->
        var editableDraft by remember(initialDraft) { mutableStateOf(initialDraft) }
        AlertDialog(
            onDismissRequest = { viewModel.dismissDraftedReply() },
            containerColor = HudSurface,
            title = { Text("ALI Draft Reply to ${notif.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Original: \"${notif.content}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editableDraft,
                        onValueChange = { editableDraft = it },
                        label = { Text("Reply Message") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmSendDraftedReply(notif.title, editableDraft) },
                    enabled = editableDraft.isNotBlank()
                ) {
                    Text("Review & Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDraftedReply() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PermissionRowCard(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, if (granted) EmeraldTelemetry.copy(alpha = 0.5f) else HudBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (granted) EmeraldTelemetry else AmberCaution,
                modifier = Modifier.size(26.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (granted) {
                Surface(
                    color = EmeraldTelemetry.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "GRANTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldTelemetry,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = DeepObsidian)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun NotificationPreviewCard(
    item: CapturedNotification,
    onDraftReply: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, HudBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.appName.uppercase(), style = MaterialTheme.typography.labelSmall, color = ArcCyan)
                Text(item.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(item.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onDraftReply,
                colors = ButtonDefaults.buttonColors(containerColor = NeuralViolet)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Draft ALI Reply")
            }
        }
    }
}
