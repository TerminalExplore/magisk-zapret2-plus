package com.zapret2.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zapret2.app.ui.theme.*
import com.zapret2.app.viewmodel.HostsEditorViewModel

@Composable
fun HostsEditorScreen(viewModel: HostsEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadHosts()
    }

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("/system/etc/hosts", fontSize = 12.sp, color = TextTertiary)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (state.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(), 
                    color = AccentLightBlue, 
                    trackColor = SurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            OutlinedTextField(
                value = state.content,
                onValueChange = { viewModel.updateContent(it) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = state.actionsEnabled && !state.isLoading,
                textStyle = MonospaceStyle,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentLightBlue, 
                    unfocusedBorderColor = Border, 
                    focusedTextColor = TextPrimary, 
                    unfocusedTextColor = TextPrimary, 
                    cursorColor = AccentLightBlue
                ),
                placeholder = {
                    Text("Loading hosts...", color = TextTertiary)
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.loadHosts() }, 
                    enabled = state.actionsEnabled && !state.isLoading, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reload", color = TextPrimary)
                }
                Button(
                    onClick = { viewModel.saveHosts() }, 
                    enabled = state.actionsEnabled && !state.isLoading, 
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue), 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save", color = TextPrimary)
                }
            }
            
            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Error: ${state.error}",
                    fontSize = 12.sp,
                    color = StatusError
                )
            }
        }
    }
}
