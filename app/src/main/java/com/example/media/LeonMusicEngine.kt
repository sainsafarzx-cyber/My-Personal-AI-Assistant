package com.example.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.example.service.VoiceServiceBridge
import kotlin.math.PI
import kotlin.math.sin

private const val TAG = "LeonMusicEngine"
private const val SAMPLE_RATE = 22050

data class AmbientTrackPreset(
    val name: String,
    val chordsHz: List<FloatArray>
)

/**
 * Lock-screen-safe Music Controller, Native MediaSession & Built-In Ambient Synthesizer for LEON AI.
 *
 * - Attaches an Android MediaSession so playback stays visible and controllable on the lock screen
 *   and via Bluetooth/headset buttons even after the screen turns off.
 * - Keeps TTS speech separate from external music detection so spoken confirmations never suppress music.
 * - Falls back seamlessly to a warm polyphonic ambient synthesizer when no external player is active.
 */
object LeonMusicEngine {

    private val presets = listOf(
        AmbientTrackPreset(
            name = "LEON Cyber Calm",
            chordsHz = listOf(
                floatArrayOf(220.0f, 261.63f, 329.63f, 392.00f),
                floatArrayOf(174.61f, 220.00f, 261.63f, 329.63f),
                floatArrayOf(196.00f, 246.94f, 293.66f, 392.00f),
                floatArrayOf(164.81f, 196.00f, 246.94f, 329.63f)
            )
        ),
        AmbientTrackPreset(
            name = "Starlight Lo-Fi",
            chordsHz = listOf(
                floatArrayOf(293.66f, 349.23f, 440.00f, 523.25f),
                floatArrayOf(261.63f, 329.63f, 392.00f, 493.88f),
                floatArrayOf(233.08f, 293.66f, 349.23f, 440.00f),
                floatArrayOf(220.00f, 261.63f, 329.63f, 392.00f)
            )
        ),
        AmbientTrackPreset(
            name = "Deep Focus Aura",
            chordsHz = listOf(
                floatArrayOf(196.00f, 233.08f, 293.66f, 349.23f),
                floatArrayOf(155.56f, 196.00f, 233.08f, 293.66f),
                floatArrayOf(174.61f, 220.00f, 261.63f, 349.23f),
                floatArrayOf(146.83f, 174.61f, 220.00f, 293.66f)
            )
        ),
        AmbientTrackPreset(
            name = "Morning Horizon",
            chordsHz = listOf(
                floatArrayOf(261.63f, 329.63f, 392.00f, 493.88f),
                floatArrayOf(220.00f, 261.63f, 329.63f, 392.00f),
                floatArrayOf(174.61f, 220.00f, 261.63f, 329.63f),
                floatArrayOf(196.00f, 246.94f, 293.66f, 392.00f)
            )
        )
    )

    @Volatile
    private var internalPlaying = false

    @Volatile
    private var ducked = false

    @Volatile
    private var ttsSpeaking = false

    @Volatile
    private var presetIndex = 0

    @Volatile
    private var playbackThread: Thread? = null

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var mediaSession: MediaSession? = null

    private var audioFocusRequest: AudioFocusRequest? = null

    fun isPlaying(): Boolean = internalPlaying || VoiceServiceBridge.musicPlaying.value

    fun setTtsSpeaking(speaking: Boolean) {
        ttsSpeaking = speaking
        ducked = speaking
    }

