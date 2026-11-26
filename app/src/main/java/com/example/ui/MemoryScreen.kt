package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.AssistantLanguage
import com.example.data.CloudSyncState
import com.example.data.MemoryCategory
import com.example.data.MemoryItem
import com.example.data.UiState
import com.example.data.UserPreferences
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.EmeraldTelemetry
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.NeuralViolet
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

private const val AUTH_TAG = "Auth"

@Composable
fun MemoryScreen(
    viewModel: LeonViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val prefsState by viewModel.preferencesState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val alwaysReadyRunning by viewModel.alwaysReadyRunning.collectAsStateWithLifecycle()
    val cloudSyncState by viewModel.cloudSyncState.collectAsStateWithLifecycle()

    val prefs = (prefsState as? UiState.Success)?.data ?: UserPreferences()
    val memories = (memoriesState as? UiState.Success)?.data ?: emptyList()

    var speedSlider by remember(prefs.voiceSpeed) { mutableFloatStateOf(prefs.voiceSpeed.toFloat()) }
    var ownerNameInput by remember(prefs.ownerName) { mutableStateOf(prefs.ownerName) }
    var taglineInput by remember(prefs.creatorTagline) { mutableStateOf(prefs.creatorTagline) }
    var showAddMemory by remember { mutableStateOf(false) }
    var googleSignInLoading by remember { mutableStateOf(false) }

    val alwaysReadyPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.refreshTelemetry()
        val micOk = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micOk) {
            viewModel.toggleAlwaysReadyMode(true)
        }
    }

    LaunchedEffect(prefs.ownerName) {
        if (prefs.ownerName.isNotBlank() && ownerNameInput.isBlank()) {
            ownerNameInput = prefs.ownerName
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("memory_list"),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)) {
                Text(
                    text = "ALI MEMORY & FOREVER IDENTITY",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ArcCyan
                )
                Text(
                    text = "एक बार बता दें तो हमेशा के लिए याद रखे + नए डाउनलोड पर आपका नाम बताए",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Forever Owner & Creator Identity + Google Cloud Sync Card
        item {
            ForeverOwnerIdentityCard(
                ownerNameInput = ownerNameInput,
                onOwnerNameChange = { ownerNameInput = it },
                taglineInput = taglineInput,
                onTaglineChange = { taglineInput = it },
                savedOwnerName = prefs.ownerName,
                cloudSyncState = cloudSyncState,
                googleSignInLoading = googleSignInLoading,
                onSaveOwnerIdentity = {
                    viewModel.saveOwnerIdentity(ownerNameInput, taglineInput, publishGlobally = true)
                },
                onPublishGlobally = { viewModel.publishCreatorStampGlobally() },
                onSyncNow = { viewModel.syncCloudVaultNow() },
                onGoogleSignIn = {
                    googleSignInLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onAuthSuccess = {
                            googleSignInLoading = false
                            viewModel.onGoogleSignInSuccess()
                        },
                        onAuthError = { msg ->
                            googleSignInLoading = false
                            viewModel.reportError(msg)
                        },
                        onAuthCancelled = {
                            googleSignInLoading = false
                        }
                    )
                },
                onSignOut = { viewModel.signOutCloud(credentialManager) }
            )
        }

        // Always-Ready & Voice Preferences
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = HudSurface),
                border = BorderStroke(1.dp, HudBorder),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LEON AI Always-Ready Mode",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Wake word \"Hey Leon\" via foreground service",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = alwaysReadyRunning,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    val perms = buildList {
                                        add(Manifest.permission.RECORD_AUDIO)
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }.toTypedArray()
                                    alwaysReadyPermissionsLauncher.launch(perms)
                                } else {
                                    viewModel.toggleAlwaysReadyMode(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = EmeraldTelemetry
                            ),
                            modifier = Modifier.testTag("always_ready_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lock-Screen Mode (लॉक के बाद भी काम)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Keep \"Hey Leon\", voice calling, AI Q&A, music, torch & alert reading working while screen is off (WhatsApp/SMS stay unlock-protected)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.lockScreenModeEnabled,
                            onCheckedChange = { viewModel.updateLockScreenMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = ArcCyan
                            ),
                            modifier = Modifier.testTag("lock_screen_mode_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = { viewModel.triggerLockScreenBriefing() },
                        border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("lock_screen_briefing_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Speak Lock-Screen Briefing",
                            tint = ArcCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Speak Lock-Screen Alerts & Tasks", color = ArcCyan)
                    }

                    Text("Default Voice & Reply Language", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistantLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = prefs.defaultLanguage == lang.code,
                                onClick = { viewModel.updateLanguage(lang) },
                                label = { Text(lang.label) }
                            )
                        }
                    }

                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Voice Speed", style = MaterialTheme.typography.bodyMedium)
                            Text("${String.format(Locale.getDefault(), "%.1f", speedSlider)}x", style = MaterialTheme.typography.labelMedium, color = ArcCyan)
                        }
                        Slider(
                            value = speedSlider,
                            onValueChange = { speedSlider = it },
                            onValueChangeFinished = { viewModel.updateVoiceSpeed(speedSlider) },
                            valueRange = 0.7f..1.5f,
                            colors = SliderDefaults.colors(thumbColor = ArcCyan, activeTrackColor = ArcCyan)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Forever Auto-Memory (एक बार में याद)", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Save anything you tell ALI to remember permanently in one command",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.memoryConsentGranted,
                            onCheckedChange = { viewModel.updateMemoryConsent(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = NeuralViolet
                            ),
                            modifier = Modifier.testTag("memory_consent_switch")
                        )
                    }
                }
            }
        }

        // Memory Vault Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "FOREVER MEMORIES (${memories.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = ArcCyan
                )
                Button(
                    onClick = { showAddMemory = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = DeepObsidian)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Add Memory")
                }
            }
        }

        if (memories.isEmpty()) {
            item {
                Surface(
                    color = HudSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, HudBorder),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Psychology, contentDescription = null, tint = NeuralViolet)
                        Text(
                            text = "No saved memories yet. Say \"हमेशा याद रखो...\" or tap Add Memory.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(memories, key = { it.id }) { mem ->
                MemoryItemCard(memory = mem, onDelete = { viewModel.deleteMemory(mem.id) })
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.clearConversation() },
                border = BorderStroke(1.dp, HudBorder),
                modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
            ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Clear Chat History")
            }
        }
    }

    if (showAddMemory) {
        AddMemoryDialog(
            onDismiss = { showAddMemory = false },
            onSave = { k, v, c ->
                viewModel.addManualMemory(k, v, c)
                showAddMemory = false
            }
        )
    }
}

