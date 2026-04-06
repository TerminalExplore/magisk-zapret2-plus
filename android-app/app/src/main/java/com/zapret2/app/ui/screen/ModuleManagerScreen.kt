package com.zapret2.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import com.zapret2.app.BuildConfig
import com.zapret2.app.ui.components.FluentCard
import com.zapret2.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleManagerScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var currentVersion by remember { mutableStateOf("Unknown") }
    var moduleStatus by remember { mutableStateOf("Checking...") }
    var isInstalling by remember { mutableStateOf(false) }
    var installProgress by remember { mutableStateOf("") }
    var hasModuleZip by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            // Check current module status
            val modDir = File("/data/adb/modules/zapret2")
            if (modDir.exists()) {
                val propFile = File(modDir, "module.prop")
                if (propFile.exists()) {
                    val content = propFile.readText()
                    val versionMatch = Regex("version=(\\S+)").find(content)
                    currentVersion = versionMatch?.groupValues?.get(1) ?: "Unknown"
                    moduleStatus = "Installed"
                }
            } else {
                currentVersion = "Not installed"
                moduleStatus = "Not installed"
            }
            
            // Check if module ZIP exists in assets
            try {
                context.assets.open("module.zip").close()
                hasModuleZip = true
            } catch (e: Exception) {
                hasModuleZip = false
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Module Manager") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current Status
            FluentCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Module",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = moduleStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = when (moduleStatus) {
                                    "Installed" -> StatusSuccess
                                    "Not installed" -> StatusError
                                    else -> TextSecondary
                                }
                            )
                        }
                        Icon(
                            when (moduleStatus) {
                                "Installed" -> Icons.Default.CheckCircle
                                "Not installed" -> Icons.Default.Cancel
                                else -> Icons.Default.HourglassEmpty
                            },
                            contentDescription = null,
                            tint = when (moduleStatus) {
                                "Installed" -> StatusSuccess
                                "Not installed" -> StatusError
                                else -> TextSecondary
                            },
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Module Version", fontSize = 12.sp, color = TextSecondary)
                            Text(currentVersion, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("App Version", fontSize = 12.sp, color = TextSecondary)
                            Text(BuildConfig.VERSION_NAME, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            
            // Install/Update Section
            FluentCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Module Installation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Install or update the Magisk module directly from the app.",
                        fontSize = 13.sp,
                        color = TextTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isInstalling) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = installProgress,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                isInstalling = true
                                installProgress = "Preparing..."
                                
                                scope.launch(Dispatchers.IO) {
                                    val result = installModule(context) { progress ->
                                        installProgress = progress
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        isInstalling = false
                                        if (result) {
                                            Toast.makeText(context, "Module installed successfully!", Toast.LENGTH_LONG).show()
                                            // Refresh status
                                            val modDir = File("/data/adb/modules/zapret2")
                                            if (modDir.exists()) {
                                                val propFile = File(modDir, "module.prop")
                                                if (propFile.exists()) {
                                                    val content = propFile.readText()
                                                    val versionMatch = Regex("version=(\\S+)").find(content)
                                                    currentVersion = versionMatch?.groupValues?.get(1) ?: "Unknown"
                                                    moduleStatus = "Installed"
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "Installation failed. Check logs.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (moduleStatus == "Installed") "Reinstall Module" else "Install Module")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (moduleStatus == "Installed") 
                                "Reinstall will update the module to the latest version bundled in this app." 
                            else 
                                "Installation requires Magisk. The module will be installed to /data/adb/modules/",
                            fontSize = 11.sp,
                            color = TextTertiary
                        )
                    }
                }
            }
            
            // Advanced Options
            FluentCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Advanced",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Uninstall Module
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val result = Shell.cmd(
                                    "rm -rf /data/adb/modules/zapret2 2>&1"
                                ).exec()
                                
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        Toast.makeText(context, "Module uninstalled", Toast.LENGTH_SHORT).show()
                                        moduleStatus = "Not installed"
                                        currentVersion = "Not installed"
                                    } else {
                                        Toast.makeText(context, "Failed to uninstall", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Uninstall Module")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Restart Magisk
                    OutlinedButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                Shell.cmd("magisk --stop 2>&1").exec()
                                Shell.cmd("magisk --start 2>&1").exec()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Magisk restarting...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Restart Magisk")
                    }
                }
            }
        }
    }
}

private suspend fun installModule(context: android.content.Context, onProgress: (String) -> Unit): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            onProgress("Extracting module...")
            
            // Check if module ZIP is in assets
            val moduleZip: File
            try {
                moduleZip = File(context.cacheDir, "module.zip")
                context.assets.open("module.zip").use { input ->
                    FileOutputStream(moduleZip).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // No bundled ZIP, try to use the update mechanism
                onProgress("No bundled module ZIP found")
                return@withContext false
            }
            
            onProgress("Installing module...")
            
            // Extract to /data/adb/modules/zapret2
            val modDir = File("/data/adb/modules/zapret2")
            
            // Remove existing module
            Shell.cmd("rm -rf $modDir 2>/dev/null").exec()
            
            // Create module directory
            Shell.cmd("mkdir -p $modDir").exec()
            
            // Extract using unzip
            val result = Shell.cmd(
                "unzip -o '${moduleZip.absolutePath}' -d $modDir 2>&1"
            ).exec()
            
            if (!result.isSuccess) {
                onProgress("Extraction failed: ${result.out.joinToString()}")
                return@withContext false
            }
            
            // Set permissions
            Shell.cmd("chmod -R 755 $modDir 2>/dev/null").exec()
            
            // Clean up
            moduleZip.delete()
            
            onProgress("Module installed!")
            return@withContext true
        } catch (e: Exception) {
            onProgress("Error: ${e.message}")
            return@withContext false
        }
    }
}
