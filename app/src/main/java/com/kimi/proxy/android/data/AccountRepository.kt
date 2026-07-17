package com.kimi.proxy.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent storage for accounts + proxy settings backed by DataStore.
 * The whole accounts list is serialised to JSON inside a single preference,
 * mirroring the structure of the kimi-proxy-web `accounts.json` file.
 */
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore("kimi_proxy")

class AccountRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val ACCOUNTS_KEY = stringPreferencesKey("accounts_json")
    private val SETTINGS_KEY = stringPreferencesKey("proxy_settings_json")
    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val DYNAMIC_KEY = booleanPreferencesKey("dynamic_color")

    val accounts: Flow<List<KimiAccount>> = context.appDataStore.data.map { prefs ->
        prefs[ACCOUNTS_KEY]?.let { raw ->
            runCatching { json.decodeFromString<List<KimiAccount>>(raw) }.getOrNull()
        } ?: emptyList()
    }

    val settings: Flow<ProxySettings> = context.appDataStore.data.map { prefs ->
        prefs[SETTINGS_KEY]?.let { raw ->
            runCatching { json.decodeFromString<ProxySettings>(raw) }.getOrNull()
        } ?: ProxySettings()
    }

    val themeMode: Flow<String> = context.appDataStore.data.map { it[THEME_KEY] ?: "system" }
    val dynamicColor: Flow<Boolean> = context.appDataStore.data.map { it[DYNAMIC_KEY] ?: true }

    suspend fun upsert(account: KimiAccount) {
        context.appDataStore.edit { prefs ->
            val current = prefs[ACCOUNTS_KEY]?.let { raw ->
                runCatching { json.decodeFromString<List<KimiAccount>>(raw) }.getOrNull()
            } ?: emptyList()
            val idx = current.indexOfFirst { it.id == account.id }
            val updated = if (idx >= 0) current.toMutableList().apply { set(idx, account) }
            else current + account
            prefs[ACCOUNTS_KEY] = json.encodeToString(updated)
        }
    }

    suspend fun delete(id: String) {
        context.appDataStore.edit { prefs ->
            val current = prefs[ACCOUNTS_KEY]?.let { raw ->
                runCatching { json.decodeFromString<List<KimiAccount>>(raw) }.getOrNull()
            } ?: emptyList()
            prefs[ACCOUNTS_KEY] = json.encodeToString(current.filterNot { it.id == id })
        }
    }

    suspend fun saveSettings(settings: ProxySettings) {
        context.appDataStore.edit { prefs ->
            prefs[SETTINGS_KEY] = json.encodeToString(settings)
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.appDataStore.edit { it[THEME_KEY] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.appDataStore.edit { it[DYNAMIC_KEY] = enabled }
    }
}
