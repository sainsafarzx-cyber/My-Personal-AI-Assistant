package com.example.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.AssistantLanguage

/**
 * Synchronous SharedPreferences store for runtime permission batch state and
 * persistent Always-Ready / Continuous Voice service recovery across task swipe-away
 * (onTaskRemoved), process restarts (START_STICKY with null intent), and device reboot.
 */
object LeonRuntimePrefs {
    private const val PREFS_NAME = "leon_runtime_state"
    private const val KEY_ONE_TIME_PERMS_ASKED = "one_time_perms_asked"
    private const val KEY_ALWAYS_READY_ACTIVE = "always_ready_active"
    private const val KEY_CONTINUOUS_ACTIVE = "continuous_active"
    private const val KEY_LOCK_SCREEN_MODE = "lock_screen_mode"
    private const val KEY_LANGUAGE_CODE = "language_code"
    private const val KEY_VOICE_SPEED = "voice_speed"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOneTimePermissionsAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONE_TIME_PERMS_ASKED, false)

    fun setOneTimePermissionsAsked(context: Context, asked: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONE_TIME_PERMS_ASKED, asked).apply()
    }

    fun isAlwaysReadyActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ALWAYS_READY_ACTIVE, false)

    fun setAlwaysReadyActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALWAYS_READY_ACTIVE, active).apply()
    }

    fun isContinuousModeActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONTINUOUS_ACTIVE, false)

    fun setContinuousModeActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONTINUOUS_ACTIVE, active).apply()
    }

    fun isLockScreenModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_SCREEN_MODE, true)

    fun setLockScreenModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_SCREEN_MODE, enabled).apply()
    }

    fun getLanguageCode(context: Context): String =
        prefs(context).getString(KEY_LANGUAGE_CODE, AssistantLanguage.HINDI.code)
            ?: AssistantLanguage.HINDI.code

    fun setLanguageCode(context: Context, code: String) {
        prefs(context).edit().putString(KEY_LANGUAGE_CODE, code).apply()
    }

    fun getVoiceSpeed(context: Context): Float =
        prefs(context).getFloat(KEY_VOICE_SPEED, 1.0f).coerceIn(0.5f, 2.0f)

    fun setVoiceSpeed(context: Context, speed: Float) {
        prefs(context).edit().putFloat(KEY_VOICE_SPEED, speed.coerceIn(0.5f, 2.0f)).apply()
    }

    fun hasMicPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun coreRuntimePermissions(): List<String> = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }

    fun getMissingCorePermissions(context: Context): Array<String> =
        coreRuntimePermissions().filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

    fun hasAllCoreRuntimePermissions(context: Context): Boolean =
        getMissingCorePermissions(context).isEmpty()
}
