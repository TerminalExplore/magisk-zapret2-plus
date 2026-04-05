package com.zapret2.app.ui.screen

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.topjohnwu.superuser.Shell
import com.zapret2.app.ui.components.FluentCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var wifiEnabled by remember { mutableStateOf(false) }
    var mobileEnabled by remember { mutableStateOf(false) }
    var wifiApps by remember { mutableStateOf(setOf<String>()) }
    var mobileApps by remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(selectedTab, wifiApps, mobileApps) {
        selectedApps = if (selectedTab == 0) wifiApps else mobileApps
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            isLoading = true
            val pm = context.packageManager
            
            val apps = mutableListOf<AppInfo>()
            
            try {
                val installedPackages = pm.getInstalledApplications(
                    PackageManager.GET_META_DATA or PackageManager.GET_SHARED_LIBRARY_FILES
                )
                
                for (appInfo in installedPackages) {
                    if (appInfo.packageName.startsWith("com.android.") ||
                        appInfo.packageName.startsWith("com.google.android.input") ||
                        appInfo.packageName.startsWith("com.android.launcher") ||
                        appInfo.packageName.startsWith("com.android.systemui") ||
                        appInfo.packageName == "com.zapret2.app") {
                        continue
                    }
                    
                    try {
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val icon = try {
                            pm.getApplicationIcon(appInfo.packageName)
                        } catch (e: Exception) {
                            null
                        }
                        apps.add(AppInfo(appInfo.packageName, appName, icon))
                    } catch (e: Exception) {
                        apps.add(AppInfo(appInfo.packageName, appInfo.packageName, null))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            installedApps = apps.sortedBy { it.appName.lowercase() }
            
            loadSettings(
                onWifiEnabledLoaded = { wifiEnabled = it },
                onMobileEnabledLoaded = { mobileEnabled = it },
                onWifiAppsLoaded = { wifiApps = it },
                onMobileAppsLoaded = { mobileApps = it }
            )
            isLoading = false
        }
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("App Filter") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("WiFi") },
                icon = { Icon(Icons.Default.Wifi, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Mobile") },
                icon = { Icon(Icons.Default.SignalCellular4Bar, contentDescription = null) }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    val enabled = if (selectedTab == 0) wifiEnabled else mobileEnabled
                    val onEnabledChange: (Boolean) -> Unit = if (selectedTab == 0) {
                        { value: Boolean -> wifiEnabled = value; saveWifiEnabled(value) }
                    } else {
                        { value: Boolean -> mobileEnabled = value; saveMobileEnabled(value) }
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
                                        text = "App Filter ${if (selectedTab == 0) "WiFi" else "Mobile"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (enabled) "Enabled" else "Disabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (enabled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = onEnabledChange
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = "Selected: ${selectedApps.size} apps",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                items(filteredApps) { app ->
                    val isSelected = selectedApps.contains(app.packageName)
                    val onToggle = { selected: Boolean ->
                        val newSet = if (selected) {
                            selectedApps + app.packageName
                        } else {
                            selectedApps - app.packageName
                        }
                        selectedApps = newSet
                        
                        if (selectedTab == 0) {
                            wifiApps = newSet
                            saveWifiApps(newSet)
                        } else {
                            mobileApps = newSet
                            saveMobileApps(newSet)
                        }
                    }

                    AppFilterItem(
                        app = app,
                        isSelected = isSelected,
                        onToggle = onToggle
                    )
                }

                if (filteredApps.isEmpty() && searchQuery.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No apps found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppFilterItem(
    app: AppInfo,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    FluentCard(
        modifier = Modifier.clickable { onToggle(!isSelected) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (app.icon != null) {
                    Image(
                        bitmap = app.icon.toBitmap(48, 48).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Android,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle(it) }
            )
        }
    }
}

private suspend fun loadSettings(
    onWifiEnabledLoaded: (Boolean) -> Unit,
    onMobileEnabledLoaded: (Boolean) -> Unit,
    onWifiAppsLoaded: (Set<String>) -> Unit,
    onMobileAppsLoaded: (Set<String>) -> Unit
) {
    withContext(Dispatchers.IO) {
        val result = Shell.cmd(
            "cat /data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
        ).exec()

        if (result.isSuccess) {
            val content = result.out.joinToString("\n")
            
            val wifiEnabledVal = content.contains("WIFI_APP_FILTER=1")
            val mobileEnabledVal = content.contains("MOBILE_APP_FILTER=1")
            
            val wifiAppsRegex = Regex("WIFI_APPS=\"([^\"]*)\"").find(content)
            val mobileAppsRegex = Regex("MOBILE_APPS=\"([^\"]*)\"").find(content)
            
            val wifiAppsVal = wifiAppsRegex?.groupValues?.get(1)
                ?.split(" ")
                ?.filter { app -> app.isNotBlank() }
                ?.toSet() ?: emptySet()
            
            val mobileAppsVal = mobileAppsRegex?.groupValues?.get(1)
                ?.split(" ")
                ?.filter { app -> app.isNotBlank() }
                ?.toSet() ?: emptySet()
            
            onWifiEnabledLoaded(wifiEnabledVal)
            onMobileEnabledLoaded(mobileEnabledVal)
            onWifiAppsLoaded(wifiAppsVal)
            onMobileAppsLoaded(mobileAppsVal)
        }
    }
}

private fun saveWifiEnabled(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/WIFI_APP_FILTER=[01]/WIFI_APP_FILTER=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()
}

private fun saveMobileEnabled(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/MOBILE_APP_FILTER=[01]/MOBILE_APP_FILTER=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()
}

private fun saveWifiApps(apps: Set<String>) {
    val appsStr = apps.joinToString(" ")
    Shell.cmd(
        "sed -i 's/WIFI_APPS=\"[^\"]*\"/WIFI_APPS=\"$appsStr\"/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()
}

private fun saveMobileApps(apps: Set<String>) {
    val appsStr = apps.joinToString(" ")
    Shell.cmd(
        "sed -i 's/MOBILE_APPS=\"[^\"]*\"/MOBILE_APPS=\"$appsStr\"/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null"
    ).exec()
}
