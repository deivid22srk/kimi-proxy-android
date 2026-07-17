package com.kimi.proxy.android.data

import kotlinx.serialization.Serializable

/**
 * Mirrors the account object stored by the kimi-proxy-web `accounts.json`.
 * Captured by intercepting requests from the Kimi WebView.
 */
@Serializable
data class KimiAccount(
    val id: String,
    val email: String,
    val token: String,             // Full "Bearer eyJ..." string
    val deviceId: String = "",
    val sessionId: String = "",
    val shieldData: String = "",
    val trafficId: String = "",
    val timezone: String = "America/Sao_Paulo",
    val jwtSub: String? = null,
    val createdAt: String,
    val jwtExpiresAt: String,
    val lastUsedAt: String? = null
) {
    /** True when the JWT exp claim is in the past. */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean {
        return try {
            val iso = jwtExpiresAt.replace("Z", "+00:00")
            val expMs = java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
            expMs <= now
        } catch (_: Throwable) {
            false
        }
    }
}

@Serializable
data class ProxySettings(
    // Default to localhost:8080 — matches the Termux use case where the
    // proxy runs on the same device as the app.
    val baseUrl: String = "http://127.0.0.1:8080",
    val apiKey: String = "",
    val autoSendOnCapture: Boolean = true
)

@Serializable
data class ProxyTestResult(
    val ok: Boolean,
    val message: String,
    val latencyMs: Long? = null
)
