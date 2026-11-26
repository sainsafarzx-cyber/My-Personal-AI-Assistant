package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AssistantLanguage
import com.example.data.ConversationMessage
import com.example.data.DeviceTelemetry
import com.example.data.HumanReflectionBanner
import com.example.data.MemoryProposal
import com.example.data.PendingConfirmation
import com.example.data.UiState
import com.example.service.VoiceOrbState
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.NeuralViolet

@Composable
fun HudDashboardScreen(
    viewModel: LeonViewModel,
    onOpenNews: () -> Unit = {},
    onOpenOfflineVision: () -> Unit = {},
    onOpenSocialPoster: () -> Unit = {},
    onOpenLifeCompass: () -> Unit = {},
    onOpenCallAssistant: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val orbState by viewModel.orbState.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val alwaysReadyRunning by viewModel.alwaysReadyRunning.collectAsStateWithLifecycle()
    val musicPlaying by viewModel.musicPlaying.collectAsStateWithLifecycle()
    val voiceRepliesEnabled by viewModel.voiceRepliesEnabled.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val prefsState by viewModel.preferencesState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val pendingMemoryProposal by viewModel.pendingMemoryProposal.collectAsStateWithLifecycle()
    val latestHumanReflection by viewModel.latestHumanReflection.collectAsStateWithLifecycle()
    val callReceptionistState by viewModel.callReceptionistState.collectAsStateWithLifecycle()

    val prefs = (prefsState as? UiState.Success)?.data
    val lockScreenModeEnabled = prefs?.lockScreenModeEnabled ?: true
    val ownerName = prefs?.ownerName.orEmpty().trim()

    val listState = rememberLazyListState()
    var pendingPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val preferredLocaleTag = prefs?.let { AssistantLanguage.fromCode(it.defaultLanguage).localeTag } ?: "hi-IN"
    val latestPreferredLocaleTag by rememberUpdatedState(preferredLocaleTag)

    var fgRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var isFgListening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                fgRecognizer?.cancel()
                fgRecognizer?.destroy()
            }
            fgRecognizer = null
            viewModel.finishForegroundMicCapture(capturedTranscript = false)
        }
    }

    val speechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
        } else {
            null
        }
        if (!spoken.isNullOrBlank()) {
            viewModel.finishForegroundMicCapture(capturedTranscript = true)
            viewModel.submitPrompt(spoken, speakResponse = true)
        } else {
            viewModel.finishForegroundMicCapture(capturedTranscript = false)
        }
    }

    val speechFallbackTrigger by viewModel.speechFallbackTrigger.collectAsStateWithLifecycle()

    val launchSpeechIntentFallback: () -> Unit = {
        viewModel.prepareForegroundMicCapture()
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, latestPreferredLocaleTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, latestPreferredLocaleTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलिए बॉस — LEON सुन रहा है...")
        }
        if (speechIntent.resolveActivity(context.packageManager) != null) {
            speechIntentLauncher.launch(speechIntent)
        } else {
            viewModel.finishForegroundMicCapture(capturedTranscript = false)
            viewModel.reportError("Voice speech service network unavailable — please type your command below.")
        }
    }

    LaunchedEffect(speechFallbackTrigger) {
        if (speechFallbackTrigger > 0) {
            launchSpeechIntentFallback()
        }
    }

    val startVoiceCapture: () -> Unit = {
        if (isFgListening) {
            isFgListening = false
            runCatching { fgRecognizer?.stopListening() }
        } else if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            launchSpeechIntentFallback()
        } else {
            viewModel.prepareForegroundMicCapture()
            val started = runCatching {
                val recognizer = fgRecognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { created ->
                    created.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isFgListening = true
                            viewModel.updateForegroundOrbListening(VoiceOrbState.ACTIVE_LISTENING, "")
                        }

                        override fun onBeginningOfSpeech() {
                            isFgListening = true
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isFgListening = false
                        }

                        override fun onError(error: Int) {
                            isFgListening = false
                            when (error) {
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                    viewModel.finishForegroundMicCapture(capturedTranscript = false)
                                    viewModel.reportError("Microphone permission is required for voice commands.")
                                }
                                SpeechRecognizer.ERROR_NO_MATCH,
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    viewModel.finishForegroundMicCapture(capturedTranscript = false)
                                    viewModel.reportError("आवाज़ साफ़ नहीं सुनाई दी — दुबारा Voice Orb दबाएं या टाइप करें।")
                                }
                                else -> {
                                    launchSpeechIntentFallback()
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            isFgListening = false
                            val spoken = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                                .orEmpty()
                            if (spoken.isNotBlank()) {
                                viewModel.finishForegroundMicCapture(capturedTranscript = true)
                                viewModel.submitPrompt(spoken, speakResponse = true)
                            } else {
                                viewModel.finishForegroundMicCapture(capturedTranscript = false)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                .orEmpty()
                            if (partial.isNotBlank()) {
                                viewModel.updateForegroundOrbListening(VoiceOrbState.ACTIVE_LISTENING, partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    fgRecognizer = created
                }
                val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, latestPreferredLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, latestPreferredLocaleTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                isFgListening = true
                recognizer.startListening(listenIntent)
                true
            }.getOrDefault(false)

            if (!started) {
                isFgListening = false
                launchSpeechIntentFallback()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshTelemetry()
        if (granted) {
            startVoiceCapture()
        } else {
            viewModel.reportError("Microphone permission is needed for voice commands.")
        }
    }

    val alwaysReadyPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.refreshTelemetry()
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            viewModel.toggleAlwaysReadyMode(true)
        } else {
            viewModel.reportError("Microphone permission is required for 24/7 Lock-Screen Always-Ready mode.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val maxDim = maxOf(info.size.width, info.size.height)
                        if (maxDim > 1280) {
                            val scale = 1280f / maxDim.toFloat()
                            decoder.setTargetSize(
                                (info.size.width * scale).toInt().coerceAtLeast(1),
                                (info.size.height * scale).toInt().coerceAtLeast(1)
                            )
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val decoded = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    val maxDim = maxOf(decoded.width, decoded.height)
                    if (maxDim > 1280) {
                        val scale = 1280f / maxDim.toFloat()
                        Bitmap.createScaledBitmap(
                            decoded,
                            (decoded.width * scale).toInt().coerceAtLeast(1),
                            (decoded.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                    } else {
                        decoded
                    }
                }
                pendingPhotoBitmap = rawBitmap
            } catch (e: Exception) {
                viewModel.reportError(e.localizedMessage ?: "Unable to open selected photo.")
            }
        }
    }

    val messages = (conversationsState as? UiState.Success)?.data ?: emptyList()
    val lastMessageId = messages.lastOrNull()?.id

    LaunchedEffect(lastMessageId, isThinking) {
        if (messages.isNotEmpty()) {
            val targetIdx = if (isThinking) messages.size else messages.lastIndex
            listState.animateScrollToItem(targetIdx)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .weight(1f)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            TelemetryRibbon(
                telemetry = telemetry,
                alwaysReadyRunning = alwaysReadyRunning,
                musicPlaying = musicPlaying,
                voiceRepliesEnabled = voiceRepliesEnabled,
                isSpeaking = isSpeaking,
                onVoiceToggle = {
                    if (isSpeaking) viewModel.stopSpeaking() else viewModel.toggleVoiceReplies()
                },
                onToggleInternet = {
                    viewModel.openDeviceShortcut(
                        com.example.data.ActionType.TOGGLE_INTERNET,
                        payload = if (telemetry.internetConnected) "off" else "on"
                    )
                },
                onOpenNews = onOpenNews,
                onOpenOfflineVision = onOpenOfflineVision,
                onOpenSocialPoster = onOpenSocialPoster,
                onOpenLifeCompass = onOpenLifeCompass,
                callReceptionistActive = callReceptionistState.config.enabled,
                onOpenCallAssistant = onOpenCallAssistant,
                onMusicToggle = { viewModel.toggleMusic() },
                onEmergencyStop = { viewModel.emergencyStop() }
            )

            Spacer(modifier = Modifier.height(10.dp))

            LeonOrbHeader(
                orbState = if (isSpeaking) VoiceOrbState.SPEAKING else orbState,
                isThinking = isThinking,
                partialTranscript = partialTranscript,
                alwaysReadyRunning = alwaysReadyRunning,
                lockScreenModeEnabled = lockScreenModeEnabled,
                ownerName = ownerName,
                onOrbClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        startVoiceCapture()
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onToggleAlwaysReady = { enable ->
                    if (enable) {
                        val missingPerms = buildList {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                add(Manifest.permission.RECORD_AUDIO)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        if (missingPerms.isEmpty()) {
                            viewModel.toggleAlwaysReadyMode(true)
                        } else {
                            alwaysReadyPermissionsLauncher.launch(missingPerms.toTypedArray())
                        }
                    } else {
                        viewModel.toggleAlwaysReadyMode(false)
                    }
                }
            )

            AnimatedVisibility(visible = pendingConfirmation != null) {
                pendingConfirmation?.let { pending ->
                    PendingConfirmationCard(
                        pending = pending,
                        onConfirm = { viewModel.confirmPendingAction() },
                        onCancel = { viewModel.cancelPendingAction() }
                    )
                }
            }

            AnimatedVisibility(visible = pendingMemoryProposal != null) {
                pendingMemoryProposal?.let { proposal ->
                    MemoryProposalCard(
                        proposal = proposal,
                        onApprove = { viewModel.approveMemoryProposal() },
                        onDismiss = { viewModel.dismissMemoryProposal() }
                    )
                }
            }

            AnimatedVisibility(visible = latestHumanReflection != null) {
                latestHumanReflection?.let { reflection ->
                    HumanReflectionInsightCard(
                        reflection = reflection,
                        onOpenCompass = {
                            viewModel.openLifeCompassWithSeed(reflection.sourceQuery)
                        },
                        onDismiss = { viewModel.dismissHumanReflection() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (conversationsState) {
                    is UiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            EmptyLeonWelcomeCard(
                                ownerName = ownerName,
                                onWakePhraseClick = { viewModel.submitPrompt("Hey Leon") }
                            )
                        }
                    }

                    is UiState.Error -> {
                        val err = (conversationsState as UiState.Error).message
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (messages.isEmpty() && !isThinking) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                EmptyLeonWelcomeCard(
                                    ownerName = ownerName,
                                    onWakePhraseClick = { viewModel.submitPrompt("Hey Leon") }
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = messages,
                                    key = { it.id },
                                    contentType = { "chat_msg" }
                                ) { msg ->
                                    ConversationBubble(
                                        message = msg,
                                        onSpeakClick = { viewModel.speakMessage(msg.text, msg.language) }
                                    )
                                }
                                if (isThinking) {
                                    item(
                                        key = "ali_thinking_indicator",
                                        contentType = "thinking"
                                    ) {
                                        AliThinkingBubble(
                                            onCancelClick = { viewModel.cancelActiveRequest() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Smart suggestions strip
            SuggestionsStrip(
                suggestions = suggestions,
                onSuggestionClick = { viewModel.submitPrompt(it) }
            )

            // Isolated Command Input Dock so keystrokes never recompose the chat list or orb
            CommandInputDock(
                isThinking = isThinking,
                pendingPhotoBitmap = pendingPhotoBitmap,
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onConsumePhoto = { pendingPhotoBitmap = null },
                onSubmitText = { text, bitmap ->
                    if (bitmap != null) {
                        viewModel.analyzePhoto(bitmap, text)
                    } else {
                        viewModel.submitPrompt(text)
                    }
                }
            )
        }
    }
}

@Composable
private fun SuggestionsStrip(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onSuggestionClick(suggestion) },
                label = {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = ArcCyan,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = HudSurface,
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, HudBorder)
            )
        }
    }
}

@Composable
private fun CommandInputDock(
    isThinking: Boolean,
    pendingPhotoBitmap: Bitmap?,
    onPickPhoto: () -> Unit,
    onConsumePhoto: () -> Unit,
    onSubmitText: (String, Bitmap?) -> Unit
) {
    var inputText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(pendingPhotoBitmap) {
        if (pendingPhotoBitmap != null) {
            val caption = inputText.trim()
            inputText = ""
            onSubmitText(caption, pendingPhotoBitmap)
            onConsumePhoto()
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ArcCyan,
        unfocusedBorderColor = HudBorder,
        focusedContainerColor = HudSurface,
        unfocusedContainerColor = HudSurface
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onPickPhoto,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(HudSurface)
                .testTag("photo_analyze_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.ImageSearch,
                contentDescription = "Analyze photo with ALI Vision",
                tint = ArcCyan
            )
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = {
                Text(
                    text = "बोलें या टाइप करें (Hindi / English / Hinglish)...",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            keyboardOptions = remember { KeyboardOptions(imeAction = ImeAction.Send) },
            keyboardActions = remember {
                KeyboardActions(
                    onSend = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotEmpty()) {
                            inputText = ""
                            onSubmitText(trimmed, null)
                        }
                    }
                )
            },
            shape = RoundedCornerShape(24.dp),
            colors = textFieldColors,
            modifier = Modifier
                .weight(1f)
                .testTag("command_input")
        )

        val canSend = inputText.isNotBlank()
        IconButton(
            onClick = {
                val trimmed = inputText.trim()
                if (trimmed.isNotEmpty()) {
                    inputText = ""
                    onSubmitText(trimmed, null)
                }
            },
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (canSend) ArcCyan else HudSurfaceElevated)
                .testTag("send_command_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Send command to ALI AI",
                tint = if (canSend) DeepObsidian else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TelemetryRibbon(
    telemetry: DeviceTelemetry,
    alwaysReadyRunning: Boolean,
    musicPlaying: Boolean,
    voiceRepliesEnabled: Boolean,
    isSpeaking: Boolean,
    onVoiceToggle: () -> Unit,
    onToggleInternet: () -> Unit,
    onOpenNews: () -> Unit,
    onOpenOfflineVision: () -> Unit,
    onOpenSocialPoster: () -> Unit,
    onOpenLifeCompass: () -> Unit,
    callReceptionistActive: Boolean,
    onOpenCallAssistant: () -> Unit,
    onMusicToggle: () -> Unit,
    onEmergencyStop: () -> Unit
) {
    Surface(
        color = HudSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, HudBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                TelemetryChip(
                    icon = Icons.Rounded.BatteryFull,
                    label = "${telemetry.batteryPercent}%",
                    active = true
                )
                Surface(
                    onClick = onToggleInternet,
                    color = if (telemetry.internetConnected) ArcCyan.copy(alpha = 0.14f) else HudSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (telemetry.internetConnected) ArcCyan.copy(alpha = 0.6f) else HudBorder
                    ),
                    modifier = Modifier.testTag("hud_internet_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Wifi,
                            contentDescription = "Toggle Internet ON or OFF",
                            tint = if (telemetry.internetConnected) ArcCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (telemetry.internetConnected) "Net ON" else "Net OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (telemetry.internetConnected) ArcCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    onClick = onOpenOfflineVision,
                    color = EmeraldTelemetry.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.65f)),
                    modifier = Modifier.testTag("hud_offline_vision_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RemoveRedEye,
                            contentDescription = "Open Offline Camera Object Vision",
                            tint = EmeraldTelemetry,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Eye AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldTelemetry,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    onClick = onOpenSocialPoster,
                    color = NeuralViolet.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NeuralViolet.copy(alpha = 0.65f)),
                    modifier = Modifier.testTag("hud_social_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Open Social Story & Video Auto-Poster",
                            tint = ArcCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Social",
                            style = MaterialTheme.typography.labelSmall,
                            color = ArcCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    onClick = onOpenLifeCompass,
                    color = EmeraldTelemetry.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.65f)),
                    modifier = Modifier.testTag("hud_compass_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = "Open Sahi Raah Life Compass",
                            tint = EmeraldTelemetry,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Sahi Raah",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldTelemetry,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Surface(
                    onClick = onOpenCallAssistant,
                    color = if (callReceptionistActive) EmeraldTelemetry.copy(alpha = 0.16f) else ArcCyan.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (callReceptionistActive) EmeraldTelemetry.copy(alpha = 0.75f) else ArcCyan.copy(alpha = 0.65f)
                    ),
                    modifier = Modifier.testTag("hud_call_assistant_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = "Open AI Call Receptionist",
                            tint = if (callReceptionistActive) EmeraldTelemetry else ArcCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (callReceptionistActive) "Call AI ON" else "Call AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (callReceptionistActive) EmeraldTelemetry else ArcCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                TelemetryChip(
                    icon = Icons.Rounded.Bluetooth,
                    label = if (telemetry.bluetoothEnabled) "BT" else "Off",
                    active = telemetry.bluetoothEnabled
                )
                if (telemetry.flashlightOn) {
                    TelemetryChip(
                        icon = Icons.Rounded.FlashlightOn,
                        label = "Torch",
                        active = true
                    )
                }
                Surface(
                    onClick = onVoiceToggle,
                    color = if (voiceRepliesEnabled) ArcCyan.copy(alpha = 0.16f) else HudSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (voiceRepliesEnabled) ArcCyan else HudBorder),
                    modifier = Modifier.testTag("hud_voice_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (voiceRepliesEnabled) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = if (voiceRepliesEnabled) "Voice replies ON" else "Voice replies muted",
                            tint = if (voiceRepliesEnabled) ArcCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isSpeaking) "Speaking" else if (voiceRepliesEnabled) "Voice" else "Muted",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (voiceRepliesEnabled) ArcCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    onClick = onOpenNews,
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.55f)),
                    modifier = Modifier.testTag("hud_news_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Newspaper,
                            contentDescription = "Open Live News Hub",
                            tint = ArcCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "News",
                            style = MaterialTheme.typography.labelSmall,
                            color = ArcCyan
                        )
                    }
                }
                if (musicPlaying) {
                    Surface(
                        onClick = onMusicToggle,
                        color = EmeraldTelemetry.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EmeraldTelemetry),
                        modifier = Modifier.testTag("hud_music_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Music playing — tap to pause",
                                tint = EmeraldTelemetry,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Music",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldTelemetry
                            )
                        }
                    }
                }
            }

            Surface(
                color = if (alwaysReadyRunning) MaterialTheme.colorScheme.error.copy(alpha = 0.2f) else HudSurfaceElevated,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    if (alwaysReadyRunning) MaterialTheme.colorScheme.error else HudBorder
                ),
                modifier = Modifier
                    .clickable(onClick = onEmergencyStop)
                    .testTag("emergency_stop_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.StopCircle,
                        contentDescription = "Emergency Stop",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "STOP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) ArcCyan else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LeonOrbHeader(
    orbState: VoiceOrbState,
    isThinking: Boolean,
    partialTranscript: String,
    alwaysReadyRunning: Boolean,
    lockScreenModeEnabled: Boolean,
    ownerName: String,
    onOrbClick: () -> Unit,
    onToggleAlwaysReady: (Boolean) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "leon_orb")
    val rotationState: State<Float> = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isThinking) 2200 else 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val pulseState: State<Float> = infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val effectiveState = if (isThinking) VoiceOrbState.PROCESSING else orbState
    val statusText = when (effectiveState) {
        VoiceOrbState.IDLE -> if (alwaysReadyRunning) "HEY LEON READY" else "TAP ORB TO SPEAK"
        VoiceOrbState.WAKE_LISTENING -> "LISTENING: \"HEY LEON\""
        VoiceOrbState.ACTIVE_LISTENING -> "LISTENING BOSS..."
        VoiceOrbState.PROCESSING -> "ALI AI THINKING..."
        VoiceOrbState.SPEAKING -> "LEON SPEAKING..."
    }

    Surface(
        color = HudSurface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOrbClick)
                    .drawWithCache {
                        val radialBrush = Brush.radialGradient(
                            colors = listOf(
                                ArcCyan.copy(alpha = 0.35f),
                                NeuralViolet.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                        val arcStroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        val innerStroke = Stroke(width = 1.5.dp.toPx())
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)
                        val baseRadius = (size.minDimension / 2f) * 0.88f

                        onDrawBehind {
                            val r = baseRadius * pulseState.value
                            drawCircle(brush = radialBrush, radius = r)
                            rotate(rotationState.value) {
                                drawArc(
                                    color = ArcCyan,
                                    startAngle = 0f,
                                    sweepAngle = 110f,
                                    useCenter = false,
                                    style = arcStroke
                                )
                                drawArc(
                                    color = NeuralViolet,
                                    startAngle = 180f,
                                    sweepAngle = 110f,
                                    useCenter = false,
                                    style = arcStroke
                                )
                            }
                            drawCircle(
                                color = ArcCyan.copy(alpha = 0.55f),
                                radius = r * 0.54f,
                                style = innerStroke,
                                center = centerOffset
                            )
                        }
                    }
                    .testTag("voice_orb_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Activate LEON Voice Command",
                    tint = ArcCyan,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LEON AI × ALI",
                    style = MaterialTheme.typography.titleLarge,
                    color = ArcCyan
                )
                if (ownerName.isNotBlank()) {
                    Text(
                        text = "CREATOR: $ownerName",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldTelemetry,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("hud_owner_badge")
                    )
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (effectiveState == VoiceOrbState.ACTIVE_LISTENING) EmeraldTelemetry else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (partialTranscript.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "\"$partialTranscript\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { onToggleAlwaysReady(!alwaysReadyRunning) },
                    color = if (alwaysReadyRunning) EmeraldTelemetry.copy(alpha = 0.18f) else HudSurfaceElevated,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (alwaysReadyRunning) EmeraldTelemetry else HudBorder),
                    modifier = Modifier.testTag("hud_always_ready_toggle")
                ) {
                    Text(
                        text = if (alwaysReadyRunning) "24/7 ON" else "24/7 OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (alwaysReadyRunning) EmeraldTelemetry else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                if (alwaysReadyRunning && lockScreenModeEnabled) {
                    Text(
                        text = "LOCK SAFE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ArcCyan,
                        modifier = Modifier.testTag("hud_lock_ready_badge")
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingConfirmationCard(
    pending: PendingConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmberCaution.copy(alpha = 0.14f)),
        border = BorderStroke(1.dp, AmberCaution),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = AmberCaution)
                Text(
                    text = "Security Confirmation Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = AmberCaution
                )
            }
            Text(
                text = pending.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldTelemetry, contentColor = DeepObsidian),
                    modifier = Modifier.weight(1f).testTag("confirm_sensitive_action")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Confirm")
                }
                OutlinedButton(
                    onClick = onCancel,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).testTag("cancel_sensitive_action")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun MemoryProposalCard(
    proposal: MemoryProposal,
    onApprove: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NeuralViolet.copy(alpha = 0.16f)),
        border = BorderStroke(1.dp, NeuralViolet),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Psychology, contentDescription = null, tint = ArcCyan)
                Text(
                    text = "Save to ALI Long-Term Memory?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${proposal.key}: ${proposal.value}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = DeepObsidian),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Approve & Remember")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun HumanReflectionInsightCard(
    reflection: HumanReflectionBanner,
    onOpenCompass: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = EmeraldTelemetry.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("hud_wisdom_insight_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        tint = EmeraldTelemetry,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "सही राह • ${reflection.emotionalTone}",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldTelemetry,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("hud_wisdom_dismiss_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss Sahi Raah Insight",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (reflection.innerThought.isNotBlank()) {
                Text(
                    text = reflection.innerThought,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (reflection.rightPathAdvice.isNotBlank()) {
                Text(
                    text = reflection.rightPathAdvice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onOpenCompass,
                    modifier = Modifier.testTag("hud_wisdom_open_compass_button")
                ) {
                    Text("Open Sahi Raah Compass", color = ArcCyan, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun EmptyLeonWelcomeCard(
    ownerName: String,
    onWakePhraseClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, HudBorder),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (ownerName.isNotBlank()) {
                    "“जी $ownerName बॉस! बताइए, मैं आपकी क्या मदद कर सकता हूँ?”"
                } else {
                    "“जी बॉस! बताइए, मैं आपकी क्या मदद कर सकता हूँ?”"
                },
                style = MaterialTheme.typography.titleLarge,
                color = ArcCyan
            )
            if (ownerName.isNotBlank()) {
                Surface(
                    color = EmeraldTelemetry.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "Built & Personalized Forever by $ownerName",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldTelemetry,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Text(
                text = "Speak or type in Hindi, English, or Hinglish. Tell ALI something once (\"हमेशा याद रखो...\" or \"मेरा नाम ... है\") and it remembers forever!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onWakePhraseClick,
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = DeepObsidian)
            ) {
                Text("Test \"Hey Leon\"")
            }
        }
    }
}

private val UserBubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 18.dp,
    bottomEnd = 4.dp
)

private val ModelBubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomStart = 4.dp,
    bottomEnd = 18.dp
)

private val UserBubbleBg = NeuralViolet.copy(alpha = 0.25f)
private val UserBubbleBorder = BorderStroke(1.dp, NeuralViolet.copy(alpha = 0.55f))
private val ModelBubbleBorder = BorderStroke(1.dp, HudBorder)

@Composable
private fun ConversationBubble(
    message: ConversationMessage,
    onSpeakClick: () -> Unit
) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) UserBubbleBg else HudSurface,
            shape = if (isUser) UserBubbleShape else ModelBubbleShape,
            border = if (isUser) UserBubbleBorder else ModelBubbleBorder,
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Row(
                modifier = Modifier.padding(
                    start = 14.dp,
                    top = 10.dp,
                    bottom = 10.dp,
                    end = if (isUser) 14.dp else 4.dp
                ),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = if (isUser) "BOSS" else "LEON × ALI",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onSecondaryContainer else ArcCyan
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (message.actionSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ACTION: ${message.actionSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldTelemetry
                        )
                    }
                }
                if (!isUser) {
                    IconButton(
                        onClick = onSpeakClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("bubble_speak_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Speak LEON reply aloud",
                            tint = ArcCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AliThinkingBubble(onCancelClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = HudSurface,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = ArcCyan,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "ALI AI सोच रही है...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArcCyan
                )
                TextButton(
                    onClick = onCancelClick,
                    modifier = Modifier.testTag("cancel_thinking_button")
                ) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
