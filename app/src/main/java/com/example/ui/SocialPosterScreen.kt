package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ActionType
import com.example.social.SocialPlatform
import com.example.social.SocialPostType
import com.example.ui.theme.AmberCaution
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.NeuralViolet
import java.util.Locale

@Composable
fun SocialPosterScreen(
    viewModel: LeonViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val socialState by viewModel.socialState.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshSocialPlatforms()
        viewModel.refreshTelemetry()
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onSocialMediaPicked(uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onSocialMediaPicked(uri)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("social_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Item 0: Header + Active Queue Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("social_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to LEON HUD",
                            tint = ArcCyan
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SOCIAL AUTO-POSTER",
                            style = MaterialTheme.typography.headlineSmall,
                            color = ArcCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("social_screen_title")
                        )
                        Text(
                            text = "Instagram • Facebook • WhatsApp Stories & Video Posts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                socialState.activeJob?.let { job ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ArcCyan.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, ArcCyan),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "ACTIVE QUEUE (${job.currentIndex + 1}/${job.platforms.size}): ${job.currentPlatform?.displayName ?: "Done"}",
                                style = MaterialTheme.typography.titleSmall,
                                color = ArcCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${job.postType.hindiLabel} • ${job.media.displayName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.launchNextSocialPlatform() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldTelemetry,
                                        contentColor = DeepObsidian
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("social_next_platform_button")
                                ) {
                                    Text(if (job.hasNextPlatform) "Post Next App" else "Complete Queue")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.clearSocialQueue() },
                                    border = BorderStroke(1.dp, HudBorder),
                                    modifier = Modifier.testTag("social_clear_queue_button")
                                ) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear Queue", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.size(4.dp))
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Item 1: Target Social Platforms
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "1. SELECT PLATFORMS (प्लेटफ़ॉर्म चुनें)",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SocialPlatform.entries.forEach { platform ->
                            val selected = platform in socialState.selectedPlatforms
                            val installed = socialState.installedPlatforms[platform]
                            val tag = "social_platform_${platform.wireValue}"
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleSocialPlatform(platform) },
                                label = {
                                    Column {
                                        Text(
                                            text = platform.displayName,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when (installed) {
                                                true -> "Installed"
                                                false -> "Not found"
                                                null -> "Checking"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) DeepObsidian.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ArcCyan,
                                    selectedLabelColor = DeepObsidian,
                                    selectedLeadingIconColor = DeepObsidian,
                                    containerColor = HudSurfaceElevated
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(tag)
                            )
                        }
                    }
                }
            }
        }

        // Item 2: Story / Video Post Type Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "2. POST MODE (स्टोरी या वीडियो पोस्ट)",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SocialPostType.entries.forEach { mode ->
                            val selected = socialState.postType == mode
                            val tag = if (mode == SocialPostType.STORY) "social_type_story" else "social_type_video_post"
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setSocialPostType(mode) },
                                label = {
                                    Column {
                                        Text(mode.label, style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            mode.hindiLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) DeepObsidian.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EmeraldTelemetry,
                                    selectedLabelColor = DeepObsidian,
                                    containerColor = HudSurfaceElevated
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(tag)
                            )
                        }
                    }
                }
            }
        }

        // Item 3: Zero-permission Android Photo Picker
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "3. PICK VIDEO OR PHOTO (मीडिया चुनें)",
                        style = MaterialTheme.typography.labelMedium,
                        color = ArcCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ArcCyan,
                                contentColor = DeepObsidian
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("social_pick_video_button")
                        ) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Pick Video")
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.7f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("social_pick_photo_button")
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Pick Photo", color = ArcCyan)
                        }
                    }

                    val staged = socialState.stagedMedia
                    Surface(
                        color = HudSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (staged != null) EmeraldTelemetry else HudBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (staged?.isVideo == true) Icons.Rounded.VideoLibrary else Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = if (staged != null) EmeraldTelemetry else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (staged != null) {
                                    val sizeKb = (staged.sizeBytes / 1024L).coerceAtLeast(1L)
                                    val sizeLabel = if (sizeKb >= 1024L) {
                                        String.format(Locale.US, "%.1f MB", sizeKb / 1024f)
                                    } else {
                                        "${sizeKb} KB"
                                    }
                                    "Staged ${if (staged.isVideo) "Video" else "Photo"}: ${staged.displayName} ($sizeLabel)"
                                } else {
                                    "No media selected yet. Pick a video or photo above (zero storage permission needed)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (staged != null) EmeraldTelemetry else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Item 4: Caption + AI Writer
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
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
                        Text(
                            text = "4. CAPTION & HASHTAGS",
                            style = MaterialTheme.typography.labelMedium,
                            color = ArcCyan
                        )
                        Button(
                            onClick = { viewModel.generateAiSocialCaption() },
                            enabled = !socialState.isGeneratingCaption,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeuralViolet,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("social_ai_caption_button")
                        ) {
                            if (socialState.isGeneratingCaption) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.size(6.dp))
                            Text("AI Caption")
                        }
                    }
                    OutlinedTextField(
                        value = socialState.caption,
                        onValueChange = { viewModel.updateSocialCaption(it) },
                        placeholder = {
                            Text("अपना कैप्शन लिखें या 'AI Caption' दबाएँ (क्लिपबोर्ड में भी कॉपी होगा)...")
                        },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcCyan,
                            unfocusedBorderColor = HudBorder,
                            focusedContainerColor = HudSurfaceElevated,
                            unfocusedContainerColor = HudSurfaceElevated
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("social_caption_input")
                    )
                }
            }
        }

        // Item 5: Hands-free Auto-Tap Accessibility Setting
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = AmberCaution)
                            Column {
                                Text(
                                    text = "Hands-Free Auto-Tap Publish",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (telemetry.accessibilityEnabled) {
                                        "LEON Automation Helper ACTIVE — Share / Story / Send बटन अपने आप दबाएगा"
                                    } else {
                                        "बिना टैप किए ऑटो-पोस्ट के लिए LEON Accessibility Helper ऑन करें"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (telemetry.accessibilityEnabled) EmeraldTelemetry else AmberCaution
                                )
                            }
                        }
                        Switch(
                            checked = socialState.autoTapEnabled,
                            onCheckedChange = { viewModel.setSocialAutoTapEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = EmeraldTelemetry
                            ),
                            modifier = Modifier.testTag("social_auto_tap_switch")
                        )
                    }
                    if (!telemetry.accessibilityEnabled) {
                        TextButton(
                            onClick = {
                                viewModel.openDeviceShortcut(
                                    ActionType.OPEN_SETTINGS,
                                    target = "accessibility"
                                )
                            },
                            modifier = Modifier.testTag("social_enable_accessibility_button")
                        ) {
                            Text("Enable Accessibility Helper", color = ArcCyan)
                        }
                    }
                }
            }
        }

        // Item 6: Launch Primary Auto-Post Queue Button
        item {
            Button(
                onClick = { viewModel.startSocialAutoPostQueue() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldTelemetry,
                    contentColor = DeepObsidian
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .height(54.dp)
                    .testTag("social_launch_post_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "AUTO-POST TO SELECTED APPS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Item 7: Live Activity Logs
        if (socialState.activityLogs.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = HudSurface),
                    border = BorderStroke(1.dp, HudBorder),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, tint = ArcCyan, modifier = Modifier.size(16.dp))
                            Text(
                                text = "AUTO-POST ACTIVITY LOG",
                                style = MaterialTheme.typography.labelMedium,
                                color = ArcCyan
                            )
                        }
                        socialState.activityLogs.forEach { entry ->
                            Text(
                                text = "• $entry",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
