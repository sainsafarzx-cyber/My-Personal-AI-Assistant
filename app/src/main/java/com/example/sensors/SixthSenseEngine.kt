package com.example.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.example.data.AirGestureAction
import com.example.data.SixthSenseTelemetry
import com.example.service.VoiceOrbState
import com.example.service.VoiceServiceBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Neural Sixth Sense Engine:
 * Combines real Android hardware sensors (Lux, Compass/EMF Magnetometer, 3-axis Accelerometer,
 * Proximity Air-Wave), real Acoustic Decibel SPL monitoring via AudioRecord, and an
 * Optical ITU Morse Code Flashlight Beacon Transceiver. Zero mock/simulated values.
 */
class SixthSenseEngine(
    private val context: Context,
    private val onAirGestureTriggered: (AirGestureAction) -> Unit,
    private val onChopFlashTriggered: () -> Unit,
    private val onTorchPulse: (Boolean) -> Boolean
) : SensorEventListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val magneticSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private val _telemetry = MutableStateFlow(
        SixthSenseTelemetry(
            lightAvailable = lightSensor != null,
            magnetometerAvailable = magneticSensor != null,
            accelerometerAvailable = accelSensor != null,
            proximityAvailable = proximitySensor != null,
            batteryTempCelsius = readBatteryTemperatureC()
        )
    )
    val telemetry: StateFlow<SixthSenseTelemetry> = _telemetry.asStateFlow()

    private var sensorsStarted = false
    private var radarScreenOpen = false
    private var lastUiSyncMs = 0L

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeo = false

    // Proximity hand-wave tracking
    private var wasNear = false
    private var nearEnteredMs = 0L
    private var lastAirGestureMs = 0L

    // Kinetic wrist double-chop tracking
    private var firstChopPeakMs = 0L
    private var lastChopTriggerMs = 0L

    // Accelerometer rolling variance for stability percentage
    private var varianceAccum = 0f
    private var varianceSamples = 0

    // Real-time audio dB SPL job
    private var audioMeterJob: Job? = null

    // Optical Morse transceiver job
    private var morseJob: Job? = null

    fun setRadarVisible(visible: Boolean) {
        radarScreenOpen = visible
        syncSensorRegistration()
    }

    fun setAirWaveEnabled(enabled: Boolean) {
        _telemetry.update { it.copy(airWaveEnabled = enabled) }
        syncSensorRegistration()
    }

    fun setChopFlashEnabled(enabled: Boolean) {
        _telemetry.update { it.copy(chopFlashEnabled = enabled) }
        syncSensorRegistration()
    }

    fun setAirGestureTarget(target: AirGestureAction) {
        _telemetry.update { it.copy(airGestureTarget = target) }
    }

    fun startOrRefresh() {
        _telemetry.update {
            it.copy(
                lightAvailable = lightSensor != null,
                magnetometerAvailable = magneticSensor != null,
                accelerometerAvailable = accelSensor != null,
                proximityAvailable = proximitySensor != null,
                batteryTempCelsius = readBatteryTemperatureC()
            )
        }
        syncSensorRegistration()
    }

    private fun syncSensorRegistration() {
        val state = _telemetry.value
        val shouldListen = radarScreenOpen || state.airWaveEnabled || state.chopFlashEnabled
        if (shouldListen && !sensorsStarted) {
            registerHardwareListeners()
        } else if (!shouldListen && sensorsStarted) {
            unregisterHardwareListeners()
        }
    }

    private fun registerHardwareListeners() {
        val sm = sensorManager ?: return
        lightSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magneticSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        proximitySensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        sensorsStarted = true
        _telemetry.update {
            it.copy(
                sensorsActive = true,
                batteryTempCelsius = readBatteryTemperatureC()
            )
        }
    }

    private fun unregisterHardwareListeners() {
        sensorManager?.unregisterListener(this)
        sensorsStarted = false
        stopAcousticDbMeter()
        _telemetry.update { it.copy(sensorsActive = false) }
    }

    @SuppressLint("MissingPermission")
    fun toggleAcousticDbMeter(enable: Boolean): Boolean {
        if (!enable) {
            stopAcousticDbMeter()
            return true
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return false

        if (audioMeterJob?.isActive == true) return true
        _telemetry.update { it.copy(audioMeterActive = true) }

        audioMeterJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 16_000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)

            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    return@launch
                }
                recorder.startRecording()
                val buffer = ShortArray(minBuf / 2)
                while (isActive) {
                    // Yield microphone immediately whenever LEON SpeechRecognizer wakes up
                    val orb = VoiceServiceBridge.orbState.value
                    if (orb == VoiceOrbState.ACTIVE_LISTENING || orb == VoiceOrbState.WAKE_LISTENING) {
                        delay(450L)
                        continue
                    }
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = buffer[i].toDouble()
                            sumSq += s * s
                        }
                        val rms = sqrt(sumSq / read).coerceAtLeast(1.0)
                        val dbFs = 20.0 * log10(rms / 32767.0)
                        val approxSpl = (dbFs + 96.0).toFloat().coerceIn(20f, 110f)
                        _telemetry.update { it.copy(ambientDbSpl = approxSpl) }
                    }
                    delay(180L)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                // Hardware mic busy or permission revoked
            } finally {
                runCatching {
                    recorder?.stop()
                    recorder?.release()
                }
                _telemetry.update { it.copy(audioMeterActive = false, ambientDbSpl = null) }
            }
        }
        return true
    }

    fun stopAcousticDbMeter() {
        audioMeterJob?.cancel()
        audioMeterJob = null
        _telemetry.update { it.copy(audioMeterActive = false, ambientDbSpl = null) }
    }

    fun transmitMorse(rawText: String, onDone: ((String) -> Unit)? = null) {
        val cleanText = rawText.trim().uppercase(Locale.ROOT).ifBlank { "SOS" }.take(24)
        val pattern = encodeToMorse(cleanText)
        if (pattern.isBlank()) {
            onDone?.invoke("No Morse-encodable characters found.")
            return
        }

        morseJob?.cancel()
        _telemetry.update {
            it.copy(
                morseTransmitting = true,
                morseCurrentText = cleanText,
                morseEncodedPattern = pattern,
                morsePulseOn = false
            )
        }

        morseJob = scope.launch(Dispatchers.Default) {
            val dotMs = 150L
            try {
                for (symbol in pattern) {
                    if (!isActive) break
                    when (symbol) {
                        '·' -> {
                            onTorchPulse(true)
                            _telemetry.update { it.copy(morsePulseOn = true) }
                            delay(dotMs)
                            onTorchPulse(false)
                            _telemetry.update { it.copy(morsePulseOn = false) }
                            delay(dotMs)
                        }
                        '—' -> {
                            onTorchPulse(true)
                            _telemetry.update { it.copy(morsePulseOn = true) }
                            delay(dotMs * 3)
                            onTorchPulse(false)
                            _telemetry.update { it.copy(morsePulseOn = false) }
                            delay(dotMs)
                        }
                        ' ' -> {
                            delay(dotMs * 2)
                        }
                        '/' -> {
                            delay(dotMs * 4)
                        }
                    }
                }
            } finally {
                onTorchPulse(false)
                _telemetry.update {
                    it.copy(
                        morseTransmitting = false,
                        morsePulseOn = false
                    )
                }
                onDone?.invoke("Optical Morse transmission complete for '$cleanText'.")
            }
        }
    }

    fun stopMorse() {
        morseJob?.cancel()
        morseJob = null
        onTorchPulse(false)
        _telemetry.update {
            it.copy(morseTransmitting = false, morsePulseOn = false)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                val lux = event.values[0].coerceAtLeast(0f)
                if (now - lastUiSyncMs >= 100L) {
                    _telemetry.update { it.copy(ambientLux = lux) }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                hasGeo = true
                val flux = sqrt(
                    geomagnetic[0] * geomagnetic[0] +
                        geomagnetic[1] * geomagnetic[1] +
                        geomagnetic[2] * geomagnetic[2]
                )
                val heading = computeHeadingDegrees()
                if (now - lastUiSyncMs >= 100L) {
                    _telemetry.update {
                        it.copy(
                            magneticFluxMicroTesla = flux,
                            compassHeadingDeg = heading
                        )
                    }
                }
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravity, 0, 3)
                hasGravity = true
                val magMs2 = sqrt(
                    gravity[0] * gravity[0] +
                        gravity[1] * gravity[1] +
                        gravity[2] * gravity[2]
                )
                val gForce = magMs2 / SensorManager.GRAVITY_EARTH
                val deltaG = abs(gForce - 1.0f)

                varianceAccum += deltaG
                varianceSamples += 1
                if (varianceSamples >= 8) {
                    val avgDelta = varianceAccum / varianceSamples
                    val stability = (100f - avgDelta * 120f).roundToInt().coerceIn(5, 100)
                    varianceAccum = 0f
                    varianceSamples = 0
                    val heading = computeHeadingDegrees()
                    lastUiSyncMs = now
                    _telemetry.update {
                        it.copy(
                            motionGForce = gForce,
                            stabilityPercent = stability,
                            compassHeadingDeg = heading,
                            batteryTempCelsius = readBatteryTemperatureC()
                        )
                    }
                }

                // Kinetic double-chop detection for hands-free Torch toggle
                if (_telemetry.value.chopFlashEnabled && deltaG > 1.35f) {
                    if (now - lastChopTriggerMs > 1800L) {
                        if (now - firstChopPeakMs in 180L..750L) {
                            lastChopTriggerMs = now
                            firstChopPeakMs = 0L
                            _telemetry.update {
                                it.copy(lastGestureTriggered = "Wrist Double-Chop → Torch")
                            }
                            onChopFlashTriggered()
                        } else {
                            firstChopPeakMs = now
                        }
                    }
                }
            }
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange
                val isNear = event.values[0] < minOf(maxRange, 5f)
                _telemetry.update { it.copy(proximityNear = isNear) }

                if (_telemetry.value.airWaveEnabled) {
                    if (isNear && !wasNear) {
                        nearEnteredMs = now
                    } else if (!isNear && wasNear) {
                        val hoverDuration = now - nearEnteredMs
                        if (hoverDuration in 40L..900L && now - lastAirGestureMs > 1400L) {
                            lastAirGestureMs = now
                            val action = _telemetry.value.airGestureTarget
                            _telemetry.update {
                                it.copy(lastGestureTriggered = "Air-Wave → ${action.label}")
                            }
                            onAirGestureTriggered(action)
                        }
                    }
                }
                wasNear = isNear
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun computeHeadingDegrees(): Float {
        if (!hasGravity || !hasGeo) return _telemetry.value.compassHeadingDeg
        val r = FloatArray(9)
        val i = FloatArray(9)
        if (!SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
            return _telemetry.value.compassHeadingDeg
        }
        val orientation = FloatArray(3)
        SensorManager.getOrientation(r, orientation)
        val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        return ((deg + 360f) % 360f)
    }

    private fun readBatteryTemperatureC(): Float {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return 0f
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        return tenths / 10f
    }

    fun release() {
        unregisterHardwareListeners()
        stopMorse()
        scope.cancel()
    }

    companion object {
        private val MORSE_TABLE = mapOf(
            'A' to "·—", 'B' to "—···", 'C' to "—·—·", 'D' to "—··",
            'E' to "·", 'F' to "··—·", 'G' to "——·", 'H' to "····",
            'I' to "··", 'J' to "·———", 'K' to "—·—", 'L' to "·—··",
            'M' to "——", 'N' to "—·", 'O' to "———", 'P' to "·——·",
            'Q' to "——·—", 'R' to "·—·", 'S' to "···", 'T' to "—",
            'U' to "··—", 'V' to "···—", 'W' to "·——", 'X' to "—··—",
            'Y' to "—·——", 'Z' to "——··",
            '0' to "—————", '1' to "·————", '2' to "··———", '3' to "···——",
            '4' to "····—", '5' to "·····", '6' to "—····", '7' to "——···",
            '8' to "———··", '9' to "————·"
        )

        fun encodeToMorse(input: String): String {
            val words = input.uppercase(Locale.ROOT).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return words.joinToString(" / ") { word ->
                word.mapNotNull { ch -> MORSE_TABLE[ch] }.joinToString(" ")
            }
        }
    }
}
