package com.kimi.proxy.android.data

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.TimeUnit

/**
 * Talks to a running kimi-proxy-web instance.
 *
 * The proxy itself does not expose a dedicated "POST /accounts" REST route in
 * the upstream version, so we ship an `accounts.json` snippet via a small
 * convention: POST `/__accounts` with the JSON payload. If the server does not
 * implement it, the app falls back to a "copy to clipboard" UX and exposes the
 * raw JSON for the user to paste into their server's `accounts.json`.
 */
class ProxyApi(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Quick liveness probe against /health. */
    suspend fun test(baseUrl: String, apiKey: String): ProxyTestResult = withContext(Dispatchers.IO) {
        val cleaned = baseUrl.trimEnd('/')
        val start = System.currentTimeMillis()
        try {
            val builder = Request.Builder().url("$cleaned/health").get()
            if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
            client.newCall(builder.build()).execute().use { res ->
                val latency = System.currentTimeMillis() - start
                if (res.isSuccessful) ProxyTestResult(true, "HTTP ${res.code}", latency)
                else ProxyTestResult(false, "HTTP ${res.code}", latency)
            }
        } catch (t: Throwable) {
            ProxyTestResult(false, t.message ?: "erro desconhecido")
        }
    }

    /**
     * Push an account payload to the proxy. The endpoint is a small convention
     * used by hardened forks of kimi-proxy-web: POST /__accounts.
     * Returns true if the server acknowledged (2xx), false otherwise.
     */
    suspend fun pushAccount(account: KimiAccount, settings: ProxySettings): ProxyTestResult =
        withContext(Dispatchers.IO) {
            val cleaned = settings.baseUrl.trimEnd('/')
            val payload = account.toJsonObject()
            val body = json.encodeToString(JsonObject.serializer(), payload)
                .toRequestBody("application/json".toMediaType())
            val builder = Request.Builder()
                .url("$cleaned/__accounts")
                .post(body)
                .header("X-Kimi-Proxy-Source", "android-app")
            if (settings.apiKey.isNotBlank()) {
                builder.header("Authorization", "Bearer ${settings.apiKey}")
            }
            try {
                client.newCall(builder.build()).execute().use { res ->
                    val latency = 0L
                    if (res.isSuccessful) ProxyTestResult(true, "HTTP ${res.code}", latency)
                    else ProxyTestResult(false, "HTTP ${res.code}", latency)
                }
            } catch (t: Throwable) {
                ProxyTestResult(false, t.message ?: "erro desconhecido")
            }
        }

    /**
     * Produces a JSON payload compatible with kimi-proxy-web `accounts.json`.
     * Useful when the proxy server does not expose /__accounts — the user can
     * paste this into their `accounts.json` manually.
     */
    fun exportAccountJson(account: KimiAccount): String {
        return json.encodeToString(KimiAccount.serializer(), account)
    }

    private fun KimiAccount.toJsonObject(): JsonObject = buildJsonObject {
        put("id", id)
        put("email", email)
        put("token", token)
        put("deviceId", deviceId)
        put("sessionId", sessionId)
        put("shieldData", shieldData)
        put("trafficId", trafficId)
        put("timezone", timezone)
        jwtSub?.let { put("jwtSub", it) }
        put("createdAt", createdAt)
        put("jwtExpiresAt", jwtExpiresAt)
    }
}
