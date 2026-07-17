package com.kimi.proxy.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.kimi.proxy.android.data.AppContainer

/**
 * Application entrypoint. Bootstraps the manual DI container and the
 * notification channel used by foreground capture events.
 */
class KimiProxyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        registerNotificationChannel()
    }

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_CAPTURE,
            "Captura de token",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Avisos sobre a captura do JWT no WebView"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        private lateinit var instance: KimiProxyApp
        fun get(): KimiProxyApp = instance
        const val CHANNEL_CAPTURE = "capture_status"
    }
}
