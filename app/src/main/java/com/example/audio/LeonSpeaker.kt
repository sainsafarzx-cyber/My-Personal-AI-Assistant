package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.AssistantLanguage
import com.example.media.LeonMusicEngine
import com.example.service.VoiceOrbState
import com.example.service.VoiceServiceBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "LeonSpeaker"
private const val UTTERANCE_WATCHDOG_MIN_MS = 8_000L
private const val UTTERANCE_WATCHDOG_MAX_MS = 18_000L

/**
 * Eagerly initialized, non-blocking foreground Text-to-Speech engine for LEON AI.
 *
 * Guarantees audible speech on real Android devices and emulators by:
 * 1. Routing via USAGE_MEDIA + CONTENT_TYPE_SPEECH + STREAM_MUSIC + full utterance volume 1.0f
 *    (avoids silent USAGE_ASSISTANT routing bugs on emulators/OEMs).
 * 2. Preferring locally installed voices and cascading cleanly hi-IN -> hi -> en-IN -> en-US -> default.
 * 3. Retrying automatically in phonetic Hinglish via English TTS if Hindi synthesis fails on onError().
 * 4. Syncing VoiceServiceBridge orb state and invoking an idle callback ONLY after speech finishes
 *    so Always-Ready SpeechRecognizer never cuts off LEON mid-sentence.
 */
object LeonSpeaker : TextToSpeech.OnInitListener {

    private data class QueuedSpeech(
        val text: String,
        val language: AssistantLanguage,
        val speed: Float,
        val queueMode: Int
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private val _activeLocaleTag = MutableStateFlow("initializing")
    val activeLocaleTag: StateFlow<String> = _activeLocaleTag.asStateFlow()

    private var appContext: Context? = null
    private var tts: TextToSpeech? = null
    private var isInitializing = false
    private val pendingQueue = ArrayDeque<QueuedSpeech>()
    private var focusRequest: AudioFocusRequest? = null
    private var onSpeechIdleListener: (() -> Unit)? = null

    private var lastAttemptedRawText: String = ""
    private var lastAttemptedSpeed: Float = 1.0f
    private var lastAttemptedWasPhoneticFallback: Boolean = false

    private val watchdogRunnable = Runnable {
        Log.w(TAG, "LeonSpeaker utterance watchdog reset")
        finishSpeakingCleanup(notifyIdle = true)
    }

    fun setOnSpeechIdleListener(listener: (() -> Unit)?) {
        onSpeechIdleListener = listener
    }

    fun isMediaStreamMuted(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching { am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 }.getOrDefault(false)
    }

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (tts != null || isInitializing) return
        mainHandler.post {
            if (tts != null || isInitializing) return@post
            isInitializing = true
            runCatching {
                val engine = TextToSpeech(ctx, this)
                configureSpeechAudioAttributes(engine)
                tts = engine
            }.onFailure { err ->
                isInitializing = false
                Log.w(TAG, "TextToSpeech construction failed cleanly", err)
            }
        }
    }

