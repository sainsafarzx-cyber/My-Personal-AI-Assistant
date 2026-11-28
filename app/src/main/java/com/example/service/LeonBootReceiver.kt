package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.core.LeonRuntimePrefs
import com.example.data.AssistantLanguage

/**
 * Restores Always-Ready / Continuous LEON Voice Service after device reboot or app update
 * whenever the user had turned LEON on and has not stopped it.
 */
class LeonBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        val action = intent?.action ?: return
        if (action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON"
            )
        ) {
            return
        }
        if (LeonRuntimePrefs.isAlwaysReadyActive(ctx) && LeonRuntimePrefs.hasMicPermission(ctx)) {
            LeonVoiceService.startAlwaysReady(
                context = ctx,
                language = AssistantLanguage.fromCode(LeonRuntimePrefs.getLanguageCode(ctx)),
                speed = LeonRuntimePrefs.getVoiceSpeed(ctx),
                lockScreenMode = LeonRuntimePrefs.isLockScreenModeEnabled(ctx),
                continuousMode = LeonRuntimePrefs.isContinuousModeActive(ctx)
            )
        }
    }
}
