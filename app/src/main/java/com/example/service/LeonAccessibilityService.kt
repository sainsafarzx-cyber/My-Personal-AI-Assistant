package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.social.SocialAutoPosterBridge
import com.example.social.SocialPlatform
import com.example.social.SocialPostJob
import com.example.social.SocialPostType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LeonAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var lastAutoActionAtMs: Long = 0L
    private var lastCaptionStepKey: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.setConnected(true)
        collectJob?.cancel()
        collectJob = serviceScope.launch {
            AccessibilityBridge.globalActionRequests.collect { actionId ->
                performGlobalAction(actionId)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString().orEmpty()
        if (pkg.isNotEmpty()) {
            AccessibilityBridge.updateActivePackage(pkg)
            val armedJob = SocialAutoPosterBridge.isStepArmedForPackage(pkg) ?: return
            handleSocialAutoPublish(pkg, armedJob)
        }
    }

    private fun handleSocialAutoPublish(pkg: String, job: SocialPostJob) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAutoActionAtMs < AUTO_TAP_COOLDOWN_MS) return

        val root = rootInActiveWindow ?: return
        try {
            val platform = job.currentPlatform ?: return
            val stepKey = "${job.id}_${job.currentIndex}"

            if (job.caption.isNotBlank() && lastCaptionStepKey != stepKey) {
                val captionFilled = tryPopulateCaptionEditText(root, job.caption)
                if (captionFilled) {
                    lastCaptionStepKey = stepKey
                    lastAutoActionAtMs = now
                    SocialAutoPosterBridge.appendLog("${platform.displayName}: Caption inserted automatically.")
                    return
                }
            }

            when (platform) {
                SocialPlatform.WHATSAPP -> {
                    if (job.postType == SocialPostType.STORY) {
                        if (clickFirstMatchingNode(root, listOf("my status", "मेरा स्टेटस"))) {
                            lastAutoActionAtMs = now
                            SocialAutoPosterBridge.appendLog("WhatsApp: Selected 'My status'.")
                            return
                        }
                    }
                    if (clickFirstMatchingNode(root, listOf("send", "भेजें"))) {
                        lastAutoActionAtMs = now
                        SocialAutoPosterBridge.markCurrentStepAutoPublished(
                            SocialPlatform.WHATSAPP,
                            "Tapped Send automatically."
                        )
                    }
                }

                SocialPlatform.INSTAGRAM -> {
                    val publishLabels = if (job.postType == SocialPostType.STORY) {
                        listOf("your story", "आपकी स्टोरी", "share", "शेयर करें")
                    } else {
                        listOf("share", "शेयर करें")
                    }
                    if (clickFirstMatchingNode(root, publishLabels)) {
                        lastAutoActionAtMs = now
                        SocialAutoPosterBridge.markCurrentStepAutoPublished(
                            SocialPlatform.INSTAGRAM,
                            "Tapped Publish/Share automatically."
                        )
                        return
                    }
                    if (clickFirstMatchingNode(root, listOf("next", "आगे", "continue"))) {
                        lastAutoActionAtMs = now
                        SocialAutoPosterBridge.appendLog("Instagram: Advanced via Next.")
                    }
                }

                SocialPlatform.FACEBOOK -> {
                    val publishLabels = if (job.postType == SocialPostType.STORY) {
                        listOf("share to story", "स्टोरी पर शेयर करें", "share now", "share", "शेयर करें")
                    } else {
                        listOf("post", "पोस्ट करें", "share now", "share", "शेयर करें")
                    }
                    if (clickFirstMatchingNode(root, publishLabels)) {
                        lastAutoActionAtMs = now
                        SocialAutoPosterBridge.markCurrentStepAutoPublished(
                            SocialPlatform.FACEBOOK,
                            "Tapped Post/Share automatically."
                        )
                        return
                    }
                    if (clickFirstMatchingNode(root, listOf("next", "आगे"))) {
                        lastAutoActionAtMs = now
                        SocialAutoPosterBridge.appendLog("Facebook: Advanced via Next.")
                    }
                }
            }
        } finally {
            @Suppress("DEPRECATION")
            runCatching { root.recycle() }
        }
    }

    private fun tryPopulateCaptionEditText(root: AccessibilityNodeInfo, caption: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val cls = node.className?.toString().orEmpty()
            val isEditable = node.isEditable || cls.contains("EditText", ignoreCase = true)
            if (isEditable && node.isVisibleToUser) {
                val existing = node.text?.toString()?.trim().orEmpty()
                val hint = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    node.hintText?.toString()?.trim().orEmpty()
                } else ""
                if (existing.isEmpty() || existing == hint) {
                    val args = Bundle().apply {
                        putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            caption
                        )
                    }
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                        return true
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun clickFirstMatchingNode(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isVisibleToUser && node.isEnabled) {
                val text = node.text?.toString()?.trim()?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
                val matched = keywords.any { key ->
                    text == key || desc == key ||
                        (key.length >= 4 && (text.contains(key) || desc.contains(key)))
                }
                if (matched && performClickUpTree(node)) {
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun performClickUpTree(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 4) {
            if (cur.isClickable && cur.isEnabled) {
                if (cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            cur = cur.parent
            depth++
        }
        return false
    }

    override fun onInterrupt() {
        // Required override
    }

    override fun onDestroy() {
        super.onDestroy()
        AccessibilityBridge.setConnected(false)
        collectJob?.cancel()
        serviceScope.cancel()
    }

    companion object {
        private const val AUTO_TAP_COOLDOWN_MS = 1_400L
    }
}
