package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.ai.AliAiBrain
import com.example.ai.LocalCommandRouter
import com.example.audio.LeonSpeaker
import com.example.automation.LeonActionExecutor
import com.example.data.ActionType
import com.example.data.AliBrainResult
import com.example.data.AssistantLanguage
import com.example.data.DeviceAction
import com.example.data.LeonDatabase
import com.example.data.LeonRepository
import com.example.data.TaskPriority
import com.example.media.LeonMusicEngine
import com.example.news.LiveNewsEngine
import com.example.news.NewsCategory
import com.example.social.SocialAutoPosterBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "LeonVoiceService"
private const val CHANNEL_ID = "leon_always_ready_channel"
private const val NOTIFICATION_ID = 7001
private const val WAKELOCK_TAG = "leon:LockScreenAssistantLock"
private const val LISTEN_WATCHDOG_MS = 10_000L
private const val TTS_WATCHDOG_MS = 12_000L

class LeonVoiceService : Service(), TextToSpeech.OnInitListener {

    private data class PendingSpeech(
        val text: String,
        val language: AssistantLanguage,
        val queueMode: Int
    )

    companion object {
        const val ACTION_START_ALWAYS_READY = "com.example.action.START_ALWAYS_READY"
        const val ACTION_CAPTURE_ONCE = "com.example.action.CAPTURE_ONCE"
        const val ACTION_PAUSE_LISTENING = "com.example.action.PAUSE_LISTENING"
        const val ACTION_SPEAK = "com.example.action.SPEAK"
        const val ACTION_PROCESSING_DONE = "com.example.action.PROCESSING_DONE"
        const val ACTION_EMERGENCY_STOP = "com.example.action.EMERGENCY_STOP"
        const val ACTION_SET_LOCK_SCREEN_MODE = "com.example.action.SET_LOCK_SCREEN_MODE"
        const val ACTION_READ_LOCKSCREEN_ALERTS = "com.example.action.READ_LOCKSCREEN_ALERTS"
        const val ACTION_TOGGLE_MUSIC = "com.example.action.TOGGLE_MUSIC"
        const val ACTION_CONTROL_MUSIC = "com.example.action.CONTROL_MUSIC"
        const val ACTION_START_TIMER_BACKGROUND = "com.example.action.START_TIMER_BACKGROUND"

        const val EXTRA_SPEAK_TEXT = "extra_speak_text"
        const val EXTRA_LANGUAGE_CODE = "extra_language_code"
        const val EXTRA_VOICE_SPEED = "extra_voice_speed"
        const val EXTRA_QUEUE_MODE = "extra_queue_mode"
        const val EXTRA_LOCK_SCREEN_MODE = "extra_lock_screen_mode"
        const val EXTRA_MUSIC_COMMAND = "extra_music_command"
        const val EXTRA_MUSIC_QUERY = "extra_music_query"
        const val EXTRA_TIMER_SECONDS = "extra_timer_seconds"
        const val EXTRA_TIMER_LABEL = "extra_timer_label"

        private val WAKE_WORD_REGEX = Regex(
            pattern = "(?i)(hey\\s+leon|hi\\s+leon|ok\\s+leon|hello\\s+leon|hey\\s+lian|hey\\s+leone|हे\\s*लियोन|हे\\s*लिऑन|हाय\\s*लियोन|है\\s*लियोन|हलो\\s*लियोन|ओके\\s*लियोन|लियोन)"
        )

        fun startAlwaysReady(
            context: Context,
            language: AssistantLanguage,
            speed: Float = 1.0f,
            lockScreenMode: Boolean = true
        ) {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_START_ALWAYS_READY
                putExtra(EXTRA_LANGUAGE_CODE, language.code)
                putExtra(EXTRA_VOICE_SPEED, speed)
                putExtra(EXTRA_LOCK_SCREEN_MODE, lockScreenMode)
            }
            startForegroundSafe(context, intent)
        }

