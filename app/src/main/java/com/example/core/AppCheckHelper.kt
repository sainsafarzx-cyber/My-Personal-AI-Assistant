package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

object AppCheckDiagnostics {
    @Volatile
    var hasDebugSecret: Boolean = false
        private set

    @Volatile
    var providerMode: String = "uninitialized"
        private set

    @Volatile
    var initError: String? = null
        private set

    internal fun record(hasDebugSecret: Boolean, providerMode: String, initError: String? = null) {
        this.hasDebugSecret = hasDebugSecret
        this.providerMode = providerMode
        this.initError = initError
    }
}

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (BuildConfig.DEBUG) {
            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
            if (!debugToken.isNullOrEmpty()) {
                // Synchronous commit() ensures the debug secret is persisted before factory install.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }
            val secretPresent = !prefs.getString(DEBUG_SECRET_KEY, null).isNullOrEmpty()

            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            AppCheckDiagnostics.record(
                hasDebugSecret = secretPresent,
                providerMode = "debug"
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed (secretPresent=$secretPresent).")
        } else {
            val resId = context.resources.getIdentifier(
                RECAPTCHA_SITE_KEY_RES,
                "string",
                context.packageName
            )
            if (resId != 0) {
                val siteKey = context.getString(resId)
                if (siteKey.isNotBlank()) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                    AppCheckDiagnostics.record(
                        hasDebugSecret = true,
                        providerMode = "recaptcha"
                    )
                    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                } else {
                    AppCheckDiagnostics.record(
                        hasDebugSecret = false,
                        providerMode = "recaptcha_blank",
                        initError = "recaptcha_site_key is blank"
                    )
                    Log.e(TAG, "recaptcha_site_key resource string is blank.")
                }
            } else {
                AppCheckDiagnostics.record(
                    hasDebugSecret = false,
                    providerMode = "recaptcha_missing",
                    initError = "recaptcha_site_key missing"
                )
                Log.e(TAG, "recaptcha_site_key string resource not found in package.")
            }
        }
    } catch (e: Exception) {
        AppCheckDiagnostics.record(
            hasDebugSecret = false,
            providerMode = "error",
            initError = e.localizedMessage ?: e::class.java.simpleName
        )
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
