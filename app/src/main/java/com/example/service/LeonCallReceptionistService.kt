package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.AliAiBrain
import com.example.ai.CallReceptionistEngine
import com.example.audio.LeonSpeaker
import com.example.data.AssistantLanguage
import com.example.data.LeonDatabase
import com.example.data.LeonRepository
import com.example.data.TaskPriority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LeonCallService"
private const val CALL_CHANNEL_ID = "leon_call_receptionist_channel"
private const val CALL_NOTIFICATION_ID = 7002
private const val MAX_CALL_DIALOGUE_TURNS = 4

/**
 * Foreground service (`microphone|mediaPlayback`) that manages unanswered incoming call countdowns,
 * accepts ringing calls via TelecomManager.acceptRingingCall() (API 26+), routes speakerphone audio,
 * speaks via LeonSpeaker, listens via SpeechRecognizer, and generates polite ALI AI responses.
 */
class LeonCallReceptionistService : Service() {

    companion object {
        const val ACTION_CALL_RINGING = "com.example.call.ACTION_RINGING"
        const val ACTION_BOSS_ANSWERED = "com.example.call.ACTION_BOSS_ANSWERED"
        const val ACTION_CALL_ENDED = "com.example.call.ACTION_CALL_ENDED"
        const val ACTION_STOP_RECEPTIONIST = "com.example.call.ACTION_STOP"

        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_CALLER_DISPLAY = "extra_caller_display"

        fun onCallRinging(context: Context, callerNumber: String, callerDisplay: String) {
            val intent = Intent(context, LeonCallReceptionistService::class.java).apply {
                action = ACTION_CALL_RINGING
                putExtra(EXTRA_CALLER_NUMBER, callerNumber)
                putExtra(EXTRA_CALLER_DISPLAY, callerDisplay)
            }
            startForegroundSafe(context, intent)
        }

        fun onBossAnsweredManually(context: Context) {
            val intent = Intent(context, LeonCallReceptionistService::class.java).apply {
                action = ACTION_BOSS_ANSWERED
            }
            startServiceSafe(context, intent)
        }

        fun onCallEnded(context: Context) {
            val intent = Intent(context, LeonCallReceptionistService::class.java).apply {
                action = ACTION_CALL_ENDED
            }
            startServiceSafe(context, intent)
        }

        fun stopActiveCall(context: Context) {
            val intent = Intent(context, LeonCallReceptionistService::class.java).apply {
                action = ACTION_STOP_RECEPTIONIST
            }
            startServiceSafe(context, intent)
        }

        private fun startForegroundSafe(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to startForegroundService cleanly, falling back", e)
                startServiceSafe(context, intent)
            }
        }

        private fun startServiceSafe(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to startService cleanly", e)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repository by lazy {
        LeonRepository(LeonDatabase.getInstance(applicationContext), applicationContext)
    }
    private val aliBrain by lazy { AliAiBrain() }

    private var countdownJob: Job? = null
    private var dialogueJob: Job? = null
    private var callerRecognizer: SpeechRecognizer? = null

    private var previousAudioMode: Int? = null
    private var previousSpeakerState: Boolean? = null
    private var activeCallEndedByUsOrCarrier = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CALL_RINGING -> {
                val number = intent.getStringExtra(EXTRA_CALLER_NUMBER).orEmpty()
                val display = intent.getStringExtra(EXTRA_CALLER_DISPLAY).orEmpty()
                handleIncomingRinging(number, display)
            }

            ACTION_BOSS_ANSWERED -> {
                countdownJob?.cancel()
                CallReceptionistBridge.cancelCountdownBossAnswered()
                stopForegroundCompat()
                stopSelf()
            }

