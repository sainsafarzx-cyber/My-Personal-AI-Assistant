package com.example.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AssistantLanguage(val code: String, val label: String, val localeTag: String) {
    HINDI("hi", "हिन्दी (Default)", "hi-IN"),
    ENGLISH("en", "English", "en-IN"),
    HINGLISH("hinglish", "Hinglish", "hi-IN");

    companion object {
        fun fromCode(raw: String?): AssistantLanguage =
            entries.firstOrNull { it.code == raw } ?: HINDI
    }
}

enum class MemoryCategory(val wireValue: String, val label: String) {
    PREFERENCE("preference", "Preference"),
    FACT("fact", "Personal Fact"),
    CONTACT("contact", "Contact Hint"),
    ROUTINE_HINT("routine_hint", "Routine Hint");

    companion object {
        fun fromWire(raw: String?): MemoryCategory =
            entries.firstOrNull { it.wireValue == raw } ?: PREFERENCE
    }
}

enum class TaskPriority(val wireValue: String, val label: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High");

    companion object {
        fun fromWire(raw: String?): TaskPriority =
            entries.firstOrNull { it.wireValue == raw } ?: MEDIUM
    }
}

@Entity(tableName = "user_preferences")
data class UserPreferences(
    @PrimaryKey val id: Int = 1,
    val assistantName: String = "LEON AI",
    val ownerName: String = "",
    val creatorTagline: String = "",
    val defaultLanguage: String = AssistantLanguage.HINDI.code,
    val alwaysReadyEnabled: Boolean = false,
    val lockScreenModeEnabled: Boolean = true,
    val memoryConsentGranted: Boolean = true,
    val voiceSpeed: Double = 1.0,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "memories")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val memoryKey: String = "",
    val memoryValue: String = "",
    val category: String = MemoryCategory.PREFERENCE.wireValue,
    val approved: Boolean = true,
    val isPermanent: Boolean = true,
    val cloudDocId: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "routines")
data class AutomationRoutine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val triggerPhrase: String = "",
    val steps: List<String> = emptyList(),
    val enabled: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class DailyTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val notes: String = "",
    val dueLabel: String = "",
    val priority: String = TaskPriority.MEDIUM.wireValue,
    val completed: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Immutable
@Entity(tableName = "conversations")
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val role: String = "user",
    val text: String = "",
    val language: String = AssistantLanguage.HINDI.code,
    val actionSummary: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

enum class ActionType {
    SPEAK_ONLY,
    OPEN_APP,
    MAKE_CALL,
    SEND_WHATSAPP,
    SEND_SMS,
    READ_NOTIFICATIONS,
    PLAY_MUSIC,
    LOCK_SCREEN_BRIEFING,
    OPTICAL_MORSE,
    TOGGLE_AIR_GESTURES,
    ANALYZE_ENVIRONMENT,
    SAVE_MEMORY,
    SET_OWNER_IDENTITY,
    YOUTUBE_SEARCH,
    GOOGLE_SEARCH,
    OPEN_WEBSITE,
    OPEN_CAMERA,
    TOGGLE_FLASHLIGHT,
    SET_VOLUME,
    SET_BRIGHTNESS,
    SET_ALARM,
    SET_TIMER,
    CREATE_TASK,
    CREATE_ROUTINE,
    OPEN_WIFI_PANEL,
    OPEN_BLUETOOTH_SETTINGS,
    OPEN_SETTINGS,
    ACCESSIBILITY_GLOBAL,
    FETCH_NEWS,
    TOGGLE_INTERNET,
    OPEN_OFFLINE_VISION,
    SOCIAL_AUTO_POST,
    OPEN_LIFE_COMPASS,
    OPEN_CALL_ASSISTANT,
    EMERGENCY_STOP
}

@Immutable
data class NewsUiState(
    val category: com.example.news.NewsCategory = com.example.news.NewsCategory.TOP,
    val articles: List<com.example.news.NewsArticle> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = ""
)

data class DeviceAction(
    val type: ActionType,
    val target: String = "",
    val payload: String = "",
    val requiresConfirmation: Boolean = false,
    val confirmationReason: String = ""
)

@Immutable
data class MemoryProposal(
    val key: String,
    val value: String,
    val category: MemoryCategory
)

data class AliBrainResult(
    val replyText: String,
    val spokenLanguage: AssistantLanguage,
    val actions: List<DeviceAction>,
    val memoryProposal: MemoryProposal? = null,
    val suggestions: List<String> = emptyList(),
    val emotionalTone: String = "",
    val innerThought: String = "",
    val rightPathAdvice: String = ""
)

@Immutable
data class HumanReflectionBanner(
    val emotionalTone: String,
    val innerThought: String,
    val rightPathAdvice: String,
    val sourceQuery: String = ""
)

