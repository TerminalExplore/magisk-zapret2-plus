package com.zapret2.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.topjohnwu.superuser.Shell
import com.zapret2.app.ui.components.FluentCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AutoSwitchSettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenVpnSettings: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var autoSwitchEnabled by remember { mutableStateOf(false) }
    var vpnEnabled by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            loadSettings(
                onAutoSwitchLoaded = { autoSwitchEnabled = it },
                onVpnEnabledLoaded = { vpnEnabled = it }
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
                    text = "VPN Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Subscription import, server list and manual VPN config now live on the dedicated VPN screen. Auto-switch only controls when VPN is used.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onOpenVpnSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open VPN Screen")
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
    onVpnEnabledLoaded: (Boolean) -> Unit
) {
    withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "cat /data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
        ).exec()

        if (result.isSuccess) {
            val content = result.out.joinToString("\n")
            onAutoSwitchLoaded(content.contains("AUTO_SWITCH=1"))
            onVpnEnabledLoaded(content.contains("VPN_ENABLED=1"))
        }
    }
}

private fun saveAutoSwitchSetting(enabled: Boolean) {
    upsertEnvFlag("AUTO_SWITCH", enabled)
}

private fun saveVpnEnabledSetting(enabled: Boolean) {
    upsertEnvFlag("VPN_ENABLED", enabled)
}

private fun upsertEnvFlag(key: String, enabled: Boolean) {
    val path = "/data/adb/modules/zapret2/zapret2/vpn-config.env"
    val value = if (enabled) "1" else "0"
    Shell.cmd(
        "FILE='$path'; " +
            "TMP=\$(mktemp '/data/adb/modules/zapret2/zapret2/vpn-config.env.XXXXXX'); " +
            "(grep -v '^${key}=' \"\$FILE\" 2>/dev/null || true) > \"\$TMP\" && " +
            "printf '%s\\n' '${key}=$value' >> \"\$TMP\" && " +
            "mv \"\$TMP\" \"\$FILE\" && chmod 644 \"\$FILE\""
    ).exec()
}

private suspend fun getNetworkStatus(): String {
    return withContext(Dispatchers.IO) {
        val networkInfo = Shell.cmd(
            "sh /data/adb/modules/zapret2/zapret2/scripts/network-monitor.sh status 2>/dev/null || " +
                "(" +
                "echo 'Monitor: unknown'; " +
                "echo 'WiFi: ' && ip link show wlan0 2>/dev/null | grep -q 'state UP' && echo 'Connected' || echo 'Disconnected'; " +
                "echo 'Mobile: ' && ip link show rmnet0 2>/dev/null | grep -q 'state UP' && echo 'Connected' || echo 'Disconnected'; " +
                "echo 'Zapret2: ' && [ -f /data/local/tmp/nfqws2.pid ] && kill -0 \$(cat /data/local/tmp/nfqws2.pid) 2>/dev/null && echo 'Running' || echo 'Stopped'; " +
                "echo 'VPN: ' && [ -f /data/adb/modules/zapret2/zapret2/xray.pid ] && kill -0 \$(cat /data/adb/modules/zapret2/zapret2/xray.pid) 2>/dev/null && echo 'Running' || echo 'Stopped'" +
                ")"
        ).exec()
        
        networkInfo.out.joinToString("\n")
    }
}
