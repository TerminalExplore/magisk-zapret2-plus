package com.zapret2.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zapret2.app.data.DnsFilterManager
import com.zapret2.app.ui.theme.AccentPrimary
import com.zapret2.app.ui.theme.TextSecondary
import com.zapret2.app.viewmodel.DnsFilterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsFilterScreen(
    viewModel: DnsFilterViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DnsFilterViewModel.Event.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Per-App DNS") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Header info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Per-App DNS Filter",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Redirect DNS queries from specific apps to custom DNS servers",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Default DNS selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Default DNS",
                    style = MaterialTheme.typography.bodyLarge
                )
                DefaultDnsSelector(
                    selected = state.defaultDns,
                    onSelect = { viewModel.setDefaultDns(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Apps list
            if (state.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.loadingText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.apps) { app ->
                        AppDnsItem(
                            app = app,
                            defaultDns = state.defaultDns,
                            onToggle = { viewModel.toggleApp(app.packageName, it) },
                            onDnsChange = { viewModel.setAppDns(app.packageName, it) }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.checkStatus() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Status")
                }
                OutlinedButton(
                    onClick = { viewModel.flushFilter() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = { viewModel.applyChanges() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
fun AppDnsItem(
    app: DnsFilterManager.AppDnsConfig,
    defaultDns: String,
    onToggle: (Boolean) -> Unit,
    onDnsChange: (String?) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = app.isEnabled,
                    onCheckedChange = onToggle,
                    enabled = app.isInstalled
                )
            }
            
            if (app.isEnabled && app.isInstalled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = AccentPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DnsServerSelector(
                        selected = app.dnsServer ?: defaultDns,
                        onSelect = onDnsChange
                    )
                }
            }
            
            if (!app.isInstalled) {
                Text(
                    text = "Not installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun DnsServerSelector(
    selected: String,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val serverName = DnsFilterManager.DNS_SERVERS.find { it.ip == selected }?.name ?: selected
    
    Column {
        OutlinedButton(
            onClick = { expanded = true }
        ) {
            Text("$selected ($serverName)")
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DnsFilterManager.DNS_SERVERS.forEach { server ->
                DropdownMenuItem(
                    text = { Text("${server.ip} (${server.name})") },
                    onClick = {
                        onSelect(if (server.ip == "system") null else server.ip)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun DefaultDnsSelector(
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val serverName = DnsFilterManager.DNS_SERVERS.find { it.ip == selected }?.name ?: selected
    
    Column {
        OutlinedButton(
            onClick = { expanded = true }
        ) {
            Text("$selected")
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DnsFilterManager.DNS_SERVERS.filter { it.ip != "system" }.forEach { server ->
                DropdownMenuItem(
                    text = { Text("${server.ip} (${server.name})") },
                    onClick = {
                        onSelect(server.ip)
                        expanded = false
                    }
                )
            }
        }
    }
}
