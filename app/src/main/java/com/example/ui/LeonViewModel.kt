package com.example.ui

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AliAiBrain
import com.example.ai.CallReceptionistEngine
import com.example.ai.HumanWisdomEngine
import com.example.ai.LocalCommandRouter
import com.example.audio.LeonSpeaker
import com.example.automation.LeonActionExecutor
import com.example.data.ActionType
import com.example.data.AirGestureAction
import com.example.data.AssistantLanguage
import com.example.data.AutomationRoutine
import com.example.data.BossAvailabilityMode
import com.example.data.CallReceptionistUiState
import com.example.data.CapturedNotification
import com.example.data.CloudSyncState
import com.example.data.ConversationMessage
import com.example.data.DailyTask
import com.example.data.DeviceAction
import com.example.data.DeviceTelemetry
import com.example.data.FirebaseCloudSync
import com.example.data.HandledCallRecord
import com.example.data.HumanReflectionBanner
import com.example.data.LeonRepository
import com.example.data.LifeCompassUiState
import com.example.data.MemoryCategory
import com.example.data.MemoryItem
import com.example.data.MemoryProposal
import com.example.data.NewsUiState
import com.example.data.PendingConfirmation
import com.example.data.SixthSenseTelemetry
import com.example.data.TaskPriority
import com.example.data.UiState
import com.example.data.UserPreferences
import com.example.news.LiveNewsEngine
import com.example.news.NewsCategory
import com.example.sensors.SixthSenseEngine
import com.example.service.CallReceptionistBridge
import com.example.service.LeonCallReceptionistService
import com.example.service.LeonVoiceService
import com.example.service.LockScreenPolicy
import com.example.service.NotificationBridge
import com.example.service.VoiceOrbState
import com.example.service.VoiceServiceBridge
import com.example.service.VoiceServiceEvent
import com.example.social.SocialAutoPosterBridge
import com.example.social.SocialMediaEngine
import com.example.social.SocialPlatform
import com.example.social.SocialPostJob
import com.example.social.SocialPostType
import com.example.social.SocialStudioState
import com.example.vision.OfflineCameraVisionAnalyzer
import com.example.vision.OfflineVisionAnalysis
import android.speech.tts.TextToSpeech
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "LeonViewModel"
private const val STOP_TIMEOUT_MILLIS = 5000L

