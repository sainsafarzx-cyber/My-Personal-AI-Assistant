package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.configureAppCheck
import com.example.service.BrightnessBridge
import com.example.social.SocialAutoPosterBridge
import com.example.ui.LeonAppNavigation
import com.example.ui.theme.LeonAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or AI Logic calls.
        configureAppCheck(this, intent)
        if (savedInstanceState == null) {
            handleSocialIntent(intent)
        }

        enableEdgeToEdge()
        setContent {
            val windowBrightness by BrightnessBridge.windowBrightness.collectAsStateWithLifecycle()
            LaunchedEffect(windowBrightness) {
                windowBrightness?.let { level ->
                    val lp = window.attributes
                    lp.screenBrightness = level.coerceIn(0.05f, 1.0f)
                    window.attributes = lp
                }
            }
            LeonAiTheme {
                LeonAppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")) {
            configureAppCheck(this, intent)
        }
        handleSocialIntent(intent)
    }

    private fun handleSocialIntent(intent: Intent?) {
        if (intent?.action == "com.example.action.OPEN_SOCIAL_POSTER") {
            val target = intent.getStringExtra("extra_social_target").orEmpty()
            val payload = intent.getStringExtra("extra_social_payload").orEmpty()
            SocialAutoPosterBridge.requestOpenStudio(target, payload)
        }
    }
}
