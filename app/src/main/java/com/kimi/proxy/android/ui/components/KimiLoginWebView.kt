package com.kimi.proxy.android.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
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
 * - For Google sign-in we MUST enable `setSupportMultipleWindows(true)` and
 *   properly handle `onCreateWindow` — Google's OAuth flow opens a popup,
 *   and Google's anti-abuse system blocks WebViews that look automated.
 *   We bypass the detection by:
 *     1. Using a real Chrome **mobile** UA (no "wv" / "Version/4.0" markers).
 *     2. Injecting stealth JS on every page load that undefines
 *        `navigator.webdriver`, fakes `window.chrome.runtime`, populates
 *        `navigator.plugins` and `navigator.languages`.
 *     3. Adding the popup WebView to the view hierarchy (otherwise it
 *        stays invisible and the user can never complete OAuth).
 * - The same hardening is applied to the popup WebView created in
 *   `onCreateWindow` so Google's OAuth page sees a "real" browser.
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
                    applyHardening()

                    // Allow third-party cookies (Google login sets them).
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webViewClient = KimiWebViewClient { state ->
                        onStateChange(state)
                    }

                    webChromeClient = object : WebChromeClient() {
                        /**
                         * Google OAuth opens a popup window.
                         * We create a new WebView, harden it the same way,
                         * ADD it to the parent's view hierarchy (critical —
                         * otherwise the popup is invisible), and hide the
                         * parent until the popup closes.
                         */
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: android.os.Message?
                        ): Boolean {
                            val parent = view ?: return false
                            val rootView = parent.parent as? ViewGroup
                                ?: (parent.context as? android.app.Activity)
                                    ?.findViewById<ViewGroup>(android.R.id.content)
                                ?: return false

                            val chromeClient = this
                            val popup = WebView(parent.context).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                applyHardening()
                                CookieManager.getInstance()
                                    .setAcceptThirdPartyCookies(this, true)
                                webViewClient = KimiWebViewClient { state ->
                                    onStateChange(state)
                                }
                                webChromeClient = chromeClient
                            }

                            // Hide parent, show popup on top.
                            parent.visibility = View.INVISIBLE
                            rootView.addView(popup)

                            // Track popup so onCloseWindow can clean up.
                            popup.tag = parent

                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            transport?.webView = popup
                            resultMsg?.sendToTarget()
                            return true
                        }

                        /**
                         * Restore parent visibility when the OAuth popup closes.
                         */
                        override fun onCloseWindow(window: WebView?) {
                            val popup = window ?: return
                            val parent = popup.tag as? WebView
                            val root = popup.parent as? ViewGroup
                            root?.removeView(popup)
                            popup.destroy()
                            parent?.visibility = View.VISIBLE
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

/**
 * Configures a WebView to look like a real Chrome browser to Google's
 * anti-abuse system. Applied to BOTH the main WebView and any OAuth
 * popups created via [WebChromeClient.onCreateWindow].
 */
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.applyHardening() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        // Mobile Chrome UA — NO "wv" suffix and NO "Version/4.0".
        // Both markers are checked by Google's bot detection.
        userAgentString = MOBILE_UA
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        loadWithOverviewMode = true
        useWideViewPort = true
        allowFileAccess = false
        allowContentAccess = true
        mediaPlaybackRequiresUserGesture = false
        // CRITICAL for Google OAuth popup flow.
        javaScriptCanOpenWindowsAutomatically = true
        setSupportMultipleWindows(true)
        setGeolocationEnabled(false)
    }
}

/**
 * Stealth JS that masks every signal Google's bot detection checks:
 * - `navigator.webdriver` → undefined
 * - `window.chrome.runtime` → exists (real Chrome has it)
 * - `navigator.plugins` → non-empty array (real Chrome has 5 PDF plugins)
 * - `navigator.languages` → realistic array
 * - `navigator.permissions.query` → consistent with Notification.permission
 *
 * Injected BEFORE any page script runs via [WebViewClient.onPageStarted].
 */
