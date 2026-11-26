package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LifeCompassAnalysis
import com.example.data.ProactiveLifeGuidance
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.NeuralViolet

private data class DilemmaPreset(
    val tagSuffix: String,
    val label: String,
    val prompt: String
)

private val QUICK_DILEMMA_PRESETS = listOf(
    DilemmaPreset(
        tagSuffix = "anger",
        label = "गुस्सा / जल्दबाज़ी",
        prompt = "मुझे अभी बहुत गुस्सा आ रहा है, समझ नहीं आ रहा तुरंत जवाब दूँ या रुकूँ?"
    ),
    DilemmaPreset(
        tagSuffix = "career",
        label = "करियर / भविष्य",
        prompt = "मैं करियर और पढ़ाई को लेकर कन्फ्यूज हूँ, आगे क्या सही राह होगी?"
    ),
    DilemmaPreset(
        tagSuffix = "stress",
        label = "तनाव / मन भारी",
        prompt = "आज मन बहुत परेशान और तनाव में है, खुद को संभालकर सही दिशा कैसे लूँ?"
    ),
    DilemmaPreset(
        tagSuffix = "habit",
        label = "आदत व फोकस",
        prompt = "मेरा ध्यान बार-बार भटकता है और काम टलता है — अनुशासन कैसे बनाऊँ?"
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LifeCompassScreen(
    viewModel: LeonViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val uiState by viewModel.lifeCompassState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (uiState.proactiveGuidance == null) {
            viewModel.refreshProactiveRightPath(speakAloud = false)
        }
    }

    LaunchedEffect(uiState.currentAnalysis?.createdAtMillis) {
        if (uiState.currentAnalysis != null) {
            listState.animateScrollToItem(3)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("compass_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Item 0: Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("compass_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to LEON HUD",
                        tint = ArcCyan
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SAHI RAAH • LIFE COMPASS",
                        style = MaterialTheme.typography.headlineSmall,
                        color = ArcCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("compass_screen_title")
                    )
                    Text(
                        text = "इंसानी समझ • भावनात्मक संतुलन • अपने से सही दिशा",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                CompassEmblem(modifier = Modifier.size(36.dp))
            }
        }

        // Item 1: Proactive Self-Thinking Card ("अपने से सोच समझकर सही राह")
        item {
            ProactiveGuidanceCard(
                guidance = uiState.proactiveGuidance,
                isAutoThinking = uiState.isAutoThinking,
                onRefresh = { viewModel.refreshProactiveRightPath(speakAloud = false) },
                onStartFocusTimer = { mins -> viewModel.startCompassFocusTimer(mins) },
                onAddRecommendedTask = { step -> viewModel.addCompassStepAsTask(step) },
                onSpeakGuidance = { text -> viewModel.speakLifeCompassSummary(text) }
            )
        }

        // Item 2: Ask Dilemma / Think Like Human Input Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = NeuralViolet
                        )
                        Text(
                            text = "दिल या दिमाग़ की उलझन साझा करें",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QUICK_DILEMMA_PRESETS.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    viewModel.updateLifeCompassQuery(preset.prompt)
                                },
                                label = {
                                    Text(
                                        text = preset.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = HudSurfaceElevated,
                                    labelColor = ArcCyan
                                ),
                                border = BorderStroke(1.dp, HudBorder),
                                modifier = Modifier.testTag("compass_preset_${preset.tagSuffix}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.queryInput,
                        onValueChange = { viewModel.updateLifeCompassQuery(it) },
                        label = { Text("अपनी परिस्थिति, दुविधा या सवाल लिखें...") },
                        placeholder = { Text("जैसे: क्या मुझे तुरंत रिएक्ट करना चाहिए या धैर्य रखना सही रहेगा?") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = HudBorder,
                            focusedContainerColor = HudSurfaceElevated,
                            unfocusedContainerColor = HudSurfaceElevated
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("compass_input")
                    )

                    Button(
                        onClick = { viewModel.deliberateOnLifeCompass(speakAloud = true) },
                        enabled = !uiState.isThinking && uiState.queryInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ArcCyan,
                            contentColor = DeepObsidian
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("compass_analyze_button")
                    ) {
                        if (uiState.isThinking) {
                            CircularProgressIndicator(
                                color = DeepObsidian,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(8.dp))
                            Text("LEON इंसान की तरह गहराई से सोच रहा है...")
                        } else {
                            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "सोच समझकर सही राह बताओ",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Item 3: Full Empathetic Human Reasoning Result Card
        uiState.currentAnalysis?.let { analysis ->
            item {
                LifeCompassResultCard(
                    analysis = analysis,
                    onSpeakSummary = { viewModel.speakLifeCompassSummary(analysis.spokenSummary) },
                    onAddStepToTasks = { step -> viewModel.addCompassStepAsTask(step) }
                )
            }
        }
    }
}

@Composable
private fun ProactiveGuidanceCard(
    guidance: ProactiveLifeGuidance?,
    isAutoThinking: Boolean,
    onRefresh: () -> Unit,
    onStartFocusTimer: (Int) -> Unit,
    onAddRecommendedTask: (String) -> Unit,
    onSpeakGuidance: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = EmeraldTelemetry.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .testTag("compass_proactive_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldTelemetry
                    )
                    Text(
                        text = "स्वयं सोची गई सही राह (Auto-Think)",
                        style = MaterialTheme.typography.labelLarge,
                        color = EmeraldTelemetry,
                        fontWeight = FontWeight.Bold
                    )
                }
                guidance?.let {
                    Surface(
                        color = EmeraldTelemetry.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = it.toneBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldTelemetry,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (guidance != null) {
                Text(
                    text = guidance.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = guidance.observation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = HudSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldTelemetry.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = guidance.rightPathNudge,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRefresh,
                        enabled = !isAutoThinking,
                        border = BorderStroke(1.dp, ArcCyan),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compass_auto_think_button")
                    ) {
                        if (isAutoThinking) {
                            CircularProgressIndicator(
                                color = ArcCyan,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.size(6.dp))
                        Text("Auto-Think Again", color = ArcCyan, maxLines = 1)
                    }

                    IconButton(
                        onClick = { onSpeakGuidance("${guidance.headline}. ${guidance.rightPathNudge}") },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("compass_proactive_speak_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Speak proactive right path aloud",
                            tint = EmeraldTelemetry
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onStartFocusTimer(guidance.suggestedFocusMinutes) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldTelemetry,
                            contentColor = DeepObsidian
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compass_proactive_timer_button")
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("${guidance.suggestedFocusMinutes}m Focus", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onAddRecommendedTask(guidance.recommendedStep) },
                        border = BorderStroke(1.dp, EmeraldTelemetry),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compass_proactive_add_task_button")
                    ) {
                        Icon(Icons.Rounded.AddTask, contentDescription = null, tint = EmeraldTelemetry, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Add Step", color = EmeraldTelemetry, maxLines = 1)
                    }
                }
            } else {
                Text(
                    text = "LEON आपकी दिनचर्या और ज़रूरतों का विश्लेषण कर रहा है...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LifeCompassResultCard(
    analysis: LifeCompassAnalysis,
    onSpeakSummary: () -> Unit,
    onAddStepToTasks: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.65f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .testTag("compass_result_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = NeuralViolet.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NeuralViolet)
                ) {
                    Text(
                        text = analysis.emotionalTone,
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                OutlinedButton(
                    onClick = onSpeakSummary,
                    border = BorderStroke(1.dp, ArcCyan),
                    modifier = Modifier.testTag("compass_speak_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Speak Advice",
                        tint = ArcCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Speak Advice", color = ArcCyan)
                }
            }

            // Emotional Understanding
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = NeuralViolet, modifier = Modifier.size(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "इंसानी समझ (Empathy)",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeuralViolet,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = analysis.emotionalUnderstanding,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Pros & Cons Reasoning
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.Balance, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "दोनों पहलुओं का विवेक (Pros & Cons)",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = analysis.prosConsAnalysis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Highlighted Right Path Decision
            Surface(
                color = EmeraldTelemetry.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, EmeraldTelemetry),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "सही राह (THE RIGHT PATH)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldTelemetry,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = analysis.rightPathDecision,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Gentle Conscience Warning
            if (analysis.gentleWarning.isNotBlank()) {
                Surface(
                    color = AmberCaution.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AmberCaution.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = AmberCaution, modifier = Modifier.size(18.dp))
                        Text(
                            text = analysis.gentleWarning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 3 Actionable Steps with 1-Tap Add to Tasks
            Text(
                text = "अगले ठोस कदम (1-Tap Add to Daily Tasks)",
                style = MaterialTheme.typography.labelMedium,
                color = ArcCyan,
                fontWeight = FontWeight.Bold
            )

            analysis.actionableSteps.forEachIndexed { idx, step ->
                Surface(
                    color = HudSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${idx + 1}. $step",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { onAddStepToTasks(step) },
                            border = BorderStroke(1.dp, EmeraldTelemetry),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("compass_add_task_$idx")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddTask,
                                contentDescription = "Add step to daily tasks",
                                tint = EmeraldTelemetry,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.size(4.dp))
                            Text("Add Task", color = EmeraldTelemetry, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompassEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(
            color = ArcCyan.copy(alpha = 0.65f),
            radius = radius * 0.9f,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx())
        )
        val northNeedle = Path().apply {
            moveTo(cx, cy - radius * 0.68f)
            lineTo(cx - radius * 0.22f, cy)
            lineTo(cx + radius * 0.22f, cy)
            close()
        }
        val southNeedle = Path().apply {
            moveTo(cx, cy + radius * 0.68f)
            lineTo(cx - radius * 0.22f, cy)
            lineTo(cx + radius * 0.22f, cy)
            close()
        }
        drawPath(northNeedle, color = EmeraldTelemetry)
        drawPath(southNeedle, color = NeuralViolet.copy(alpha = 0.7f))
    }
}