    fun ensureMediaSession(context: Context): MediaSession.Token? {
        if (mediaSession != null) return runCatching { mediaSession?.sessionToken }.getOrNull()
        val appCtx = context.applicationContext
        return runCatching {
            val session = MediaSession(appCtx, "LeonMusicSession").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        startPlayback(appCtx, "")
                    }

                    override fun onPause() {
                        stopOrPause(appCtx, stopCompletely = false)
                    }

                    override fun onSkipToNext() {
                        nextTrack(appCtx)
                    }

                    override fun onSkipToPrevious() {
                        previousTrack(appCtx)
                    }

                    override fun onStop() {
                        stopOrPause(appCtx, stopCompletely = true)
                    }
                })
                isActive = true
            }
            mediaSession = session
            updateMediaSessionState(isPlaying())
            session.sessionToken
        }.getOrNull()
    }

    fun releaseMediaSession() {
        runCatching {
            mediaSession?.isActive = false
            mediaSession?.release()
        }
        mediaSession = null
    }

    fun handleAction(context: Context, query: String, command: String): String {
        val normalizedCmd = command.lowercase().trim()
        val normalizedQuery = query.lowercase().trim()

        return when {
            normalizedCmd in setOf("pause", "stop", "off") -> stopOrPause(context, stopCompletely = normalizedCmd == "stop")
            normalizedCmd == "toggle" -> {
                if (isPlaying()) stopOrPause(context, stopCompletely = false)
                else startPlayback(context, normalizedQuery)
            }
            normalizedCmd in setOf("next", "skip") -> nextTrack(context)
            normalizedCmd in setOf("prev", "previous", "back") -> previousTrack(context)
            else -> startPlayback(context, normalizedQuery)
        }
    }

    fun toggle(context: Context): String =
        handleAction(context, "", if (isPlaying()) "pause" else "play")

    fun startPlayback(context: Context, query: String = ""): String {
        val appCtx = context.applicationContext
        ensureMediaSession(appCtx)
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // Never mistake LEON's own TTS speech for an active external music player
        val externalActive = !internalPlaying && !ttsSpeaking && (am?.isMusicActive == true)
        if (externalActive && query.isBlank()) {
            VoiceServiceBridge.setMusicPlaying(true)
            updateMediaSessionState(true)
            return "External music player is active."
        }

        presetIndex = pickPresetFromQuery(query, presetIndex)
        val trackName = presets[presetIndex].name

        if (internalPlaying) {
            VoiceServiceBridge.setMusicPlaying(true)
            updateMediaSessionState(true)
            return "Playing $trackName"
        }

        requestMediaAudioFocus(am)
        startAmbientLoop()
        VoiceServiceBridge.setMusicPlaying(true)
        updateMediaSessionState(true)
        return "Playing $trackName (Screen-Off & Lock-Screen Ready)."
    }

    fun stopOrPause(context: Context, stopCompletely: Boolean = false): String {
        val appCtx = context.applicationContext
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val wasInternal = internalPlaying

        stopAmbientLoop()
        abandonMediaAudioFocus(am)

        if (!wasInternal && !ttsSpeaking && am?.isMusicActive == true) {
            dispatchMediaKey(
                am,
                if (stopCompletely) KeyEvent.KEYCODE_MEDIA_STOP else KeyEvent.KEYCODE_MEDIA_PAUSE
            )
        }
        VoiceServiceBridge.setMusicPlaying(false)
        updateMediaSessionState(false)
        if (stopCompletely) {
            releaseMediaSession()
        }
        return if (stopCompletely) "Music stopped." else "Music paused."
    }

    fun nextTrack(context: Context): String {
        val appCtx = context.applicationContext
        ensureMediaSession(appCtx)
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (!internalPlaying && !ttsSpeaking && am?.isMusicActive == true) {
            dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_NEXT)
            return "Skipped to next track."
        }
        presetIndex = (presetIndex + 1) % presets.size
        val nextName = presets[presetIndex].name
        if (!internalPlaying) {
            requestMediaAudioFocus(am)
            startAmbientLoop()
        }
        VoiceServiceBridge.setMusicPlaying(true)
        updateMediaSessionState(true)
        return "Switched to $nextName."
    }

    fun previousTrack(context: Context): String {
        val appCtx = context.applicationContext
        ensureMediaSession(appCtx)
        val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (!internalPlaying && !ttsSpeaking && am?.isMusicActive == true) {
            dispatchMediaKey(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            return "Returned to previous track."
        }
        presetIndex = (presetIndex - 1 + presets.size) % presets.size
        val prevName = presets[presetIndex].name
        if (!internalPlaying) {
            requestMediaAudioFocus(am)
            startAmbientLoop()
        }
        VoiceServiceBridge.setMusicPlaying(true)
        updateMediaSessionState(true)
        return "Switched to $prevName."
    }

    fun setDucked(shouldDuck: Boolean) {
        ducked = shouldDuck
    }

    fun currentTrackName(): String = presets[presetIndex].name

    private fun updateMediaSessionState(playing: Boolean) {
        val session = mediaSession ?: return
        runCatching {
            val state = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    1.0f
                )
                .build()
            session.setPlaybackState(state)
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, currentTrackName())
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "LEON × ALI AI")
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "Lock-Screen Ambient Audio")
                    .build()
            )
            session.isActive = true
        }
    }

    private fun pickPresetFromQuery(query: String, current: Int): Int {
        return when {
            query.contains("lofi") || query.contains("lo-fi") || query.contains("chill") || query.contains("रात") -> 1
            query.contains("focus") || query.contains("study") || query.contains("पढ़ाई") || query.contains("मेडिटेशन") -> 2
            query.contains("morning") || query.contains("सुबह") || query.contains("happy") -> 3
            query.contains("calm") || query.contains("relax") || query.contains("शांत") -> 0
            else -> current
        }
    }

    @Synchronized
    private fun startAmbientLoop() {
        if (internalPlaying) return
        internalPlaying = true

        val thread = Thread({
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(SAMPLE_RATE / 4)

                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                val format = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()

                track = AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                activeTrack = track
                track.play()

                val chunkFrames = 1024
                val buffer = ShortArray(chunkFrames)
                var sampleIndex = 0L
                val samplesPerChord = SAMPLE_RATE * 4
                val twoPi = 2.0 * PI

                while (internalPlaying) {
                    val preset = presets[presetIndex]
                    val chord = preset.chordsHz[((sampleIndex / samplesPerChord) % preset.chordsHz.size).toInt()]
                    val barPos = (sampleIndex % samplesPerChord).toDouble() / samplesPerChord.toDouble()
                    val barEnvelope = when {
                        barPos < 0.08 -> barPos / 0.08
                        barPos > 0.90 -> (1.0 - barPos) / 0.10
                        else -> 1.0
                    }
                    val targetGain = if (ducked || ttsSpeaking) 0.22 else 0.85

                    for (i in 0 until chunkFrames) {
                        val t = (sampleIndex + i).toDouble() / SAMPLE_RATE.toDouble()
                        val lfo = 0.85 + 0.15 * sin(twoPi * 0.25 * t)
                        var mix = 0.0
                        for (freq in chord) {
                            mix += sin(twoPi * freq * t) * 0.22
                        }
                        mix += sin(twoPi * (chord[0] * 0.5) * t) * 0.18
                        val scaled = (mix * barEnvelope * lfo * targetGain * 11500.0).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[i] = scaled.toShort()
                    }
                    sampleIndex += chunkFrames

                    val written = track.write(buffer, 0, chunkFrames)
                    if (written < 0) {
                        break
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "AudioTrack output unavailable (${t.message}); running silent synth state.")
                while (internalPlaying) {
                    try {
                        Thread.sleep(250L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            } finally {
                runCatching {
                    track?.pause()
                    track?.flush()
                    track?.release()
                }
                activeTrack = null
            }
        }, "leon-ambient-music")
        thread.isDaemon = true
        playbackThread = thread
        thread.start()
    }

    @Synchronized
    private fun stopAmbientLoop() {
        internalPlaying = false
        runCatching { playbackThread?.interrupt() }
        playbackThread = null
        runCatching {
            activeTrack?.pause()
            activeTrack?.flush()
            activeTrack?.release()
        }
        activeTrack = null
    }

    private fun dispatchMediaKey(am: AudioManager, keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }.onFailure {
            Log.w(TAG, "dispatchMediaKey failed for $keyCode", it)
        }
    }

    private fun requestMediaAudioFocus(am: AudioManager?) {
        if (am == null) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        }
    }

    private fun abandonMediaAudioFocus(am: AudioManager?) {
        if (am == null) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }
}
