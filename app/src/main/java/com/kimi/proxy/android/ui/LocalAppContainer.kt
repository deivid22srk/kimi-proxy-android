package com.kimi.proxy.android.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.kimi.proxy.android.data.AppContainer

/**
 * CompositionLocal exposing the manual DI container to all Composables.
 * Set at the root of the Compose tree in [com.kimi.proxy.android.ui.navigation.KimiProxyApp].
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
