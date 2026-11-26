package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

private const val TAG = "IncomingCallReceiver"

/**
 * Listens for Android PHONE_STATE changes (RINGING -> OFFHOOK -> IDLE) so LEON AI
 * can detect unanswered ringing calls, stand down immediately if Boss picks up, and
 * capture/save the AI conversation summary as soon as the call finishes.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Phone state changed -> $stateStr")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                val config = CallReceptionistBridge.state.value.config
                if (!config.enabled) return
                val (display, number) = CallReceptionistBridge.resolveRecentCaller()
                LeonCallReceptionistService.onCallRinging(
                    context = ctx,
                    callerNumber = number,
                    callerDisplay = display
                )
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!CallReceptionistBridge.aiAcceptedCurrentCall &&
                    CallReceptionistBridge.state.value.activeCallState == "RINGING_COUNTDOWN"
                ) {
                    LeonCallReceptionistService.onBossAnsweredManually(ctx)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val active = CallReceptionistBridge.state.value.activeCallState
                if (active != "IDLE" && active != "SIMULATED_ACTIVE") {
                    LeonCallReceptionistService.onCallEnded(ctx)
                }
            }
        }
    }
}
