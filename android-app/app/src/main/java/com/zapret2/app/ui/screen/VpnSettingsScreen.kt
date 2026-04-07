package com.zapret2.app.ui.screen

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import com.zapret2.app.data.VpnConfigManager
import com.zapret2.app.ui.components.FluentCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

@Composable
fun VpnSettingsScreen(
    navController: NavController
) {
    val vpnConfigManager = remember { VpnConfigManager() }
    val scope = rememberCoroutineScope()
    var vpnEnabled by remember { mutableStateOf(false) }
    var vpnAutostart by remember { mutableStateOf(true) }
    var killSwitch by remember { mutableStateOf(false) }
    var subscriptionUrl by remember { mutableStateOf("") }
    var vlessConfig by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var vpnStatus by remember { mutableStateOf("Stopped") }
    var vpnDetail by remember { mutableStateOf("Ready to start") }
    var servers by remember { mutableStateOf<List<ServerInfo>>(emptyList()) }
    var selectedServerUri by remember { mutableStateOf<String?>(null) }
    var applyingServerUri by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }
    var pingMethod by remember { mutableStateOf("tcp") }
    var pingTimeout by remember { mutableStateOf(3) }
    var autoSelectFastest by remember { mutableStateOf(false) }
    var vpnLog by remember { mutableStateOf("No log entries") }
    var subscriptionLog by remember { mutableStateOf("No log entries") }
    var verboseLogs by remember { mutableStateOf(false) }
    var externalIp by remember { mutableStateOf<IpInfo?>(null) }
    var ipLoading by remember { mutableStateOf(false) }
    var autoSwitchEnabled by remember { mutableStateOf(false) }
    var vpnMode by remember { mutableStateOf("off") }

    suspend fun refreshStatusAndLogs() {
        val runtime = vpnConfigManager.getRuntimeStatus()
        vpnStatus = runtime.summary
        vpnDetail = runtime.detail
        selectedServerUri = runtime.selectedServerUri ?: selectedServerUri

        val logs = vpnConfigManager.getRecentLogs(24, verboseLogs)
        vpnLog = logs.vpnLog
        subscriptionLog = logs.subscriptionLog
    }

    suspend fun refreshAll() {
        val settings = vpnConfigManager.loadSettings()
        vpnEnabled = settings.vpnEnabled
        vpnAutostart = settings.vpnAutostart
        killSwitch = settings.killSwitch
        subscriptionUrl = settings.subscriptionUrl
        selectedServerUri = settings.selectedServerUri
        pingMethod = settings.pingMethod
        pingTimeout = settings.pingTimeout
        autoSelectFastest = settings.autoSelectFastest
        autoSwitchEnabled = loadAutoSwitch()
        vpnMode = loadVpnMode()
        refreshStatusAndLogs()
        loadServers(selectedServerUri) { servers = it }
    }

    fun showOperationResult(result: VpnConfigManager.OperationResult) {
        statusMessage = result.message.ifBlank { if (result.success) "Done" else "Failed" }
        statusIsError = !result.success
    }

    LaunchedEffect(Unit) {
        isLoading = true
        refreshAll()
        isLoading = false
        // Автоматически получаем IP при открытии экрана
        ipLoading = true
        externalIp = fetchExternalIp()
        ipLoading = false
    }

    // Обновляем IP когда VPN запускается/останавливается
    LaunchedEffect(vpnStatus) {
        if (externalIp != null) {
            ipLoading = true
            externalIp = fetchExternalIp()
            ipLoading = false
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

        // External IP card
        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("External IP", style = MaterialTheme.typography.titleMedium)
                        when {
                            ipLoading -> Text(
                                "Checking...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            externalIp != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    externalIp!!.countryFlag,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.width(6.dp))
                                SelectionContainer {
                                    Text(
                                        externalIp!!.ip,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (vpnStatus.startsWith("Running"))
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (externalIp!!.country.isNotEmpty()) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        externalIp!!.country,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> Text(
                                "Not checked",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            ipLoading = true
                            scope.launch {
                                externalIp = fetchExternalIp()
                                ipLoading = false
                            }
                        },
                        enabled = !ipLoading
                    ) {
                        if (ipLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text("Check")
                    }
                }
            }
        }

        // VPN Mode card
        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "VPN Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "When to use VPN vs Zapret2 DPI bypass",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                val modes = listOf(
                    Triple("off",    "🚫", "Off — Zapret2 only"),
                    Triple("mobile", "📱", "Mobile → VPN, WiFi → Zapret2"),
                    Triple("wifi",   "📶", "WiFi → VPN, Mobile → Zapret2"),
                    Triple("always", "🔒", "Always VPN (WiFi + Mobile)")
                )
                modes.forEach { (value, emoji, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = vpnMode == value,
                            onClick = {
                                vpnMode = value
                                scope.launch { saveVpnMode(value) }
                            },
                            enabled = value == "off" || vpnEnabled
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$emoji  $label",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (value != "off" && !vpnEnabled)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (!vpnEnabled && vpnMode != "off") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Enable VPN above to use this mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

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
                            scope.launch {
                                val saved = vpnConfigManager.setVpnEnabled(enabled)
                                if (saved) {
                                    vpnEnabled = enabled
                                    statusMessage = if (enabled) "VPN enabled" else "VPN disabled"
                                    statusIsError = false
                                    refreshStatusAndLogs()
                                } else {
                                    statusMessage = "Failed to update VPN_ENABLED"
                                    statusIsError = true
                                }
                            }
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
                            color = if (vpnStatus.startsWith("Running"))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = vpnDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                showOperationResult(vpnConfigManager.stopVpn())
                                refreshStatusAndLogs()
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Default.Stop, "Stop", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                showOperationResult(vpnConfigManager.startVpn())
                                refreshStatusAndLogs()
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Default.PlayArrow, "Start", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                showOperationResult(vpnConfigManager.restartVpn())
                                refreshStatusAndLogs()
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Default.Refresh, "Restart")
                        }
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                refreshStatusAndLogs()
                                statusMessage = "Status refreshed"
                                statusIsError = false
                                isLoading = false
                            }
                        }) {
                            Icon(Icons.Default.Sync, "Refresh")
                        }
                    }
                }
            }
        }

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Behavior",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingToggleRow(
                    title = "Autostart on mobile",
                    subtitle = "Allow auto-switch to start VPN automatically",
                    checked = vpnAutostart,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            if (vpnConfigManager.setVpnAutostart(enabled)) {
                                vpnAutostart = enabled
                                statusMessage = if (enabled) "VPN autostart enabled" else "VPN autostart disabled"
                                statusIsError = false
                            } else {
                                statusMessage = "Failed to update VPN_AUTOSTART"
                                statusIsError = true
                            }
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                SettingToggleRow(
                    title = "Kill switch",
                    subtitle = "Reserved setting for stricter VPN fallback behavior",
                    checked = killSwitch,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            if (vpnConfigManager.setKillSwitch(enabled)) {
                                killSwitch = enabled
                                statusMessage = if (enabled) "Kill switch enabled" else "Kill switch disabled"
                                statusIsError = false
                            } else {
                                statusMessage = "Failed to update KILL_SWITCH"
                                statusIsError = true
                            }
                        }
                    }
                )
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
                            scope.launch {
                                val result = vpnConfigManager.importSubscription(subscriptionUrl)
                                isLoading = false
                                showOperationResult(result)
                                if (result.success) {
                                    refreshAll()
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
                            scope.launch {
                                val saved = saveSubscriptionUrl(vpnConfigManager, subscriptionUrl)
                                if (!saved) {
                                    statusMessage = "Failed to save subscription URL"
                                    statusIsError = true
                                } else {
                                    val imported = vpnConfigManager.importSubscription(subscriptionUrl)
                                    showOperationResult(
                                        if (imported.success) {
                                            imported.copy(message = "Saved and imported")
                                        } else {
                                            imported
                                        }
                                    )
                                    if (imported.success) {
                                        refreshAll()
                                    }
                                }
                                isLoading = false
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
                                scope.launch {
                                    val pingedServers = pingServers(servers, pingMethod, pingTimeout)
                                    servers = pingedServers
                                    if (autoSelectFastest) {
                                        val fastest = pingedServers
                                            .filter { it.rawConfig != null && it.latency != null }
                                            .minByOrNull { it.latency ?: Int.MAX_VALUE }

                                        if (fastest?.rawConfig != null && !fastest.isSelected) {
                                            val result = vpnConfigManager.applySelectedServer(fastest.rawConfig)
                                            showOperationResult(
                                                if (result.success) {
                                                    result.copy(message = "Fastest server applied: ${fastest.name}")
                                                } else {
                                                    result
                                                }
                                            )
                                            if (result.success) {
                                                refreshAll()
                                            }
                                        } else {
                                            statusMessage = "Ping completed"
                                            statusIsError = false
                                        }
                                    } else {
                                        statusMessage = "Ping completed"
                                        statusIsError = false
                                    }
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
                        ServerItem(
                            server = server,
                            index = index,
                            isApplying = applyingServerUri == server.rawConfig,
                            onApply = if (server.rawConfig != null && !server.isSelected) {
                                {
                                    applyingServerUri = server.rawConfig
                                    scope.launch {
                                        val result = vpnConfigManager.applySelectedServer(server.rawConfig)
                                        showOperationResult(
                                            if (result.success) {
                                                result.copy(message = "Server applied: ${server.name}")
                                            } else {
                                                result
                                            }
                                        )
                                        if (result.success) {
                                            refreshAll()
                                        }
                                        applyingServerUri = null
                                    }
                                }
                            } else null
                        )
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
                            scope.launch {
                                if (vpnConfigManager.setPingMethod("icmp")) {
                                    pingMethod = "icmp"
                                }
                            }
                        },
                        label = { Text("ICMP") }
                    )
                    FilterChip(
                        selected = pingMethod == "tcp",
                        onClick = {
                            scope.launch {
                                if (vpnConfigManager.setPingMethod("tcp")) {
                                    pingMethod = "tcp"
                                }
                            }
                        },
                        label = { Text("TCP") }
                    )
                    FilterChip(
                        selected = pingMethod == "proxy",
                        onClick = {
                            scope.launch {
                                if (vpnConfigManager.setPingMethod("proxy")) {
                                    pingMethod = "proxy"
                                }
                            }
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
                        IconButton(onClick = {
                            if (pingTimeout > 1) {
                                val newTimeout = pingTimeout - 1
                                scope.launch {
                                    if (vpnConfigManager.setPingTimeout(newTimeout)) {
                                        pingTimeout = newTimeout
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Remove, "Decrease")
                        }
                        Text("$pingTimeout s", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = {
                            if (pingTimeout < 10) {
                                val newTimeout = pingTimeout + 1
                                scope.launch {
                                    if (vpnConfigManager.setPingTimeout(newTimeout)) {
                                        pingTimeout = newTimeout
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Add, "Increase")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                SettingToggleRow(
                    title = "Auto-select fastest server",
                    subtitle = "After Ping All, automatically apply the lowest-latency server",
                    checked = autoSelectFastest,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            if (vpnConfigManager.setAutoSelectFastest(enabled)) {
                                autoSelectFastest = enabled
                            }
                        }
                    }
                )
            }
        }

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Manual Config",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = vlessConfig,
                    onValueChange = { vlessConfig = it },
                    label = { Text("URI or JSON") },
                    placeholder = { Text("vless://... / vmess://... / ss://... / trojan://... / {json}") },
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
                        scope.launch {
                            val trimmed = vlessConfig.trim()
                            val result = if (
                                trimmed.startsWith("vless://", ignoreCase = true) ||
                                trimmed.startsWith("vmess://", ignoreCase = true) ||
                                trimmed.startsWith("ss://", ignoreCase = true) ||
                                trimmed.startsWith("trojan://", ignoreCase = true)
                            ) {
                                vpnConfigManager.applySelectedServer(trimmed)
                            } else {
                                vpnConfigManager.applyConfigInput(trimmed)
                            }
                            isLoading = false
                            showOperationResult(result)
                            if (result.success) {
                                refreshAll()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = vlessConfig.isNotBlank() && !isLoading
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Apply Config")
                }
            }
        }

        FluentCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                refreshStatusAndLogs()
                                statusMessage = "Logs refreshed"
                                statusIsError = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("VPN log", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Verbose", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(4.dp))
                        Switch(checked = verboseLogs, onCheckedChange = {
                            verboseLogs = it
                            scope.launch { refreshStatusAndLogs() }
                        })
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LogBlock(text = vpnLog)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Subscription log", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LogBlock(text = subscriptionLog)
            }
        }

        if (statusMessage.isNotBlank()) {
            FluentCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (!statusIsError)
                            Icons.Default.CheckCircle
                        else
                            Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (!statusIsError)
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

private suspend fun saveSubscriptionUrl(vpnConfigManager: VpnConfigManager, url: String): Boolean {
    return vpnConfigManager.saveSubscriptionUrl(url)
}

data class IpInfo(val ip: String, val country: String, val countryCode: String) {
    val countryFlag: String get() = countryCode
        .uppercase()
        .takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
        ?.map { 0x1F1E6 + (it - 'A') }
        ?.joinToString("") { String(Character.toChars(it)) }
        ?: "🌐"
}

private suspend fun fetchExternalIp(): IpInfo {
    return withContext(Dispatchers.IO) {
        // Try ip-api.com first — returns IP + country in one request
        try {
            val conn = URL("http://ip-api.com/json/?fields=query,country,countryCode").openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "curl/7.0")
            val body = conn.inputStream.bufferedReader().readText().trim()
            conn.disconnect()
            val ip = Regex(""""query"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1)
            val country = Regex(""""country"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1) ?: ""
            val code = Regex(""""countryCode"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.getOrNull(1) ?: ""
            if (!ip.isNullOrBlank() && ip.matches(Regex("[\\d.:a-fA-F]+"))) {
                return@withContext IpInfo(ip, country, code)
            }
        } catch (_: Exception) {}

        // Fallback — just IP, no country
        val fallbackEndpoints = listOf("https://api.ipify.org", "https://ifconfig.me/ip", "https://icanhazip.com")
        for (url in fallbackEndpoints) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("User-Agent", "curl/7.0")
                val ip = conn.inputStream.bufferedReader().readText().trim()
                conn.disconnect()
                if (ip.matches(Regex("[\\d.:a-fA-F]+"))) return@withContext IpInfo(ip, "", "")
            } catch (_: Exception) {}
        }
        IpInfo("Failed to get IP", "", "")
    }
}

private suspend fun loadAutoSwitch(): Boolean {
    return withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "grep '^AUTO_SWITCH=' /data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
        ).exec()
        result.out.firstOrNull()?.contains("=1") == true
    }
}

private suspend fun loadVpnMode(): String {
    return withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "grep '^VPN_MODE=' /data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
        ).exec()
        val raw = result.out.firstOrNull()
            ?.substringAfter("=")
            ?.trim()
            ?.trim('"') ?: ""
        // backward compat: if VPN_MODE not set, check AUTO_SWITCH
        if (raw.isEmpty()) {
            val autoSwitch = Shell.cmd(
                "grep '^AUTO_SWITCH=1' /data/adb/modules/zapret2/zapret2/vpn-config.env 2>/dev/null"
            ).exec()
            if (autoSwitch.isSuccess && autoSwitch.out.isNotEmpty()) "mobile" else "off"
        } else raw
    }
}

private suspend fun saveAutoSwitch(enabled: Boolean) {
    withContext(Dispatchers.IO) {
        val path = "/data/adb/modules/zapret2/zapret2/vpn-config.env"
        val value = if (enabled) "1" else "0"
        Shell.cmd(
            "FILE='$path'; TMP=\$(mktemp '${path}.XXXXXX'); " +
            "(grep -v '^AUTO_SWITCH=' \"\$FILE\" 2>/dev/null || true) > \"\$TMP\" && " +
            "printf 'AUTO_SWITCH=$value\\n' >> \"\$TMP\" && " +
            "mv \"\$TMP\" \"\$FILE\" && chmod 644 \"\$FILE\""
        ).exec()
    }
}

private suspend fun saveVpnMode(mode: String) {
    withContext(Dispatchers.IO) {
        val path = "/data/adb/modules/zapret2/zapret2/vpn-config.env"
        // also sync AUTO_SWITCH for backward compat
        val autoSwitch = if (mode == "mobile") "1" else "0"
        Shell.cmd(
            "FILE='$path'; TMP=\$(mktemp '${path}.XXXXXX'); " +
            "(grep -v '^VPN_MODE=' \"\$FILE\" | grep -v '^AUTO_SWITCH=' 2>/dev/null || true) > \"\$TMP\" && " +
            "printf 'VPN_MODE=\"$mode\"\\nAUTO_SWITCH=$autoSwitch\\n' >> \"\$TMP\" && " +
            "mv \"\$TMP\" \"\$FILE\" && chmod 644 \"\$FILE\""
        ).exec()
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LogBlock(text: String) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

data class ServerInfo(
    val name: String,
    val address: String,
    val port: Int,
    val type: String,
    val rawConfig: String? = null,
    val isSelected: Boolean = false,
    val latency: Int? = null
)

@Composable
private fun ServerItem(
    server: ServerInfo,
    index: Int,
    isApplying: Boolean,
    onApply: (() -> Unit)?
) {
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
                text = "${server.type.uppercase()} • ${server.address}:${server.port}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (server.isSelected) {
                AssistChip(
                    onClick = {},
                    label = { Text("Active") }
                )
            }
            ServerLatency(latency = server.latency)
            if (onApply != null) {
                OutlinedButton(
                    onClick = onApply,
                    enabled = !isApplying
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Use")
                    }
                }
            }
        }
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

private suspend fun loadServers(selectedUri: String?, callback: (List<ServerInfo>) -> Unit) {
    withContext(Dispatchers.IO) {
        val servers = mutableListOf<ServerInfo>()

        val rawFile = "/data/adb/modules/zapret2/zapret2/vpn-subs-raw.txt"
        val configFile = "/data/adb/modules/zapret2/zapret2/vpn-config.json"

        val subsResult = Shell.cmd("cat $rawFile 2>/dev/null").exec()
        if (subsResult.isSuccess) {
            val content = subsResult.out.joinToString("\n")
            content.lines().forEach { line ->
                val trimmed = line.trim()
                parseServerUri(trimmed, selectedUri)?.let(servers::add)
            }
        }

        if (servers.isEmpty()) {
            val configResult = Shell.cmd("cat $configFile 2>/dev/null").exec()
            if (configResult.isSuccess) {
                parseCurrentConfig(configResult.out.joinToString("\n"), selectedUri)?.let(servers::add)
            }
        }

        callback(servers.distinctBy { it.rawConfig ?: "${it.type}:${it.address}:${it.port}" })
    }
}

private fun parseServerUri(uri: String, selectedUri: String?): ServerInfo? {
    return when {
        uri.startsWith("vless://", ignoreCase = true) -> parseVlessUri(uri, selectedUri)
        uri.startsWith("vmess://", ignoreCase = true) -> parseVmessUri(uri, selectedUri)
        uri.startsWith("ss://", ignoreCase = true) -> parseSsUri(uri, selectedUri)
        uri.startsWith("trojan://", ignoreCase = true) -> parseTrojanUri(uri, selectedUri)
        else -> null
    }
}

private fun parseVlessUri(uri: String, selectedUri: String?): ServerInfo? {
    return try {
        val withoutProtocol = uri.removePrefix("vless://")
        val atIndex = withoutProtocol.indexOf('@')
        if (atIndex == -1) return null

        val rest = withoutProtocol.substring(atIndex + 1)
        val server = rest.substringBefore(':').substringBefore('/').substringBefore('?')
        val portStr = rest.substringAfter(':', "443").substringBefore('/').substringBefore('?')
        val port = portStr.toIntOrNull() ?: 443

        val name = decodeLabel(uri.substringAfter('#', "").ifEmpty { "VLESS" })

        ServerInfo(
            name = name,
            address = server,
            port = port,
            type = "vless",
            rawConfig = uri,
            isSelected = selectedUri == uri
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseVmessUri(uri: String, selectedUri: String?): ServerInfo? {
    return try {
        val payload = decodeBase64Payload(uri.removePrefix("vmess://")) ?: return null
        val address = Regex(""""add"\s*:\s*"([^"]+)"""").find(payload)?.groupValues?.getOrNull(1) ?: return null
        val port = Regex(""""port"\s*:\s*"?(\\d+)"""").find(payload)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 443
        val name = Regex(""""ps"\s*:\s*"([^"]*)"""").find(payload)?.groupValues?.getOrNull(1)
            ?.let(::decodeLabel)
            ?.ifBlank { null }
            ?: decodeLabel(uri.substringAfter('#', "").ifEmpty { "VMess" })

        ServerInfo(
            name = name,
            address = address,
            port = port,
            type = "vmess",
            rawConfig = uri,
            isSelected = selectedUri == uri
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseSsUri(uri: String, selectedUri: String?): ServerInfo? {
    return try {
        val withoutProtocol = uri.removePrefix("ss://")
        val encodedPart = withoutProtocol.substringBefore('#')
        val decodedWhole = decodeBase64Payload(encodedPart.substringBefore('?').substringBefore('@'))
        val normalized = when {
            '@' in encodedPart -> encodedPart
            decodedWhole != null -> decodedWhole
            else -> return null
        }

        val serverPart = normalized.substringAfter('@').substringBefore('?').substringBefore('#')
        val server = serverPart.substringBefore(':')
        val port = serverPart.substringAfter(':', "443").toIntOrNull() ?: 443
        val name = decodeLabel(uri.substringAfter('#', "").ifEmpty { "SS" })

        ServerInfo(
            name = name,
            address = server,
            port = port,
            type = "shadowsocks",
            rawConfig = uri,
            isSelected = selectedUri == uri
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseTrojanUri(uri: String, selectedUri: String?): ServerInfo? {
    return try {
        val withoutProtocol = uri.removePrefix("trojan://")
        val hostPart = withoutProtocol.substringAfter('@', "").substringBefore('#').substringBefore('?').substringBefore('/')
        if (hostPart.isBlank()) return null

        val server = hostPart.substringBefore(':')
        val port = hostPart.substringAfter(':', "443").toIntOrNull() ?: 443
        val name = decodeLabel(uri.substringAfter('#', "").ifEmpty { "Trojan" })

        ServerInfo(
            name = name,
            address = server,
            port = port,
            type = "trojan",
            rawConfig = uri,
            isSelected = selectedUri == uri
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseCurrentConfig(content: String, selectedUri: String?): ServerInfo? {
    val match = Regex(
        """"tag"\s*:\s*"proxy".*?"protocol"\s*:\s*"([^"]+)".*?"address"\s*:\s*"([^"]+)".*?"port"\s*:\s*(\d+)""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    ).find(content) ?: return null

    return ServerInfo(
        name = "Current server",
        address = match.groupValues[2],
        port = match.groupValues[3].toIntOrNull() ?: 443,
        type = match.groupValues[1],
        rawConfig = null,
        isSelected = selectedUri.isNullOrBlank()
    )
}

private fun decodeBase64Payload(value: String): String? {
    return try {
        val normalized = value.trim()
            .replace('-', '+')
            .replace('_', '/')
            .let {
                when (it.length % 4) {
                    2 -> "$it=="
                    3 -> "$it="
                    else -> it
                }
            }

        String(Base64.decode(normalized, Base64.DEFAULT))
    } catch (_: Exception) {
        null
    }
}

private fun decodeLabel(value: String): String {
    return try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }
}

private suspend fun pingServers(
    servers: List<ServerInfo>,
    method: String,
    timeout: Int
): List<ServerInfo> {
    return withContext(Dispatchers.IO) {
        kotlinx.coroutines.coroutineScope {
            val jobs = servers.map { server ->
                async {
                    val latency = pingServer(server.address, server.port, method, timeout)
                    server.copy(latency = latency)
                }
            }
            jobs.map { it.await() }
                .sortedWith(compareBy<ServerInfo> { it.latency == null }.thenBy { it.latency ?: Int.MAX_VALUE })
        }
    }
}

private fun pingServer(address: String, port: Int, method: String, timeout: Int): Int? {
    return try {
        val timeoutMs = timeout.coerceIn(1, 10) * 1000
        when (method.lowercase()) {
            "icmp" -> {
                // ICMP через shell — единственный способ без root API
                val result = Shell.cmd(
                    "ping -c 1 -W $timeout $address 2>/dev/null | grep -o 'time=[0-9.]*' | grep -o '[0-9.]*'"
                ).exec()
                result.out.joinToString("").trim().toDoubleOrNull()?.toInt()
            }
            else -> {
                // TCP через Kotlin Socket — надёжно, без shell, работает везде
                val start = System.currentTimeMillis()
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(address, port), timeoutMs)
                }
                (System.currentTimeMillis() - start).toInt()
            }
        }
    } catch (_: Exception) {
        null
    }
}
