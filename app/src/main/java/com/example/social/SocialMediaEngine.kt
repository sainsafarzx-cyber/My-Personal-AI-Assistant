package com.example.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import com.example.automation.ActionExecutionOutcome
import com.example.data.AssistantLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

enum class SocialPlatform(
    val wireValue: String,
    val displayName: String,
    val hindiName: String,
    val packageNames: List<String>
) {
    INSTAGRAM(
        wireValue = "instagram",
        displayName = "Instagram",
        hindiName = "इंस्टाग्राम",
        packageNames = listOf("com.instagram.android")
    ),
    FACEBOOK(
        wireValue = "facebook",
        displayName = "Facebook",
        hindiName = "फेसबुक",
        packageNames = listOf("com.facebook.katana", "com.facebook.lite")
    ),
    WHATSAPP(
        wireValue = "whatsapp",
        displayName = "WhatsApp",
        hindiName = "व्हाट्सएप",
        packageNames = listOf("com.whatsapp", "com.whatsapp.w4b")
    );

    companion object {
        fun fromWire(raw: String): SocialPlatform? = entries.firstOrNull {
            it.wireValue.equals(raw.trim(), ignoreCase = true) ||
                it.name.equals(raw.trim(), ignoreCase = true)
        }
    }
}

enum class SocialPostType(
    val wireValue: String,
    val label: String,
    val hindiLabel: String
) {
    STORY(
        wireValue = "story",
        label = "Story / Status",
        hindiLabel = "स्टोरी / स्टेटस"
    ),
    VIDEO_POST(
        wireValue = "video_post",
        label = "Video / Reel Post",
        hindiLabel = "वीडियो पोस्ट / रील"
    );

    companion object {
        fun fromWire(raw: String): SocialPostType = entries.firstOrNull {
            it.wireValue.equals(raw.trim(), ignoreCase = true) ||
                it.name.equals(raw.trim(), ignoreCase = true)
        } ?: STORY
    }
}

@Immutable
data class StagedSocialMedia(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val isVideo: Boolean,
    val sizeBytes: Long
)

@Immutable
data class SocialPostJob(
    val id: String,
    val platforms: List<SocialPlatform>,
    val currentIndex: Int = 0,
    val postType: SocialPostType,
    val caption: String,
    val autoTapEnabled: Boolean,
    val media: StagedSocialMedia,
    val stepArmedAtMillis: Long = System.currentTimeMillis()
) {
    val currentPlatform: SocialPlatform?
        get() = platforms.getOrNull(currentIndex)

    val hasNextPlatform: Boolean
        get() = currentIndex + 1 < platforms.size
}

@Immutable
data class SocialStudioState(
    val selectedPlatforms: Set<SocialPlatform> = setOf(
        SocialPlatform.INSTAGRAM,
        SocialPlatform.FACEBOOK,
        SocialPlatform.WHATSAPP
    ),
    val postType: SocialPostType = SocialPostType.STORY,
    val caption: String = "",
    val autoTapEnabled: Boolean = true,
    val stagedMedia: StagedSocialMedia? = null,
    val installedPlatforms: Map<SocialPlatform, Boolean> = emptyMap(),
    val isGeneratingCaption: Boolean = false,
    val activeJob: SocialPostJob? = null,
    val activityLogs: List<String> = emptyList()
)

object SocialAutoPosterBridge {
    private const val STEP_TTL_MILLIS = 60_000L

    private val _activeJob = MutableStateFlow<SocialPostJob?>(null)
    val activeJob: StateFlow<SocialPostJob?> = _activeJob.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _lastAutoCompletedStepId = MutableStateFlow("")
    val lastAutoCompletedStepId: StateFlow<String> = _lastAutoCompletedStepId.asStateFlow()

    private val _openStudioRequests = MutableStateFlow<Triple<String, String, Int>?>(null)
    val openStudioRequests: StateFlow<Triple<String, String, Int>?> = _openStudioRequests.asStateFlow()

    fun requestOpenStudio(target: String, payload: String) {
        val seq = (_openStudioRequests.value?.third ?: 0) + 1
        _openStudioRequests.value = Triple(target, payload, seq)
    }

    fun armJob(job: SocialPostJob) {
        _activeJob.value = job.copy(stepArmedAtMillis = System.currentTimeMillis())
        appendLog("Queue armed (${job.currentIndex + 1}/${job.platforms.size}): ${job.currentPlatform?.displayName} ${job.postType.label}")
    }

    fun isStepArmedForPackage(packageName: String): SocialPostJob? {
        val job = _activeJob.value ?: return null
        if (!job.autoTapEnabled) return null
        val platform = job.currentPlatform ?: return null
        if (System.currentTimeMillis() - job.stepArmedAtMillis > STEP_TTL_MILLIS) {
            return null
        }
        return if (packageName in platform.packageNames) job else null
    }

