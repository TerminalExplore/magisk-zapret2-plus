package com.zapret2.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import com.zapret2.app.ui.components.FluentCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AutoSwitchSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoSwitchEnabled by remember { mutableStateOf(false) }
    var vpnEnabled by remember { mutableStateOf(false) }
    var subscriptionUrl by remember { mutableStateOf("") }
    var vlessConfig by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            loadSettings(
                onAutoSwitchLoaded = { autoSwitchEnabled = it },
                onVpnEnabledLoaded = { vpnEnabled = it },
                onSubscriptionLoaded = { subscriptionUrl = it }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Auto-Switch Mode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Automatically switch between Zapret2 (WiFi) and VPN (Mobile)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Auto-Switch",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (autoSwitchEnabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (autoSwitchEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSwitchEnabled,
                        onCheckedChange = { enabled ->
                            autoSwitchEnabled = enabled
                            saveAutoSwitchSetting(enabled)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VPN for Mobile",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (vpnEnabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vpnEnabled)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vpnEnabled,
                        onCheckedChange = { enabled ->
                            vpnEnabled = enabled
                            saveVpnEnabledSetting(enabled)
                        }
                    )
                }
            }
        }

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "VPN Subscription",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = subscriptionUrl,
                    onValueChange = { subscriptionUrl = it },
                    label = { Text("Subscription URL") },
                    placeholder = { Text("https://example.com/subscription") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        isLoading = true
                        statusMessage = "Importing subscription..."
                        scope.launch(Dispatchers.IO) {
                            val success = importSubscription(subscriptionUrl)
                            isLoading = false
                            statusMessage = if (success) "Subscription imported!" else "Failed to import"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = subscriptionUrl.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Import Subscription")
                }
            }
        }

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Manual VLESS Config",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = vlessConfig,
                    onValueChange = { vlessConfig = it },
                    label = { Text("VLESS URI or JSON") },
                    placeholder = { Text("vless://...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 5
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        isLoading = true
                        statusMessage = "Applying VLESS config..."
                        scope.launch(Dispatchers.IO) {
                            val success = applyVlessConfig(vlessConfig)
                            isLoading = false
                            statusMessage = if (success) "VLESS config applied!" else "Failed to apply"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = vlessConfig.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Apply VLESS Config")
                }
            }
        }

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Current Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            val networkStatus = getNetworkStatus()
                            statusMessage = networkStatus
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check Status")
                }

                if (statusMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private suspend fun loadSettings(
    onAutoSwitchLoaded: (Boolean) -> Unit,
    onVpnEnabledLoaded: (Boolean) -> Unit,
    onSubscriptionLoaded: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "cat /data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
        ).exec()

        if (result.isSuccess) {
            val content = result.out.joinToString("\n")
            onAutoSwitchLoaded(content.contains("AUTO_SWITCH=1"))
            onVpnEnabledLoaded(content.contains("VPN_ENABLED=1"))
            
            val subMatch = Regex("VPN_SUBSCRIPTION_URL=\"(.*)\"").find(content)
            onSubscriptionLoaded(subMatch?.groupValues?.get(1) ?: "")
        }
    }
}

private fun saveAutoSwitchSetting(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/AUTO_SWITCH=[01]/AUTO_SWITCH=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
    ).exec()
}

private fun saveVpnEnabledSetting(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/VPN_ENABLED=[01]/VPN_ENABLED=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
    ).exec()
}

private suspend fun importSubscription(url: String): Boolean {
    return withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "echo 'VPN_SUBSCRIPTION_URL=\"$url\"' >> " +
            "/data/adb/modules/zapret2/zapret2/vpn-config.env && " +
            "sed -i 's/^VPN_SUBSCRIPTION_URL=.*/VPN_SUBSCRIPTION_URL=\"$url\"/' " +
            "/data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
        ).exec()
        result.isSuccess
    }
}

private suspend fun applyVlessConfig(config: String): Boolean {
    return withContext(Dispatchers.IO) {
        val escapedConfig = config
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        
        val result = Shell.cmd(
            "echo '$escapedConfig' | base64 -d > " +
            "/data/adb/modules/zapret2/zapret2/vpn-config.json 2>/dev/null"
        ).exec()
        result.isSuccess
    }
}

private suspend fun getNetworkStatus(): String {
    return withContext(Dispatchers.IO) {
        val networkInfo = Shell.cmd(
            "echo 'WiFi: ' && " +
            "ip link show wlan0 2>/dev/null | grep -q 'state UP' && echo 'Connected' || echo 'Disconnected' && " +
            "echo 'Mobile: ' && " +
            "ip link show rmnet0 2>/dev/null | grep -q 'state UP' && echo 'Connected' || echo 'Disconnected' && " +
            "echo 'Zapret2: ' && " +
            "[ -f /data/adb/modules/zapret2/zapret2/nfqws2.pid ] && kill -0 \$(cat /data/adb/modules/zapret2/zapret2/nfqws2.pid) 2>/dev/null && echo 'Running' || echo 'Stopped' && " +
            "echo 'VPN: ' && " +
            "[ -f /data/adb/modules/zapret2/zapret2/xray.pid ] && kill -0 \$(cat /data/adb/modules/zapret2/zapret2/xray.pid) 2>/dev/null && echo 'Running' || echo 'Stopped'"
        ).exec()
        
        networkInfo.out.joinToString("\n")
    }
}