class LeonViewModel(
    private val repository: LeonRepository,
    private val aliBrain: AliAiBrain,
    private val executor: LeonActionExecutor,
    private val appContext: Context,
    private val cloudSync: FirebaseCloudSync? = null
) : ViewModel() {

    val preferencesState: StateFlow<UiState<UserPreferences>> = repository.observePreferences()
        .map<UserPreferences, UiState<UserPreferences>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to load preferences", error)
            emit(UiState.Error(error.message ?: "Failed to load preferences"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val memoriesState: StateFlow<UiState<List<MemoryItem>>> = repository.observeMemories()
        .map<List<MemoryItem>, UiState<List<MemoryItem>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to load memories", error)
            emit(UiState.Error(error.message ?: "Failed to load memories"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val routinesState: StateFlow<UiState<List<AutomationRoutine>>> = repository.observeRoutines()
        .map<List<AutomationRoutine>, UiState<List<AutomationRoutine>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to load routines", error)
            emit(UiState.Error(error.message ?: "Failed to load routines"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val tasksState: StateFlow<UiState<List<DailyTask>>> = repository.observeTasks()
        .map<List<DailyTask>, UiState<List<DailyTask>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to load tasks", error)
            emit(UiState.Error(error.message ?: "Failed to load tasks"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val conversationsState: StateFlow<UiState<List<ConversationMessage>>> = repository.observeConversations()
        .map<List<ConversationMessage>, UiState<List<ConversationMessage>>> { UiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Failed to load conversation", error)
            emit(UiState.Error(error.message ?: "Failed to load conversation"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UiState.Loading)

    val orbState: StateFlow<VoiceOrbState> = VoiceServiceBridge.orbState
    val partialTranscript: StateFlow<String> = VoiceServiceBridge.partialTranscript
    val alwaysReadyRunning: StateFlow<Boolean> = VoiceServiceBridge.alwaysReadyRunning
    val musicPlaying: StateFlow<Boolean> = VoiceServiceBridge.musicPlaying
    val activeTimerSeconds: StateFlow<Int> = VoiceServiceBridge.activeTimerRemainingSeconds
    val notifications: StateFlow<List<CapturedNotification>> = NotificationBridge.notifications

    private val fallbackCloudState = MutableStateFlow(CloudSyncState())
    val cloudSyncState: StateFlow<CloudSyncState> = cloudSync?.state ?: fallbackCloudState.asStateFlow()

    private val _telemetry = MutableStateFlow(DeviceTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _suggestions = MutableStateFlow(
        listOf(
            "Hey Leon",
            "कैमरा से पहचानो",
            "मेरा नाम याद रखो",
            "गाना बजाओ"
        )
    )
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _pendingMemoryProposal = MutableStateFlow<MemoryProposal?>(null)
    val pendingMemoryProposal: StateFlow<MemoryProposal?> = _pendingMemoryProposal.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _draftedReply = MutableStateFlow<Pair<CapturedNotification, String>?>(null)
    val draftedReply: StateFlow<Pair<CapturedNotification, String>?> = _draftedReply.asStateFlow()

    private val _speechFallbackTrigger = MutableStateFlow(0)
    val speechFallbackTrigger: StateFlow<Int> = _speechFallbackTrigger.asStateFlow()

    private val _openOfflineVisionTrigger = MutableStateFlow(0)
    val openOfflineVisionTrigger: StateFlow<Int> = _openOfflineVisionTrigger.asStateFlow()

    private val _offlineVisionAnalysis = MutableStateFlow(OfflineVisionAnalysis())
    val offlineVisionAnalysis: StateFlow<OfflineVisionAnalysis> = _offlineVisionAnalysis.asStateFlow()

    val offlineVisionAnalyzer by lazy { OfflineCameraVisionAnalyzer() }

    private val _sixthSenseReport = MutableStateFlow<String?>(null)
    val sixthSenseReport: StateFlow<String?> = _sixthSenseReport.asStateFlow()

    private val _isScanningEnvironment = MutableStateFlow(false)
    val isScanningEnvironment: StateFlow<Boolean> = _isScanningEnvironment.asStateFlow()

    private val _voiceRepliesEnabled = MutableStateFlow(true)
    val voiceRepliesEnabled: StateFlow<Boolean> = _voiceRepliesEnabled.asStateFlow()

    val isSpeaking: StateFlow<Boolean> = LeonSpeaker.isSpeaking

    private val _newsState = MutableStateFlow(NewsUiState())
    val newsState: StateFlow<NewsUiState> = _newsState.asStateFlow()

    private val _socialState = MutableStateFlow(SocialStudioState())
    val socialState: StateFlow<SocialStudioState> = _socialState.asStateFlow()

    private val _openSocialPosterTrigger = MutableStateFlow(0)
    val openSocialPosterTrigger: StateFlow<Int> = _openSocialPosterTrigger.asStateFlow()

    private val _latestHumanReflection = MutableStateFlow<HumanReflectionBanner?>(null)
    val latestHumanReflection: StateFlow<HumanReflectionBanner?> = _latestHumanReflection.asStateFlow()

    private val _lifeCompassState = MutableStateFlow(LifeCompassUiState())
    val lifeCompassState: StateFlow<LifeCompassUiState> = _lifeCompassState.asStateFlow()

    private val _openLifeCompassTrigger = MutableStateFlow(0)
    val openLifeCompassTrigger: StateFlow<Int> = _openLifeCompassTrigger.asStateFlow()

    val callReceptionistState: StateFlow<CallReceptionistUiState> = CallReceptionistBridge.state

    private val _openCallAssistantTrigger = MutableStateFlow(0)
    val openCallAssistantTrigger: StateFlow<Int> = _openCallAssistantTrigger.asStateFlow()

    private val socialMediaEngine by lazy { SocialMediaEngine(appContext) }

    private val sixthSenseEngine = SixthSenseEngine(
        context = appContext,
        onAirGestureTriggered = { action -> handleAirGesture(action) },
        onChopFlashTriggered = {
            viewModelScope.launch(Dispatchers.Main) {
                toggleFlashlight()
            }
        },
        onTorchPulse = { on -> executor.setFlashlight(if (on) "on" else "off").success }
    )
    val sixthSenseTelemetry: StateFlow<SixthSenseTelemetry> = sixthSenseEngine.telemetry

    private var activeAiJob: Job? = null
    private var newsFetchJob: Job? = null
    private var compassJob: Job? = null
    private var callSimJob: Job? = null

    init {
        LeonSpeaker.init(appContext)
        LeonSpeaker.setOnSpeechIdleListener {
            if (!VoiceServiceBridge.foregroundMicActive.value) {
                LeonVoiceService.notifyProcessingComplete(appContext)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureDefaultPreferences()
            val prefs = repository.getPreferencesOnce()
            _telemetry.value = executor.readTelemetry()
            _socialState.value = _socialState.value.copy(
                installedPlatforms = socialMediaEngine.checkInstalledPlatforms()
            )
            val micGranted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (prefs.alwaysReadyEnabled && micGranted && !VoiceServiceBridge.alwaysReadyRunning.value) {
                withContext(Dispatchers.Main) {
                    LeonVoiceService.startAlwaysReady(
                        appContext,
                        AssistantLanguage.fromCode(prefs.defaultLanguage),
                        prefs.voiceSpeed.toFloat().coerceIn(0.5f, 2.0f),
                        prefs.lockScreenModeEnabled
                    )
                }
            }
        }
        viewModelScope.launch {
            SocialAutoPosterBridge.activeJob.collect { job ->
                _socialState.value = _socialState.value.copy(activeJob = job)
            }
        }
        viewModelScope.launch {
            SocialAutoPosterBridge.logs.collect { logs ->
                _socialState.value = _socialState.value.copy(activityLogs = logs)
            }
        }
        viewModelScope.launch {
            SocialAutoPosterBridge.openStudioRequests.collect { req ->
                if (req != null) {
                    applySocialRouteSpec(req.first, req.second)
                    _openSocialPosterTrigger.value += 1
                }
            }
        }
        viewModelScope.launch {
            VoiceServiceBridge.events.collect { event ->
                when (event) {
                    VoiceServiceEvent.WakeWordDetected -> {
                        val owner = currentPreferences().ownerName.trim()
                        _statusBanner.value = if (owner.isNotEmpty()) {
                            "जी $owner बॉस! बताइए, मैं आपकी क्या मदद कर सकता हूँ?"
                        } else {
                            "जी बॉस! बताइए, मैं आपकी क्या मदद कर सकता हूँ?"
                        }
                    }
                    is VoiceServiceEvent.TranscriptCaptured -> {
                        submitPrompt(event.text, speakResponse = true)
                    }
                    VoiceServiceEvent.SpeechFallbackRequested -> {
                        _speechFallbackTrigger.value += 1
                    }
                    VoiceServiceEvent.EmergencyStopTriggered -> {
                        _isThinking.value = false
                        _pendingConfirmation.value = null
                        _statusBanner.value = "Emergency Stop activated. All voice & automation paused."
                    }
                    is VoiceServiceEvent.ErrorOccurred -> {
                        _statusBanner.value = event.message
                    }
                }
            }
        }
    }

    fun refreshTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            _telemetry.value = executor.readTelemetry()
            _socialState.value = _socialState.value.copy(
                installedPlatforms = socialMediaEngine.checkInstalledPlatforms()
            )
        }
    }

    private suspend fun refreshTelemetryNow(): DeviceTelemetry {
        val updated = withContext(Dispatchers.IO) { executor.readTelemetry() }
        _telemetry.value = updated
        return updated
    }

    fun dismissBanner() {
        _statusBanner.value = null
    }

    fun reportError(message: String) {
        _statusBanner.value = message
    }

    fun triggerVoiceCapture() {
        LeonVoiceService.captureSingleCommand(appContext, currentLanguage())
    }

    fun prepareForegroundMicCapture() {
        VoiceServiceBridge.setForegroundMicActive(true)
        LeonSpeaker.stop(notifyIdle = false)
        if (VoiceServiceBridge.alwaysReadyRunning.value) {
            LeonVoiceService.pauseListeningForForegroundMic(appContext)
        }
        VoiceServiceBridge.setPartialTranscript("")
        VoiceServiceBridge.setOrbState(VoiceOrbState.ACTIVE_LISTENING)
    }

    fun finishForegroundMicCapture(capturedTranscript: Boolean = false) {
        VoiceServiceBridge.setForegroundMicActive(false)
        VoiceServiceBridge.setPartialTranscript("")
        if (!capturedTranscript) {
            if (VoiceServiceBridge.alwaysReadyRunning.value) {
                LeonVoiceService.notifyProcessingComplete(appContext)
            } else {
                VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
            }
        }
    }

    fun updateForegroundOrbListening(state: VoiceOrbState, partial: String = "") {
        VoiceServiceBridge.setOrbState(state)
        VoiceServiceBridge.setPartialTranscript(partial)
    }

    fun toggleAlwaysReadyMode(enable: Boolean) {
        val prefs = currentPreferences()
        val lang = AssistantLanguage.fromCode(prefs.defaultLanguage)
        val speed = prefs.voiceSpeed.toFloat().coerceIn(0.5f, 2.0f)
        viewModelScope.launch {
            repository.updatePreferences(alwaysReadyEnabled = enable)
        }
        if (enable) {
            LeonVoiceService.startAlwaysReady(appContext, lang, speed, prefs.lockScreenModeEnabled)
        } else {
            LeonVoiceService.emergencyStop(appContext)
        }
    }

    fun updateLockScreenMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updatePreferences(lockScreenModeEnabled = enabled)
        }
        if (alwaysReadyRunning.value) {
            LeonVoiceService.setLockScreenMode(appContext, enabled)
        }
        _statusBanner.value = if (enabled) {
            "Lock-Screen Mode enabled: LEON stays responsive after screen lock."
        } else {
            "Lock-Screen Mode paused: voice stops when screen locks."
        }
    }

    fun triggerLockScreenBriefing() {
        LeonVoiceService.readLockScreenAlerts(appContext, currentLanguage(), currentVoiceSpeed())
        _statusBanner.value = "Speaking Lock-Screen Alerts & Task Briefing..."
    }

    fun toggleMusic(query: String = "") {
        val nextCmd = if (musicPlaying.value) "pause" else "play"
        LeonVoiceService.controlMusic(appContext, nextCmd, query)
        _statusBanner.value = if (nextCmd == "play") {
            "Playing Lock-Screen Safe Music..."
        } else {
            "Music paused."
        }
    }

    fun nextMusicTrack() {
        LeonVoiceService.controlMusic(appContext, "next")
        _statusBanner.value = "Switched to next music track."
    }

    fun emergencyStop() {
        activeAiJob?.cancel()
        activeAiJob = null
        newsFetchJob?.cancel()
        newsFetchJob = null
        compassJob?.cancel()
        compassJob = null
        _isThinking.value = false
        _pendingConfirmation.value = null
        _lifeCompassState.value = _lifeCompassState.value.copy(isThinking = false, isAutoThinking = false)
        LeonSpeaker.stop()
        LeonVoiceService.emergencyStop(appContext)
        SocialAutoPosterBridge.clearJob()
        viewModelScope.launch {
            repository.updatePreferences(alwaysReadyEnabled = false)
        }
        _statusBanner.value = "Emergency Stop executed. Listening & actions halted."
    }

    fun cancelActiveRequest() {
        activeAiJob?.cancel()
        activeAiJob = null
        _isThinking.value = false
        LeonVoiceService.notifyProcessingComplete(appContext)
        _statusBanner.value = "Request cancelled."
    }

    fun toggleVoiceReplies() {
        val next = !_voiceRepliesEnabled.value
        _voiceRepliesEnabled.value = next
        if (!next) {
            LeonSpeaker.stop()
        }
        _statusBanner.value = if (next) "Voice replies ON — LEON will speak aloud." else "Voice replies muted."
    }

    fun speakMessage(text: String, languageCode: String = currentLanguage().code) {
        if (text.isBlank()) return
        _voiceRepliesEnabled.value = true
        if (LeonSpeaker.isMediaStreamMuted(appContext)) {
            _statusBanner.value = "फ़ोन का Media Volume 0% है — LEON की आवाज़ सुनने के लिए Volume बढ़ाएं!"
        }
        LeonSpeaker.speak(
            context = appContext,
            text = text,
            language = AssistantLanguage.fromCode(languageCode),
            speed = currentVoiceSpeed(),
            queueMode = TextToSpeech.QUEUE_FLUSH
        )
    }

    fun stopSpeaking() {
        LeonSpeaker.stop()
    }

    private fun speakOutLoud(
        text: String,
        language: AssistantLanguage,
        speed: Float = currentVoiceSpeed(),
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        forceForeground: Boolean = false
    ) {
        if (text.isBlank()) {
            LeonVoiceService.notifyProcessingComplete(appContext)
            return
        }
        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked == true
        if (!isLocked && (forceForeground || VoiceServiceBridge.foregroundUiActive.value)) {
            if (LeonSpeaker.isMediaStreamMuted(appContext)) {
                _statusBanner.value = "फ़ोन का Media Volume 0% है — LEON की आवाज़ सुनने के लिए Volume बढ़ाएं!"
            }
            LeonSpeaker.speak(appContext, text, language, speed, queueMode)
        } else {
            LeonVoiceService.speak(appContext, text, language, speed, queueMode)
        }
    }

    fun submitPrompt(
        prompt: String,
        speakResponse: Boolean = true,
        skipRoutineMatch: Boolean = false
    ) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return

        val lower = clean.lowercase().trimEnd('.', '!', '?', '।')
        if (lower == "stop" || lower == "emergency stop" || lower == "रुको" || lower == "बंद करो") {
            emergencyStop()
            return
        }

        if (!skipRoutineMatch) {
            val matchedRoutine = currentRoutines().firstOrNull { routine ->
                routine.enabled && routine.triggerPhrase.isNotBlank() &&
                    lower.contains(routine.triggerPhrase.lowercase())
            }
            if (matchedRoutine != null) {
                runRoutine(matchedRoutine)
                return
            }
        }

        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            val prefs = currentPreferences()
            val lang = AssistantLanguage.fromCode(prefs.defaultLanguage)
            val currentNotifications = NotificationBridge.notifications.value
            val currentTaskList = currentTasks()
            val currentMemoryList = currentMemories()
            val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = km?.isKeyguardLocked == true
            val cachedTelemetry = _telemetry.value

            // Instant local path using cached telemetry (0ms IPC lag!)
            if (!skipRoutineMatch) {
                val fastResult = LocalCommandRouter.tryFastPath(
                    rawPrompt = clean,
                    defaultLanguage = lang,
                    telemetry = cachedTelemetry,
                    notifications = currentNotifications,
                    tasks = currentTaskList,
                    isDeviceLocked = isLocked,
                    ownerName = prefs.ownerName,
                    memories = currentMemoryList
                )
                if (fastResult != null) {
                    withContext(Dispatchers.IO) {
                        repository.addConversationMessage("user", clean, lang)
                    }
                    applyBrainResult(fastResult, prefs.memoryConsentGranted, prefs.voiceSpeed.toFloat(), speakResponse)
                    refreshTelemetry()
                    return@launch
                }
            }

            _isThinking.value = true
            try {
                withContext(Dispatchers.IO) {
                    repository.addConversationMessage("user", clean, lang)
                }
                val latestTelemetry = refreshTelemetryNow()

                val result = aliBrain.processCommand(
                    userPrompt = clean,
                    defaultLanguage = lang,
                    memoryConsentGranted = prefs.memoryConsentGranted,
                    memories = currentMemoryList,
                    tasks = currentTaskList,
                    routines = currentRoutines(),
                    history = currentHistory(),
                    telemetry = latestTelemetry,
                    recentNotifications = currentNotifications,
                    ownerName = prefs.ownerName,
                    creatorTagline = prefs.creatorTagline
                )

                _isThinking.value = false

                result.onSuccess { brainResult ->
                    applyBrainResult(brainResult, prefs.memoryConsentGranted, prefs.voiceSpeed.toFloat(), speakResponse)
                }.onFailure { err ->
                    val offlineRecovery = withContext(Dispatchers.Default) {
                        LocalCommandRouter.tryFallback(
                            rawPrompt = clean,
                            defaultLanguage = lang,
                            telemetry = latestTelemetry,
                            notifications = currentNotifications,
                            tasks = currentTaskList,
                            isDeviceLocked = isLocked,
                            ownerName = prefs.ownerName,
                            memories = currentMemoryList
                        )
                    }
                    if (offlineRecovery != null) {
                        applyBrainResult(offlineRecovery, prefs.memoryConsentGranted, prefs.voiceSpeed.toFloat(), speakResponse)
                    } else {
                        val msg = AliAiBrain.describeError(err)
                        _statusBanner.value = msg
                        val failText = "माफ़ कीजिए बॉस, ALI AI अभी नेटवर्क से कनेक्ट नहीं हो पा रही ($msg)। आप ऑफ़लाइन में भी 'गाना बजाओ', 'टॉर्च ऑन', 'ताज़ा न्यूज़', 'वॉल्यूम बढ़ाओ', 'बैटरी बताओ' या 'YouTube खोलो' बोल सकते हैं।"
                        repository.addConversationMessage(
                            role = "model",
                            text = failText,
                            language = lang,
                            actionSummary = "ERROR"
                        )
                        if (speakResponse && _voiceRepliesEnabled.value) {
                            speakOutLoud(
                                "माफ़ कीजिए बॉस, इंटरनेट समस्या है — लेकिन बाकी कमांड तैयार हैं।",
                                AssistantLanguage.HINDI,
                                prefs.voiceSpeed.toFloat()
                            )
                        } else {
                            LeonVoiceService.notifyProcessingComplete(appContext)
                        }
                    }
                }
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun applyBrainResult(
        brainResult: com.example.data.AliBrainResult,
        memoryConsentGranted: Boolean,
        voiceSpeed: Float,
        speakResponse: Boolean
    ) {
        if (brainResult.suggestions.isNotEmpty()) {
            _suggestions.value = brainResult.suggestions
        }
        if (brainResult.innerThought.isNotBlank() || brainResult.rightPathAdvice.isNotBlank()) {
            _latestHumanReflection.value = HumanReflectionBanner(
                emotionalTone = brainResult.emotionalTone.ifBlank { "Human Wisdom" },
                innerThought = brainResult.innerThought,
                rightPathAdvice = brainResult.rightPathAdvice,
                sourceQuery = brainResult.replyText.take(80)
            )
        }
        if (memoryConsentGranted && brainResult.memoryProposal != null) {
            _pendingMemoryProposal.value = brainResult.memoryProposal
        }

        val actionSummary = brainResult.actions
            .filter { it.type != ActionType.SPEAK_ONLY }
            .joinToString(", ") { it.type.name }

        repository.addConversationMessage(
            role = "model",
            text = brainResult.replyText,
            language = brainResult.spokenLanguage,
            actionSummary = actionSummary
        )

        val musicActions = brainResult.actions.filter { it.type == ActionType.PLAY_MUSIC }
        val otherActions = brainResult.actions.filter { it.type != ActionType.PLAY_MUSIC }

        if (musicActions.isNotEmpty()) {
            executeActionSequence(musicActions)
        }

        val shouldSpeak = speakResponse && _voiceRepliesEnabled.value
        val hasFetchNews = otherActions.any { it.type == ActionType.FETCH_NEWS }
        if (shouldSpeak && !hasFetchNews) {
            speakOutLoud(
                brainResult.replyText,
                brainResult.spokenLanguage,
                voiceSpeed
            )
        } else if (!shouldSpeak) {
            LeonVoiceService.notifyProcessingComplete(appContext)
        }

        if (otherActions.isNotEmpty()) {
            executeActionSequence(otherActions)
        }
    }

    fun analyzePhoto(bitmap: Bitmap, question: String) {
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isThinking.value = true
            try {
                val lang = currentLanguage()
                val owner = currentPreferences().ownerName
                val userText = if (question.isBlank()) "[Photo attached for Vision]" else "[Photo] $question"
                repository.addConversationMessage("user", userText, lang)

                val res = aliBrain.analyzeImageWithAli(bitmap, question, lang)
                _isThinking.value = false
                res.onSuccess { analysis ->
                    repository.addConversationMessage("model", analysis, lang, "ALI Vision")
                    if (_voiceRepliesEnabled.value) {
                        speakOutLoud(analysis, lang, currentVoiceSpeed())
                    }
                }.onFailure { err ->
                    // Offline ML Kit automatic fallback when network/ALI AI is unavailable!
                    val offlineAnalysis = runCatching {
                        offlineVisionAnalyzer.analyzeBitmapOffline(bitmap)
                    }.getOrNull()
                    if (offlineAnalysis != null && offlineAnalysis.hasDetections) {
                        _offlineVisionAnalysis.value = offlineAnalysis
                        val offlineText = "ऑफ़लाइन विज़न पहचान (बिना इंटरनेट): ${offlineAnalysis.buildSpokenSummary(owner)}"
                        repository.addConversationMessage("model", offlineText, AssistantLanguage.HINDI, "OFFLINE_VISION")
                        if (_voiceRepliesEnabled.value) {
                            speakOutLoud(offlineText, AssistantLanguage.HINDI, currentVoiceSpeed())
                        }
                    } else {
                        val msg = AliAiBrain.describeError(err)
                        _statusBanner.value = msg
                        repository.addConversationMessage(
                            role = "model",
                            text = "माफ़ कीजिए बॉस, इमेज विश्लेषण नहीं हो सका: $msg। आप चाहें तो Eye कैमरा में इसे लाइव स्कैन कर सकते हैं।",
                            language = lang,
                            actionSummary = "ERROR"
                        )
                    }
                }
            } finally {
                _isThinking.value = false
            }
        }
    }

    private suspend fun executeActionSequence(actions: List<DeviceAction>) {
        val reports = mutableListOf<String>()
        for (index in actions.indices) {
            val action = actions[index]
            if (action.type == ActionType.EMERGENCY_STOP) {
                emergencyStop()
                return
            }
            if (action.requiresConfirmation) {
                val remaining = if (index + 1 < actions.size) actions.subList(index + 1, actions.size) else emptyList()
                val summary = action.confirmationReason.ifBlank {
                    "Confirm ${action.type.name}: ${action.target} ${action.payload}".trim()
                }
                _pendingConfirmation.value = PendingConfirmation(
                    id = UUID.randomUUID().toString(),
                    action = action,
                    summary = summary,
                    remainingActions = remaining
                )
                return
            }
            val outcome = executeSingleAction(action)
            if (outcome.isNotBlank()) reports.add(outcome)
            if (actions.size > 1) delay(300L)
        }
        refreshTelemetry()
        val nonSpeakReports = actions.zip(reports)
            .filter { (action, msg) -> action.type != ActionType.SPEAK_ONLY && msg.isNotBlank() }
            .map { it.second }
        if (nonSpeakReports.size > 1) {
            _statusBanner.value = "Sequence complete: ${nonSpeakReports.joinToString(" • ")}"
        } else if (nonSpeakReports.size == 1) {
            _statusBanner.value = nonSpeakReports.first()
        }
    }

    private suspend fun executeSingleAction(action: DeviceAction): String {
        val km = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked == true
        val lockDecision = LockScreenPolicy.evaluate(
            action = action,
            isDeviceLocked = isLocked,
            canWriteSettings = _telemetry.value.writeSettingsGranted,
            allowLockScreenCalls = currentPreferences().lockScreenModeEnabled
        )
        if (!lockDecision.allowed) {
            LeonVoiceService.speak(
                appContext,
                lockDecision.reasonHindi,
                AssistantLanguage.HINDI,
                currentVoiceSpeed(),
                queueMode = TextToSpeech.QUEUE_ADD
            )
            return lockDecision.reasonHindi
        }
        return when (action.type) {
            ActionType.CREATE_TASK -> {
                val res = repository.createTask(
                    title = action.target.ifBlank { action.payload.ifBlank { "New Task" } },
                    dueLabel = action.payload,
                    priority = TaskPriority.MEDIUM
                )
                if (res.isSuccess) "Task saved" else "Task save failed"
            }
            ActionType.CREATE_ROUTINE -> {
                val steps = action.payload.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    .ifEmpty { listOf("Speak briefing") }
                val title = action.target.ifBlank { "Custom Routine" }
                val res = repository.createRoutine(title, title, steps)
                if (res.isSuccess) "Routine '$title' created" else "Routine creation failed"
            }
            ActionType.PLAY_MUSIC -> {
                val cmd = action.payload.ifBlank { "play" }
                LeonVoiceService.controlMusic(appContext, cmd, action.target)
                when (cmd.lowercase()) {
                    "pause", "stop", "off" -> "Music paused"
                    "next" -> "Switched to next track"
                    "previous", "prev" -> "Returned to previous track"
                    else -> "Playing lock-screen safe background music"
                }
            }
                ActionType.LOCK_SCREEN_BRIEFING -> {
                val briefing = LockScreenPolicy.buildLockScreenBriefing(
                    telemetry = _telemetry.value,
                    notifications = NotificationBridge.notifications.value,
                    tasks = currentTasks(),
                    isDeviceLocked = isLocked
                )
                if (_voiceRepliesEnabled.value) {
                    speakOutLoud(briefing, AssistantLanguage.HINDI, currentVoiceSpeed(), TextToSpeech.QUEUE_ADD)
                }
                "Briefing ready"
            }
            ActionType.SET_TIMER -> {
                val secs = action.payload.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 86400) ?: 60
                val label = action.target.ifBlank { "LEON Timer" }
                LeonVoiceService.startBackgroundTimer(appContext, secs, label)
                if (!isLocked) {
                    withContext(Dispatchers.IO) { executor.execute(action) }
                }
                "Lock-safe timer started for ${secs}s"
            }
            ActionType.READ_NOTIFICATIONS -> {
                val outcome = withContext(Dispatchers.IO) { executor.execute(action) }
                if (_voiceRepliesEnabled.value) {
                    speakOutLoud(outcome.logMessage, currentLanguage(), currentVoiceSpeed(), TextToSpeech.QUEUE_ADD)
                }
                "Notifications read"
            }
            ActionType.OPTICAL_MORSE -> {
                val msg = action.target.ifBlank { "SOS" }
                sendOpticalMorse(msg)
                "Optical Morse Beacon flashing '$msg'"
            }
            ActionType.TOGGLE_AIR_GESTURES -> {
                val enable = action.payload.lowercase() != "off"
                if (action.target.equals("chop", ignoreCase = true)) {
                    setChopFlashEnabled(enable)
                } else {
                    setAirWaveEnabled(enable)
                }
                if (enable) "Touchless Air Gestures enabled" else "Touchless Air Gestures disabled"
            }
            ActionType.ANALYZE_ENVIRONMENT -> {
                runSixthSenseAiScan()
                "Scanning physical environment sensors..."
            }
            ActionType.SAVE_MEMORY -> {
                val parts = action.payload.split("::", limit = 2)
                val cat = if (parts.size == 2) MemoryCategory.fromWire(parts[0]) else MemoryCategory.FACT
                val detail = if (parts.size == 2) parts[1] else action.payload.ifBlank { action.target }
                val key = action.target.ifBlank { "Saved Fact" }
                val res = withContext(Dispatchers.IO) {
                    repository.saveMemory(key, detail, cat, isPermanent = true)
                }
                res.onSuccess { saved ->
                    viewModelScope.launch(Dispatchers.IO) { cloudSync?.pushMemoryToCloud(saved) }
                }
                if (res.isSuccess) "Saved forever: $key" else "Could not save memory"
            }
            ActionType.SET_OWNER_IDENTITY -> {
                val owner = action.target.trim()
                val tagline = if (action.payload == "publish") "" else action.payload.trim()
                if (owner.isBlank()) {
                    "Tell me your name first (e.g. 'मेरा नाम ... है')"
                } else {
                    val res = withContext(Dispatchers.IO) {
                        repository.setOwnerIdentity(owner, tagline)
                    }
                    res.onSuccess { updatedPrefs ->
                        viewModelScope.launch(Dispatchers.IO) {
                            cloudSync?.pushPreferencesToCloud(updatedPrefs)
                            cloudSync?.publishCreatorStampGlobally(updatedPrefs.ownerName, updatedPrefs.creatorTagline)
                        }
                    }
                    if (res.isSuccess) "Forever Owner Identity locked: $owner" else "Could not update owner identity"
                }
            }
            ActionType.FETCH_NEWS -> {
                val category = NewsCategory.fromTopicHint(action.target.ifBlank { action.payload })
                val owner = currentPreferences().ownerName
                val res = LiveNewsEngine.fetchNews(category)
                res.fold(
                    onSuccess = { articles ->
                        _newsState.value = _newsState.value.copy(
                            category = category,
                            articles = articles,
                            isLoading = false,
                            errorMessage = null
                        )
                        val digest = LiveNewsEngine.buildSpokenDigest(articles, category, owner)
                        withContext(Dispatchers.IO) {
                            repository.addConversationMessage("model", digest, AssistantLanguage.HINDI, "LIVE_NEWS")
                        }
                        if (_voiceRepliesEnabled.value) {
                            speakOutLoud(digest, AssistantLanguage.HINDI, currentVoiceSpeed(), TextToSpeech.QUEUE_FLUSH)
                        }
                        "Loaded ${articles.size} live headlines (${category.label})"
                    },
                    onFailure = { err ->
                        val failDigest = "बॉस, अभी न्यूज़ लोड नहीं हो सकी (${err.localizedMessage ?: "नेटवर्क चेक करें"})।"
                        withContext(Dispatchers.IO) {
                            repository.addConversationMessage("model", failDigest, AssistantLanguage.HINDI, "NEWS_ERROR")
                        }
                        if (_voiceRepliesEnabled.value) {
                            speakOutLoud(failDigest, AssistantLanguage.HINDI, currentVoiceSpeed(), TextToSpeech.QUEUE_FLUSH)
                        }
                        "News unavailable: ${err.localizedMessage}"
                    }
                )
            }
            ActionType.MAKE_CALL -> {
                val outcome = withContext(Dispatchers.IO) {
                    val savedMemories = (memoriesState.value as? UiState.Success)?.data.orEmpty()
                    executor.makeCall(
                        target = action.target,
                        memories = savedMemories,
                        allowBackgroundDirectOnly = isLocked
                    )
                }
                if (!outcome.success && _voiceRepliesEnabled.value) {
                    speakOutLoud(outcome.logMessage, AssistantLanguage.HINDI, currentVoiceSpeed(), TextToSpeech.QUEUE_FLUSH)
                }
                outcome.logMessage
            }
            ActionType.OPEN_OFFLINE_VISION -> {
                _openOfflineVisionTrigger.value += 1
                "Opened LEON Offline Eye Camera"
            }
            ActionType.SOCIAL_AUTO_POST -> {
                applySocialRouteSpec(action.target, action.payload)
                _openSocialPosterTrigger.value += 1
                "Opened LEON Social Auto-Poster"
            }
            ActionType.OPEN_LIFE_COMPASS -> {
                if (action.target.isNotBlank()) {
                    _lifeCompassState.value = _lifeCompassState.value.copy(queryInput = action.target)
                    deliberateOnLifeCompass(action.target, speakAloud = _voiceRepliesEnabled.value)
                } else {
                    refreshProactiveRightPath(speakAloud = false)
                }
                _openLifeCompassTrigger.value += 1
                "Opened Sahi Raah • Life Compass"
            }
            ActionType.OPEN_CALL_ASSISTANT -> {
                refreshTelemetry()
                _openCallAssistantTrigger.value += 1
                "Opened AI Call Receptionist"
            }
            else -> withContext(Dispatchers.IO) { executor.execute(action).logMessage }
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        viewModelScope.launch {
            val report = executeSingleAction(pending.action)
            _statusBanner.value = report
            if (pending.remainingActions.isNotEmpty()) {
                executeActionSequence(pending.remainingActions)
            }
            refreshTelemetry()
        }
    }

    fun cancelPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        _statusBanner.value = "Action cancelled by Boss."
        if (pending.remainingActions.isNotEmpty()) {
            viewModelScope.launch {
                executeActionSequence(pending.remainingActions)
            }
        }
    }

    fun approveMemoryProposal() {
        val proposal = _pendingMemoryProposal.value ?: return
        _pendingMemoryProposal.value = null
        viewModelScope.launch {
            repository.saveMemory(proposal.key, proposal.value, proposal.category)
                .onSuccess { _statusBanner.value = "Memory saved: ${proposal.key}" }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Failed to save memory." }
        }
    }

    fun dismissMemoryProposal() {
        _pendingMemoryProposal.value = null
    }

    fun runRoutine(routine: AutomationRoutine) {
        viewModelScope.launch {
            val stepsText = routine.steps.joinToString("; then ")
            _statusBanner.value = "Running routine: ${routine.title}"
            submitPrompt(
                "Execute this automation routine named '${routine.title}' step by step: $stepsText",
                speakResponse = true,
                skipRoutineMatch = true
            )
        }
    }

    fun createRoutine(title: String, trigger: String, stepsText: String) {
        val steps = stepsText.split("\n", ",", "|").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch {
            repository.createRoutine(title, trigger, steps)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Could not create routine." }
        }
    }

    fun toggleRoutine(routineId: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleRoutine(routineId, enabled)
        }
    }

    fun deleteRoutine(routineId: Long) {
        viewModelScope.launch {
            repository.deleteRoutine(routineId)
        }
    }

    fun createTask(title: String, notes: String, dueLabel: String, priority: TaskPriority) {
        viewModelScope.launch {
            repository.createTask(title, notes, dueLabel, priority)
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Could not create task." }
        }
    }

    fun toggleTaskCompleted(taskId: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.setTaskCompleted(taskId, completed)
        }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            repository.deleteTask(taskId)
        }
    }

    fun addManualMemory(key: String, value: String, category: MemoryCategory) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveMemory(key, value, category, isPermanent = true)
                .onSuccess { saved ->
                    cloudSync?.pushMemoryToCloud(saved)
                    _statusBanner.value = "Permanent memory saved: ${saved.memoryKey}"
                }
                .onFailure { _statusBanner.value = it.localizedMessage ?: "Could not save memory." }
        }
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val targetDocId = repository.getMemoryById(memoryId)?.cloudDocId.orEmpty()
            repository.deleteMemory(memoryId)
            if (targetDocId.isNotBlank()) {
                cloudSync?.deleteMemoryFromCloud(targetDocId)
            }
        }
    }

    fun saveOwnerIdentity(ownerName: String, tagline: String = "", publishGlobally: Boolean = true) {
        val clean = ownerName.trim()
        if (clean.isEmpty()) {
            _statusBanner.value = "Please enter your name to lock Forever Creator Identity."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.setOwnerIdentity(clean, tagline.trim())
                .onSuccess { updatedPrefs ->
                    cloudSync?.pushPreferencesToCloud(updatedPrefs)
                    val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
                    if (publishGlobally && uid != null) {
                        cloudSync?.publishCreatorStampGlobally(updatedPrefs.ownerName, updatedPrefs.creatorTagline)
                            ?.onSuccess {
                                _statusBanner.value = "Saved forever & published globally: ${updatedPrefs.ownerName}"
                            }
                            ?.onFailure { err ->
                                _statusBanner.value = "Saved locally for ${updatedPrefs.ownerName}; Cloud publish needs Google Sign-In (${err.localizedMessage})."
                            }
                    } else {
                        _statusBanner.value = "Forever Owner Identity saved: ${updatedPrefs.ownerName} (Sign in with Google below to sync to Cloud)"
                    }
                }
                .onFailure {
                    _statusBanner.value = it.localizedMessage ?: "Unable to save owner identity."
                }
        }
    }

    fun publishCreatorStampGlobally() {
        val prefs = currentPreferences()
        if (prefs.ownerName.isBlank()) {
            _statusBanner.value = "Enter your name first before publishing globally."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            cloudSync?.publishCreatorStampGlobally(prefs.ownerName, prefs.creatorTagline)
                ?.onSuccess {
                    _statusBanner.value = "Creator stamp published! Everyone who downloads sees: ${prefs.ownerName}"
                }
                ?.onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Unable to publish creator stamp."
                }
        }
    }

    fun onGoogleSignInSuccess() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = cloudSync?.syncAllWithCloud()
            if (res?.isSuccess == true) {
                _statusBanner.value = "Signed in with Google & synced Forever Vault!"
            } else {
                _statusBanner.value = res?.exceptionOrNull()?.localizedMessage ?: "Signed in with Google."
            }
        }
    }

    fun syncCloudVaultNow() {
        viewModelScope.launch(Dispatchers.IO) {
            cloudSync?.syncAllWithCloud()
                ?.onSuccess { _statusBanner.value = "Forever Vault & Owner Identity synced with Cloud." }
                ?.onFailure { _statusBanner.value = it.localizedMessage ?: "Cloud sync failed." }
        }
    }

    fun signOutCloud(credentialManager: CredentialManager) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    runCatching { Firebase.auth.signOut() }
                    _statusBanner.value = "Signed out of Google Cloud Sync. Local memories stay safe on device."
                }
            }
        }
    }

    fun updateLanguage(language: AssistantLanguage) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePreferences(defaultLanguage = language)
            cloudSync?.pushPreferencesToCloud(repository.getPreferencesOnce())
        }
    }

    fun updateMemoryConsent(granted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePreferences(memoryConsentGranted = granted)
            cloudSync?.pushPreferencesToCloud(repository.getPreferencesOnce())
        }
    }

    fun updateVoiceSpeed(speed: Float) {
        viewModelScope.launch {
            repository.updatePreferences(voiceSpeed = speed.toDouble())
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversations()
        }
    }

    fun toggleFlashlight() {
        val res = executor.setFlashlight("toggle")
        _statusBanner.value = res.logMessage
        refreshTelemetry()
    }

    fun setVolumePercent(pct: Int) {
        executor.setMediaVolume(pct.toString())
        refreshTelemetry()
    }

    fun setBrightnessPercent(pct: Int) {
        val res = executor.setSystemBrightness(pct.toString())
        if (!res.success) _statusBanner.value = res.logMessage
        refreshTelemetry()
    }

    fun openDeviceShortcut(type: ActionType, target: String = "", payload: String = "") {
        val res = executor.execute(DeviceAction(type = type, target = target, payload = payload))
        if (!res.success) _statusBanner.value = res.logMessage
    }

    fun readNotificationsAloud() {
        val res = executor.execute(DeviceAction(ActionType.READ_NOTIFICATIONS))
        if (_voiceRepliesEnabled.value) {
            speakOutLoud(res.logMessage, currentLanguage(), currentVoiceSpeed())
        }
        _statusBanner.value = res.logMessage
    }

    fun draftReplyForNotification(notification: CapturedNotification) {
        viewModelScope.launch {
            _isThinking.value = true
            val res = aliBrain.draftWhatsAppReply(
                sender = notification.title,
                incomingText = notification.content,
                language = currentLanguage()
            )
            _isThinking.value = false
            res.onSuccess { reply ->
                _draftedReply.value = notification to reply
            }.onFailure {
                _statusBanner.value = AliAiBrain.describeError(it)
            }
        }
    }

    fun confirmSendDraftedReply(recipient: String, replyText: String) {
        _draftedReply.value = null
        _pendingConfirmation.value = PendingConfirmation(
            id = UUID.randomUUID().toString(),
            action = DeviceAction(
                type = ActionType.SEND_WHATSAPP,
                target = recipient,
                payload = replyText,
                requiresConfirmation = true,
                confirmationReason = "Send WhatsApp reply to $recipient?"
            ),
            summary = "Send WhatsApp message to $recipient: \"$replyText\""
        )
    }

    fun dismissDraftedReply() {
        _draftedReply.value = null
    }

    fun setRadarVisible(visible: Boolean) {
        sixthSenseEngine.setRadarVisible(visible)
    }

    fun setAirWaveEnabled(enabled: Boolean) {
        sixthSenseEngine.setAirWaveEnabled(enabled)
        _statusBanner.value = if (enabled) {
            "Air-Wave Gesture active: wave hand near earpiece to trigger."
        } else {
            "Air-Wave Gesture paused."
        }
    }

    fun setChopFlashEnabled(enabled: Boolean) {
        sixthSenseEngine.setChopFlashEnabled(enabled)
        _statusBanner.value = if (enabled) {
            "Wrist Double-Chop active: brisk double-shake toggles Flashlight."
        } else {
            "Wrist Double-Chop paused."
        }
    }

    fun setAirGestureTarget(target: AirGestureAction) {
        sixthSenseEngine.setAirGestureTarget(target)
    }

    fun toggleAcousticDbMeter(enable: Boolean): Boolean {
        val ok = sixthSenseEngine.toggleAcousticDbMeter(enable)
        if (!ok && enable) {
            _statusBanner.value = "Grant Microphone permission to measure live Room dB level."
        }
        return ok
    }

    fun sendOpticalMorse(text: String) {
        val msg = text.trim().ifBlank { "SOS" }
        sixthSenseEngine.transmitMorse(msg) { resultMsg ->
            _statusBanner.value = resultMsg
            refreshTelemetry()
        }
        _statusBanner.value = "Transmitting Optical Morse Beacon: $msg"
    }

    fun stopOpticalMorse() {
        sixthSenseEngine.stopMorse()
        refreshTelemetry()
        _statusBanner.value = "Optical Morse Beacon stopped."
    }

    fun runSixthSenseAiScan() {
        if (_isScanningEnvironment.value) return
        viewModelScope.launch {
            _isScanningEnvironment.value = true
            try {
                sixthSenseEngine.startOrRefresh()
                delay(350L)
                val latestDevice = refreshTelemetryNow()
                val latestSixth = sixthSenseEngine.telemetry.value
                val lang = currentLanguage()
                val res = aliBrain.analyzePhysicalEnvironment(latestSixth, latestDevice, lang)
                res.onSuccess { brainResult ->
                    _sixthSenseReport.value = brainResult.replyText
                    if (_voiceRepliesEnabled.value) {
                        speakOutLoud(
                            brainResult.replyText,
                            brainResult.spokenLanguage,
                            currentVoiceSpeed()
                        )
                    }
                    val safeActions = brainResult.actions.filter {
                        it.type != ActionType.ANALYZE_ENVIRONMENT && it.type != ActionType.SPEAK_ONLY
                    }
                    if (safeActions.isNotEmpty()) {
                        executeActionSequence(safeActions)
                    }
                }.onFailure { err ->
                    val luxPart = latestSixth.ambientLux?.let { "${it.toInt()} Lux" } ?: "Light sensor N/A"
                    val magPart = if (latestSixth.magnetometerAvailable) {
                        "${latestSixth.magneticFluxMicroTesla.toInt()} uT (Heading ${latestSixth.compassHeadingDeg.toInt()}°)"
                    } else {
                        "Magnetometer N/A"
                    }
                    val tempPart = "${latestSixth.batteryTempCelsius}°C battery thermal"
                    val offlineBrief = "Live Hardware Scan: $luxPart • $magPart • Stability ${latestSixth.stabilityPercent}% • $tempPart. (${AliAiBrain.describeError(err)})"
                    _sixthSenseReport.value = offlineBrief
                }
            } finally {
                _isScanningEnvironment.value = false
            }
        }
    }

    fun updateNewsSearchQuery(query: String) {
        _newsState.value = _newsState.value.copy(searchQuery = query)
    }

    fun loadNews(
        category: NewsCategory = _newsState.value.category,
        query: String = _newsState.value.searchQuery,
        speakDigest: Boolean = false
    ) {
        newsFetchJob?.cancel()
        _newsState.value = _newsState.value.copy(
            category = category,
            searchQuery = query,
            isLoading = true,
            errorMessage = null
        )
        newsFetchJob = viewModelScope.launch {
            val res = LiveNewsEngine.fetchNews(category, query)
            res.onSuccess { articles ->
                _newsState.value = _newsState.value.copy(
                    articles = articles,
                    isLoading = false,
                    errorMessage = null
                )
                if (speakDigest) {
                    val digest = LiveNewsEngine.buildSpokenDigest(articles, category, currentPreferences().ownerName)
                    LeonSpeaker.speak(appContext, digest, AssistantLanguage.HINDI, currentVoiceSpeed())
                }
            }.onFailure { err ->
                _newsState.value = _newsState.value.copy(
                    isLoading = false,
                    errorMessage = err.localizedMessage ?: "Unable to load live news."
                )
            }
        }
    }

    fun readNewsAloud() {
        val current = _newsState.value
        if (current.articles.isEmpty()) {
            loadNews(current.category, current.searchQuery, speakDigest = true)
        } else {
            val digest = LiveNewsEngine.buildSpokenDigest(
                current.articles,
                current.category,
                currentPreferences().ownerName
            )
            LeonSpeaker.speak(appContext, digest, AssistantLanguage.HINDI, currentVoiceSpeed())
        }
    }

    fun openNewsArticle(url: String) {
        if (url.isBlank()) return
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }.onFailure {
            _statusBanner.value = "Unable to open article in browser."
        }
    }

    private fun handleAirGesture(action: AirGestureAction) {
        viewModelScope.launch(Dispatchers.Main) {
            when (action) {
                AirGestureAction.TOGGLE_FLASHLIGHT -> toggleFlashlight()
                AirGestureAction.PLAY_PAUSE_MUSIC -> toggleMusic()
                AirGestureAction.TRIGGER_VOICE -> triggerVoiceCapture()
            }
        }
    }

    fun updateOfflineVisionAnalysis(analysis: OfflineVisionAnalysis) {
        _offlineVisionAnalysis.value = analysis
    }

    fun analyzeBitmapWithOfflineEye(bitmap: Bitmap, speakResult: Boolean = true) {
        viewModelScope.launch {
            val analysis = offlineVisionAnalyzer.analyzeBitmapOffline(bitmap)
            _offlineVisionAnalysis.value = analysis
            val spoken = analysis.buildSpokenSummary(currentPreferences().ownerName)
            if (speakResult && _voiceRepliesEnabled.value) {
                speakOutLoud(spoken, AssistantLanguage.HINDI, currentVoiceSpeed())
            }
        }
    }

    fun speakOfflineVision(analysis: OfflineVisionAnalysis = _offlineVisionAnalysis.value) {
        val spoken = analysis.buildSpokenSummary(currentPreferences().ownerName)
        _voiceRepliesEnabled.value = true
        speakOutLoud(spoken, AssistantLanguage.HINDI, currentVoiceSpeed(), forceForeground = true)
    }

    fun saveVisionSightingToMemory(analysis: OfflineVisionAnalysis = _offlineVisionAnalysis.value) {
        if (!analysis.hasDetections) {
            _statusBanner.value = "अभी कोई चीज़ या लिखावट पहचानी नहीं गई है।"
            return
        }
        val title = analysis.labels.firstOrNull()?.hindiName
            ?: analysis.barcodes.firstOrNull()?.kindHindi
            ?: "कैमरा स्कैन नोट"
        val detail = analysis.buildSpokenSummary("")
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveMemory("कैमरा पहचान: $title", detail, MemoryCategory.FACT, isPermanent = true)
                .onSuccess { saved ->
                    cloudSync?.pushMemoryToCloud(saved)
                    _statusBanner.value = "हमेशा के लिए सेव किया गया: $title"
                }
                .onFailure {
                    _statusBanner.value = "याद सेव करने में समस्या आई।"
                }
        }
    }

    fun openSocialPoster(target: String = "", payload: String = "") {
        applySocialRouteSpec(target, payload)
        refreshSocialPlatforms()
        _openSocialPosterTrigger.value += 1
    }

    private fun applySocialRouteSpec(target: String, payload: String) {
        if (target.isBlank() && payload.isBlank()) return
        val (platforms, postType, captionHint) = SocialMediaEngine.parseSocialSpec(target, payload)
        val current = _socialState.value
        _socialState.value = current.copy(
            selectedPlatforms = platforms.toSet(),
            postType = postType,
            caption = if (captionHint.isNotBlank()) captionHint else current.caption,
            installedPlatforms = socialMediaEngine.checkInstalledPlatforms()
        )
    }

    fun refreshSocialPlatforms() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = socialMediaEngine.checkInstalledPlatforms()
            _socialState.value = _socialState.value.copy(installedPlatforms = installed)
        }
    }

    fun toggleSocialPlatform(platform: SocialPlatform) {
        val current = _socialState.value.selectedPlatforms.toMutableSet()
        if (platform in current) {
            if (current.size > 1) {
                current.remove(platform)
            } else {
                _statusBanner.value = "कम से कम 1 सोशल ऐप चुनना ज़रूरी है।"
                return
            }
        } else {
            current.add(platform)
        }
        _socialState.value = _socialState.value.copy(selectedPlatforms = current)
    }

    fun setSocialPostType(type: SocialPostType) {
        _socialState.value = _socialState.value.copy(postType = type)
    }

    fun updateSocialCaption(caption: String) {
        _socialState.value = _socialState.value.copy(caption = caption)
    }

    fun setSocialAutoTapEnabled(enabled: Boolean) {
        _socialState.value = _socialState.value.copy(autoTapEnabled = enabled)
    }

    fun onSocialMediaPicked(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = socialMediaEngine.stageMediaFromUri(uri)
            res.onSuccess { staged ->
                _socialState.value = _socialState.value.copy(stagedMedia = staged)
                SocialAutoPosterBridge.appendLog(
                    "Media staged: ${staged.displayName} (${if (staged.isVideo) "Video" else "Photo"})"
                )
                _statusBanner.value = "मीडिया तैयार है: ${staged.displayName}"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Selected media could not be staged."
            }
        }
    }

    fun generateAiSocialCaption() {
        if (_socialState.value.isGeneratingCaption) return
        val snapshot = _socialState.value
        _socialState.value = snapshot.copy(isGeneratingCaption = true)
        viewModelScope.launch {
            try {
                val platforms = snapshot.selectedPlatforms.toList().ifEmpty { SocialPlatform.entries }
                val caption = aliBrain.generateSocialCaption(
                    topicHint = snapshot.caption.ifBlank { snapshot.stagedMedia?.displayName.orEmpty() },
                    platforms = platforms,
                    postType = snapshot.postType,
                    language = currentLanguage()
                )
                _socialState.value = _socialState.value.copy(
                    caption = caption,
                    isGeneratingCaption = false
                )
                socialMediaEngine.copyCaptionToClipboard(caption)
                SocialAutoPosterBridge.appendLog("Generated & copied caption (${caption.length} chars)")
            } finally {
                _socialState.value = _socialState.value.copy(isGeneratingCaption = false)
            }
        }
    }

    fun startSocialAutoPostQueue() {
        val state = _socialState.value
        val media = state.stagedMedia
        if (media == null) {
            val msg = "बॉस, पहले नीचे से कोई वीडियो या फोटो चुनिए!"
            _statusBanner.value = msg
            if (_voiceRepliesEnabled.value) {
                speakOutLoud(msg, AssistantLanguage.HINDI, currentVoiceSpeed(), forceForeground = true)
            }
            return
        }
        val orderedPlatforms = SocialPlatform.entries.filter { it in state.selectedPlatforms }
            .ifEmpty { SocialPlatform.entries }
        val installedMap = socialMediaEngine.checkInstalledPlatforms()
        _socialState.value = state.copy(installedPlatforms = installedMap)
        val installedQueue = orderedPlatforms.filter { installedMap[it] == true }
        if (installedQueue.isEmpty()) {
            val msg = "बॉस, चुने हुए ऐप (Instagram/Facebook/WhatsApp) इस डिवाइस पर इंस्टॉल नहीं मिले।"
            _statusBanner.value = msg
            SocialAutoPosterBridge.appendLog(msg)
            return
        }

        val effectiveAutoTap = state.autoTapEnabled && _telemetry.value.accessibilityEnabled
        val job = SocialPostJob(
            id = UUID.randomUUID().toString(),
            platforms = installedQueue,
            currentIndex = 0,
            postType = state.postType,
            caption = state.caption.trim(),
            autoTapEnabled = effectiveAutoTap,
            media = media
        )
        SocialAutoPosterBridge.armJob(job)
        val firstPlatform = installedQueue.first()
        val outcome = socialMediaEngine.launchPlatformStep(
            platform = firstPlatform,
            postType = job.postType,
            media = media,
            caption = job.caption
        )
        SocialAutoPosterBridge.appendLog("${firstPlatform.displayName}: ${outcome.logMessage}")
        _statusBanner.value = outcome.logMessage
        if (!outcome.success) {
            SocialAutoPosterBridge.advanceStepManually()
        }
    }

    fun launchNextSocialPlatform() {
        val nextJob = SocialAutoPosterBridge.advanceStepManually() ?: run {
            _statusBanner.value = "सभी चुने हुए प्लेटफ़ॉर्म पूरे हो चुके हैं।"
            return
        }
        val platform = nextJob.currentPlatform ?: return
        val outcome = socialMediaEngine.launchPlatformStep(
            platform = platform,
            postType = nextJob.postType,
            media = nextJob.media,
            caption = nextJob.caption
        )
        SocialAutoPosterBridge.appendLog("${platform.displayName}: ${outcome.logMessage}")
        _statusBanner.value = outcome.logMessage
    }

    fun clearSocialQueue() {
        SocialAutoPosterBridge.clearJob()
        _statusBanner.value = "Social Auto-Poster queue cleared."
    }

    fun dismissHumanReflection() {
        _latestHumanReflection.value = null
    }

    fun openLifeCompassWithSeed(seed: String = "") {
        val cleanSeed = seed.trim()
        if (cleanSeed.isNotEmpty()) {
            _lifeCompassState.value = _lifeCompassState.value.copy(queryInput = cleanSeed)
        }
        refreshProactiveRightPath(speakAloud = false)
        _openLifeCompassTrigger.value += 1
    }

    fun updateLifeCompassQuery(query: String) {
        _lifeCompassState.value = _lifeCompassState.value.copy(queryInput = query, errorMessage = null)
    }

    fun refreshProactiveRightPath(speakAloud: Boolean = false) {
        val prefs = currentPreferences()
        val tasks = currentTasks()
        val memories = currentMemories()
        val notifs = NotificationBridge.notifications.value
        val tel = _telemetry.value

        if (_lifeCompassState.value.proactiveGuidance == null) {
            val instantBaseline = HumanWisdomEngine.buildProactiveGuidance(
                ownerName = prefs.ownerName,
                tasks = tasks,
                memories = memories,
                notifications = notifs,
                telemetry = tel
            )
            _lifeCompassState.value = _lifeCompassState.value.copy(proactiveGuidance = instantBaseline)
        }

        compassJob?.cancel()
        _lifeCompassState.value = _lifeCompassState.value.copy(isAutoThinking = true, errorMessage = null)
        compassJob = viewModelScope.launch {
            try {
                val guidance = aliBrain.generateProactiveRightPath(
                    ownerName = prefs.ownerName,
                    tasks = tasks,
                    memories = memories,
                    notifications = notifs,
                    telemetry = tel,
                    language = AssistantLanguage.fromCode(prefs.defaultLanguage)
                )
                _lifeCompassState.value = _lifeCompassState.value.copy(
                    isAutoThinking = false,
                    proactiveGuidance = guidance
                )
                if (speakAloud && _voiceRepliesEnabled.value) {
                    speakOutLoud(
                        "${guidance.headline}. ${guidance.rightPathNudge}",
                        AssistantLanguage.fromCode(prefs.defaultLanguage),
                        currentVoiceSpeed(),
                        forceForeground = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lifeCompassState.value = _lifeCompassState.value.copy(
                    isAutoThinking = false,
                    errorMessage = e.localizedMessage
                )
            }
        }
    }

    fun deliberateOnLifeCompass(queryOverride: String? = null, speakAloud: Boolean = true) {
        val query = (queryOverride ?: _lifeCompassState.value.queryInput).trim()
        if (query.isEmpty()) {
            _statusBanner.value = "बॉस, पहले अपनी उलझन या सवाल लिखें — LEON सोचकर सही राह बताएगा।"
            return
        }
        val prefs = currentPreferences()
        val lang = AssistantLanguage.fromCode(prefs.defaultLanguage)
        compassJob?.cancel()
        _lifeCompassState.value = _lifeCompassState.value.copy(
            queryInput = query,
            isThinking = true,
            errorMessage = null
        )
        compassJob = viewModelScope.launch {
            try {
                val latestTel = refreshTelemetryNow()
                val analysis = if (latestTel.internetConnected) {
                    aliBrain.deliberateOnLifeDilemma(
                        query = query,
                        ownerName = prefs.ownerName,
                        tasks = currentTasks(),
                        memories = currentMemories(),
                        history = currentHistory(),
                        language = lang
                    )
                } else {
                    HumanWisdomEngine.analyzeUserSituationOffline(
                        query = query,
                        ownerName = prefs.ownerName,
                        tasks = currentTasks(),
                        memories = currentMemories(),
                        language = lang
                    )
                }
                _lifeCompassState.value = _lifeCompassState.value.copy(
                    isThinking = false,
                    currentAnalysis = analysis
                )
                _latestHumanReflection.value = HumanReflectionBanner(
                    emotionalTone = analysis.emotionalTone,
                    innerThought = analysis.emotionalUnderstanding,
                    rightPathAdvice = analysis.rightPathDecision,
                    sourceQuery = query
                )
                if (speakAloud && _voiceRepliesEnabled.value) {
                    speakOutLoud(
                        analysis.spokenSummary,
                        lang,
                        currentVoiceSpeed(),
                        forceForeground = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val fallback = HumanWisdomEngine.analyzeUserSituationOffline(
                    query = query,
                    ownerName = prefs.ownerName,
                    tasks = currentTasks(),
                    memories = currentMemories(),
                    language = lang
                )
                _lifeCompassState.value = _lifeCompassState.value.copy(
                    isThinking = false,
                    currentAnalysis = fallback,
                    errorMessage = null
                )
            }
        }
    }

    fun speakLifeCompassSummary(text: String) {
        if (text.isBlank()) return
        _voiceRepliesEnabled.value = true
        speakOutLoud(text, currentLanguage(), currentVoiceSpeed(), forceForeground = true)
    }

    fun addCompassStepAsTask(stepTitle: String) {
        val clean = stepTitle.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.createTask(
                title = clean,
                notes = "Added from LEON Sahi Raah • Life Compass",
                dueLabel = "Today",
                priority = TaskPriority.HIGH
            ).onSuccess {
                _statusBanner.value = "सही राह का कदम Daily Tasks में जुड़ गया: $clean"
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "टास्क सेव नहीं हो सका।"
            }
        }
    }

    fun startCompassFocusTimer(minutes: Int) {
        val safeMins = minutes.coerceIn(1, 180)
        LeonVoiceService.startBackgroundTimer(
            appContext,
            safeMins * 60,
            "Sahi Raah Focus (${safeMins}m)"
        )
        _statusBanner.value = "$safeMins मिनट का फोकस टाइमर शुरू हो गया — पूरे ध्यान से आगे बढ़ें!"
    }

    // === AI Incoming Call Receptionist Controls & Interactive Simulator ===

    fun toggleCallReceptionist(enabled: Boolean) {
        CallReceptionistBridge.updateConfig { it.copy(enabled = enabled) }
        refreshTelemetry()
        _statusBanner.value = if (enabled) {
            val sec = CallReceptionistBridge.state.value.config.autoAnswerDelaySeconds
            "AI Call Receptionist चालू! अब ${sec}s तक कॉल रिसीव न होने पर LEON बात करेगा।"
        } else {
            "AI Call Receptionist बंद कर दिया गया।"
        }
    }

    fun updateCallAutoAnswerDelay(seconds: Int) {
        val clamped = seconds.coerceIn(5, 25)
        CallReceptionistBridge.updateConfig { it.copy(autoAnswerDelaySeconds = clamped) }
    }

    fun updateBossAvailabilityMode(mode: BossAvailabilityMode, customNote: String? = null) {
        CallReceptionistBridge.setAvailabilityMode(mode, customNote)
    }

    fun updateAutoTakeCallbackTask(enabled: Boolean) {
        CallReceptionistBridge.updateConfig { it.copy(autoTakeTaskOnCallback = enabled) }
    }

    fun updateCallSimulatorInput(text: String) {
        CallReceptionistBridge.setSimulatorInput(text)
    }

    fun startSimulatedIncomingCall(callerLabel: String = "राहुल शर्मा") {
        val prefs = currentPreferences()
        val lang = AssistantLanguage.fromCode(prefs.defaultLanguage)
        val config = CallReceptionistBridge.state.value.config
        val greeting = CallReceptionistEngine.buildInitialGreeting(
            ownerName = prefs.ownerName,
            callerDisplay = callerLabel,
            config = config,
            language = lang
        )
        CallReceptionistBridge.beginAiDialogueSession(
            callerDisplay = callerLabel,
            callerNumber = "+91-98765-43210",
            initialGreeting = greeting,
            isSimulation = true
        )
        if (_voiceRepliesEnabled.value) {
            speakOutLoud(greeting, lang, currentVoiceSpeed(), forceForeground = true)
        }
    }

    fun sendSimulatedCallerTurn(overrideText: String? = null) {
        val snapshot = CallReceptionistBridge.state.value
        val utterance = (overrideText ?: snapshot.simulatorInput).trim()
        if (utterance.isEmpty()) return
        if (snapshot.activeCallState !in setOf("SIMULATED_ACTIVE", "AI_TALKING")) {
            startSimulatedIncomingCall()
        }

        CallReceptionistBridge.appendCallerTurn(utterance)
        CallReceptionistBridge.setGeneratingReply(true)
        callSimJob?.cancel()
        callSimJob = viewModelScope.launch {
            try {
                val prefs = currentPreferences()
                val lang = AssistantLanguage.fromCode(prefs.defaultLanguage)
                val latestSnapshot = CallReceptionistBridge.state.value
                val tel = _telemetry.value

                val response = if (tel.internetConnected) {
                    aliBrain.generateCallReceptionistTurn(
                        callerDisplay = latestSnapshot.activeCallCaller,
                        callerUtterance = utterance,
                        priorTurns = latestSnapshot.activeTurns.dropLast(1),
                        ownerName = prefs.ownerName,
                        config = latestSnapshot.config,
                        language = lang,
                        existingCallerName = latestSnapshot.capturedCallerName,
                        existingPurpose = latestSnapshot.capturedPurpose
                    )
                } else {
                    CallReceptionistEngine.generateOfflineTurn(
                        callerDisplay = latestSnapshot.activeCallCaller,
                        callerUtterance = utterance,
                        priorTurns = latestSnapshot.activeTurns.dropLast(1),
                        ownerName = prefs.ownerName,
                        config = latestSnapshot.config,
                        language = lang,
                        existingCallerName = latestSnapshot.capturedCallerName,
                        existingPurpose = latestSnapshot.capturedPurpose
                    )
                }

                CallReceptionistBridge.appendAiTurn(
                    replyText = response.replyToCaller,
                    capturedName = response.capturedCallerName,
                    capturedPurpose = response.capturedPurpose,
                    urgency = response.urgency,
                    statusBanner = response.summaryForBoss
                )
                if (_voiceRepliesEnabled.value) {
                    speakOutLoud(response.replyToCaller, lang, currentVoiceSpeed(), forceForeground = true)
                }
                if (response.shouldEndCall) {
                    val finalized = CallReceptionistBridge.finalizeActiveCallSession("AI Call Completed")
                    if (finalized != null && latestSnapshot.config.autoTakeTaskOnCallback) {
                        saveCallbackTaskForRecord(finalized, showBanner = false)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CallReceptionistBridge.setGeneratingReply(false)
                _statusBanner.value = e.localizedMessage ?: "कॉल सिमुलेशन में त्रुटि आई।"
            }
        }
    }

    fun endActiveOrSimulatedCall() {
        callSimJob?.cancel()
        val stateNow = CallReceptionistBridge.state.value
        if (stateNow.activeCallState == "AI_TALKING" || stateNow.activeCallState == "RINGING_COUNTDOWN") {
            LeonCallReceptionistService.stopActiveCall(appContext)
        } else {
            val finalized = CallReceptionistBridge.finalizeActiveCallSession()
            if (finalized != null && stateNow.config.autoTakeTaskOnCallback) {
                saveCallbackTaskForRecord(finalized, showBanner = true)
            }
        }
    }

    fun speakHandledCallSummary(record: HandledCallRecord) {
        val alertText = CallReceptionistEngine.formatBossCallAlert(record)
        _voiceRepliesEnabled.value = true
        speakOutLoud(alertText, AssistantLanguage.HINDI, currentVoiceSpeed(), forceForeground = true)
    }

    fun addHandledCallAsTask(record: HandledCallRecord) {
        saveCallbackTaskForRecord(record, showBanner = true)
    }

    private fun saveCallbackTaskForRecord(record: HandledCallRecord, showBanner: Boolean) {
        val priority = when (record.urgency.uppercase()) {
            "HIGH" -> TaskPriority.HIGH
            "LOW" -> TaskPriority.LOW
            else -> TaskPriority.MEDIUM
        }
        viewModelScope.launch {
            repository.createTask(
                title = "Call back ${record.callerDisplay}: ${record.purposeSummary.take(65)}",
                notes = record.turns.joinToString("\n") { "${it.speaker}: ${it.text}" },
                dueLabel = if (record.urgency == "HIGH") "Urgent Today" else "Today",
                priority = priority
            ).onSuccess {
                if (showBanner) {
                    _statusBanner.value = "कॉलबैक टास्क सेव हो गया: ${record.callerDisplay}"
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        callSimJob?.cancel()
        LeonSpeaker.setOnSpeechIdleListener(null)
        VoiceServiceBridge.setForegroundMicActive(false)
        LeonSpeaker.stop(notifyIdle = false)
        runCatching { offlineVisionAnalyzer.close() }
        sixthSenseEngine.release()
        cloudSync?.release()
    }

    private fun currentPreferences(): UserPreferences =
        (preferencesState.value as? UiState.Success)?.data ?: UserPreferences()

    private fun currentLanguage(): AssistantLanguage =
        AssistantLanguage.fromCode(currentPreferences().defaultLanguage)

    private fun currentVoiceSpeed(): Float =
        currentPreferences().voiceSpeed.toFloat().coerceIn(0.5f, 2.0f)

    private fun currentMemories(): List<MemoryItem> =
        (memoriesState.value as? UiState.Success)?.data ?: emptyList()

    private fun currentRoutines(): List<AutomationRoutine> =
        (routinesState.value as? UiState.Success)?.data ?: emptyList()

    private fun currentTasks(): List<DailyTask> =
        (tasksState.value as? UiState.Success)?.data ?: emptyList()

    private fun currentHistory(): List<ConversationMessage> =
        (conversationsState.value as? UiState.Success)?.data ?: emptyList()
}
