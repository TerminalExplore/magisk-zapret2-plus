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
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var isPinging by remember { mutableStateOf(false) }
    var pingMethod by remember { mutableStateOf("tcp") }
    var pingTimeout by remember { mutableStateOf(3) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            loadVpnSettings(
                onVpnEnabledLoaded = { vpnEnabled = it },
                onSubscriptionLoaded = { subscriptionUrl = it }
            )
            checkVpnStatus { vpnStatus = it }
            loadServers { servers = it }
            loadPingSettings(
                onPingMethodLoaded = { pingMethod = it },
                onPingTimeoutLoaded = { pingTimeout = it }
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
                                Shell.cmd("sh /data/adb/modules/zapret2/zapret2/scripts/vpn-stop.sh 2>&1").exec()
                                checkVpnStatus { vpnStatus = it }
                            }
                        }) {
                            Icon(Icons.Default.Stop, "Stop", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                Shell.cmd("sh /data/adb/modules/zapret2/zapret2/scripts/vpn-start.sh 2>&1").exec()
                                checkVpnStatus { vpnStatus = it }
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, "Start", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                Shell.cmd("sh /data/adb/modules/zapret2/zapret2/scripts/vpn-stop.sh 2>&1").exec()
                                kotlinx.coroutines.delay(500)
                                Shell.cmd("sh /data/adb/modules/zapret2/zapret2/scripts/vpn-start.sh 2>&1").exec()
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
                                if (success) {
                                    loadServers { servers = it }
                                }
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
                                importSubscription(subscriptionUrl)
                                loadServers { servers = it }
                                isLoading = false
                                statusMessage = "Saved and imported"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = subscriptionUrl.isNotBlank() && !isLoading
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save & Apply")
                    }
                }
            }
        }

        if (servers.isNotEmpty()) {
            FluentCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Servers (${servers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = {
                                isPinging = true
                                scope.launch(Dispatchers.IO) {
                                    servers = pingServers(servers)
                                    isPinging = false
                                }
                            },
                            enabled = !isPinging
                        ) {
                            if (isPinging) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text(if (isPinging) "Pinging..." else "Ping All")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    servers.forEachIndexed { index, server ->
                        ServerItem(server = server, index = index)
                        if (index < servers.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ping Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Method", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = pingMethod == "icmp",
                        onClick = { 
                            pingMethod = "icmp"
                            scope.launch(Dispatchers.IO) { savePingMethod("icmp") }
                        },
                        label = { Text("ICMP") }
                    )
                    FilterChip(
                        selected = pingMethod == "tcp",
                        onClick = { 
                            pingMethod = "tcp"
                            scope.launch(Dispatchers.IO) { savePingMethod("tcp") }
                        },
                        label = { Text("TCP") }
                    )
                    FilterChip(
                        selected = pingMethod == "proxy",
                        onClick = { 
                            pingMethod = "proxy"
                            scope.launch(Dispatchers.IO) { savePingMethod("proxy") }
                        },
                        label = { Text("Proxy") }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Timeout", style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (pingTimeout > 1) pingTimeout-- }) {
                            Icon(Icons.Default.Remove, "Decrease")
                        }
                        Text("$pingTimeout s", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { if (pingTimeout < 10) pingTimeout++ }) {
                            Icon(Icons.Default.Add, "Increase")
                        }
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
        val script = "/data/adb/modules/zapret2/zapret2/scripts/subscription-parser.sh"
        val result = Shell.cmd(
            "sh '$script' import '$url' 2>&1"
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
            "echo '$escaped' | base64 -d > /data/adb/modules/zapret2/zapret2/vpn-config.json 2>&1"
        ).exec()
        
        if (result.isSuccess) {
            Shell.cmd("cp /data/adb/modules/zapret2/zapret2/vpn-config.json /data/adb/modules/zapret2/zapret2/xray-config.json 2>&1").exec()
        }
        
        result.isSuccess
    }
}
        
        result.isSuccess
    }
}

private fun checkVpnStatus(callback: (String) -> Unit) {
    val result = Shell.cmd(
        "if [ -f /data/adb/modules/zapret2/zapret2/xray.pid ]; then " +
        "  PID=\$(cat /data/adb/modules/zapret2/zapret2/xray.pid); " +
        "  if kill -0 \$PID 2>/dev/null; then " +
        "    echo 'Running (PID: '\$PID')'; " +
        "  else " +
        "    echo 'Stopped (stale pid)'; " +
        "  fi; " +
        "else " +
        "  echo 'Stopped'; " +
        "fi"
    ).exec()
    
    callback(result.out.joinToString("").trim())
}

data class ServerInfo(
    val name: String,
    val address: String,
    val port: Int,
    val type: String,
    val latency: Int? = null
)

@Composable
private fun ServerItem(server: ServerInfo, index: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.name.ifEmpty { "Server ${index + 1}" },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${server.address}:${server.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ServerLatency(latency = server.latency)
    }
}

