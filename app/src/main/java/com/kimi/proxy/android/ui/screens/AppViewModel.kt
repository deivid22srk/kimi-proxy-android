package com.kimi.proxy.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kimi.proxy.android.data.AccountRepository
import com.kimi.proxy.android.data.KimiAccount
import com.kimi.proxy.android.data.ProxyApi
import com.kimi.proxy.android.data.ProxySettings
import com.kimi.proxy.android.data.ProxyTestResult
import com.kimi.proxy.android.KimiProxyApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Single shared ViewModel for accounts + proxy state. Owned by the Activity
 * scope via `viewModel()` in each screen.
 */
class AppViewModel : ViewModel() {

    private val container = KimiProxyApp.get().container
    private val repo: AccountRepository = container.repository
    private val api: ProxyApi = container.proxyApi

    val accounts: StateFlow<List<KimiAccount>> = repo.accounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<ProxySettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProxySettings())

    private val _lastCapture = MutableStateFlow<KimiAccount?>(null)
    val lastCapture: StateFlow<KimiAccount?> = _lastCapture

    private val _sendStatus = MutableStateFlow<ProxyTestResult?>(null)
    val sendStatus: StateFlow<ProxyTestResult?> = _sendStatus

    private val _testStatus = MutableStateFlow<ProxyTestResult?>(null)
    val testStatus: StateFlow<ProxyTestResult?> = _testStatus

    fun onAccountCaptured(account: KimiAccount) {
        viewModelScope.launch {
            repo.upsert(account)
            _lastCapture.value = account
            val s = settings.value
            if (s.autoSendOnCapture) {
                pushAccount(account)
            }
        }
    }

    fun pushAccount(account: KimiAccount) {
        viewModelScope.launch {
            val s = settings.value
            val res = api.pushAccount(account, s)
            _sendStatus.value = res
        }
    }

    fun clearSendStatus() { _sendStatus.value = null }

    fun deleteAccount(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun saveSettings(settings: ProxySettings) {
        viewModelScope.launch { repo.saveSettings(settings) }
    }

    fun testProxy() {
        viewModelScope.launch {
            val s = settings.value
            _testStatus.value = ProxyTestResult(false, "Testando…")
            _testStatus.value = api.test(s.baseUrl, s.apiKey)
        }
    }

    fun clearTestStatus() { _testStatus.value = null }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { repo.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repo.setDynamicColor(enabled) }
    }

    fun exportJson(account: KimiAccount): String = api.exportAccountJson(account)
}
