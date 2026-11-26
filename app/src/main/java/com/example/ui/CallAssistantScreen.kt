package com.example.ui

import android.Manifest
import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PhoneCallback
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BossAvailabilityMode
import com.example.data.HandledCallRecord
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.CrimsonEmergency
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.NeuralViolet

private val DELAY_OPTIONS_SEC = listOf(5, 10, 15, 20)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallAssistantScreen(
    viewModel: LeonViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val callState by viewModel.callReceptionistState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var customNoteDraft by remember(callState.config.customBossNote) {
        mutableStateOf(callState.config.customBossNote)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshTelemetry()
    }

    val callPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshTelemetry()
    }

    val screeningRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshTelemetry()
    }

    val allPhonePermsGranted = telemetry.readPhoneStateGranted &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || telemetry.answerPhoneCallsGranted) &&
        telemetry.micPermissionGranted

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("call_assistant_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Item 0: Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("call_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to HUD",
                        tint = ArcCyan
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI CALL RECEPTIONIST",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ArcCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("call_screen_title")
                    )
                    Text(
                        text = "जब आप कॉल रिसीव न कर पाएँ, तो LEON विनम्रता से बात करके रिपोर्ट सेव करेगा",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = if (callState.config.enabled) EmeraldTelemetry.copy(alpha = 0.16f) else HudSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (callState.config.enabled) EmeraldTelemetry else HudBorder)
                ) {
                    Text(
                        text = if (callState.config.enabled) "ACTIVE" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (callState.config.enabled) EmeraldTelemetry else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Item 1: Master Switch, Delay & Auto-Task Config
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(
                    1.dp,
                    if (callState.config.enabled) EmeraldTelemetry.copy(alpha = 0.7f) else HudBorder
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneInTalk,
                                contentDescription = "AI Call Receptionist toggle",
                                tint = if (callState.config.enabled) EmeraldTelemetry else ArcCyan
                            )
                            Column {
                                Text(
                                    text = "Auto-Answer Unanswered Calls",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "रिंग तय सेकंड तक न उठाने पर LEON कॉल रिसीव कर लेगा",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = callState.config.enabled,
                            onCheckedChange = { viewModel.toggleCallReceptionist(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = EmeraldTelemetry
                            ),
                            modifier = Modifier.testTag("call_enabled_switch")
                        )
                    }

                    Text(
                        text = "रिंग वेट टाइम (Auto-Answer Delay: ${callState.config.autoAnswerDelaySeconds}s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DELAY_OPTIONS_SEC.forEach { sec ->
                            val selected = callState.config.autoAnswerDelaySeconds == sec
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateCallAutoAnswerDelay(sec) },
                                label = { Text("${sec}s Ring") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ArcCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ArcCyan
                                ),
                                modifier = Modifier.testTag("call_delay_$sec")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ऑटो कॉलबैक टास्क बनाएँ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "बात पूरी होने पर कॉलर का नाम व काम Daily Tasks में जोड़ें",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = callState.config.autoTakeTaskOnCallback,
                            onCheckedChange = { viewModel.updateAutoTakeCallbackTask(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = ArcCyan
                            ),
                            modifier = Modifier.testTag("call_auto_task_switch")
                        )
                    }

                    Surface(
                        color = HudSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HudBorder)
                    ) {
                        Text(
                            text = "नोट: Android OS में थर्ड-पार्टी ऐप्स सीधे टेलीकॉम अपलिंक में ऑडियो इंजेक्ट नहीं कर सकते — इसलिए AI कॉल रिसीव करते समय Speakerphone + Mic के ज़रिए कॉलर से साफ बातचीत करता है।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Item 2: Permissions & Android Call Screening Role Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Permissions and screening role",
                            tint = ArcCyan
                        )
                        Text(
                            text = "CALL PERMISSIONS & SCREENING SETUP",
                            style = MaterialTheme.typography.labelLarge,
                            color = ArcCyan
                        )
                    }

                    PermissionStatusLine("Phone State (रिंग पहचान)", telemetry.readPhoneStateGranted)
                    PermissionStatusLine("Answer Phone Calls (कॉल उठाना)", telemetry.answerPhoneCallsGranted)
                    PermissionStatusLine("Microphone (कॉलर की बात सुनना)", telemetry.micPermissionGranted)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PermissionStatusLine("Call Screening Role (नंबर पहचान)", telemetry.callScreeningRoleHeld)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val perms = buildList {
                                    add(Manifest.permission.READ_PHONE_STATE)
                                    add(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        add(Manifest.permission.ANSWER_PHONE_CALLS)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    add(Manifest.permission.READ_CONTACTS)
                                }.toTypedArray()
                                callPermLauncher.launch(perms)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (allPhonePermsGranted) EmeraldTelemetry else ArcCyan,
                                contentColor = DeepObsidian
                            ),
                            modifier = Modifier.testTag("call_grant_perms_button")
                        ) {
                            Text(
                                if (allPhonePermsGranted) "Permissions Granted" else "Grant Call Permissions"
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            OutlinedButton(
                                onClick = {
                                    val rm = context.getSystemService(RoleManager::class.java)
                                    if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                                        screeningRoleLauncher.launch(
                                            rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("call_role_button")
                            ) {
                                Text(
                                    if (telemetry.callScreeningRoleHeld) "Screening Active" else "Set Call Screening Role",
                                    color = if (telemetry.callScreeningRoleHeld) EmeraldTelemetry else ArcCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        // Item 3: Boss Availability Reason & Custom Greeting Note
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "BOSS AVAILABILITY REASON (कॉलर को क्या बताएँ?)",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeuralViolet
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BossAvailabilityMode.entries.forEach { mode ->
                            val selected = callState.config.availabilityMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateBossAvailabilityMode(mode) },
                                label = { Text(mode.title) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeuralViolet.copy(alpha = 0.25f),
                                    selectedLabelColor = ArcCyan
                                ),
                                modifier = Modifier.testTag("call_mode_${mode.wireValue}")
                            )
                        }
                    }

                    if (callState.config.availabilityMode == BossAvailabilityMode.CUSTOM) {
                        OutlinedTextField(
                            value = customNoteDraft,
                            onValueChange = {
                                customNoteDraft = it
                                viewModel.updateBossAvailabilityMode(BossAvailabilityMode.CUSTOM, it)
                            },
                            label = { Text("कस्टम संदेश (जैसे: 3 बजे के बाद फ्री होंगे)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArcCyan,
                                unfocusedBorderColor = HudBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_custom_note_input")
                        )
                    }
                }
            }
        }

        // Item 4: Live Call Status OR Interactive Simulator Test Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurfaceElevated),
                border = BorderStroke(
                    1.dp,
                    if (callState.activeCallState == "IDLE") ArcCyan.copy(alpha = 0.55f) else EmeraldTelemetry
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = "Live AI call dialogue",
                                tint = EmeraldTelemetry
                            )
                            Text(
                                text = when (callState.activeCallState) {
                                    "RINGING_COUNTDOWN" -> "INCOMING RING (${callState.countdownSeconds}s)"
                                    "AI_TALKING" -> "LIVE CALL WITH ${callState.activeCallCaller}"
                                    "SIMULATED_ACTIVE" -> "LIVE AI CALL TEST (${callState.activeCallCaller})"
                                    else -> "LIVE TEST & CALL SIMULATOR"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = EmeraldTelemetry,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (callState.activeCallState != "IDLE") {
                            OutlinedButton(
                                onClick = { viewModel.endActiveOrSimulatedCall() },
                                border = BorderStroke(1.dp, CrimsonEmergency),
                                modifier = Modifier.testTag("call_sim_end_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CallEnd,
                                    contentDescription = "End and save call report",
                                    tint = CrimsonEmergency,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                                Text("End & Save", color = CrimsonEmergency)
                            }
                        }
                    }

                    if (callState.statusBanner.isNotBlank()) {
                        Text(
                            text = callState.statusBanner,
                            style = MaterialTheme.typography.bodySmall,
                            color = ArcCyan
                        )
                    }

                    if (callState.activeCallState == "IDLE") {
                        Text(
                            text = "बिना दूसरी सिम के अभी लाइव टेस्ट करें कि जब कोई कॉल आए और आप न उठा पाएँ, तो LEON AI कैसे बात करता है:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.startSimulatedIncomingCall() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcCyan,
                                contentColor = DeepObsidian
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_simulate_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.PhoneCallback, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Simulate Unanswered Call (Live Demo)", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Active transcript turns
                        callState.activeTurns.forEachIndexed { idx, turn ->
                            val isLeon = turn.speaker == "LEON AI"
                            Surface(
                                color = if (isLeon) ArcCyan.copy(alpha = 0.14f) else NeuralViolet.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isLeon) ArcCyan.copy(alpha = 0.5f) else NeuralViolet.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("call_turn_$idx")
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = turn.speaker,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isLeon) ArcCyan else NeuralViolet,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = turn.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (callState.isGeneratingReply) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ArcCyan)
                                Text("LEON AI कॉलर के लिए जवाब सोच रहा है...", style = MaterialTheme.typography.bodySmall, color = ArcCyan)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    viewModel.sendSimulatedCallerTurn("मैं राहुल बोल रहा हूँ, कल सुबह 10 बजे मीटिंग के लिए कॉल किया है")
                                },
                                label = { Text("मीटिंग संदेश बोलें") },
                                colors = AssistChipDefaults.assistChipColors(labelColor = ArcCyan)
                            )
                            AssistChip(
                                onClick = {
                                    viewModel.sendSimulatedCallerTurn("यह बहुत अर्जेंट है, प्लीज़ तुरंत कॉल बैक करने बोलिए")
                                },
                                label = { Text("अर्जेंट कॉल टेस्ट") },
                                colors = AssistChipDefaults.assistChipColors(labelColor = AmberCaution)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = callState.simulatorInput,
                                onValueChange = { viewModel.updateCallSimulatorInput(it) },
                                placeholder = { Text("कॉलर की तरह बोलें/लिखें...") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ArcCyan,
                                    unfocusedBorderColor = HudBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("call_sim_input")
                            )
                            IconButton(
                                onClick = { viewModel.sendSimulatedCallerTurn() },
                                enabled = callState.simulatorInput.isNotBlank() && !callState.isGeneratingReply,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("call_sim_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send caller reply",
                                    tint = if (callState.simulatorInput.isNotBlank()) ArcCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Item 5: Handled Call Logs Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AI HANDLED CALL REPORTS (${callState.handledCalls.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = ArcCyan
                )
            }
        }

        if (callState.handledCalls.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = HudSurface),
                    border = BorderStroke(1.dp, HudBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                ) {
                    Text(
                        text = "अभी तक कोई कॉल AI ने हैंडल नहीं की है। ऊपर 'Simulate Unanswered Call' दबाकर या Auto-Answer ON करके लाइव टेस्ट करें।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            itemsIndexed(callState.handledCalls, key = { _, item -> item.id }) { idx, record ->
                HandledCallRecordCard(
                    index = idx,
                    record = record,
                    onSpeak = { viewModel.speakHandledCallSummary(record) },
                    onSaveTask = { viewModel.addHandledCallAsTask(record) }
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusLine(label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (granted) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "$label granted",
                    tint = EmeraldTelemetry,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = if (granted) "READY" else "NEEDED",
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) EmeraldTelemetry else AmberCaution,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandledCallRecordCard(
    index: Int,
    record: HandledCallRecord,
    onSpeak: () -> Unit,
    onSaveTask: () -> Unit
) {
    val urgencyColor = when (record.urgency.uppercase()) {
        "HIGH" -> CrimsonEmergency
        "LOW" -> EmeraldTelemetry
        else -> AmberCaution
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .testTag("call_record_$index")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.callerDisplay,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${record.statusLabel}${if (record.callerNumber.isNotBlank()) " • ${record.callerNumber}" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = urgencyColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, urgencyColor.copy(alpha = 0.65f))
                ) {
                    Text(
                        text = record.urgency,
                        style = MaterialTheme.typography.labelSmall,
                        color = urgencyColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "सारांश: ${record.purposeSummary}",
                style = MaterialTheme.typography.bodyMedium,
                color = ArcCyan
            )

            record.turns.takeLast(4).forEach { turn ->
                Text(
                    text = "${turn.speaker}: ${turn.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onSpeak,
                    modifier = Modifier.testTag("call_speak_record_$index")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = "Speak call summary", tint = ArcCyan, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Speak Summary", color = ArcCyan)
                }
                OutlinedButton(
                    onClick = onSaveTask,
                    modifier = Modifier.testTag("call_save_task_$index")
                ) {
                    Icon(Icons.Rounded.AddTask, contentDescription = "Add callback task", tint = EmeraldTelemetry, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Add Callback Task", color = EmeraldTelemetry)
                }
            }
        }
    }
}