            ACTION_CALL_ENDED, ACTION_STOP_RECEPTIONIST -> {
                concludeAndPersistCall()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleIncomingRinging(rawNumber: String, rawDisplay: String) {
        val currentState = CallReceptionistBridge.state.value
        val config = currentState.config
        if (!config.enabled) {
            stopSelf()
            return
        }
        // Avoid restarting countdown if already ringing/talking for the same call
        if (currentState.activeCallState in setOf("RINGING_COUNTDOWN", "AI_TALKING")) {
            return
        }

        activeCallEndedByUsOrCarrier = false
        promoteToForeground(includeMic = hasMicPermission())

        serviceScope.launch {
            val resolvedName = resolveContactDisplayName(rawNumber).ifBlank { rawDisplay }
                .ifBlank { rawNumber.ifBlank { "अज्ञात कॉलर" } }
            val delaySec = config.autoAnswerDelaySeconds.coerceIn(4, 30)
            CallReceptionistBridge.beginRingingCountdown(resolvedName, rawNumber, delaySec)
            refreshNotification("कॉल आ रही है ($resolvedName) • ${delaySec}s में AI उत्तर देगा")

            countdownJob?.cancel()
            countdownJob = serviceScope.launch {
                for (remaining in delaySec downTo 1) {
                    CallReceptionistBridge.updateCountdown(remaining)
                    refreshNotification("कॉल आ रही है ($resolvedName) • ${remaining}s में AI रिसीव करेगा")
                    delay(1_000L)
                    if (activeCallEndedByUsOrCarrier ||
                        CallReceptionistBridge.state.value.activeCallState != "RINGING_COUNTDOWN"
                    ) {
                        return@launch
                    }
                }
                answerAndStartConversation(resolvedName, rawNumber)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun answerAndStartConversation(callerDisplay: String, callerNumber: String) {
        if (!hasAnswerPermission() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            CallReceptionistBridge.finalizeActiveCallSession(
                statusLabelOverride = "ANSWER_PHONE_CALLS परमिशन न होने से AI कॉल नहीं उठा सका।"
            )
            stopForegroundCompat()
            stopSelf()
            return
        }

        val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        CallReceptionistBridge.markAiAcceptingCall(true)
        val accepted = try {
            @Suppress("DEPRECATION")
            telecom?.acceptRingingCall()
            true
        } catch (se: SecurityException) {
            Log.e(TAG, "Missing permission to accept ringing call", se)
            false
        } catch (e: Exception) {
            Log.e(TAG, "acceptRingingCall failed", e)
            false
        }

        if (!accepted) {
            CallReceptionistBridge.markAiAcceptingCall(false)
            CallReceptionistBridge.finalizeActiveCallSession(
                statusLabelOverride = "कॉल समाप्त हो चुकी थी या सिस्टम ने ऑटो-आंसर रोक दिया।"
            )
            stopForegroundCompat()
            stopSelf()
            return
        }

        val prefs = runCatching { repository.getPreferencesOnce() }.getOrNull()
        val ownerName = prefs?.ownerName.orEmpty()
        val language = AssistantLanguage.fromCode(prefs?.defaultLanguage)
        val config = CallReceptionistBridge.state.value.config
        val greeting = CallReceptionistEngine.buildInitialGreeting(
            ownerName = ownerName,
            callerDisplay = callerDisplay,
            config = config,
            language = language
        )

        enableSpeakerForCallAudio()
        CallReceptionistBridge.beginAiDialogueSession(
            callerDisplay = callerDisplay,
            callerNumber = callerNumber,
            initialGreeting = greeting,
            isSimulation = false
        )
        refreshNotification("LEON AI कॉल पर बात कर रहा है ($callerDisplay)")

        // Give telecom audio bridge a short moment to establish before speaking greeting
        delay(850L)
        speakThenListenNextTurn(greeting, language, turnIndex = 0)
    }

    private fun speakThenListenNextTurn(
        text: String,
        language: AssistantLanguage,
        turnIndex: Int
    ) {
        if (activeCallEndedByUsOrCarrier ||
            CallReceptionistBridge.state.value.activeCallState != "AI_TALKING"
        ) {
            return
        }

        LeonSpeaker.setOnSpeechIdleListener {
            LeonSpeaker.setOnSpeechIdleListener(null)
            if (activeCallEndedByUsOrCarrier ||
                CallReceptionistBridge.state.value.activeCallState != "AI_TALKING"
            ) {
                return@setOnSpeechIdleListener
            }
            if (turnIndex < MAX_CALL_DIALOGUE_TURNS && hasMicPermission()) {
                startCallerSpeechCapture(language, turnIndex)
            }
        }
        LeonSpeaker.speak(applicationContext, text, language)
    }

    private fun startCallerSpeechCapture(language: AssistantLanguage, turnIndex: Int) {
        mainHandler.post {
            if (activeCallEndedByUsOrCarrier ||
                CallReceptionistBridge.state.value.activeCallState != "AI_TALKING"
            ) {
                return@post
            }
            if (!SpeechRecognizer.isRecognitionAvailable(applicationContext)) {
                return@post
            }

            stopCallerRecognizer()
            CallReceptionistBridge.setListeningCaller(true)

            runCatching {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext)
                callerRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        CallReceptionistBridge.setListeningCaller(false)
                    }

                    override fun onError(error: Int) {
                        CallReceptionistBridge.setListeningCaller(false)
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            if (turnIndex == 0 && !activeCallEndedByUsOrCarrier) {
                                val promptAgain = "हैलो, क्या आप मुझे सुन पा रहे हैं? कृपया अपना नाम और संदेश बताइए।"
                                CallReceptionistBridge.appendAiTurn(promptAgain)
                                speakThenListenNextTurn(promptAgain, language, turnIndex + 1)
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        CallReceptionistBridge.setListeningCaller(false)
                        val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim().orEmpty()
                        if (spoken.isNotBlank()) {
                            handleCallerSpeech(spoken, language, turnIndex)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val recIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                recognizer.startListening(recIntent)
            }.onFailure {
                CallReceptionistBridge.setListeningCaller(false)
                Log.w(TAG, "Unable to listen for caller speech", it)
            }
        }
    }

    private fun handleCallerSpeech(
        utterance: String,
        language: AssistantLanguage,
        turnIndex: Int
    ) {
        dialogueJob?.cancel()
        dialogueJob = serviceScope.launch {
            CallReceptionistBridge.appendCallerTurn(utterance)
            CallReceptionistBridge.setGeneratingReply(true)
            val snapshot = CallReceptionistBridge.state.value
            val prefs = runCatching { repository.getPreferencesOnce() }.getOrNull()
            val ownerName = prefs?.ownerName.orEmpty()

            val response = aliBrain.generateCallReceptionistTurn(
                callerDisplay = snapshot.activeCallCaller,
                callerUtterance = utterance,
                priorTurns = snapshot.activeTurns.dropLast(1),
                ownerName = ownerName,
                config = snapshot.config,
                language = language,
                existingCallerName = snapshot.capturedCallerName,
                existingPurpose = snapshot.capturedPurpose
            )

            CallReceptionistBridge.appendAiTurn(
                replyText = response.replyToCaller,
                capturedName = response.capturedCallerName,
                capturedPurpose = response.capturedPurpose,
                urgency = response.urgency,
                statusBanner = response.summaryForBoss
            )

            if (response.shouldEndCall || turnIndex + 1 >= MAX_CALL_DIALOGUE_TURNS) {
                LeonSpeaker.setOnSpeechIdleListener {
                    LeonSpeaker.setOnSpeechIdleListener(null)
                    if (!activeCallEndedByUsOrCarrier) {
                        CallReceptionistBridge.appendAiTurn(
                            replyText = "संदेश सुरक्षित सेव हो गया है।",
                            statusBanner = response.summaryForBoss
                        )
                    }
                }
                LeonSpeaker.speak(applicationContext, response.replyToCaller, language)
            } else {
                speakThenListenNextTurn(response.replyToCaller, language, turnIndex + 1)
            }
        }
    }

    private fun concludeAndPersistCall() {
        activeCallEndedByUsOrCarrier = true
        countdownJob?.cancel()
        dialogueJob?.cancel()
        LeonSpeaker.setOnSpeechIdleListener(null)
        stopCallerRecognizer()
        restoreAudioAfterCall()

        val record = CallReceptionistBridge.finalizeActiveCallSession()
        if (record != null) {
            serviceScope.launch {
                val cfg = CallReceptionistBridge.state.value.config
                if (cfg.autoTakeTaskOnCallback) {
                    val priority = when (record.urgency) {
                        "HIGH" -> TaskPriority.HIGH
                        "LOW" -> TaskPriority.LOW
                        else -> TaskPriority.MEDIUM
                    }
                    runCatching {
                        repository.createTask(
                            title = "Call back ${record.callerDisplay}: ${record.purposeSummary.take(65)}",
                            notes = record.turns.joinToString("\n") { "${it.speaker}: ${it.text}" },
                            dueLabel = if (record.urgency == "HIGH") "Urgent Today" else "Today",
                            priority = priority
                        )
                    }
                }
                postMissedCallSummaryNotification(record)
                withContext(Dispatchers.Main) {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        } else {
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun enableSpeakerForCallAudio() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (previousAudioMode == null) previousAudioMode = am.mode
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = am.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    am.setCommunicationDevice(speaker)
                }
            } else {
                @Suppress("DEPRECATION")
                if (previousSpeakerState == null) previousSpeakerState = am.isSpeakerphoneOn
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            }
        }.onFailure {
            Log.w(TAG, "Audio speaker routing adjustment warning", it)
        }
    }

    private fun restoreAudioAfterCall() {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                previousSpeakerState?.let {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = it
                }
                previousSpeakerState = null
            }
            previousAudioMode?.let { am.mode = it }
            previousAudioMode = null
        }
    }

    private fun stopCallerRecognizer() {
        mainHandler.post {
            runCatching {
                callerRecognizer?.cancel()
                callerRecognizer?.destroy()
            }
            callerRecognizer = null
            CallReceptionistBridge.setListeningCaller(false)
        }
    }

    private fun resolveContactDisplayName(rawNumber: String): String {
        val cleaned = rawNumber.trim()
        if (cleaned.isBlank()) return ""
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return ""
        }
        return runCatching {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(cleaned)
            )
            contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0).orEmpty()
                } else {
                    ""
                }
            }.orEmpty()
        }.getOrDefault("")
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun hasAnswerPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    private fun promoteToForeground(includeMic: Boolean) {
        val notif = buildNotification("LEON AI कॉल रिसेप्शनिस्ट तैयार है")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val type = if (includeMic) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
                startForeground(CALL_NOTIFICATION_ID, notif, type)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(CALL_NOTIFICATION_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(CALL_NOTIFICATION_ID, notif)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to promote call service to foreground safely", e)
            runCatching {
                getSystemService(NotificationManager::class.java)?.notify(CALL_NOTIFICATION_ID, notif)
            }
        }
    }

    private fun refreshNotification(subtitle: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.notify(CALL_NOTIFICATION_ID, buildNotification(subtitle))
        }
    }

    private fun buildNotification(subtitle: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            30,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_leon_notification)
            .setContentTitle("LEON AI • Call Receptionist")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun postMissedCallSummaryNotification(record: com.example.data.HandledCallRecord) {
        val summary = CallReceptionistEngine.formatBossCallAlert(record)
        val openIntent = PendingIntent.getActivity(
            this,
            31,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_leon_notification)
            .setContentTitle("कॉल रिपोर्ट: ${record.callerDisplay} (${record.urgency})")
            .setContentText(record.purposeSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(7003, notif)
        }
    }

    private fun stopForegroundCompat() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALL_CHANNEL_ID,
                getString(R.string.call_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.call_channel_desc)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        dialogueJob?.cancel()
        LeonSpeaker.setOnSpeechIdleListener(null)
        stopCallerRecognizer()
        restoreAudioAfterCall()
        serviceScope.cancel()
        super.onDestroy()
    }
}
