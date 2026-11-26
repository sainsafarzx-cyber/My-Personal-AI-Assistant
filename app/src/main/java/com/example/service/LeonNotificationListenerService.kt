package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.CapturedNotification

class LeonNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationBridge.setConnected(true)
        try {
            activeNotifications?.forEach { sbn ->
                recordNotification(sbn)
            }
        } catch (_: Exception) {
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationBridge.setConnected(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) {
            recordNotification(sbn)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null) {
            NotificationBridge.removeNotification(sbn.key)
        }
    }

    private fun recordNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        if (title.isEmpty() && text.isEmpty()) return

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName.substringAfterLast('.')
        }

        NotificationBridge.pushNotification(
            CapturedNotification(
                key = sbn.key,
                appName = appLabel,
                packageName = sbn.packageName,
                title = title.ifEmpty { appLabel },
                content = text,
                timestampMillis = sbn.postTime
            )
        )
    }
}
