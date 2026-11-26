package com.example.service

import com.example.data.BossAvailabilityMode
import com.example.data.CallReceptionistConfig
import com.example.data.CallReceptionistUiState
import com.example.data.CallTranscriptTurn
import com.example.data.HandledCallRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Shared thread-safe state bridge connecting LeonCallScreeningService, IncomingCallReceiver,
 * LeonCallReceptionistService, and LeonViewModel / CallAssistantScreen.
 */
object CallReceptionistBridge {

    private val _state = MutableStateFlow(CallReceptionistUiState())
    val state: StateFlow<CallReceptionistUiState> = _state.asStateFlow()

    @Volatile
    var lastScreenedNumber: String = ""
        private set

    @Volatile
    var lastScreenedDisplayName: String = ""
        private set

    @Volatile
    var lastScreenedAtMillis: Long = 0L
        private set

    @Volatile
    var aiAcceptedCurrentCall: Boolean = false
        private set

    fun updateConfig(transform: (CallReceptionistConfig) -> CallReceptionistConfig) {
        _state.update { it.copy(config = transform(it.config)) }
    }

    fun setAvailabilityMode(mode: BossAvailabilityMode, customNote: String? = null) {
        _state.update { current ->
            current.copy(
                config = current.config.copy(
                    availabilityMode = mode,
                    customBossNote = customNote ?: current.config.customBossNote
                )
            )
        }
    }

    fun setSimulatorInput(text: String) {
        _state.update { it.copy(simulatorInput = text) }
    }

    fun setGeneratingReply(generating: Boolean) {
        _state.update { it.copy(isGeneratingReply = generating) }
    }

    fun setListeningCaller(listening: Boolean) {
        _state.update { it.copy(isListeningCaller = listening) }
    }

    fun onIncomingCallScreened(number: String, displayName: String) {
        lastScreenedNumber = number.trim()
        lastScreenedDisplayName = displayName.trim()
        lastScreenedAtMillis = System.currentTimeMillis()
    }

    fun resolveRecentCaller(): Pair<String, String> {
        val fresh = (System.currentTimeMillis() - lastScreenedAtMillis) < 90_000L
        val number = if (fresh) lastScreenedNumber else ""
        val display = if (fresh) {
            lastScreenedDisplayName.ifBlank { number.ifBlank { "अज्ञात कॉलर" } }
        } else {
            "अज्ञात कॉलर"
        }
        return display to number
    }

    fun beginRingingCountdown(callerDisplay: String, callerNumber: String, delaySeconds: Int) {
        aiAcceptedCurrentCall = false
        _state.update {
            it.copy(
                activeCallCaller = callerDisplay.ifBlank { "अज्ञात कॉलर" },
                activeCallNumber = callerNumber,
                activeCallState = "RINGING_COUNTDOWN",
                countdownSeconds = delaySeconds,
                activeTurns = emptyList(),
                capturedCallerName = "",
                capturedPurpose = "",
                capturedUrgency = "MEDIUM",
                statusBanner = "कॉल आ रही है — ${delaySeconds}s में उत्तर न मिलने पर LEON AI रिसीव करेगा।"
            )
        }
    }

    fun updateCountdown(secondsLeft: Int) {
        _state.update { it.copy(countdownSeconds = secondsLeft.coerceAtLeast(0)) }
    }

    fun markAiAcceptingCall(accepting: Boolean) {
        aiAcceptedCurrentCall = accepting
    }

    fun beginAiDialogueSession(
        callerDisplay: String,
        callerNumber: String,
        initialGreeting: String,
        isSimulation: Boolean = false
    ) {
        aiAcceptedCurrentCall = !isSimulation
        _state.update {
            it.copy(
                activeCallCaller = callerDisplay.ifBlank { "अज्ञात कॉलर" },
                activeCallNumber = callerNumber,
                activeCallState = if (isSimulation) "SIMULATED_ACTIVE" else "AI_TALKING",
                countdownSeconds = 0,
                activeTurns = listOf(
                    CallTranscriptTurn(
                        speaker = "LEON AI",
                        text = initialGreeting
                    )
                ),
                capturedCallerName = "",
                capturedPurpose = "",
                capturedUrgency = "MEDIUM",
                statusBanner = if (isSimulation) {
                    "Live Call Simulator सक्रिय — नीचे कॉलर की तरह संदेश लिखकर टेस्ट करें।"
                } else {
                    "LEON AI ने कॉल रिसीव कर ली है और कॉलर से बात कर रहा है।"
                }
            )
        }
    }

