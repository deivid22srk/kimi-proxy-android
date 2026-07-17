package com.kimi.proxy.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kimi.proxy.android.KimiProxyApp
import com.kimi.proxy.android.R
import com.kimi.proxy.android.data.ProxySettings
import com.kimi.proxy.android.data.ProxyTestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm: AppViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val testStatus by vm.testStatus.collectAsState()
    val themeMode by KimiProxyApp.get().container.repository.themeMode.collectAsState(initial = "system")
    val dynamicColor by KimiProxyApp.get().container.repository.dynamicColor.collectAsState(initial = true)

    var url by remember(settings) { mutableStateOf(settings.baseUrl) }
    var apiKey by remember(settings) { mutableStateOf(settings.apiKey) }
    var autoSend by remember(settings) { mutableStateOf(settings.autoSendOnCapture) }
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProxySection(
                url = url, onUrl = { url = it },
                apiKey = apiKey, onApiKey = { apiKey = it },
                showKey = showKey, onToggleShow = { showKey = !showKey },
                autoSend = autoSend, onAutoSend = { autoSend = it },
                testStatus = testStatus,
                onTest = { vm.testProxy() },
                onSave = {
                    vm.saveSettings(
                        ProxySettings(
                            baseUrl = url.trim(),
                            apiKey = apiKey.trim(),
                            autoSendOnCapture = autoSend
                        )
                    )
                }
            )

            AppearanceSection(
                themeMode = themeMode,
                onTheme = { vm.setThemeMode(it) },
                dynamicColor = dynamicColor,
                onDynamic = { vm.setDynamicColor(it) }
            )

            AboutSection()
        }
    }
}

@Composable
private fun ProxySection(
    url: String, onUrl: (String) -> Unit,
    apiKey: String, onApiKey: (String) -> Unit,
    showKey: Boolean, onToggleShow: () -> Unit,
    autoSend: Boolean, onAutoSend: (Boolean) -> Unit,
    testStatus: ProxyTestResult?,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Proxy local", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            OutlinedTextField(
                value = url,
                onValueChange = onUrl,
                label = { Text(stringResource(R.string.settings_proxy_url)) },
                placeholder = { Text(stringResource(R.string.settings_proxy_url_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKey,
                label = { Text(stringResource(R.string.settings_proxy_api_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                visualTransformation = if (showKey) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    androidx.compose.material3.TextButton(onClick = onToggleShow) {
                        Text(if (showKey) "Ocultar" else "Mostrar")
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Enviar token automaticamente ao capturar",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = autoSend, onCheckedChange = onAutoSend)
            }

            TestStatusRow(testStatus)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(R.string.settings_proxy_test)) }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun TestStatusRow(testStatus: ProxyTestResult?) {
    when {
        testStatus == null -> {}
        testStatus.message == "Testando…" -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(testStatus.message, style = MaterialTheme.typography.bodySmall)
        }
        testStatus.ok -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_test_ok), style = MaterialTheme.typography.bodySmall)
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.settings_test_fail, testStatus.message), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(
    themeMode: String,
    onTheme: (String) -> Unit,
    dynamicColor: Boolean,
    onDynamic: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Aparência", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            val options = listOf("system" to "Sistema", "light" to "Claro", "dark" to "Escuro")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { idx, (key, label) ->
                    SegmentedButton(
                        selected = themeMode == key,
                        onClick = { onTheme(key) },
                        shape = SegmentedButtonDefaults.itemShape(idx, options.size)
                    ) { Text(label) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_dynamic_color),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = dynamicColor, onCheckedChange = onDynamic)
            }
        }
    }
}

@Composable
private fun AboutSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.settings_about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Versão 1.0.0 • Material You 3 Expressive",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
