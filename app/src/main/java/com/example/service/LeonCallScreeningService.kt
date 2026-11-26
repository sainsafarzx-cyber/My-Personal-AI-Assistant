package com.example.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

private const val TAG = "LeonCallScreening"

/**
 * Android CallScreeningService (RoleManager.ROLE_CALL_SCREENING) that safely obtains
 * incoming caller handles and verification info WITHOUT requiring READ_CALL_LOG permission.
 */
class LeonCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            true
        }

        if (isIncoming) {
            val number = callDetails.handle?.schemeSpecificPart.orEmpty()
            val displayName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                callDetails.callerDisplayName.orEmpty()
            } else {
                ""
            }
            Log.i(TAG, "Screened incoming call from=${displayName.ifBlank { "number" }}")
            CallReceptionistBridge.onIncomingCallScreened(number = number, displayName = displayName)

            val config = CallReceptionistBridge.state.value.config
            if (config.enabled) {
                LeonCallReceptionistService.onCallRinging(
                    context = applicationContext,
                    callerNumber = number,
                    callerDisplay = displayName
                )
            }
        }

        // Always allow normal call ringing so the Boss has first chance to answer manually.
        val responseBuilder = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            responseBuilder.setSilenceCall(false)
        }
        respondToCall(callDetails, responseBuilder.build())
    }
}