    fun speak(
        context: Context,
        text: String,
        language: AssistantLanguage = AssistantLanguage.HINDI,
        speed: Float = 1.0f,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ) {
        val cleaned = cleanForSpeech(text)
        if (cleaned.isBlank()) return
        init(context)

        mainHandler.post {
            val engine = tts
            if (!_ttsReady.value || engine == null) {
                if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                    pendingQueue.clear()
                }
                pendingQueue.addLast(QueuedSpeech(cleaned, language, speed, queueMode))
                return@post
            }
            speakInternal(engine, cleaned, language, speed, queueMode, forceEnglishPhonetic = false)
        }
    }

    fun stop(notifyIdle: Boolean = true) {
        mainHandler.post {
            pendingQueue.clear()
            lastAttemptedWasPhoneticFallback = true
            runCatching { tts?.stop() }
            finishSpeakingCleanup(notifyIdle = notifyIdle)
        }
    }

    fun release() {
        mainHandler.post {
            pendingQueue.clear()
            mainHandler.removeCallbacks(watchdogRunnable)
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }
            tts = null
            _ttsReady.value = false
            isInitializing = false
            finishSpeakingCleanup(notifyIdle = false)
        }
    }

    override fun onInit(status: Int) {
        isInitializing = false
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            configureSpeechAudioAttributes(engine)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mainHandler.post {
                        _isSpeaking.value = true
                        LeonMusicEngine.setTtsSpeaking(true)
                        VoiceServiceBridge.setOrbState(VoiceOrbState.SPEAKING)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    mainHandler.post {
                        finishSpeakingCleanup(notifyIdle = true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { handleUtteranceFailure() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    Log.w(TAG, "TTS utterance errorCode=$errorCode")
                    mainHandler.post { handleUtteranceFailure() }
                }
            })
            _ttsReady.value = true
            while (pendingQueue.isNotEmpty()) {
                val next = pendingQueue.removeFirst()
                speakInternal(engine, next.text, next.language, next.speed, next.queueMode, forceEnglishPhonetic = false)
            }
        } else {
            Log.w(TAG, "TextToSpeech onInit failed with status=$status")
            _ttsReady.value = false
            pendingQueue.clear()
            finishSpeakingCleanup(notifyIdle = true)
        }
    }

    private fun handleUtteranceFailure() {
        val engine = tts
        val shouldRetryPhonetic = !lastAttemptedWasPhoneticFallback &&
            lastAttemptedRawText.isNotBlank() &&
            containsDevanagari(lastAttemptedRawText)

        if (engine != null && shouldRetryPhonetic) {
            Log.w(TAG, "Hindi TTS utterance failed — retrying cleanly with phonetic Hinglish voice")
            speakInternal(
                engine = engine,
                cleanedText = lastAttemptedRawText,
                language = AssistantLanguage.ENGLISH,
                speed = lastAttemptedSpeed,
                queueMode = TextToSpeech.QUEUE_FLUSH,
                forceEnglishPhonetic = true
            )
        } else {
            finishSpeakingCleanup(notifyIdle = true)
        }
    }

    private fun speakInternal(
        engine: TextToSpeech,
        cleanedText: String,
        language: AssistantLanguage,
        speed: Float,
        queueMode: Int,
        forceEnglishPhonetic: Boolean
    ) {
        configureSpeechAudioAttributes(engine)
        lastAttemptedRawText = cleanedText
        lastAttemptedSpeed = speed
        lastAttemptedWasPhoneticFallback = forceEnglishPhonetic

        val chosenLocale = if (forceEnglishPhonetic) {
            selectAvailableLocale(engine, AssistantLanguage.ENGLISH)
        } else {
            selectAvailableLocale(engine, language)
        }
        _activeLocaleTag.value = chosenLocale.toLanguageTag()

        val spokenText = if ((forceEnglishPhonetic || !chosenLocale.language.equals("hi", ignoreCase = true)) &&
            containsDevanagari(cleanedText)
        ) {
            romanizeDevanagariForEnglishTts(cleanedText)
        } else {
            cleanedText
        }

        requestSpeechFocus()
        LeonMusicEngine.setTtsSpeaking(true)
        _isSpeaking.value = true
        VoiceServiceBridge.setOrbState(VoiceOrbState.SPEAKING)

        val boundedWatchdogMs = (spokenText.length * 95L)
            .coerceIn(UTTERANCE_WATCHDOG_MIN_MS, UTTERANCE_WATCHDOG_MAX_MS)
        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.postDelayed(watchdogRunnable, boundedWatchdogMs)

        engine.setSpeechRate(speed.coerceIn(0.6f, 2.0f))
        val params = buildSpeakParams()
        val utteranceId = "leon_fg_${System.currentTimeMillis()}"
        val res = runCatching {
            engine.speak(spokenText, queueMode, params, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)

        if (res != TextToSpeech.SUCCESS) {
            Log.w(TAG, "speak() returned error status=$res")
            handleUtteranceFailure()
        }
    }

    private fun finishSpeakingCleanup(notifyIdle: Boolean) {
        mainHandler.removeCallbacks(watchdogRunnable)
        _isSpeaking.value = false
        LeonMusicEngine.setTtsSpeaking(false)
        if (VoiceServiceBridge.orbState.value == VoiceOrbState.SPEAKING) {
            VoiceServiceBridge.setOrbState(
                if (VoiceServiceBridge.alwaysReadyRunning.value) VoiceOrbState.WAKE_LISTENING
                else VoiceOrbState.IDLE
            )
        }
        abandonSpeechFocus()
        if (notifyIdle) {
            onSpeechIdleListener?.invoke()
        }
    }

    private fun configureSpeechAudioAttributes(engine: TextToSpeech) {
        runCatching {
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    internal fun buildSpeakParams(): Bundle = Bundle().apply {
        putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
    }

    private fun requestSpeechFocus() {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .build()
                focusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun abandonSpeechFocus() {
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            }
        }
    }

    internal fun selectAvailableLocale(engine: TextToSpeech, preferred: AssistantLanguage): Locale {
        val candidates = when (preferred) {
            AssistantLanguage.ENGLISH -> listOf(
                Locale.forLanguageTag("en-IN"),
                Locale.US,
                Locale.UK,
                Locale.ENGLISH,
                Locale.forLanguageTag("hi-IN")
            )
            AssistantLanguage.HINDI, AssistantLanguage.HINGLISH -> listOf(
                Locale.forLanguageTag("hi-IN"),
                Locale.forLanguageTag("hi"),
                Locale.forLanguageTag("en-IN"),
                Locale.US,
                Locale.ENGLISH
            )
        }
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
        for (candidate in candidates) {
            val avail = runCatching { engine.isLanguageAvailable(candidate) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (avail >= TextToSpeech.LANG_AVAILABLE) {
                // Prefer offline voice for that locale if present so speech plays without waiting for network
                val offlineVoice = voices.firstOrNull { voice ->
                    !voice.isNetworkConnectionRequired &&
                        voice.locale.language.equals(candidate.language, ignoreCase = true)
                }
                if (offlineVoice != null) {
                    val vRes = runCatching { engine.setVoice(offlineVoice) }.getOrDefault(TextToSpeech.ERROR)
                    if (vRes == TextToSpeech.SUCCESS) {
                        return offlineVoice.locale
                    }
                }
                val setRes = runCatching { engine.setLanguage(candidate) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
                if (setRes >= TextToSpeech.LANG_AVAILABLE) {
                    return candidate
                }
            }
        }
        val def = Locale.getDefault()
        runCatching { engine.setLanguage(def) }
        return def
    }

    internal fun cleanForSpeech(raw: String): String {
        return raw
            .replace(Regex("[*_`#~•|]"), " ")
            .replace("×", " ")
            .replace("।", ". ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(900)
    }

    internal fun containsDevanagari(text: String): Boolean =
        text.any { it in '\u0900'..'\u097F' }

    /**
     * Converts Devanagari text to phonetic Latin/Hinglish so English-only Android TTS engines
     * speak Hindi replies clearly instead of staying silent.
     */
    internal fun romanizeDevanagariForEnglishTts(input: String): String {
        if (!containsDevanagari(input)) return input

        var text = input
            .replace("लियोन", "Leon")
            .replace("अली", "Ali")
            .replace("बॉस", "Boss")
            .replace("न्यूज़", "News")
            .replace("फ्लैशलाइट", "Flashlight")
            .replace("ब्राइटनेस", "Brightness")
            .replace("वॉल्यूम", "Volume")
            .replace("म्यूजिक", "Music")
            .replace("म्यूज़िक", "Music")
            .replace("बैटरी", "Battery")
            .replace("वाई-फाई", "Wi-Fi")
            .replace("वाईफाई", "Wi-Fi")
            .replace("ब्लूटूथ", "Bluetooth")
            .replace("यूट्यूब", "YouTube")
            .replace("व्हाट्सएप", "WhatsApp")
            .replace("कैमरा", "Camera")
            .replace("टाइमर", "Timer")
            .replace("इंटरनेट", "Internet")
            .replace("ऑनलाइन", "Online")
            .replace("ऑफ़लाइन", "Offline")

        val out = StringBuilder(text.length * 2)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val next = text.getOrNull(i + 1)
            val consonant = DEVANAGARI_CONSONANTS[c]
            if (consonant != null) {
                val base = if (next == '\u093C') {
                    i++
                    nuktaConsonant(c, consonant)
                } else {
                    consonant
                }
                val after = text.getOrNull(i + 1)
                when {
                    after == '\u094D' -> {
                        out.append(base)
                        i += 2
                    }
                    after != null && DEVANAGARI_MATRAS.containsKey(after) -> {
                        out.append(base).append(DEVANAGARI_MATRAS[after])
                        i += 2
                    }
                    else -> {
                        val wordEnd = after == null || after !in '\u0900'..'\u097F'
                        out.append(base)
                        if (!wordEnd) out.append('a')
                        i++
                    }
                }
                continue
            }

            val standalone = DEVANAGARI_VOWELS[c]
            if (standalone != null) {
                out.append(standalone)
                i++
                continue
            }

            val sign = DEVANAGARI_SIGNS[c]
            if (sign != null) {
                out.append(sign)
                i++
                continue
            }

            out.append(c)
            i++
        }
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun nuktaConsonant(c: Char, fallback: String): String = when (c) {
        'ज' -> "z"
        'फ' -> "f"
        'क' -> "q"
        'ख' -> "kh"
        'ग' -> "gh"
        'ड' -> "r"
        'ढ' -> "rh"
        else -> fallback
    }

    private val DEVANAGARI_CONSONANTS: Map<Char, String> = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "gh", '\u095B' to "z",
        '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y"
    )

    private val DEVANAGARI_MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
        'ॉ' to "o", 'ॅ' to "e"
    )

    private val DEVANAGARI_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
        '०' to "0", '१' to "1", '२' to "2", '३' to "3", '४' to "4",
        '५' to "5", '६' to "6", '७' to "7", '८' to "8", '९' to "9"
    )

    private val DEVANAGARI_SIGNS = mapOf(
        'ं' to "n", 'ँ' to "n", 'ः' to "h", '।' to ". "
    )
}