private const val STEALTH_JS = """
(function() {
    try {
        // 1. Hide webdriver flag
        Object.defineProperty(navigator, 'webdriver', {
            get: () => undefined, configurable: true
        });

        // 2. Fake window.chrome object
        if (!window.chrome) {
            window.chrome = {};
        }
        if (!window.chrome.runtime) {
            window.chrome.runtime = {
                OnInstalledReason: { INSTALL: 'install', UPDATE: 'update', CHROME_UPDATE: 'chrome_update', SHARED_MODULE_UPDATE: 'shared_module_update' },
                OnRestartRequiredReason: { APP_UPDATE: 'app_update', OS_UPDATE: 'os_update', PERIODIC: 'periodic' }
            };
        }

        // 3. Fake plugins (real Chrome has 5 PDF plugins)
        Object.defineProperty(navigator, 'plugins', {
            get: () => {
                const pdf = { name: 'PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' };
                const arr = [
                    pdf,
                    { name: 'Chrome PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                    { name: 'Chromium PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                    { name: 'Microsoft Edge PDF Viewer', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
                    { name: 'WebKit built-in PDF', filename: 'internal-pdf-viewer', description: 'Portable Document Format' }
                ];
                arr.refresh = function() {};
                arr.item = function(i) { return arr[i] || null; };
                arr.namedItem = function(n) { return arr.find(p => p.name === n) || null; };
                return arr;
            },
            configurable: true
        });

        // 4. Realistic languages
        Object.defineProperty(navigator, 'languages', {
            get: () => ['pt-BR', 'pt', 'en-US', 'en'],
            configurable: true
        });

        // 5. Fix permissions.query to be consistent with Notification.permission
        if (window.navigator.permissions && window.navigator.permissions.query) {
            const origQuery = window.navigator.permissions.query.bind(window.navigator.permissions);
            window.navigator.permissions.query = function(p) {
                if (p && p.name === 'notifications') {
                    return Promise.resolve({ state: (typeof Notification !== 'undefined' ? Notification.permission : 'default'), onchange: null });
                }
                return origQuery(p);
            };
        }

        // 6. WebGL vendor/renderer spoofing (some bot checks use this)
        try {
            const getParameter = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(parameter) {
                // UNMASKED_VENDOR_WEBGL = 37445, UNMASKED_RENDERER_WEBGL = 37446
                if (parameter === 37445) return 'Qualcomm';
                if (parameter === 37446) return 'Adreno (TM) 740';
                return getParameter.call(this, parameter);
            };
        } catch (e) {}
    } catch (e) {
        // Silent fail — page must still load.
    }
})();
"""

private class KimiWebViewClient(
    private val onState: (CaptureState) -> Unit
) : WebViewClient() {

    private var captured = false

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        // Inject stealth JS BEFORE any page script runs.
        // evaluateJavascript runs in the page's main world, so the overrides
        // take effect before Google's bot-detection script executes.
        view?.evaluateJavascript(STEALTH_JS, null)
        onState(CaptureState.Loading)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        // Keep Kimi + Google OAuth + common IdPs in-app.
        if (url.startsWith("https://www.kimi.com") ||
            url.startsWith("https://kimi.com") ||
            url.startsWith("https://accounts.google.com") ||
            url.startsWith("https://accounts.gstatic.com") ||
            url.startsWith("https://www.google.com/accounts") ||
            url.startsWith("https://www.google.com/recaptcha") ||
            url.startsWith("https://www.recaptcha.net") ||
            url.startsWith("https://securetoken.google.com") ||
            url.startsWith("https://login.microsoftonline.com") ||
            url.startsWith("https://www.linkedin.com") ||
            url.startsWith("https://github.com/login")
        ) {
            return false
        }
        // External deep links (mailto:, tel:, intent:, custom schemes) →
        // let the system handle them.
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

// Mobile Chrome UA — NO "wv" suffix and NO "Version/4.0" marker.
// Both are checked by Google's bot detection as WebView indicators.
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro Build/UQ1A.240205.004) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"