    fun appendCallerTurn(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _state.update {
            it.copy(
                activeTurns = it.activeTurns + CallTranscriptTurn(speaker = "CALLER", text = trimmed),
                simulatorInput = ""
            )
        }
    }

    fun appendAiTurn(
        replyText: String,
        capturedName: String = "",
        capturedPurpose: String = "",
        urgency: String = "MEDIUM",
        statusBanner: String? = null
    ) {
        _state.update { current ->
            val resolvedName = capturedName.ifBlank { current.capturedCallerName }
            val resolvedPurpose = capturedPurpose.ifBlank { current.capturedPurpose }
            current.copy(
                activeCallCaller = if (resolvedName.isNotBlank() &&
                    (current.activeCallCaller == "अज्ञात कॉलर" || current.activeCallCaller == "Test Caller" || current.activeCallCaller.all { ch -> ch.isDigit() || ch == '+' || ch == '-' || ch == ' ' })
                ) {
                    resolvedName
                } else {
                    current.activeCallCaller
                },
                activeTurns = current.activeTurns + CallTranscriptTurn(speaker = "LEON AI", text = replyText),
                capturedCallerName = resolvedName,
                capturedPurpose = resolvedPurpose,
                capturedUrgency = urgency,
                isGeneratingReply = false,
                statusBanner = statusBanner ?: current.statusBanner
            )
        }
    }

    fun cancelCountdownBossAnswered() {
        aiAcceptedCurrentCall = false
        _state.update { current ->
            if (current.activeCallState == "RINGING_COUNTDOWN") {
                current.copy(
                    activeCallState = "IDLE",
                    countdownSeconds = 0,
                    statusBanner = "बॉस ने कॉल स्वयं रिसीव कर ली।"
                )
            } else {
                current
            }
        }
    }

    fun finalizeActiveCallSession(statusLabelOverride: String? = null): HandledCallRecord? {
        aiAcceptedCurrentCall = false
        var finalized: HandledCallRecord? = null
        _state.update { current ->
            val wasActive = current.activeCallState in setOf("AI_TALKING", "SIMULATED_ACTIVE") &&
                current.activeTurns.isNotEmpty()
            if (wasActive) {
                val caller = current.capturedCallerName.ifBlank { current.activeCallCaller }.ifBlank { "अज्ञात कॉलर" }
                val purpose = current.capturedPurpose.ifBlank {
                    current.activeTurns.firstOrNull { it.speaker == "CALLER" }?.text?.take(120)
                        ?: "कॉलर ने कोई वॉइस संदेश नहीं छोड़ा।"
                }
                val record = HandledCallRecord(
                    id = UUID.randomUUID().toString(),
                    callerDisplay = caller,
                    callerNumber = current.activeCallNumber,
                    statusLabel = statusLabelOverride ?: if (current.activeCallState == "SIMULATED_ACTIVE") {
                        "AI Call Test Completed"
                    } else {
                        "AI Receptionist Answered"
                    },
                    purposeSummary = purpose,
                    urgency = current.capturedUrgency,
                    aiAnswered = true,
                    turns = current.activeTurns
                )
                finalized = record
                current.copy(
                    activeCallCaller = "",
                    activeCallNumber = "",
                    activeCallState = "IDLE",
                    activeTurns = emptyList(),
                    countdownSeconds = 0,
                    isGeneratingReply = false,
                    isListeningCaller = false,
                    capturedCallerName = "",
                    capturedPurpose = "",
                    capturedUrgency = "MEDIUM",
                    handledCalls = (listOf(record) + current.handledCalls).take(30),
                    statusBanner = "कॉल रिपोर्ट सेव हो गई: $caller — $purpose"
                )
            } else {
                current.copy(
                    activeCallCaller = "",
                    activeCallNumber = "",
                    activeCallState = "IDLE",
                    activeTurns = emptyList(),
                    countdownSeconds = 0,
                    isGeneratingReply = false,
                    isListeningCaller = false,
                    statusBanner = statusLabelOverride ?: current.statusBanner
                )
            }
        }
        return finalized
    }
}
