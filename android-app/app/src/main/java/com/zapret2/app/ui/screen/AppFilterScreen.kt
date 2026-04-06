package com.zapret2.app.ui.screen

import android.content.Intent
import android.content.pm.ApplicationInfo
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
    val isSystem: Boolean = false
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
    var wifiApps by remember { mutableStateOf(emptySet<String>()) }
    var mobileApps by remember { mutableStateOf(emptySet<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var installedApps by remember { mutableStateOf(listOf<AppInfo>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedApps by remember { mutableStateOf(emptySet<String>()) }

    // Load apps on first composition
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager

                // queryIntentActivities с CATEGORY_LAUNCHER гарантированно возвращает
                // все приложения видимые в лаунчере, включая пользовательские
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val apps = pm.queryIntentActivities(launcherIntent, 0)
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { app -> app.packageName != "com.zapret2.app" }
                    .map { app ->
                        try {
                            AppInfo(
                                packageName = app.packageName,
                                appName = pm.getApplicationLabel(app).toString(),
                                icon = try { pm.getApplicationIcon(app.packageName) } catch (_: Exception) { null },
                                isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            )
                        } catch (_: Exception) {
                            AppInfo(app.packageName, app.packageName, null, true)
                        }
                    }
                    .sortedWith(compareBy({ it.isSystem }, { it.appName.lowercase() }))

                withContext(Dispatchers.Main) {
                    installedApps = apps
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    // Load settings
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            loadSettings(
                onWifiEnabledLoaded = { wifiEnabled = it },
                onMobileEnabledLoaded = { mobileEnabled = it },
                onWifiAppsLoaded = { wifiApps = it },
                onMobileAppsLoaded = { mobileApps = it }
            )
        }
    }

    // Update selectedApps when tab changes
    LaunchedEffect(selectedTab) {
        selectedApps = if (selectedTab == 0) wifiApps else mobileApps
    }

    // Filter apps by search
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
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                        text = "${selectedApps.size} apps selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Switch(
                                    checked = enabled,
                                    onCheckedChange = onEnabledChange
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "${filteredApps.size} apps available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(filteredApps, key = { it.packageName }) { app ->
                    val isSelected = selectedApps.contains(app.packageName)
                    val onToggle: (Boolean) -> Unit = { selected ->
                        val newSet = if (selected) {
                            selectedApps + app.packageName
                        } else {
                            selectedApps - app.packageName
                        }
                        selectedApps = newSet
                        
                        if (selectedTab == 0) {
                            wifiApps = newSet
                            scope.launch(Dispatchers.IO) { saveWifiApps(newSet) }
                        } else {
                            mobileApps = newSet
                            scope.launch(Dispatchers.IO) { saveMobileApps(newSet) }
                        }
                    }

                    AppFilterItem(
                        app = app,
                        isSelected = isSelected,
                        onToggle = onToggle
                    )
                }

                if (filteredApps.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (searchQuery.isNotEmpty()) "No apps found" else "No apps installed",
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
                
                Column(modifier = Modifier.weight(1f)) {
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
        try {
            val result = Shell.cmd(
                "cat /data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null || echo ''"
            ).exec()

            val content = if (result.isSuccess) result.out.joinToString("\n") else ""
            
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
        } catch (e: Exception) {
            e.printStackTrace()
            onWifiEnabledLoaded(false)
            onMobileEnabledLoaded(false)
            onWifiAppsLoaded(emptySet())
            onMobileAppsLoaded(emptySet())
        }
    }
}

private fun saveWifiEnabled(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/WIFI_APP_FILTER=[01]/WIFI_APP_FILTER=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null || " +
        "echo 'WIFI_APP_FILTER=${if (enabled) 1 else 0}' >> /data/adb/modules/zapret2/zapret2/app-filter.ini"
    ).exec()
}

private fun saveMobileEnabled(enabled: Boolean) {
    Shell.cmd(
        "sed -i 's/MOBILE_APP_FILTER=[01]/MOBILE_APP_FILTER=${if (enabled) 1 else 0}/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null || " +
        "echo 'MOBILE_APP_FILTER=${if (enabled) 1 else 0}' >> /data/adb/modules/zapret2/zapret2/app-filter.ini"
    ).exec()
}

private fun saveWifiApps(apps: Set<String>) {
    val appsStr = apps.joinToString(" ")
    Shell.cmd(
        "sed -i 's/WIFI_APPS=\"[^\"]*\"/WIFI_APPS=\"$appsStr\"/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null || " +
        "echo 'WIFI_APPS=\"$appsStr\"' >> /data/adb/modules/zapret2/zapret2/app-filter.ini"
    ).exec()
}

private fun saveMobileApps(apps: Set<String>) {
    val appsStr = apps.joinToString(" ")
    Shell.cmd(
        "sed -i 's/MOBILE_APPS=\"[^\"]*\"/MOBILE_APPS=\"$appsStr\"/' " +
        "/data/adb/modules/zapret2/zapret2/app-filter.ini 2>/dev/null || " +
        "echo 'MOBILE_APPS=\"$appsStr\"' >> /data/adb/modules/zapret2/zapret2/app-filter.ini"
    ).exec()
}