    fun markCurrentStepAutoPublished(platform: SocialPlatform, detail: String) {
        val job = _activeJob.value ?: return
        if (job.currentPlatform != platform) return
        _lastAutoCompletedStepId.value = "${job.id}_${job.currentIndex}"
        appendLog("${platform.displayName}: $detail")
        if (job.hasNextPlatform) {
            _activeJob.value = job.copy(
                currentIndex = job.currentIndex + 1,
                stepArmedAtMillis = System.currentTimeMillis()
            )
        } else {
            _activeJob.value = null
            appendLog("All queued social platforms processed.")
        }
    }

    fun advanceStepManually(): SocialPostJob? {
        val job = _activeJob.value ?: return null
        return if (job.hasNextPlatform) {
            val nextJob = job.copy(
                currentIndex = job.currentIndex + 1,
                stepArmedAtMillis = System.currentTimeMillis()
            )
            _activeJob.value = nextJob
            appendLog("Moved to next platform: ${nextJob.currentPlatform?.displayName}")
            nextJob
        } else {
            _activeJob.value = null
            appendLog("Social posting queue completed.")
            null
        }
    }

    fun clearJob() {
        _activeJob.value = null
        appendLog("Social posting queue cleared.")
    }

    fun appendLog(entry: String) {
        val clean = entry.trim()
        if (clean.isEmpty()) return
        _logs.value = (listOf(clean) + _logs.value).take(16)
    }
}

class SocialMediaEngine(private val context: Context) {

    fun checkInstalledPlatforms(): Map<SocialPlatform, Boolean> =
        SocialPlatform.entries.associateWith { isPlatformInstalled(it) }

    fun isPlatformInstalled(platform: SocialPlatform): Boolean =
        resolveInstalledPackage(platform) != null

