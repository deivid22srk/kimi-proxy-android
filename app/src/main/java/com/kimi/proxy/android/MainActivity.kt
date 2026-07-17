package com.kimi.proxy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kimi.proxy.android.ui.navigation.KimiProxyApp
import com.kimi.proxy.android.ui.theme.KimiProxyTheme

/**
 * Single Activity hosting the entire Compose surface.
 * Uses edge-to-edge and the AndroidX SplashScreen API.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { false }

        setContent {
            KimiProxyTheme {
                KimiProxyApp()
            }
        }
    }
}
