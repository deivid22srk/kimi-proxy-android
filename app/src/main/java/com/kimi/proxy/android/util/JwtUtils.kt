package com.kimi.proxy.android.util

import android.util.Base64
import org.json.JSONObject

/**
 * Minimal JWT decoder (no signature verification — we only inspect claims
 * captured from the WebView's own requests). Mirrors the decoding logic
 * used by kimi-proxy-web/scripts/add-account.mjs.
 */
object JwtUtils {

    data class Claims(
        val sub: String?,
        val ssid: String?,
        val email: String?,
        val exp: Long?,
        val region: String?
    )

    /** Returns null when the token is not a well-formed JWT. */
    fun decode(token: String): Claims? {
        val raw = if (token.startsWith("Bearer ", ignoreCase = true)) {
            token.substring(7)
        } else token
        val parts = raw.split(".")
        if (parts.size != 3) return null
        return try {
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            val obj = JSONObject(payload)
            Claims(
                sub = obj.optString("sub").takeIf { it.isNotBlank() },
                ssid = obj.optString("ssid").takeIf { it.isNotBlank() },
                email = obj.optString("email").takeIf { it.isNotBlank() },
                exp = obj.optLong("exp", 0L).takeIf { it > 0 },
                region = obj.optString("region").takeIf { it.isNotBlank() }
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** True when the JWT carries both `sub` and `ssid` claims (kimi-proxy-web requirement). */
    fun isUsable(token: String): Boolean {
        val c = decode(token) ?: return false
        return !c.sub.isNullOrBlank() && !c.ssid.isNullOrBlank()
    }
}
