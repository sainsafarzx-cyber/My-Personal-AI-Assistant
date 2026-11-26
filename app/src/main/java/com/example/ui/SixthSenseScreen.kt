package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AirGestureAction
import com.example.data.SixthSenseTelemetry
import com.example.sensors.SixthSenseEngine
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.NeuralViolet
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SixthSenseScreen(
    viewModel: LeonViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sense by viewModel.sixthSenseTelemetry.collectAsStateWithLifecycle()
    val aiReport by viewModel.sixthSenseReport.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanningEnvironment.collectAsStateWithLifecycle()

    var morseInput by rememberSaveable { mutableStateOf("SOS") }

    DisposableEffect(Unit) {
        viewModel.setRadarVisible(true)
        onDispose {
            viewModel.setRadarVisible(false)
        }
    }

    val micForDbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleAcousticDbMeter(true)
        } else {
            viewModel.reportError("Microphone permission required for Room Acoustic dB meter.")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("sixth_sense_list"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                Text(
                    text = "NEURAL SIXTH SENSE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ArcCyan,
                    modifier = Modifier.testTag("sixth_sense_title")
                )
                Text(
                    text = "Live 360° sensor radar, touchless air gestures & Optical Morse Beacon",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Holographic Polar Radar Visualizer + Quick AI Scan Trigger
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PolarRadarDial(sense = sense)

                    if (sense.lastGestureTriggered.isNotBlank()) {
                        Surface(
                            color = EmeraldTelemetry.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Last Gesture: ${sense.lastGestureTriggered}",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldTelemetry,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { viewModel.runSixthSenseAiScan() },
                        enabled = !isScanning,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArcCyan,
                            contentColor = DeepObsidian
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("sixth_sense_scan_button")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                color = DeepObsidian,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(if (isScanning) "ALI Reading Physical World..." else "ALI Sixth-Sense AI Environment Scan")
                    }

                    AnimatedVisibility(visible = !aiReport.isNullOrBlank()) {
                        Surface(
                            color = NeuralViolet.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, NeuralViolet),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = aiReport.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live Real Hardware Sensor Telemetry Grid
        item {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val luxLabel = when {
                        !sense.lightAvailable -> "Sensor N/A"
                        sense.ambientLux == null -> "Reading..."
                        sense.ambientLux!! < 12f -> "${sense.ambientLux!!.toInt()} Lux (Dark)"
                        sense.ambientLux!! < 350f -> "${sense.ambientLux!!.toInt()} Lux (Indoor)"
                        else -> "${sense.ambientLux!!.toInt()} Lux (Bright)"
                    }
                    SensorMetricCard(
                        icon = Icons.Rounded.LightMode,
                        title = "Ambient Light",
                        value = luxLabel,
                        accent = AmberCaution,
                        modifier = Modifier.weight(1f)
                    )

                    val emfLabel = if (sense.magnetometerAvailable) {
                        "${sense.compassHeadingDeg.toInt()}° • ${sense.magneticFluxMicroTesla.toInt()} µT"
                    } else {
                        "Compass N/A"
                    }
                    SensorMetricCard(
                        icon = Icons.Rounded.Explore,
                        title = "Compass & EMF",
                        value = emfLabel,
                        accent = ArcCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val motionLabel = if (sense.accelerometerAvailable) {
                        "${"%.2f".format(sense.motionGForce)}g • ${sense.stabilityPercent}% Steady"
                    } else {
                        "Accel N/A"
                    }
                    SensorMetricCard(
                        icon = Icons.Rounded.Vibration,
                        title = "Motion & Stability",
                        value = motionLabel,
                        accent = EmeraldTelemetry,
                        modifier = Modifier.weight(1f)
                    )

                    SensorMetricCard(
                        icon = Icons.Rounded.Thermostat,
                        title = "Battery Thermal",
                        value = if (sense.batteryTempCelsius > 0f) "${sense.batteryTempCelsius}°C" else "Reading...",
                        accent = NeuralViolet,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Live Acoustic Room Decibel Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = HudSurface),
                    border = BorderStroke(1.dp, if (sense.audioMeterActive) EmeraldTelemetry else HudBorder),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "Acoustic Room Decibel Meter",
                                tint = if (sense.audioMeterActive) EmeraldTelemetry else ArcCyan
                            )
                            Column {
                                Text("Live Acoustic Room dB", style = MaterialTheme.typography.titleMedium)
                                val dbText = when {
                                    !sense.audioMeterActive -> "Off — tap switch to sample real room sound"
                                    sense.ambientDbSpl == null -> "Calibrating microphone RMS..."
                                    sense.ambientDbSpl!! < 45f -> "${sense.ambientDbSpl!!.toInt()} dB (Quiet Focus)"
                                    sense.ambientDbSpl!! < 68f -> "${sense.ambientDbSpl!!.toInt()} dB (Conversational)"
                                    else -> "${sense.ambientDbSpl!!.toInt()} dB (Loud Noise)"
                                }
                                Text(
                                    text = dbText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = sense.audioMeterActive,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.toggleAcousticDbMeter(true)
                                    } else {
                                        micForDbLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    viewModel.toggleAcousticDbMeter(false)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = DeepObsidian, checkedTrackColor = EmeraldTelemetry),
                            modifier = Modifier.testTag("acoustic_db_switch")
                        )
                    }
                }
            }
        }

        // Touchless Air-Wave & Wrist Double-Chop Gesture Studio
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "TOUCHLESS AIR GESTURES",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PanTool, contentDescription = null, tint = ArcCyan)
                            Column {
                                Text("Air-Wave Proximity Control", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (sense.proximityAvailable) {
                                        "Wave hand above top earpiece without touching screen"
                                    } else {
                                        "Proximity sensor unavailable on this device"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = sense.airWaveEnabled,
                            enabled = sense.proximityAvailable,
                            onCheckedChange = { viewModel.setAirWaveEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = DeepObsidian, checkedTrackColor = ArcCyan),
                            modifier = Modifier.testTag("air_wave_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AirGestureAction.entries.forEach { target ->
                            FilterChip(
                                selected = sense.airGestureTarget == target,
                                onClick = { viewModel.setAirGestureTarget(target) },
                                label = {
                                    Text(
                                        text = when (target) {
                                            AirGestureAction.TOGGLE_FLASHLIGHT -> "Torch"
                                            AirGestureAction.PLAY_PAUSE_MUSIC -> "Music"
                                            AirGestureAction.TRIGGER_VOICE -> "Voice"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ArcCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ArcCyan
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Sensors, contentDescription = null, tint = NeuralViolet)
                            Column {
                                Text("Kinetic Double-Chop Torch", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = if (sense.accelerometerAvailable) {
                                        "Brisk double-flick toggles camera flashlight"
                                    } else {
                                        "Accelerometer unavailable on this device"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = sense.chopFlashEnabled,
                            enabled = sense.accelerometerAvailable,
                            onCheckedChange = { viewModel.setChopFlashEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = DeepObsidian, checkedTrackColor = NeuralViolet),
                            modifier = Modifier.testTag("chop_flash_switch")
                        )
                    }
                }
            }
        }

        // Optical Morse Code Flashlight Beacon Transceiver
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(
                    width = if (sense.morsePulseOn) 2.dp else 1.dp,
                    color = if (sense.morsePulseOn) AmberCaution else HudBorder
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FlashlightOn,
                                contentDescription = "Optical Morse Code Beacon",
                                tint = if (sense.morsePulseOn) AmberCaution else ArcCyan
                            )
                            Text(
                                text = "OPTICAL MORSE TORCH BEACON",
                                style = MaterialTheme.typography.labelMedium,
                                color = ArcCyan
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (sense.morsePulseOn) AmberCaution
                                    else if (sense.morseTransmitting) ArcCyan.copy(alpha = 0.35f)
                                    else HudSurfaceElevated
                                )
                        )
                    }

                    Text(
                        text = "Converts any message into live ITU Morse pulses through your camera flashlight & HUD beacon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val previewPattern = SixthSenseEngine.encodeToMorse(morseInput)
                    if (previewPattern.isNotBlank()) {
                        Surface(
                            color = HudSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HudBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = previewPattern,
                                style = MaterialTheme.typography.titleMedium,
                                color = AmberCaution,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = morseInput,
                        onValueChange = { if (it.length <= 24) morseInput = it },
                        label = { Text("Signal Message (A-Z, 0-9)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = HudBorder
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("morse_text_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SOS", "HELP", "LEON").forEach { preset ->
                            OutlinedButton(
                                onClick = { morseInput = preset },
                                border = BorderStroke(1.dp, HudBorder),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(preset, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.sendOpticalMorse(morseInput) },
                            enabled = !sense.morseTransmitting && morseInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberCaution,
                                contentColor = DeepObsidian
                            ),
                            modifier = Modifier.weight(1f).testTag("morse_transmit_button")
                        ) {
                            Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (sense.morseTransmitting) "Transmitting..." else "Flash Morse")
                        }

                        if (sense.morseTransmitting) {
                            OutlinedButton(
                                onClick = { viewModel.stopOpticalMorse() },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f).testTag("morse_stop_button")
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.size(6.dp))
                                Text("Stop", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolarRadarDial(sense: SixthSenseTelemetry) {
    val transition = rememberInfiniteTransition(label = "sixth_sense_radar")
    val sweepAngle: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(196.dp)
            .drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f * 0.92f
                val ringStroke = Stroke(width = 1.4.dp.toPx())
                val beamStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                val emfStrength = (sense.magneticFluxMicroTesla / 120f).coerceIn(0.15f, 1f)
                val luxRatio = ((sense.ambientLux ?: 120f) / 1000f).coerceIn(0.1f, 1f)
                val sweepBrush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        ArcCyan.copy(alpha = 0.08f),
                        ArcCyan.copy(alpha = 0.42f)
                    ),
                    center = center
                )

                onDrawBehind {
                    drawCircle(brush = sweepBrush, radius = maxR * 0.94f, center = center)
                    drawCircle(color = HudBorder, radius = maxR, style = ringStroke, center = center)
                    drawCircle(color = ArcCyan.copy(alpha = 0.3f), radius = maxR * 0.66f, style = ringStroke, center = center)
                    drawCircle(color = NeuralViolet.copy(alpha = 0.35f), radius = maxR * 0.36f, style = ringStroke, center = center)

                    // Crosshair lines
                    drawLine(
                        color = HudBorder.copy(alpha = 0.6f),
                        start = Offset(center.x - maxR, center.y),
                        end = Offset(center.x + maxR, center.y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = HudBorder.copy(alpha = 0.6f),
                        start = Offset(center.x, center.y - maxR),
                        end = Offset(center.x, center.y + maxR),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Sweeping radar scanner line
                    rotate(sweepAngle.value, pivot = center) {
                        drawLine(
                            color = ArcCyan,
                            start = center,
                            end = Offset(center.x + maxR * 0.94f, center.y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Real magnetic flux vector dot on radar plane
                    val rad = Math.toRadians((sense.compassHeadingDeg - 90f).toDouble())
                    val dotRadius = maxR * emfStrength * 0.85f
                    val dotOffset = Offset(
                        x = center.x + (dotRadius * cos(rad)).toFloat(),
                        y = center.y + (dotRadius * sin(rad)).toFloat()
                    )
                    drawCircle(color = EmeraldTelemetry, radius = 6.dp.toPx(), center = dotOffset)

                    // Ambient light arc gauge
                    drawArc(
                        color = AmberCaution,
                        startAngle = -90f,
                        sweepAngle = 360f * luxRatio,
                        useCenter = false,
                        style = beamStroke
                    )

                    // Morse optical core pulse
                    if (sense.morsePulseOn) {
                        drawCircle(
                            color = AmberCaution.copy(alpha = 0.48f),
                            radius = maxR * 0.35f,
                            center = center
                        )
                    }
                }
            }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (sense.morseTransmitting) "MORSE" else if (sense.magnetometerAvailable) "${sense.compassHeadingDeg.toInt()}°" else "RADAR",
                style = MaterialTheme.typography.titleLarge,
                color = if (sense.morsePulseOn) AmberCaution else ArcCyan,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (sense.sensorsActive) "6TH SENSE LIVE" else "STANDBY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SensorMetricCard(
    icon: ImageVector,
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurfaceElevated),
        border = BorderStroke(1.dp, HudBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(18.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