        fun setLockScreenMode(context: Context, enabled: Boolean) {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_SET_LOCK_SCREEN_MODE
                putExtra(EXTRA_LOCK_SCREEN_MODE, enabled)
            }
            startServiceSafe(context, intent)
        }

        fun captureSingleCommand(context: Context, language: AssistantLanguage) {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_CAPTURE_ONCE
                putExtra(EXTRA_LANGUAGE_CODE, language.code)
            }
            startServiceSafe(context, intent)
        }

        fun readLockScreenAlerts(context: Context, language: AssistantLanguage, speed: Float = 1.0f) {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_READ_LOCKSCREEN_ALERTS
                putExtra(EXTRA_LANGUAGE_CODE, language.code)
                putExtra(EXTRA_VOICE_SPEED, speed)
            }
            startServiceSafe(context, intent)
        }

        fun controlMusic(context: Context, command: String = "play", query: String = "") {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_CONTROL_MUSIC
                putExtra(EXTRA_MUSIC_COMMAND, command)
                putExtra(EXTRA_MUSIC_QUERY, query)
            }
            val cmdLower = command.lowercase()
            if (cmdLower in setOf("play", "resume", "next", "previous", "toggle")) {
                startForegroundSafe(context, intent)
            } else {
                startServiceSafe(context, intent)
            }
        }

        fun startBackgroundTimer(context: Context, seconds: Int, label: String = "LEON Timer") {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_START_TIMER_BACKGROUND
                putExtra(EXTRA_TIMER_SECONDS, seconds)
                putExtra(EXTRA_TIMER_LABEL, label)
            }
            startForegroundSafe(context, intent)
        }

        fun speak(
            context: Context,
            text: String,
            language: AssistantLanguage,
            speed: Float = 1.0f,
            queueMode: Int = TextToSpeech.QUEUE_FLUSH
        ) {
            if (text.isBlank()) return
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_SPEAK
                putExtra(EXTRA_SPEAK_TEXT, text)
                putExtra(EXTRA_LANGUAGE_CODE, language.code)
                putExtra(EXTRA_VOICE_SPEED, speed)
                putExtra(EXTRA_QUEUE_MODE, queueMode)
            }
            startServiceSafe(context, intent)
        }

        fun notifyProcessingComplete(context: Context) {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_PROCESSING_DONE
            }
            startServiceSafe(context, intent)
        }

        fun pauseListeningForForegroundMic(context: Context) {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_PAUSE_LISTENING
            }
            startServiceSafe(context, intent)
        }

        fun emergencyStop(context: Context) {
            val intent = Intent(context, LeonVoiceService::class.java).apply {
                action = ACTION_EMERGENCY_STOP
            }
            startServiceSafe(context, intent)
        }

        private fun startForegroundSafe(context: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to startForegroundService cleanly, falling back to startService", e)
                startServiceSafe(context, intent)
            }
        }

        private fun startServiceSafe(context: Context, intent: Intent) {
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start LeonVoiceService cleanly", e)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backgroundAiJob: Job? = null
    private var backgroundTimerJob: Job? = null

    private val repository by lazy {
        LeonRepository(LeonDatabase.getInstance(applicationContext), applicationContext)
    }
    private val aliBrain by lazy { AliAiBrain() }
    private val executor by lazy { LeonActionExecutor(applicationContext) }

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingSpeechQueue = ArrayDeque<PendingSpeech>()
    private var alwaysReadyMode = false
    private var lockScreenModeEnabled = true
    private var awaitingActiveCommand = false
    private var isPromotedForeground = false
    private var currentLanguage = AssistantLanguage.HINDI
    private var speechSpeed = 1.0f
    private var isListening = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastSpokenRawText = ""
    private var lastSpokenWasPhoneticFallback = false

    private val listenRunnable = Runnable { startListeningInternal() }

    private val listenWatchdogRunnable = Runnable {
        if (isListening || VoiceServiceBridge.orbState.value in setOf(VoiceOrbState.WAKE_LISTENING, VoiceOrbState.ACTIVE_LISTENING)) {
            Log.w(TAG, "SpeechRecognizer watchdog recovery triggered")
            isListening = false
            awaitingActiveCommand = false
            LeonMusicEngine.setDucked(false)
            stopRecognizerInternal()
            if (alwaysReadyMode && (lockScreenModeEnabled || !isDeviceLocked())) {
                scheduleListen(500L)
            } else {
                VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
            }
        }
    }

    private val ttsWatchdogRunnable = Runnable {
        if (VoiceServiceBridge.orbState.value == VoiceOrbState.SPEAKING) {
            Log.w(TAG, "TTS watchdog recovery triggered")
            pendingSpeechQueue.clear()
            LeonMusicEngine.setTtsSpeaking(false)
            recoverOrbAfterSpeechFailure()
        }
    }

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val locked = isDeviceLocked()
            VoiceServiceBridge.setDeviceLocked(locked)
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (alwaysReadyMode) {
                        if (lockScreenModeEnabled) {
                            ensureWakeLockHeld()
                            refreshNotification()
                            if (!isListening && pendingSpeechQueue.isEmpty() && tts?.isSpeaking != true) {
                                scheduleListen(400L)
                            }
                        } else {
                            cancelListenTimers()
                            stopRecognizerInternal()
                            if (!LeonMusicEngine.isPlaying() && backgroundTimerJob?.isActive != true) {
                                releaseWakeLock()
                            }
                            VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
                        }
                    } else if (LeonMusicEngine.isPlaying() || backgroundTimerJob?.isActive == true) {
                        ensureWakeLockHeld()
                        refreshNotification()
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    refreshNotification()
                    if (alwaysReadyMode && !locked && !isListening && tts?.isSpeaking != true) {
                        scheduleListen(350L)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        tts = TextToSpeech(this, this)
        VoiceServiceBridge.setDeviceLocked(isDeviceLocked())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenLockReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        serviceScope.launch {
            runCatching {
                val prefs = repository.getPreferencesOnce()
                lockScreenModeEnabled = prefs.lockScreenModeEnabled
                currentLanguage = AssistantLanguage.fromCode(prefs.defaultLanguage)
                speechSpeed = prefs.voiceSpeed.toFloat().coerceIn(0.5f, 2.0f)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_LANGUAGE_CODE)?.let {
            currentLanguage = AssistantLanguage.fromCode(it)
        }
        intent?.getFloatExtra(EXTRA_VOICE_SPEED, speechSpeed)?.let {
            speechSpeed = it.coerceIn(0.5f, 2.0f)
        }
        if (intent?.hasExtra(EXTRA_LOCK_SCREEN_MODE) == true) {
            lockScreenModeEnabled = intent.getBooleanExtra(EXTRA_LOCK_SCREEN_MODE, lockScreenModeEnabled)
        }

        VoiceServiceBridge.setDeviceLocked(isDeviceLocked())
        val action = intent?.action

        // Ensure foreground promotion happens only for persistent foreground modes (Always-Ready, Music, Timer)
        // or when invoked while Always-Ready / screen-locked; never fail single foreground UI captures.
        if (action in setOf(
                ACTION_START_ALWAYS_READY,
                ACTION_READ_LOCKSCREEN_ALERTS,
                ACTION_TOGGLE_MUSIC,
                ACTION_CONTROL_MUSIC,
                ACTION_START_TIMER_BACKGROUND
            ) || (action == ACTION_CAPTURE_ONCE && (alwaysReadyMode || isDeviceLocked() || !VoiceServiceBridge.foregroundUiActive.value))
        ) {
            val needMic = (action == ACTION_START_ALWAYS_READY || action == ACTION_CAPTURE_ONCE || alwaysReadyMode) && hasMicPermission()
            promoteToForegroundSafely(includeMic = needMic)
        }

        when (action) {
            ACTION_START_ALWAYS_READY -> {
                if (!hasMicPermission()) {
                    VoiceServiceBridge.setRunning(false)
                    VoiceServiceBridge.emitEvent(
                        VoiceServiceEvent.ErrorOccurred("Microphone permission is required for Hey Leon Always-Ready mode.")
                    )
                    if (!LeonMusicEngine.isPlaying()) stopSelf()
                    return START_NOT_STICKY
                }
                alwaysReadyMode = true
                awaitingActiveCommand = false
                if (lockScreenModeEnabled || LeonMusicEngine.isPlaying()) {
                    ensureWakeLockHeld()
                } else {
                    releaseWakeLock()
                }
                promoteToForegroundSafely(includeMic = true)
                VoiceServiceBridge.setRunning(true)
                VoiceServiceBridge.setLockScreenModeActive(lockScreenModeEnabled)
                VoiceServiceBridge.setOrbState(VoiceOrbState.WAKE_LISTENING)
                scheduleListen(delayMs = 200L)
            }

            ACTION_SET_LOCK_SCREEN_MODE -> {
                if (alwaysReadyMode || LeonMusicEngine.isPlaying()) {
                    if (lockScreenModeEnabled || LeonMusicEngine.isPlaying()) ensureWakeLockHeld() else releaseWakeLock()
                    VoiceServiceBridge.setLockScreenModeActive(lockScreenModeEnabled)
                    refreshNotification()
                } else {
                    stopSelf()
                }
            }

            ACTION_CAPTURE_ONCE -> {
                if (!hasMicPermission()) {
                    awaitingActiveCommand = false
                    VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
                    if (isDeviceLocked() || !VoiceServiceBridge.foregroundUiActive.value) {
                        speakText("बॉस, आवाज़ कमांड के लिए माइक्रोफ़ोन परमिशन ज़रूरी है।", AssistantLanguage.HINDI)
                    } else {
                        VoiceServiceBridge.emitEvent(
                            VoiceServiceEvent.ErrorOccurred("Please grant Microphone permission to use voice commands.")
                        )
                    }
                    return if (shouldStaySticky()) START_STICKY else START_NOT_STICKY
                }
                if (lockScreenModeEnabled) ensureWakeLockHeld()
                pendingSpeechQueue.clear()
                tts?.stop()
                LeonSpeaker.stop(notifyIdle = false)
                mainHandler.removeCallbacks(ttsWatchdogRunnable)
                awaitingActiveCommand = true
                VoiceServiceBridge.setOrbState(VoiceOrbState.ACTIVE_LISTENING)
                scheduleListen(delayMs = 150L)
            }

            ACTION_READ_LOCKSCREEN_ALERTS -> {
                if (lockScreenModeEnabled) ensureWakeLockHeld()
                speakLockScreenBriefing()
            }

            ACTION_TOGGLE_MUSIC -> {
                ensureWakeLockHeld()
                val outcome = LeonMusicEngine.toggle(applicationContext)
                promoteOrUpdateMediaNotification()
                if (!VoiceServiceBridge.foregroundUiActive.value || isDeviceLocked()) {
                    val announce = if (LeonMusicEngine.isPlaying()) {
                        "जी बॉस, ${LeonMusicEngine.currentTrackName()} चल रहा है।"
                    } else {
                        "जी बॉस, म्यूजिक पॉज़ कर दिया है।"
                    }
                    speakText(announce, AssistantLanguage.HINDI, TextToSpeech.QUEUE_FLUSH)
                }
                Log.i(TAG, "Lock-screen music toggle: $outcome")
            }

            ACTION_CONTROL_MUSIC -> {
                val cmd = intent.getStringExtra(EXTRA_MUSIC_COMMAND).orEmpty().ifBlank { "play" }
                val query = intent.getStringExtra(EXTRA_MUSIC_QUERY).orEmpty()
                if (cmd.lowercase() !in setOf("pause", "stop", "off")) {
                    ensureWakeLockHeld()
                }
                LeonMusicEngine.handleAction(applicationContext, query, cmd)
                promoteOrUpdateMediaNotification()
            }

            ACTION_START_TIMER_BACKGROUND -> {
                val seconds = intent.getIntExtra(EXTRA_TIMER_SECONDS, 60).coerceIn(1, 86400)
                val label = intent.getStringExtra(EXTRA_TIMER_LABEL).orEmpty().ifBlank { "LEON Timer" }
                ensureWakeLockHeld()
                promoteToForegroundSafely(includeMic = alwaysReadyMode && hasMicPermission())
                scheduleBackgroundTimer(seconds, label)
            }

            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_SPEAK_TEXT).orEmpty()
                val queueMode = intent.getIntExtra(EXTRA_QUEUE_MODE, TextToSpeech.QUEUE_FLUSH)
                if (text.isNotBlank()) {
                    speakText(text, currentLanguage, queueMode)
                }
            }

            ACTION_PAUSE_LISTENING -> {
                cancelListenTimers()
                stopRecognizerInternal()
                awaitingActiveCommand = false
                if (!shouldStaySticky()) {
                    return START_NOT_STICKY
                }
            }

            ACTION_PROCESSING_DONE -> {
                if (!VoiceServiceBridge.foregroundMicActive.value &&
                    !LeonSpeaker.isSpeaking.value &&
                    pendingSpeechQueue.isEmpty() &&
                    tts?.isSpeaking != true
                ) {
                    recoverOrbAfterSpeechFailure()
                }
            }

            ACTION_EMERGENCY_STOP -> {
                performEmergencyStop()
            }
        }
        return if (shouldStaySticky()) START_STICKY else START_NOT_STICKY
    }

    private fun shouldStaySticky(): Boolean =
        alwaysReadyMode || LeonMusicEngine.isPlaying() || backgroundTimerJob?.isActive == true

    private fun promoteOrUpdateMediaNotification() {
        if (alwaysReadyMode || LeonMusicEngine.isPlaying() || backgroundTimerJob?.isActive == true) {
            promoteToForegroundSafely(includeMic = alwaysReadyMode && hasMicPermission())
            refreshNotification()
        } else {
            if (!alwaysReadyMode && backgroundTimerJob?.isActive != true) {
                releaseWakeLock()
                stopForegroundCompat()
                isPromotedForeground = false
            }
        }
    }

    private fun isDeviceLocked(): Boolean {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return km?.isKeyguardLocked == true
    }

    private fun isScreenInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isInteractive != false
    }

    private fun isScreenOffOrLocked(): Boolean = isDeviceLocked() || !isScreenInteractive()

    private fun isAudioInCall(): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return false
        return am.mode == android.media.AudioManager.MODE_IN_CALL ||
            am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION
    }

    @SuppressLint("WakelockTimeout")
    private fun ensureWakeLockHeld() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(4 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to acquire partial wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to release wake lock cleanly", e)
        }
    }

    private fun promoteToForegroundSafely(includeMic: Boolean) {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val fgType = when {
                    includeMic && hasMicPermission() ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
                startForeground(NOTIFICATION_ID, notification, fgType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isPromotedForeground = true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed safely (${e.message})", e)
            runCatching {
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun refreshNotification() {
        if (!alwaysReadyMode && !LeonMusicEngine.isPlaying() && backgroundTimerJob?.isActive != true && !isPromotedForeground) {
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun servicePendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, LeonVoiceService::class.java).apply { this.action = action }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && action != ACTION_EMERGENCY_STOP) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val listenIntent = servicePendingIntent(2, ACTION_CAPTURE_ONCE)
        val toggleMusicIntent = servicePendingIntent(4, ACTION_TOGGLE_MUSIC)
        val stopIntent = servicePendingIntent(1, ACTION_EMERGENCY_STOP)

        val locked = isDeviceLocked()
        val musicActive = LeonMusicEngine.isPlaying()
        val timerSec = VoiceServiceBridge.activeTimerRemainingSeconds.value
        val subtitle = buildString {
            if (musicActive) {
                append("♪ ${LeonMusicEngine.currentTrackName()} • ")
            }
            if (timerSec > 0) {
                append("⏱ ${timerSec}s • ")
            }
            when {
                locked && lockScreenModeEnabled && alwaysReadyMode ->
                    append("Lock-Screen Active • Voice Calls, Music & Alerts Ready")
                alwaysReadyMode && lockScreenModeEnabled ->
                    append("24/7 Lock-Ready (${currentLanguage.label}) • Say \"Hey Leon\"")
                alwaysReadyMode ->
                    append("Listening for \"Hey Leon\" (${currentLanguage.label})")
                else ->
                    append("Lock-Screen Background Audio Ready")
            }
        }

        val musicLabel = if (musicActive) {
            getString(R.string.lock_screen_action_music_pause)
        } else {
            getString(R.string.lock_screen_action_music_play)
        }
        val musicIcon = if (musicActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_leon_notification)
            .setContentTitle("LEON × ALI AI • Always-Ready")
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(if (musicActive) NotificationCompat.CATEGORY_TRANSPORT else NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, getString(R.string.lock_screen_action_listen), listenIntent)
            .addAction(musicIcon, musicLabel, toggleMusicIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.lock_screen_action_stop), stopIntent)

        if (musicActive) {
            LeonMusicEngine.ensureMediaSession(applicationContext)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.voice_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.voice_channel_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun cancelListenTimers() {
        mainHandler.removeCallbacks(listenRunnable)
        mainHandler.removeCallbacks(listenWatchdogRunnable)
    }

    private fun scheduleListen(delayMs: Long) {
        cancelListenTimers()
        mainHandler.postDelayed(listenRunnable, delayMs)
    }

    private fun startListeningInternal() {
        if (VoiceServiceBridge.foregroundMicActive.value) {
            LeonMusicEngine.setDucked(false)
            return
        }
        if (isAudioInCall()) {
            LeonMusicEngine.setDucked(false)
            if (alwaysReadyMode) {
                scheduleListen(3500L)
            }
            return
        }
        if (LeonSpeaker.isSpeaking.value || VoiceServiceBridge.orbState.value == VoiceOrbState.SPEAKING) {
            if (alwaysReadyMode) {
                scheduleListen(600L)
            }
            return
        }
        if (alwaysReadyMode && !lockScreenModeEnabled && isScreenOffOrLocked()) {
            LeonMusicEngine.setDucked(false)
            VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
            return
        }
        if (!hasMicPermission()) {
            awaitingActiveCommand = false
            LeonMusicEngine.setDucked(false)
            VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            awaitingActiveCommand = false
            LeonMusicEngine.setDucked(false)
            VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
            if (alwaysReadyMode) {
                alwaysReadyMode = false
                VoiceServiceBridge.setRunning(false)
                if (!LeonMusicEngine.isPlaying()) {
                    releaseWakeLock()
                    stopForegroundCompat()
                    isPromotedForeground = false
                }
            }
            VoiceServiceBridge.emitEvent(
                VoiceServiceEvent.ErrorOccurred("Speech recognition service unavailable on this device. Please type your command below.")
            )
            return
        }
        try {
            stopRecognizerInternal()
            if (awaitingActiveCommand) {
                LeonMusicEngine.setDucked(true)
            }
            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    mainHandler.removeCallbacks(listenWatchdogRunnable)
                    mainHandler.postDelayed(listenWatchdogRunnable, LISTEN_WATCHDOG_MS)
                    VoiceServiceBridge.setOrbState(
                        if (awaitingActiveCommand) VoiceOrbState.ACTIVE_LISTENING
                        else VoiceOrbState.WAKE_LISTENING
                    )
                }

                override fun onBeginningOfSpeech() {
                    LeonMusicEngine.setDucked(true)
                    mainHandler.removeCallbacks(listenWatchdogRunnable)
                    mainHandler.postDelayed(listenWatchdogRunnable, LISTEN_WATCHDOG_MS)
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    mainHandler.removeCallbacks(listenWatchdogRunnable)
                    LeonMusicEngine.setDucked(false)
                    VoiceServiceBridge.setPartialTranscript("")
                    if (alwaysReadyMode) {
                        awaitingActiveCommand = false
                        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                            alwaysReadyMode = false
                            VoiceServiceBridge.setRunning(false)
                            if (!LeonMusicEngine.isPlaying()) {
                                releaseWakeLock()
                                stopForegroundCompat()
                                isPromotedForeground = false
                            }
                            VoiceServiceBridge.emitEvent(
                                VoiceServiceEvent.ErrorOccurred("Microphone permission revoked. Always-Ready stopped.")
                            )
                        } else if (!lockScreenModeEnabled && isDeviceLocked()) {
                            VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
                        } else {
                            VoiceServiceBridge.setOrbState(VoiceOrbState.WAKE_LISTENING)
                            val backoff = when (error) {
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                                SpeechRecognizer.ERROR_SERVER -> 3500L
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1500L
                                else -> 800L
                            }
                            scheduleListen(backoff)
                        }
                    } else {
                        awaitingActiveCommand = false
                        VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
                        val uiActive = VoiceServiceBridge.foregroundUiActive.value && !isDeviceLocked()
                        when (error) {
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SERVER,
                            SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
                                if (uiActive) {
                                    if (!LeonMusicEngine.isPlaying()) releaseWakeLock()
                                    VoiceServiceBridge.emitEvent(VoiceServiceEvent.SpeechFallbackRequested)
                                } else {
                                    speakText("बॉस, नेटवर्क की वजह से वॉइस पहचान में दिक्कत आ रही है।", AssistantLanguage.HINDI)
                                }
                            }
                            SpeechRecognizer.ERROR_AUDIO -> {
                                if (!LeonMusicEngine.isPlaying()) releaseWakeLock()
                                if (uiActive) {
                                    VoiceServiceBridge.emitEvent(VoiceServiceEvent.SpeechFallbackRequested)
                                } else {
                                    VoiceServiceBridge.emitEvent(
                                        VoiceServiceEvent.ErrorOccurred("Microphone audio error. Check device mic or type your command.")
                                    )
                                }
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                if (!LeonMusicEngine.isPlaying()) releaseWakeLock()
                                VoiceServiceBridge.emitEvent(
                                    VoiceServiceEvent.ErrorOccurred("Microphone permission is required.")
                                )
                            }
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                if (!LeonMusicEngine.isPlaying()) releaseWakeLock()
                                if (uiActive) {
                                    VoiceServiceBridge.emitEvent(
                                        VoiceServiceEvent.ErrorOccurred("Didn't catch voice input. Tap the mic orb again or type below.")
                                    )
                                }
                            }
                            else -> {
                                if (!LeonMusicEngine.isPlaying()) releaseWakeLock()
                                if (uiActive) {
                                    VoiceServiceBridge.emitEvent(VoiceServiceEvent.SpeechFallbackRequested)
                                } else {
                                    VoiceServiceBridge.emitEvent(
                                        VoiceServiceEvent.ErrorOccurred("Voice capture unavailable (code $error). Try typing your command.")
                                    )
                                }
                            }
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    mainHandler.removeCallbacks(listenWatchdogRunnable)
                    LeonMusicEngine.setDucked(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    VoiceServiceBridge.setPartialTranscript("")
                    handleSpokenText(spoken)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        VoiceServiceBridge.setPartialTranscript(partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val recogIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage.localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLanguage.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            mainHandler.postDelayed(listenWatchdogRunnable, LISTEN_WATCHDOG_MS)
            recognizer.startListening(recogIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SpeechRecognizer", e)
            isListening = false
            awaitingActiveCommand = false
            mainHandler.removeCallbacks(listenWatchdogRunnable)
            LeonMusicEngine.setDucked(false)
            VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
            if (alwaysReadyMode) scheduleListen(1500L)
        }
    }

    private fun handleSpokenText(spoken: String) {
        if (spoken.isBlank()) {
            if (alwaysReadyMode) scheduleListen(450L)
            else VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
            return
        }

        val lower = spoken.lowercase(Locale.ROOT)
        val isStopCommand = lower.contains("emergency stop") ||
            lower == "stop" ||
            lower == "leon stop" ||
            lower.contains("रुको") ||
            lower.contains("बंद करो")

        if (isStopCommand) {
            performEmergencyStop()
            return
        }

        val wakeMatch = WAKE_WORD_REGEX.find(spoken)
        if (!awaitingActiveCommand && alwaysReadyMode) {
            if (wakeMatch != null) {
                val remainder = WAKE_WORD_REGEX.replaceFirst(spoken, "")
                    .trim(' ', ',', '.', '!', '?', '।', ':')
                if (remainder.length > 2) {
                    dispatchCommand(remainder)
                } else {
                    awaitingActiveCommand = true
                    VoiceServiceBridge.emitEvent(VoiceServiceEvent.WakeWordDetected)
                    serviceScope.launch {
                        val ownerName = runCatching { repository.getPreferencesOnce().ownerName }.getOrDefault("")
                        val greeting = if (ownerName.isNotBlank()) {
                            "जी $ownerName बॉस! बताइए, मैं आपकी क्या मदद कर सकता हूँ?"
                        } else {
                            getString(R.string.default_greeting_hi)
                        }
                        withContext(Dispatchers.Main) {
                            speakText(greeting, AssistantLanguage.HINDI)
                        }
                    }
                }
            } else {
                scheduleListen(450L)
            }
        } else {
            awaitingActiveCommand = false
            dispatchCommand(spoken)
        }
    }

    private fun dispatchCommand(command: String) {
        VoiceServiceBridge.setOrbState(VoiceOrbState.PROCESSING)
        val canHandleInUi = VoiceServiceBridge.foregroundUiActive.value && !isScreenOffOrLocked()
        if (canHandleInUi) {
            VoiceServiceBridge.emitEvent(VoiceServiceEvent.TranscriptCaptured(command))
        } else {
            processCommandAutonomously(command)
        }
    }

    private fun processCommandAutonomously(rawPrompt: String) {
        val clean = rawPrompt.trim()
        if (clean.isEmpty()) {
            recoverOrbAfterSpeechFailure()
            return
        }

        backgroundAiJob?.cancel()
        backgroundAiJob = serviceScope.launch {
            val lower = clean.lowercase(Locale.ROOT).trimEnd('.', '!', '?', '।')
            val snapshot = repository.loadBackgroundSnapshot()
            val prefs = snapshot.preferences
            val lang = AssistantLanguage.fromCode(prefs.defaultLanguage)
            currentLanguage = lang
            speechSpeed = prefs.voiceSpeed.toFloat().coerceIn(0.5f, 2.0f)
            val lockedOrScreenOff = isScreenOffOrLocked()
            val telemetry = executor.readTelemetry()
            val currentNotifications = NotificationBridge.notifications.value

            val matchedRoutine = snapshot.routines.firstOrNull { routine ->
                routine.enabled && routine.triggerPhrase.isNotBlank() &&
                    lower.contains(routine.triggerPhrase.lowercase(Locale.ROOT))
            }

            // 1. Check instant local command router first (zero latency, works offline & when screen is off!)
            if (matchedRoutine == null) {
                val fastResult = LocalCommandRouter.tryFastPath(
                    rawPrompt = clean,
                    defaultLanguage = lang,
                    telemetry = telemetry,
                    notifications = currentNotifications,
                    tasks = snapshot.tasks,
                    isDeviceLocked = lockedOrScreenOff,
                    ownerName = prefs.ownerName,
                    memories = snapshot.memories
                )
                if (fastResult != null) {
                    repository.addConversationMessage("user", clean, lang)
                    applyAutonomousBrainResult(
                        brainResult = fastResult,
                        lang = lang,
                        locked = lockedOrScreenOff,
                        canWriteSettings = telemetry.writeSettingsGranted,
                        memories = snapshot.memories
                    )
                    return@launch
                }
            }

            val effectivePrompt = if (matchedRoutine != null) {
                "Execute this automation routine named '${matchedRoutine.title}' step by step: ${matchedRoutine.steps.joinToString("; then ")}"
            } else {
                clean
            }

            repository.addConversationMessage("user", clean, lang)

            val result = aliBrain.processCommand(
                userPrompt = effectivePrompt,
                defaultLanguage = lang,
                memoryConsentGranted = prefs.memoryConsentGranted,
                memories = snapshot.memories,
                tasks = snapshot.tasks,
                routines = snapshot.routines,
                history = snapshot.history,
                telemetry = telemetry,
                recentNotifications = currentNotifications,
                ownerName = prefs.ownerName,
                creatorTagline = prefs.creatorTagline
            )

            result.onSuccess { brainResult ->
                applyAutonomousBrainResult(
                    brainResult = brainResult,
                    lang = lang,
                    locked = lockedOrScreenOff,
                    canWriteSettings = telemetry.writeSettingsGranted,
                    memories = snapshot.memories
                )
            }.onFailure { err ->
                // 2. Offline / error recovery via LocalCommandRouter before returning error
                val offlineRecovery = LocalCommandRouter.tryFallback(
                    rawPrompt = clean,
                    defaultLanguage = lang,
                    telemetry = telemetry,
                    notifications = currentNotifications,
                    tasks = snapshot.tasks,
                    isDeviceLocked = lockedOrScreenOff,
                    ownerName = prefs.ownerName,
                    memories = snapshot.memories
                )
                if (offlineRecovery != null) {
                    applyAutonomousBrainResult(
                        brainResult = offlineRecovery,
                        lang = lang,
                        locked = lockedOrScreenOff,
                        canWriteSettings = telemetry.writeSettingsGranted,
                        memories = snapshot.memories
                    )
                } else {
                    val errMsg = AliAiBrain.describeError(err)
                    val failText = "माफ़ कीजिए बॉस, ALI AI अभी नेटवर्क से जुड़ नहीं पाई ($errMsg)। आप ऑफ़लाइन में भी 'गाना बजाओ', 'टॉर्च ऑन', 'कॉल लगाओ', 'वॉल्यूम बढ़ाओ' या 'बैटरी बताओ' बोल सकते हैं।"
                    repository.addConversationMessage(
                        role = "model",
                        text = failText,
                        language = lang,
                        actionSummary = "ERROR"
                    )
                    withContext(Dispatchers.Main) {
                        speakText("माफ़ कीजिए बॉस, इंटरनेट समस्या है — लेकिन कॉलिंग, म्यूजिक, टॉर्च और वॉल्यूम ऑफ़लाइन भी चल रहे हैं।", AssistantLanguage.HINDI)
                    }
                }
            }
        }
    }

    private suspend fun applyAutonomousBrainResult(
        brainResult: AliBrainResult,
        lang: AssistantLanguage,
        locked: Boolean,
        canWriteSettings: Boolean,
        memories: List<com.example.data.MemoryItem> = emptyList()
    ) {
        val actionSummary = brainResult.actions
            .filter { it.type != ActionType.SPEAK_ONLY }
            .joinToString(", ") { it.type.name }

        repository.addConversationMessage(
            role = "model",
            text = brainResult.replyText,
            language = brainResult.spokenLanguage,
            actionSummary = if (locked && actionSummary.isNotBlank()) "LOCKED: $actionSummary" else actionSummary
        )

        // Start music / immediate non-spoken actions first so TTS never blocks music detection
        val preMusicActions = brainResult.actions.filter { it.type == ActionType.PLAY_MUSIC }
        val otherActions = brainResult.actions.filter { it.type != ActionType.PLAY_MUSIC }

        if (preMusicActions.isNotEmpty()) {
            executeBackgroundActions(preMusicActions, lang, locked, canWriteSettings, memories)
        }

        val hasOutgoingCall = otherActions.any { it.type == ActionType.MAKE_CALL }
        if (!hasOutgoingCall) {
            withContext(Dispatchers.Main) {
                speakText(brainResult.replyText, brainResult.spokenLanguage, TextToSpeech.QUEUE_FLUSH)
            }
        }

        if (otherActions.isNotEmpty()) {
            executeBackgroundActions(otherActions, lang, locked, canWriteSettings, memories)
        }
    }

    private suspend fun executeBackgroundActions(
        actions: List<DeviceAction>,
        language: AssistantLanguage,
        isLocked: Boolean,
        canWriteSettings: Boolean,
        memories: List<com.example.data.MemoryItem> = emptyList()
    ) {
        var lockNoticeSpoken = false
        for (action in actions) {
            if (action.type == ActionType.EMERGENCY_STOP) {
                withContext(Dispatchers.Main) { performEmergencyStop() }
                return
            }

            val lockDecision = LockScreenPolicy.evaluate(
                action = action,
                isDeviceLocked = isLocked,
                canWriteSettings = canWriteSettings,
                allowLockScreenCalls = lockScreenModeEnabled
            )
            if (!lockDecision.allowed) {
                if (!lockNoticeSpoken) {
                    lockNoticeSpoken = true
                    withContext(Dispatchers.Main) {
                        speakText(lockDecision.reasonHindi, AssistantLanguage.HINDI, TextToSpeech.QUEUE_ADD)
                    }
                }
                continue
            }

            when (action.type) {
                ActionType.SPEAK_ONLY -> Unit
                ActionType.PLAY_MUSIC -> {
                    withContext(Dispatchers.Main) {
                        val cmd = action.payload.ifBlank { "play" }
                        if (cmd.lowercase() !in setOf("pause", "stop", "off")) {
                            ensureWakeLockHeld()
                        }
                        LeonMusicEngine.handleAction(applicationContext, action.target, cmd)
                        promoteOrUpdateMediaNotification()
                    }
                }
                ActionType.LOCK_SCREEN_BRIEFING -> {
                    speakLockScreenBriefing()
                }
                ActionType.SET_TIMER -> {
                    val secs = action.payload.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 86400) ?: 60
                    val label = action.target.ifBlank { "LEON Timer" }
                    if (isLocked) {
                        withContext(Dispatchers.Main) {
                            ensureWakeLockHeld()
                            promoteToForegroundSafely(includeMic = alwaysReadyMode && hasMicPermission())
                            scheduleBackgroundTimer(secs, label)
                        }
                    } else {
                        executor.execute(action)
                    }
                }
                ActionType.CREATE_TASK -> {
                    repository.createTask(
                        title = action.target.ifBlank { action.payload.ifBlank { "New Task" } },
                        dueLabel = action.payload,
                        priority = TaskPriority.MEDIUM
                    )
                }
                ActionType.CREATE_ROUTINE -> {
                    val steps = action.payload.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                        .ifEmpty { listOf("Speak briefing") }
                    val title = action.target.ifBlank { "Custom Routine" }
                    repository.createRoutine(title, title, steps)
                }
                ActionType.READ_NOTIFICATIONS -> {
                    val outcome = executor.execute(action)
                    withContext(Dispatchers.Main) {
                        speakText(outcome.logMessage, language, TextToSpeech.QUEUE_ADD)
                    }
                }
                ActionType.SAVE_MEMORY -> {
                    val parts = action.payload.split("::", limit = 2)
                    val category = if (parts.size == 2) com.example.data.MemoryCategory.fromWire(parts[0])
                    else com.example.data.MemoryCategory.FACT
                    val value = if (parts.size == 2) parts[1] else action.payload.ifBlank { action.target }
                    repository.saveMemory(
                        key = action.target.ifBlank { "Saved Fact" },
                        value = value,
                        category = category,
                        isPermanent = true
                    )
                }
                ActionType.SET_OWNER_IDENTITY -> {
                    if (action.target.isNotBlank()) {
                        repository.setOwnerIdentity(
                            ownerName = action.target,
                            creatorTagline = if (action.payload == "publish") "" else action.payload
                        )
                    }
                }
                ActionType.FETCH_NEWS -> {
                    val category = NewsCategory.fromTopicHint(action.target.ifBlank { action.payload })
                    val owner = runCatching { repository.getPreferencesOnce().ownerName }.getOrDefault("")
                    val newsRes = LiveNewsEngine.fetchNews(category)
                    val digest = newsRes.fold(
                        onSuccess = { articles -> LiveNewsEngine.buildSpokenDigest(articles, category, owner) },
                        onFailure = { err -> "बॉस, न्यूज़ लोड नहीं हो सकी: ${err.localizedMessage ?: "नेटवर्क चेक करें"}।" }
                    )
                    repository.addConversationMessage("model", digest, AssistantLanguage.HINDI, "FETCH_NEWS")
                    withContext(Dispatchers.Main) {
                        speakText(digest, AssistantLanguage.HINDI, TextToSpeech.QUEUE_ADD)
                    }
                }
                ActionType.MAKE_CALL -> {
                    withContext(Dispatchers.Main) {
                        cancelListenTimers()
                        stopRecognizerInternal()
                    }
                    val callOutcome = executor.makeCall(
                        target = action.target,
                        memories = memories,
                        allowBackgroundDirectOnly = isLocked
                    )
                    if (callOutcome.success) {
                        withContext(Dispatchers.Main) {
                            VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
                            if (alwaysReadyMode) {
                                scheduleListen(7_000L)
                            }
                        }
                    } else {
                        repository.addConversationMessage("model", callOutcome.logMessage, AssistantLanguage.HINDI, "CALL_FAILED")
                        withContext(Dispatchers.Main) {
                            speakText(callOutcome.logMessage, AssistantLanguage.HINDI, TextToSpeech.QUEUE_FLUSH)
                        }
                    }
                }
                ActionType.SOCIAL_AUTO_POST -> {
                    SocialAutoPosterBridge.requestOpenStudio(action.target, action.payload)
                    withContext(Dispatchers.Main) {
                        val openStudioIntent = Intent(applicationContext, MainActivity::class.java).apply {
                            setAction("com.example.action.OPEN_SOCIAL_POSTER")
                            putExtra("extra_social_target", action.target)
                            putExtra("extra_social_payload", action.payload)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        runCatching { startActivity(openStudioIntent) }
                    }
                }
                else -> executor.execute(action)
            }
            if (actions.size > 1) delay(250L)
        }
    }

    private fun scheduleBackgroundTimer(seconds: Int, label: String) {
        backgroundTimerJob?.cancel()
        backgroundTimerJob = serviceScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                VoiceServiceBridge.setActiveTimerRemainingSeconds(remaining)
                if (remaining == seconds || remaining % 15 == 0 || remaining <= 5) {
                    withContext(Dispatchers.Main) { refreshNotification() }
                }
                delay(1_000L)
                remaining -= 1
            }
            VoiceServiceBridge.setActiveTimerRemainingSeconds(0)
            withContext(Dispatchers.Main) {
                refreshNotification()
                speakText("बॉस, आपका $label टाइमर पूरा हो गया है!", AssistantLanguage.HINDI, TextToSpeech.QUEUE_FLUSH)
            }
        }
    }

    private fun speakLockScreenBriefing() {
        serviceScope.launch {
            val snapshot = repository.loadBackgroundSnapshot()
            val telemetry = executor.readTelemetry()
            val briefing = LockScreenPolicy.buildLockScreenBriefing(
                telemetry = telemetry,
                notifications = NotificationBridge.notifications.value,
                tasks = snapshot.tasks,
                isDeviceLocked = isDeviceLocked()
            )
            withContext(Dispatchers.Main) {
                speakText(briefing, AssistantLanguage.HINDI, TextToSpeech.QUEUE_FLUSH)
            }
        }
    }

    private fun speakText(
        text: String,
        language: AssistantLanguage,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ) {
        if (!ttsReady) {
            if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                pendingSpeechQueue.clear()
            }
            pendingSpeechQueue.addLast(PendingSpeech(text, language, queueMode))
            mainHandler.removeCallbacks(ttsWatchdogRunnable)
            mainHandler.postDelayed(ttsWatchdogRunnable, TTS_WATCHDOG_MS)
            return
        }
        speakNow(text, language, queueMode)
    }

    private fun speakNow(
        text: String,
        language: AssistantLanguage,
        queueMode: Int,
        forceEnglishPhonetic: Boolean = false
    ) {
        val engine = tts
        if (engine == null) {
            recoverOrbAfterSpeechFailure()
            return
        }
        cancelListenTimers()
        stopRecognizerInternal()
        LeonSpeaker.stop(notifyIdle = false)
        LeonMusicEngine.setTtsSpeaking(true)
        VoiceServiceBridge.setOrbState(VoiceOrbState.SPEAKING)

        runCatching {
            engine.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }

        val cleanedText = LeonSpeaker.cleanForSpeech(text)
        lastSpokenRawText = cleanedText
        lastSpokenWasPhoneticFallback = forceEnglishPhonetic

        val chosenLocale = if (forceEnglishPhonetic) {
            LeonSpeaker.selectAvailableLocale(engine, AssistantLanguage.ENGLISH)
        } else {
            LeonSpeaker.selectAvailableLocale(engine, language)
        }
        val spokenText = if ((forceEnglishPhonetic || !chosenLocale.language.equals("hi", ignoreCase = true)) &&
            LeonSpeaker.containsDevanagari(cleanedText)
        ) {
            LeonSpeaker.romanizeDevanagariForEnglishTts(cleanedText)
        } else {
            cleanedText
        }

        val dynamicWatchdogMs = (spokenText.length * 95L).coerceIn(TTS_WATCHDOG_MS, 20_000L)
        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        mainHandler.postDelayed(ttsWatchdogRunnable, dynamicWatchdogMs)

        engine.setSpeechRate(speechSpeed)
        val speakStatus = runCatching {
            engine.speak(
                spokenText,
                queueMode,
                LeonSpeaker.buildSpeakParams(),
                "leon_utt_${System.currentTimeMillis()}"
            )
        }.getOrDefault(TextToSpeech.ERROR)
        if (speakStatus != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TextToSpeech.speak returned status=$speakStatus")
            handleServiceTtsFailure()
        }
    }

    private fun handleServiceTtsFailure() {
        val canRetry = tts != null &&
            !lastSpokenWasPhoneticFallback &&
            lastSpokenRawText.isNotBlank() &&
            LeonSpeaker.containsDevanagari(lastSpokenRawText)
        if (canRetry) {
            Log.w(TAG, "Service Hindi TTS failed — retrying in phonetic Hinglish")
            speakNow(
                text = lastSpokenRawText,
                language = AssistantLanguage.ENGLISH,
                queueMode = TextToSpeech.QUEUE_FLUSH,
                forceEnglishPhonetic = true
            )
        } else {
            mainHandler.removeCallbacks(ttsWatchdogRunnable)
            LeonMusicEngine.setTtsSpeaking(false)
            recoverOrbAfterSpeechFailure()
        }
    }

    private fun recoverOrbAfterSpeechFailure() {
        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        LeonMusicEngine.setTtsSpeaking(false)
        if (VoiceServiceBridge.foregroundMicActive.value || LeonSpeaker.isSpeaking.value) {
            return
        }
        if (alwaysReadyMode || awaitingActiveCommand) {
            if (alwaysReadyMode && !lockScreenModeEnabled && isScreenOffOrLocked()) {
                VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
            } else if (isAudioInCall()) {
                VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
                scheduleListen(3500L)
            } else {
                VoiceServiceBridge.setOrbState(
                    if (awaitingActiveCommand) VoiceOrbState.ACTIVE_LISTENING else VoiceOrbState.WAKE_LISTENING
                )
                scheduleListen(350L)
            }
        } else {
            if (!LeonMusicEngine.isPlaying() && backgroundTimerJob?.isActive != true) {
                releaseWakeLock()
            }
            VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
        }
    }

    private fun performEmergencyStop() {
        alwaysReadyMode = false
        awaitingActiveCommand = false
        backgroundAiJob?.cancel()
        backgroundAiJob = null
        backgroundTimerJob?.cancel()
        backgroundTimerJob = null
        VoiceServiceBridge.setActiveTimerRemainingSeconds(0)
        pendingSpeechQueue.clear()
        cancelListenTimers()
        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        stopRecognizerInternal()
        tts?.stop()
        LeonMusicEngine.setTtsSpeaking(false)
        LeonMusicEngine.stopOrPause(applicationContext, stopCompletely = true)
        SocialAutoPosterBridge.clearJob()
        releaseWakeLock()
        VoiceServiceBridge.setRunning(false)
        VoiceServiceBridge.setOrbState(VoiceOrbState.IDLE)
        VoiceServiceBridge.emitEvent(VoiceServiceEvent.EmergencyStopTriggered)
        stopForegroundCompat()
        isPromotedForeground = false
        stopSelf()
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopRecognizerInternal() {
        mainHandler.removeCallbacks(listenWatchdogRunnable)
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
        isListening = false
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            runCatching {
                tts?.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        mainHandler.removeCallbacks(ttsWatchdogRunnable)
                        LeonMusicEngine.setTtsSpeaking(false)
                        recoverOrbAfterSpeechFailure()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { handleServiceTtsFailure() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TAG, "Service TTS errorCode=$errorCode")
                    mainHandler.post { handleServiceTtsFailure() }
                }
            })
            while (pendingSpeechQueue.isNotEmpty()) {
                val next = pendingSpeechQueue.removeFirst()
                speakNow(next.text, next.language, next.queueMode)
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status=$status")
            pendingSpeechQueue.clear()
            mainHandler.removeCallbacks(ttsWatchdogRunnable)
            LeonMusicEngine.setTtsSpeaking(false)
            recoverOrbAfterSpeechFailure()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenLockReceiver) }
        backgroundAiJob?.cancel()
        backgroundTimerJob?.cancel()
        serviceScope.cancel()
        pendingSpeechQueue.clear()
        cancelListenTimers()
        mainHandler.removeCallbacks(ttsWatchdogRunnable)
        stopRecognizerInternal()
        tts?.stop()
        tts?.shutdown()
        LeonMusicEngine.setTtsSpeaking(false)
        LeonMusicEngine.stopOrPause(applicationContext, stopCompletely = true)
        releaseWakeLock()
        VoiceServiceBridge.setRunning(false)
        VoiceServiceBridge.setActiveTimerRemainingSeconds(0)
        isPromotedForeground = false
    }
}
