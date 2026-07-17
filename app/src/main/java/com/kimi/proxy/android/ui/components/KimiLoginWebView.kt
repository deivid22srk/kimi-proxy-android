package com.kimi.proxy.android.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kimi.proxy.android.data.KimiAccount
import com.kimi.proxy.android.util.JwtUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * State emitted by the WebView while we sniff the Kimi JWT out of the
 * outgoing requests.
 */
sealed class CaptureState {
    data object Idle : CaptureState()
    data object Loading : CaptureState()
    data class Captured(val account: KimiAccount) : CaptureState()
    data class Failed(val message: String) : CaptureState()
}

/**
 * Kimi WebView with token capture + Google sign-in support.
 *
 * Implementation notes:
 * - We attach a custom [WebViewClient] to intercept every outgoing request and
 *   sniff the `Authorization: Bearer` header on `kimi.com/apiv2/` calls. This
 *   mirrors the Playwright script in `kimi-proxy-web/scripts/add-account.mjs`.
 * - For Google sign-in we let the WebView open popups via [WebChromeClient]
 *   `onCreateWindow` — Kimi's "Continue with Google" button uses a popup,
 *   so we forward it to a new in-app WebView rather than blocking it.
 * - We force desktop user-agent so Kimi serves the full web UI (not the
 *   limited mobile page).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KimiLoginWebView(
    onStateChange: (CaptureState) -> Unit,
    modifier: Modifier = Modifier
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
            webView = null
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                // Pre-warm cookies so existing Kimi sessions are picked up.
                CookieManager.getInstance().setAcceptCookie(true)

                WebView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    settings.userAgentString = DESKTOP_UA
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setGeolocationEnabled(false)

                    // Allow third-party cookies (Google login sets them).
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = KimiWebViewClient { state ->
                        onStateChange(state)
                    }

                    webChromeClient = object : WebChromeClient() {
                        // Google login opens a popup window — accept it.
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            val newView = WebView(ctx)
                            newView.settings.javaScriptEnabled = true
                            newView.settings.domStorageEnabled = true
                            newView.settings.userAgentString = DESKTOP_UA
                            newView.webViewClient = KimiWebViewClient { state ->
                                onStateChange(state)
                            }
                            newView.webChromeClient = this
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            transport?.webView = newView
                            resultMsg?.sendToTarget()
                            return true
                        }
                    }

                    loadUrl(KIMI_HOME)
                    webView = this
                }
            },
            update = { /* no-op */ },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private class KimiWebViewClient(
    private val onState: (CaptureState) -> Unit
) : WebViewClient() {

    private var captured = false

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        onState(CaptureState.Loading)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        // Keep Kimi + Google OAuth in-app.
        if (url.startsWith("https://www.kimi.com") ||
            url.startsWith("https://kimi.com") ||
            url.startsWith("https://accounts.google.com") ||
            url.startsWith("https://login.microsoftonline.com") ||
            url.startsWith("https://www.linkedin.com") ||
            url.startsWith("https://github.com/login")
        ) {
            return false
        }
        // External deep links (mailto:, tel:, intent:) → let system handle.
        if (url.startsWith("http://") || url.startsWith("https://")) return false
        return true
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        try {
            val url = request?.url?.toString() ?: return null
            if (url.contains("kimi.com/apiv2/") && !captured) {
                val auth = request.requestHeaders?.get("Authorization")
                    ?: request.requestHeaders?.get("authorization")
                if (auth != null && auth.startsWith("Bearer ")) {
                    val raw = auth.substring(7)
                    val claims = JwtUtils.decode(raw)
                    if (claims != null && !claims.sub.isNullOrBlank() && !claims.ssid.isNullOrBlank()) {
                        captured = true
                        val headers = request.requestHeaders ?: emptyMap()
                        val account = buildAccount(raw, claims, headers)
                        Log.i("KimiLogin", "JWT captured sub=${claims.sub} email=${claims.email}")
                        onState(CaptureState.Captured(account))
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("KimiLogin", "intercept error", t)
        }
        return null
    }
}

private fun buildAccount(
    rawToken: String,
    claims: JwtUtils.Claims,
    headers: Map<String, String>
): KimiAccount {
    val email = claims.email ?: claims.sub ?: "kimi-${claims.sub?.take(6)}"
    val expIso = claims.exp?.let { epoch ->
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        sdf.format(Date(epoch * 1000))
    } ?: ""
    val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())

    return KimiAccount(
        id = email,
        email = email,
        token = "Bearer $rawToken",
        deviceId = headers["x-msh-device-id"] ?: headers["X-Msh-Device-Id"] ?: "0",
        sessionId = headers["x-msh-session-id"] ?: headers["X-Msh-Session-Id"] ?: claims.ssid ?: "0",
        shieldData = headers["x-msh-shield-data"] ?: headers["X-Msh-Shield-Data"] ?: "",
        trafficId = headers["x-traffic-id"] ?: headers["X-Traffic-Id"] ?: claims.sub ?: "",
        timezone = if (claims.region == "overseas" || claims.region.isNullOrBlank()) {
            "America/Sao_Paulo"
        } else "UTC",
        jwtSub = claims.sub,
        createdAt = nowIso,
        jwtExpiresAt = expIso
    )
}

private const val KIMI_HOME = "https://www.kimi.com/"
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"