@Composable
private fun ServerLatency(latency: Int?) {
    val (color, text) = when {
        latency == null -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "—")
        latency < 100 -> Pair(MaterialTheme.colorScheme.primary, "${latency}ms")
        latency < 300 -> Pair(MaterialTheme.colorScheme.tertiary, "${latency}ms")
        else -> Pair(MaterialTheme.colorScheme.error, "${latency}ms")
    }
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall
    )
}

private suspend fun loadServers(callback: (List<ServerInfo>) -> Unit) {
    withContext(Dispatchers.IO) {
        val servers = mutableListOf<ServerInfo>()
        
        val rawFile = "/data/adb/modules/zapret2/zapret2/vpn-subs-raw.txt"
        val configFile = "/data/adb/modules/zapret2/zapret2/vpn-config.json"
        
        val subsResult = Shell.cmd("cat $rawFile 2>/dev/null").exec()
        if (subsResult.isSuccess) {
            val content = subsResult.out.joinToString("\n")
            content.lines().forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("vless://") -> {
                        val info = parseVlessUri(trimmed)
                        if (info != null) servers.add(info)
                    }
                    trimmed.startsWith("ss://") -> {
                        val info = parseSsUri(trimmed)
                        if (info != null) servers.add(info)
                    }
                }
            }
        }
        
        if (servers.isEmpty()) {
            val configResult = Shell.cmd("cat $configFile 2>/dev/null").exec()
            if (configResult.isSuccess) {
                val content = configResult.out.joinToString("\n")
                val addrMatch = Regex("\"address\"\\s*:\\s*\"([^\"]+)\"").find(content)
                val portMatch = Regex("\"port\"\\s*:\\s*(\\d+)").find(content)
                
                if (addrMatch != null) {
                    servers.add(ServerInfo(
                        name = "Custom Server",
                        address = addrMatch.groupValues[1],
                        port = portMatch?.groupValues?.get(1)?.toIntOrNull() ?: 443,
                        type = "custom"
                    ))
                }
            }
        }
        
        callback(servers)
    }
}

private fun parseVlessUri(uri: String): ServerInfo? {
    return try {
        val withoutProtocol = uri.removePrefix("vless://")
        val atIndex = withoutProtocol.indexOf('@')
        if (atIndex == -1) return null
        
        val rest = withoutProtocol.substring(atIndex + 1)
        val server = rest.substringBefore(':').substringBefore('/').substringBefore('?')
        val portStr = rest.substringAfter(':', "443").substringBefore('/').substringBefore('?')
        val port = portStr.toIntOrNull() ?: 443
        
        val name = uri.substringAfter('#', "").ifEmpty { "VLESS" }
        
        ServerInfo(name = name, address = server, port = port, type = "vless")
    } catch (e: Exception) {
        null
    }
}

private fun parseSsUri(uri: String): ServerInfo? {
    return try {
        val withoutProtocol = uri.removePrefix("ss://")
        val atIndex = withoutProtocol.indexOf('@')
        if (atIndex == -1) return null
        
        val rest = withoutProtocol.substring(atIndex + 1)
        val serverPart = rest.substringBefore('#')
        val server = serverPart.substringBefore(':')
        val port = serverPart.substringAfter(':', "443").toIntOrNull() ?: 443
        
        val name = uri.substringAfter('#', "").ifEmpty { "SS" }
        
        ServerInfo(name = name, address = server, port = port, type = "shadowsocks")
    } catch (e: Exception) {
        null
    }
}

private suspend fun pingServers(servers: List<ServerInfo>): List<ServerInfo> {
    return withContext(Dispatchers.IO) {
        servers.map { server ->
            val latency = pingServer(server.address, server.port)
            server.copy(latency = latency)
        }
    }
}

private fun pingServer(address: String, port: Int): Int? {
    return try {
        val result = Shell.cmd(
            "ping -c 1 -W 2 $address 2>/dev/null | grep 'time=' | sed 's/.*time=\\([0-9.]*\\).*/\\1/' | cut -d. -f1"
        ).exec()
        
        val output = result.out.joinToString("").trim()
        output.toIntOrNull()
    } catch (e: Exception) {
        null
    }
}

private suspend fun loadPingSettings(
    onPingMethodLoaded: (String) -> Unit,
    onPingTimeoutLoaded: (Int) -> Unit
) {
    withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "cat /data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
        ).exec()

        if (result.isSuccess) {
            val content = result.out.joinToString("\n")
            
            val pingMethodMatch = Regex("PING_METHOD=\"([^\"]+)\"").find(content)
            onPingMethodLoaded(pingMethodMatch?.groupValues?.get(1) ?: "tcp")
            
            val pingTimeoutMatch = Regex("PING_TIMEOUT=(\\d+)").find(content)
            onPingTimeoutLoaded(pingTimeoutMatch?.groupValues?.get(1)?.toIntOrNull() ?: 3)
        }
    }
}

private fun savePingMethod(method: String) {
    Shell.cmd(
        "sed -i 's/PING_METHOD=\"[^\"]*\"/PING_METHOD=\"$method\"/' " +
        "/data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
    ).exec()
}