    fun resolveInstalledPackage(platform: SocialPlatform): String? {
        val pm = context.packageManager
        for (pkg in platform.packageNames) {
            val installed = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                true
            }.getOrDefault(false)
            if (installed) return pkg
        }
        return null
    }

    fun stageMediaFromUri(sourceUri: Uri): Result<StagedSocialMedia> {
        return runCatching {
            val resolver = context.contentResolver
            val detectedMime = resolver.getType(sourceUri).orEmpty().ifBlank { "video/mp4" }
            val isVideo = detectedMime.startsWith("video/", ignoreCase = true)
            val originalName = queryDisplayName(sourceUri).orEmpty()
            val extension = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(detectedMime)
                ?.takeIf { it.isNotBlank() }
                ?: if (isVideo) "mp4" else "jpg"

            val stageDir = File(context.cacheDir, "social_media").apply { mkdirs() }
            stageDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach {
                runCatching { it.delete() }
            }

            val destFile = File(stageDir, "leon_social_${UUID.randomUUID().toString().take(8)}.$extension")
            resolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 16 * 1024)
                }
            } ?: error("Selected media stream could not be opened.")

            val providerUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
            }.getOrElse {
                Uri.fromFile(destFile)
            }

            val label = originalName.ifBlank { destFile.name }
            StagedSocialMedia(
                uri = providerUri,
                mimeType = detectedMime,
                displayName = label,
                isVideo = isVideo,
                sizeBytes = destFile.length()
            )
        }
    }

    fun copyCaptionToClipboard(caption: String) {
        val trimmed = caption.trim()
        if (trimmed.isEmpty()) return
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("LEON Social Caption", trimmed))
        }
    }

    fun launchPlatformStep(
        platform: SocialPlatform,
        postType: SocialPostType,
        media: StagedSocialMedia,
        caption: String
    ): ActionExecutionOutcome {
        val pkg = resolveInstalledPackage(platform)
            ?: return ActionExecutionOutcome(
                false,
                "बॉस, इस फ़ोन में ${platform.hindiName} (${platform.displayName}) इंस्टॉल नहीं है।"
            )

        copyCaptionToClipboard(caption)
        grantMediaReadUri(pkg, media.uri)

        return when (platform) {
            SocialPlatform.INSTAGRAM -> launchInstagram(pkg, postType, media, caption)
            SocialPlatform.FACEBOOK -> launchFacebook(pkg, postType, media, caption)
            SocialPlatform.WHATSAPP -> launchWhatsAppMedia(pkg, postType, media, caption)
        }
    }

    private fun launchInstagram(
        pkg: String,
        postType: SocialPostType,
        media: StagedSocialMedia,
        caption: String
    ): ActionExecutionOutcome {
        if (postType == SocialPostType.STORY) {
            val storyIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setPackage(pkg)
                setDataAndType(media.uri, media.mimeType)
                putExtra("interactive_asset_uri", media.uri)
                putExtra("content_url", caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(storyIntent)) {
                return ActionExecutionOutcome(
                    true,
                    "Instagram Story एडिटर खुल गया है${if (caption.isNotBlank()) " (कैप्शन कॉपी हो गया)" else ""}।"
                )
            }
        } else {
            val feedIntent = Intent("com.instagram.share.ADD_TO_FEED").apply {
                setPackage(pkg)
                setDataAndType(media.uri, media.mimeType)
                putExtra(Intent.EXTRA_STREAM, media.uri)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(feedIntent)) {
                return ActionExecutionOutcome(true, "Instagram वीडियो/रील पोस्ट एडिटर खुल गया है।")
            }
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            setPackage(pkg)
            type = media.mimeType
            putExtra(Intent.EXTRA_STREAM, media.uri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tryStartActivity(sendIntent)) {
            ActionExecutionOutcome(true, "Instagram शेयर एडिटर (${postType.hindiLabel}) खुल गया है।")
        } else {
            ActionExecutionOutcome(false, "Instagram मीडिया कम्पोज़र नहीं खुल सका। कृपया Instagram अपडेट चेक करें।")
        }
    }

    private fun launchFacebook(
        pkg: String,
        postType: SocialPostType,
        media: StagedSocialMedia,
        caption: String
    ): ActionExecutionOutcome {
        if (postType == SocialPostType.STORY) {
            val fbStoryIntent = Intent("com.facebook.stories.ADD_TO_STORY").apply {
                setPackage(pkg)
                setDataAndType(media.uri, media.mimeType)
                putExtra("com.facebook.platform.extra.APPLICATION_ID", context.packageName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStartActivity(fbStoryIntent)) {
                return ActionExecutionOutcome(true, "Facebook Story एडिटर खुल गया है।")
            }
        }

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            setPackage(pkg)
            type = media.mimeType
            putExtra(Intent.EXTRA_STREAM, media.uri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tryStartActivity(sendIntent)) {
            ActionExecutionOutcome(true, "Facebook ${postType.hindiLabel} कम्पोज़र खुल गया है (कैप्शन क्लिपबोर्ड में है)।")
        } else {
            ActionExecutionOutcome(false, "Facebook मीडिया कम्पोज़र नहीं खुल सका।")
        }
    }

    private fun launchWhatsAppMedia(
        pkg: String,
        postType: SocialPostType,
        media: StagedSocialMedia,
        caption: String
    ): ActionExecutionOutcome {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            setPackage(pkg)
            type = media.mimeType
            putExtra(Intent.EXTRA_STREAM, media.uri)
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (tryStartActivity(sendIntent)) {
            val label = if (postType == SocialPostType.STORY) "WhatsApp Status" else "WhatsApp Video Share"
            ActionExecutionOutcome(true, "$label स्क्रीन खुल गई है — My Status चुनकर पोस्ट करें (Auto-Tap ऑन है तो खुद दबाएगा)।")
        } else {
            ActionExecutionOutcome(false, "WhatsApp मीडिया शेयर नहीं खुल सका।")
        }
    }

    private fun grantMediaReadUri(pkg: String, uri: Uri) {
        runCatching {
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        val canResolve = runCatching {
            intent.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
        if (!canResolve) return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    companion object {
        fun parseSocialSpec(
            target: String,
            payload: String
        ): Triple<List<SocialPlatform>, SocialPostType, String> {
            val platforms = target.split(",")
                .mapNotNull { SocialPlatform.fromWire(it) }
                .distinct()
                .ifEmpty { SocialPlatform.entries }

            val parts = payload.split("::", limit = 2)
            val postType = SocialPostType.fromWire(parts.firstOrNull().orEmpty())
            val caption = parts.getOrNull(1)?.trim().orEmpty()
            return Triple(platforms, postType, caption)
        }

        fun encodeSocialSpec(
            platforms: List<SocialPlatform>,
            postType: SocialPostType,
            caption: String
        ): Pair<String, String> {
            val target = platforms.distinct().ifEmpty { SocialPlatform.entries }
                .joinToString(",") { it.wireValue }
            val payload = "${postType.wireValue}::${caption.trim()}"
            return target to payload
        }

        fun buildOfflineCaption(
            topicHint: String,
            platforms: List<SocialPlatform>,
            postType: SocialPostType,
            language: AssistantLanguage
        ): String {
            val cleanTopic = topicHint.trim()
            val tags = buildString {
                if (postType == SocialPostType.STORY) append("#StoryTime ") else append("#Reels #VideoPost ")
                if (SocialPlatform.INSTAGRAM in platforms) append("#Instagram ")
                if (SocialPlatform.FACEBOOK in platforms) append("#Facebook ")
                if (SocialPlatform.WHATSAPP in platforms) append("#WhatsAppStatus ")
                append("#Trending")
            }.trim()

            return when (language) {
                AssistantLanguage.ENGLISH -> {
                    val body = if (cleanTopic.isNotBlank()) cleanTopic else "New moment shared via LEON AI!"
                    "$body\n\n$tags"
                }
                AssistantLanguage.HINGLISH -> {
                    val body = if (cleanTopic.isNotBlank()) cleanTopic else "Naya update aur video — kaisa laga zaroor batayein!"
                    "$body\n\n$tags"
                }
                AssistantLanguage.HINDI -> {
                    val body = if (cleanTopic.isNotBlank()) cleanTopic else "नया पल और वीडियो अपडेट — ज़रूर देखें और शेयर करें!"
                    "$body\n\n$tags"
                }
            }
        }
    }
}
