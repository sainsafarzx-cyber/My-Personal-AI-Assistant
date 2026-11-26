package com.example.service

import com.example.data.ActionType
import com.example.data.CapturedNotification
import com.example.data.DailyTask
import com.example.data.DeviceAction
import com.example.data.DeviceTelemetry

data class LockScreenDecision(
    val allowed: Boolean,
    val reasonHindi: String = ""
)

object LockScreenPolicy {

    private val lockSafeActions = setOf(
        ActionType.SPEAK_ONLY,
        ActionType.TOGGLE_FLASHLIGHT,
        ActionType.SET_VOLUME,
        ActionType.PLAY_MUSIC,
        ActionType.LOCK_SCREEN_BRIEFING,
        ActionType.SET_TIMER,
        ActionType.READ_NOTIFICATIONS,
        ActionType.CREATE_TASK,
        ActionType.CREATE_ROUTINE,
        ActionType.SAVE_MEMORY,
        ActionType.SET_OWNER_IDENTITY,
        ActionType.FETCH_NEWS,
        ActionType.EMERGENCY_STOP
    )

    private val communicationSensitiveActions = setOf(
        ActionType.SEND_WHATSAPP,
        ActionType.SEND_SMS
    )

    fun evaluate(
        action: DeviceAction,
        isDeviceLocked: Boolean,
        canWriteSettings: Boolean = false,
        allowLockScreenCalls: Boolean = true
    ): LockScreenDecision {
        if (!isDeviceLocked) {
            return LockScreenDecision(allowed = true)
        }
        if (action.type in lockSafeActions) {
            return LockScreenDecision(allowed = true)
        }
        if (action.type == ActionType.MAKE_CALL && allowLockScreenCalls) {
            return LockScreenDecision(allowed = true)
        }
        if (action.type == ActionType.SET_BRIGHTNESS && canWriteSettings) {
            return LockScreenDecision(allowed = true)
        }
        val reason = when {
            action.type == ActionType.MAKE_CALL ->
                "बॉस, लॉक-स्क्रीन कॉलिंग सेटिंग बंद है — कृपया Lock-Screen Mode चालू करें या फ़ोन अनलॉक करें।"
            action.type == ActionType.SOCIAL_AUTO_POST ->
                "बॉस, फ़ोन लॉक है — Instagram, Facebook या WhatsApp पर स्टोरी/वीडियो पोस्ट करने के लिए कृपया फ़ोन अनलॉक करें।"
            action.type == ActionType.OPEN_LIFE_COMPASS ->
                "बॉस, फ़ोन लॉक है — सही राह कम्पास स्क्रीन खोलने के लिए कृपया फ़ोन अनलॉक करें।"
            action.type == ActionType.OPEN_CALL_ASSISTANT ->
                "बॉस, फ़ोन लॉक है — AI Call Receptionist सेटिंग खोलने के लिए कृपया फ़ोन अनलॉक करें।"
            action.type in communicationSensitiveActions || action.requiresConfirmation ->
                "बॉस, फ़ोन लॉक है — सुरक्षा के लिए WhatsApp या SMS अनलॉक करने के बाद ही चलेगा।"
            else ->
                "बॉस, फ़ोन लॉक है — यह ऐप या सेटिंग खोलने के लिए कृपया फ़ोन अनलॉक करें।"
        }
        return LockScreenDecision(allowed = false, reasonHindi = reason)
    }

    fun buildLockScreenBriefing(
        telemetry: DeviceTelemetry,
        notifications: List<CapturedNotification>,
        tasks: List<DailyTask>,
        isDeviceLocked: Boolean
    ): String {
        val lockNote = if (isDeviceLocked) "फ़ोन अभी लॉक मोड में है।" else "फ़ोन अनलॉक है।"
        val chargingNote = if (telemetry.charging) "चार्ज हो रहा है" else "बैटरी पर है"
        val pendingTasks = tasks.filter { !it.completed }

        val notifPart = if (notifications.isEmpty()) {
            "कोई नई नोटिफिकेशन नहीं है।"
        } else {
            val topNotifs = notifications.take(3).joinToString("। ") { n ->
                val title = n.title.ifBlank { n.appName }
                val body = n.content.take(80)
                if (body.isNotBlank()) "${n.appName}: $title — $body" else "${n.appName}: $title"
            }
            "कुल ${notifications.size} नोटिफिकेशन हैं: $topNotifs।"
        }

        val taskPart = if (pendingTasks.isEmpty()) {
            "आज कोई पेंडिंग टास्क नहीं है।"
        } else {
            val topTasks = pendingTasks.take(2).joinToString(", ") { it.title }
            "आपके ${pendingTasks.size} पेंडिंग टास्क हैं: $topTasks।"
        }

        return "बॉस, लॉक-स्क्रीन रिपोर्ट सुनिए। $lockNote बैटरी ${telemetry.batteryPercent} प्रतिशत है और $chargingNote। $notifPart $taskPart"
    }
}
