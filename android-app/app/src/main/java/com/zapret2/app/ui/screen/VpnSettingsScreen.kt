package com.zapret2.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import com.zapret2.app.ui.components.FluentCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VpnSettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var vpnEnabled by remember { mutableStateOf(false) }
    var subscriptionUrl by remember { mutableStateOf("") }
    var vlessConfig by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var vpnStatus by remember { mutableStateOf("Stopped") }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            loadVpnSettings(
                onVpnEnabledLoaded = { vpnEnabled = it },
                onSubscriptionLoaded = { subscriptionUrl = it }
            )
            checkVpnStatus { vpnStatus = it }
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
            text = "VPN Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
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
                            text = "VPN Enabled",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Enable VPN for mobile data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vpnEnabled,
                        onCheckedChange = { enabled ->
                            vpnEnabled = enabled
                            setVpnEnabled(enabled)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = vpnStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vpnStatus == "Running")
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null").exec()
                                checkVpnStatus { vpnStatus = it }
                            }
                        }) {
                            Icon(Icons.Default.Stop, "Stop", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                Shell.cmd("su -c zapret2-vpn-start 2>/dev/null").exec()
                                checkVpnStatus { vpnStatus = it }
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, "Start", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                Shell.cmd("su -c zapret2-vpn-stop 2>/dev/null").exec()
                                kotlinx.coroutines.delay(500)
                                Shell.cmd("su -c zapret2-vpn-start 2>/dev/null").exec()
                                kotlinx.coroutines.delay(1000)
                                checkVpnStatus { vpnStatus = it }
                            }
                        }) {
                            Icon(Icons.Default.Refresh, "Restart")
                        }
                    }
                }
            }
        }

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Subscription",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = subscriptionUrl,
                    onValueChange = { subscriptionUrl = it },
                    label = { Text("Subscription URL") },
                    placeholder = { Text("https://example.com/sub") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Link, contentDescription = null)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isLoading = true
                            scope.launch(Dispatchers.IO) {
                                val success = importSubscription(subscriptionUrl)
                                isLoading = false
                                statusMessage = if (success) "Imported successfully" else "Import failed"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = subscriptionUrl.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Import")
                    }

                    OutlinedButton(
                        onClick = {
                            isLoading = true
                            scope.launch(Dispatchers.IO) {
                                saveSubscriptionUrl(subscriptionUrl)
                                isLoading = false
                                statusMessage = "Saved"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = subscriptionUrl.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save URL")
                    }
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
                    label = { Text("VLESS URI") },
                    placeholder = { Text("vless://...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    maxLines = 5,
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null)
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        isLoading = true
                        scope.launch(Dispatchers.IO) {
                            val success = applyVlessConfig(vlessConfig)
                            isLoading = false
                            statusMessage = if (success) "Config applied" else "Failed to apply"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = vlessConfig.isNotBlank() && !isLoading
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply VLESS Config")
                }
            }
        }

        if (statusMessage.isNotBlank()) {
            FluentCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (statusMessage.contains("success") || statusMessage.contains("Saved"))
                            Icons.Default.CheckCircle
                        else
                            Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (statusMessage.contains("success") || statusMessage.contains("Saved"))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(statusMessage)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private suspend fun loadVpnSettings(
    onVpnEnabledLoaded: (Boolean) -> Unit,
    onSubscriptionLoaded: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "cat /data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
        ).exec()

        if (result.isSuccess) {
            val content = result.out.joinToString("\n")
            onVpnEnabledLoaded(content.contains("VPN_ENABLED=1"))
            
            val subMatch = Regex("VPN_SUBSCRIPTION_URL=\"(.*)\"").find(content)
            onSubscriptionLoaded(subMatch?.groupValues?.get(1) ?: "")
        }
    }
}

private fun setVpnEnabled(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/VPN_ENABLED=[01]/VPN_ENABLED=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
    ).exec()
}

private fun saveSubscriptionUrl(url: String) {
    Shell.cmd(
        "sed -i 's|^VPN_SUBSCRIPTION_URL=.*|VPN_SUBSCRIPTION_URL=\"$url\"|' " +
        "/data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
    ).exec()
}

private suspend fun importSubscription(url: String): Boolean {
    return withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "su -c '/data/adb/modules/zapret2/zapret2/scripts/subscription-parser.sh import $url' 2>/dev/null"
        ).exec()
        result.isSuccess
    }
}

private suspend fun applyVlessConfig(config: String): Boolean {
    return withContext(Dispatchers.IO) {
        val escaped = config
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        
        val result = Shell.cmd(
            "echo '$escaped' | su -c 'base64 -d > /data/adb/modules/zapret2/zapret2/vpn-config.json 2>/dev/null' && " +
            "cp /data/adb/modules/zapret2/zapret2/vpn-config.json /data/adb/modules/zapret2/zapret2/xray-config.json 2>/dev/null || true"
        ).exec()
        
        if (!result.isSuccess) {
            Shell.cmd(
                "echo '$config' | su -c '/data/adb/modules/zapret2/zapret2/scripts/subscription-parser.sh vless \"\$1\"' bash \"$config\" 2>/dev/null"
            ).exec()
        }
        
        result.isSuccess
    }
}

private fun checkVpnStatus(callback: (String) -> Unit) {
    val result = Shell.cmd(
        "[ -f /data/adb/modules/zapret2/zapret2/xray.pid ] && " +
        "kill -0 \$(cat /data/adb/modules/zapret2/zapret2/xray.pid) 2>/dev/null && " +
        "echo 'Running' || echo 'Stopped'"
    ).exec()
    
    callback(result.out.joinToString("").trim())
}
