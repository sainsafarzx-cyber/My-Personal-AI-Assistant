package com.example.service

import com.example.data.CapturedNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceOrbState {
    IDLE,
    WAKE_LISTENING,
    ACTIVE_LISTENING,
    PROCESSING,
    SPEAKING
}

object NotificationBridge {
    private val _notifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val notifications: StateFlow<List<CapturedNotification>> = _notifications.asStateFlow()

    private val _listenerConnected = MutableStateFlow(false)
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    fun setConnected(connected: Boolean) {
        _listenerConnected.value = connected
    }

    fun pushNotification(item: CapturedNotification) {
        val current = _notifications.value.filterNot { it.key == item.key }
        _notifications.value = (listOf(item) + current).take(25)
    }

    fun removeNotification(key: String) {
        _notifications.value = _notifications.value.filterNot { it.key == key }
    }

    fun clearAll() {
        _notifications.value = emptyList()
    }
}

object AccessibilityBridge {
    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _activePackage = MutableStateFlow("")
    val activePackage: StateFlow<String> = _activePackage.asStateFlow()

    private val _globalActionRequests = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val globalActionRequests: SharedFlow<Int> = _globalActionRequests.asSharedFlow()

    fun setConnected(connected: Boolean) {
        _serviceConnected.value = connected
    }

    fun updateActivePackage(pkg: String) {
        _activePackage.value = pkg
    }

    fun requestGlobalAction(globalAction: Int): Boolean {
        return _serviceConnected.value && _globalActionRequests.tryEmit(globalAction)
    }
}

sealed interface VoiceServiceEvent {
    data object WakeWordDetected : VoiceServiceEvent
    data class TranscriptCaptured(val text: String) : VoiceServiceEvent
    data object SpeechFallbackRequested : VoiceServiceEvent
    data object EmergencyStopTriggered : VoiceServiceEvent
    data class ErrorOccurred(val message: String) : VoiceServiceEvent
}

object VoiceServiceBridge {
    private val _alwaysReadyRunning = MutableStateFlow(false)
    val alwaysReadyRunning: StateFlow<Boolean> = _alwaysReadyRunning.asStateFlow()

    private val _lockScreenModeActive = MutableStateFlow(false)
    val lockScreenModeActive: StateFlow<Boolean> = _lockScreenModeActive.asStateFlow()

    private val _deviceLocked = MutableStateFlow(false)
    val deviceLocked: StateFlow<Boolean> = _deviceLocked.asStateFlow()

    private val _foregroundUiActive = MutableStateFlow(false)
    val foregroundUiActive: StateFlow<Boolean> = _foregroundUiActive.asStateFlow()

    private val _foregroundMicActive = MutableStateFlow(false)
    val foregroundMicActive: StateFlow<Boolean> = _foregroundMicActive.asStateFlow()

    private val _musicPlaying = MutableStateFlow(false)
    val musicPlaying: StateFlow<Boolean> = _musicPlaying.asStateFlow()

    private val _activeTimerRemainingSeconds = MutableStateFlow(0)
    val activeTimerRemainingSeconds: StateFlow<Int> = _activeTimerRemainingSeconds.asStateFlow()

    private val _orbState = MutableStateFlow(VoiceOrbState.IDLE)
    val orbState: StateFlow<VoiceOrbState> = _orbState.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _events = MutableSharedFlow<VoiceServiceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<VoiceServiceEvent> = _events.asSharedFlow()

    fun setRunning(running: Boolean) {
        _alwaysReadyRunning.value = running
        if (!running) {
            _lockScreenModeActive.value = false
            _orbState.value = VoiceOrbState.IDLE
            _partialTranscript.value = ""
        }
    }

    fun setLockScreenModeActive(active: Boolean) {
        _lockScreenModeActive.value = active
    }

    fun setDeviceLocked(locked: Boolean) {
        _deviceLocked.value = locked
    }

    fun setForegroundUiActive(active: Boolean) {
        _foregroundUiActive.value = active
    }

    fun setForegroundMicActive(active: Boolean) {
        _foregroundMicActive.value = active
    }

    fun setMusicPlaying(playing: Boolean) {
        _musicPlaying.value = playing
    }

    fun setActiveTimerRemainingSeconds(seconds: Int) {
        _activeTimerRemainingSeconds.value = seconds.coerceAtLeast(0)
    }

    fun setOrbState(state: VoiceOrbState) {
        _orbState.value = state
    }

    fun setPartialTranscript(text: String) {
        _partialTranscript.value = text
    }

    fun emitEvent(event: VoiceServiceEvent) {
        _events.tryEmit(event)
    }
}

object BrightnessBridge {
    private val _windowBrightness = MutableStateFlow<Float?>(null)
    val windowBrightness: StateFlow<Float?> = _windowBrightness.asStateFlow()

    fun setWindowBrightness(fraction: Float?) {
        _windowBrightness.value = fraction?.coerceIn(0.05f, 1.0f)
    }
}