@Composable
private fun ForeverOwnerIdentityCard(
    ownerNameInput: String,
    onOwnerNameChange: (String) -> Unit,
    taglineInput: String,
    onTaglineChange: (String) -> Unit,
    savedOwnerName: String,
    cloudSyncState: CloudSyncState,
    googleSignInLoading: Boolean,
    onSaveOwnerIdentity: () -> Unit,
    onPublishGlobally: () -> Unit,
    onSyncNow: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val activeOwner = savedOwnerName.ifBlank { cloudSyncState.publishedCreatorName }
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, ArcCyan.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .testTag("owner_identity_card")
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Badge, contentDescription = null, tint = ArcCyan)
                    Text(
                        text = "FOREVER OWNER & CREATOR ID",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArcCyan
                    )
                }
                if (activeOwner.isNotBlank()) {
                    Surface(
                        color = EmeraldTelemetry.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EmeraldTelemetry)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Verified, contentDescription = null, tint = EmeraldTelemetry, modifier = Modifier.size(14.dp))
                            Text(
                                text = activeOwner,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldTelemetry,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Text(
                text = if (activeOwner.isNotBlank()) {
                    "LOCKED FOREVER: कोई भी यह ऐप डाउनलोड करे या पूछे \"तुमको किसने बनाया?\", ALI AI हमेशा \"$activeOwner\" जी का ही नाम बताएगी।"
                } else {
                    "एक बार अपना नाम डालें (या बोलें: \"मेरा नाम ... है\") — यह हमेशा के लिए सेव होगा और नए डाउनलोड करने वालों को आपका ही नाम बताएगा।"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = ownerNameInput,
                onValueChange = onOwnerNameChange,
                label = { Text("Your Name (Owner / App Creator)") },
                placeholder = { Text("e.g. Ali Zafar") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArcCyan,
                    unfocusedBorderColor = HudBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("owner_name_input")
            )

            OutlinedTextField(
                value = taglineInput,
                onValueChange = onTaglineChange,
                label = { Text("Creator Intro / Tagline (Optional)") },
                placeholder = { Text("e.g. Crafted by Boss — Always-Ready AI") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeuralViolet,
                    unfocusedBorderColor = HudBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("owner_tagline_input")
            )

            Button(
                onClick = onSaveOwnerIdentity,
                enabled = ownerNameInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ArcCyan, contentColor = DeepObsidian),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_owner_button")
            ) {
                Icon(Icons.Rounded.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Save Name Forever")
            }

            // Google Cloud Forever Vault & Global Broadcast Section
            Surface(
                color = HudSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, HudBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isSignedIn = !cloudSyncState.signedInUid.isNullOrBlank()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSignedIn) Icons.Rounded.CloudDone else Icons.Rounded.CloudUpload,
                            contentDescription = null,
                            tint = if (isSignedIn) EmeraldTelemetry else NeuralViolet,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isSignedIn) {
                                "Cloud Vault Connected: ${cloudSyncState.userEmail ?: cloudSyncState.displayName ?: "Google Account"}"
                            } else {
                                "Cloud Backup & Global Download Creator Stamp"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSignedIn) EmeraldTelemetry else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = if (isSignedIn) {
                            cloudSyncState.statusMessage.ifBlank {
                                "Memories & Owner Identity stay safe even if you delete/reinstall or switch phones."
                            }
                        } else {
                            "Sign in with Google to back up your Forever Memories across devices and publish your Creator Name globally to every new app download."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isSignedIn) {
                        Button(
                            onClick = onGoogleSignIn,
                            enabled = !googleSignInLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeuralViolet,
                                contentColor = DeepObsidian
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("google_signin_button")
                        ) {
                            if (googleSignInLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = DeepObsidian,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Sign in with Google to Sync Forever")
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onPublishGlobally,
                                enabled = activeOwner.isNotBlank() && !cloudSyncState.isSyncing,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldTelemetry,
                                    contentColor = DeepObsidian
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("publish_creator_button")
                            ) {
                                Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Publish Global")
                            }
                            OutlinedButton(
                                onClick = onSyncNow,
                                enabled = !cloudSyncState.isSyncing,
                                border = BorderStroke(1.dp, ArcCyan),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_now_button")
                            ) {
                                Text(if (cloudSyncState.isSyncing) "Syncing..." else "Sync Now", color = ArcCyan)
                            }
                        }
                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Sign out of Cloud", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
    scope.launch {
        try {
            val activity = context as? Activity
                ?: throw IllegalStateException("Activity context required for Google Sign-In.")
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type returned.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "No Google credentials available on device: ${e.message}", e)
            onAuthError("No Google account found on this device. Please add a Google account in Settings.")
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed")
        }
    }
}

@Composable
private fun MemoryItemCard(
    memory: MemoryItem,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HudSurface),
        border = BorderStroke(1.dp, if (memory.isPermanent) ArcCyan.copy(alpha = 0.45f) else HudBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = memory.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = NeuralViolet
                    )
                    if (memory.isPermanent) {
                        Surface(
                            color = ArcCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "FOREVER",
                                style = MaterialTheme.typography.labelSmall,
                                color = ArcCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = memory.memoryKey,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = memory.memoryValue,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Forget memory", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AddMemoryDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, MemoryCategory) -> Unit
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MemoryCategory.FACT) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HudSurface,
        title = { Text("Add Forever Memory") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Label (e.g. Favorite Drink)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Detail (e.g. Adrak Chai at 5 PM)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MemoryCategory.entries.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(key, value, category) },
                enabled = key.isNotBlank() && value.isNotBlank()
            ) {
                Text("Save Forever")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