@Immutable
data class LifeCompassAnalysis(
    val query: String,
    val emotionalTone: String,
    val emotionalUnderstanding: String,
    val prosConsAnalysis: String,
    val rightPathDecision: String,
    val actionableSteps: List<String>,
    val gentleWarning: String,
    val spokenSummary: String,
    val fromCloudAi: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Immutable
data class ProactiveLifeGuidance(
    val headline: String,
    val observation: String,
    val rightPathNudge: String,
    val recommendedStep: String,
    val suggestedFocusMinutes: Int = 25,
    val toneBadge: String = "Caring Mentor",
    val fromCloudAi: Boolean = false
)

@Immutable
data class LifeCompassUiState(
    val queryInput: String = "",
    val isThinking: Boolean = false,
    val isAutoThinking: Boolean = false,
    val proactiveGuidance: ProactiveLifeGuidance? = null,
    val currentAnalysis: LifeCompassAnalysis? = null,
    val errorMessage: String? = null
)

@Immutable
data class CapturedNotification(
    val key: String,
    val appName: String,
    val packageName: String,
    val title: String,
    val content: String,
    val timestampMillis: Long
)

@Immutable
data class DeviceTelemetry(
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val wifiConnected: Boolean = false,
    val internetConnected: Boolean = false,
    val bluetoothAvailable: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val flashlightOn: Boolean = false,
    val mediaVolumePercent: Int = 50,
    val brightnessPercent: Int = 60,
    val micPermissionGranted: Boolean = false,
    val notificationsPermissionGranted: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val contactsPermissionGranted: Boolean = false,
    val phonePermissionGranted: Boolean = false,
    val readPhoneStateGranted: Boolean = false,
    val answerPhoneCallsGranted: Boolean = false,
    val callScreeningRoleHeld: Boolean = false,
    val writeSettingsGranted: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false
)

enum class AirGestureAction(val label: String) {
    TOGGLE_FLASHLIGHT("Torch On/Off"),
    PLAY_PAUSE_MUSIC("Play/Pause Music"),
    TRIGGER_VOICE("Wake LEON Voice")
}

@Immutable
data class SixthSenseTelemetry(
    val sensorsActive: Boolean = false,
    val lightAvailable: Boolean = false,
    val ambientLux: Float? = null,
    val magnetometerAvailable: Boolean = false,
    val compassHeadingDeg: Float = 0f,
    val magneticFluxMicroTesla: Float = 0f,
    val accelerometerAvailable: Boolean = false,
    val motionGForce: Float = 1.0f,
    val stabilityPercent: Int = 100,
    val proximityAvailable: Boolean = false,
    val proximityNear: Boolean = false,
    val audioMeterActive: Boolean = false,
    val ambientDbSpl: Float? = null,
    val batteryTempCelsius: Float = 0f,
    val airWaveEnabled: Boolean = false,
    val chopFlashEnabled: Boolean = false,
    val airGestureTarget: AirGestureAction = AirGestureAction.TOGGLE_FLASHLIGHT,
    val morseTransmitting: Boolean = false,
    val morseCurrentText: String = "",
    val morseEncodedPattern: String = "",
    val morsePulseOn: Boolean = false,
    val lastGestureTriggered: String = ""
)

@Immutable
data class PendingConfirmation(
    val id: String,
    val action: DeviceAction,
    val summary: String,
    val remainingActions: List<DeviceAction> = emptyList()
)

@Immutable
data class CloudSyncState(
    val signedInUid: String? = null,
    val userEmail: String? = null,
    val displayName: String? = null,
    val publishedCreatorName: String = "",
    val publishedCreatorTagline: String = "",
    val publishedCreatorOwnerId: String = "",
    val isSyncing: Boolean = false,
    val lastSyncedAtMillis: Long = 0L,
    val statusMessage: String = "",
    val errorMessage: String? = null
)

enum class BossAvailabilityMode(
    val wireValue: String,
    val title: String,
    val hindiPhrase: String,
    val englishPhrase: String
) {
    BUSY("busy", "Busy", "अभी व्यस्त हैं", "is currently busy"),
    MEETING("meeting", "In Meeting", "अभी मीटिंग में हैं", "is currently in a meeting"),
    DRIVING("driving", "Driving", "अभी ड्राइविंग कर रहे हैं", "is currently driving"),
    SLEEPING("sleeping", "Resting / Sleep", "अभी आराम कर रहे हैं", "is resting right now"),
    CUSTOM("custom", "Custom Note", "अभी कॉल रिसीव नहीं कर पा रहे हैं", "is unavailable right now")
}

@Immutable
data class CallTranscriptTurn(
    val speaker: String,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Immutable
data class HandledCallRecord(
    val id: String,
    val callerDisplay: String,
    val callerNumber: String,
    val statusLabel: String,
    val purposeSummary: String,
    val urgency: String,
    val aiAnswered: Boolean,
    val turns: List<CallTranscriptTurn>,
    val startedAtMillis: Long = System.currentTimeMillis()
)

@Immutable
data class CallReceptionistConfig(
    val enabled: Boolean = false,
    val autoAnswerDelaySeconds: Int = 10,
    val availabilityMode: BossAvailabilityMode = BossAvailabilityMode.BUSY,
    val customBossNote: String = "",
    val autoTakeTaskOnCallback: Boolean = true
)

@Immutable
data class CallReceptionistUiState(
    val config: CallReceptionistConfig = CallReceptionistConfig(),
    val activeCallCaller: String = "",
    val activeCallNumber: String = "",
    val activeCallState: String = "IDLE",
    val activeTurns: List<CallTranscriptTurn> = emptyList(),
    val countdownSeconds: Int = 0,
    val isGeneratingReply: Boolean = false,
    val isListeningCaller: Boolean = false,
    val capturedCallerName: String = "",
    val capturedPurpose: String = "",
    val capturedUrgency: String = "MEDIUM",
    val handledCalls: List<HandledCallRecord> = emptyList(),
    val simulatorInput: String = "",
    val statusBanner: String = ""
)

data class CallReceptionistResponse(
    val replyToCaller: String,
    val capturedCallerName: String = "",
    val capturedPurpose: String = "",
    val urgency: String = "MEDIUM",
    val shouldEscalateToBoss: Boolean = false,
    val shouldEndCall: Boolean = false,
    val summaryForBoss: String = ""
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
